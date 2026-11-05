// Written by Neha Chakraborty, with assistance from
// Claude (Anthropic) via Claude Code.
const assert = require("node:assert/strict");
const test = require("node:test");
const {callableModule, resetState, state, storedFirestore} = require("./support/fakes");
const {assertNoReferenceTo, expectHttpsError} = require("./support/assertions");
const {
  BUCKET,
  EMPTY_BUCKET,
  deleteCircleBeforeWriting,
  freshAuth,
  setupAccounts,
  storedMedia,
} = require("./fixtures/accounts");

test.beforeEach(resetState);

test("deleteAccount rejects anonymous callers and stale sign-ins without writing anything", async (t) => {
  const staleSeconds = Math.floor(Date.now() / 1000) - 6 * 60;
  const signInRequired = [
    "failed-precondition",
    "Sign in again before deleting your account",
    "RECENT_SIGN_IN_REQUIRED",
  ];
  for (const [name, auth, ...expected] of [
    ["no authentication", undefined, "unauthenticated", "Sign in before deleting your account"],
    ["a stale auth_time", {uid: "gone", token: {auth_time: staleSeconds}}, ...signInRequired],
    ["no auth_time", {uid: "gone", token: {}}, ...signInRequired],
    ["a malformed auth_time", {uid: "gone", token: {auth_time: "now"}}, ...signInRequired],
  ]) {
    await t.test(`rejects ${name}`, async () => {
      setupAccounts();
      const before = new Map(state.store);
      const {deleteAccount} = callableModule({firestore: storedFirestore()});

      await expectHttpsError(deleteAccount({auth, data: {}}), ...expected);

      assert.deepEqual(state.store, before);
      assert.equal(state.authUsers.has("gone"), true);
    });
  }
});

test("deleteAccount only deletes the caller's account, whatever uid the request names, and can run again", async () => {
  setupAccounts();
  const {deleteAccount} = callableModule({firestore: storedFirestore()});

  assert.deepEqual(await deleteAccount({auth: freshAuth(), data: {uid: "other"}}), {deleted: true});

  assert.equal(state.store.has("users/gone"), false);
  assert.equal(state.store.has("invitations/created"), false);
  assert.equal(state.store.has("invitations/used"), false);
  assert.equal(state.store.has("invitations/unrelated"), true);
  assert.equal(state.store.has("users/other"), true);
  assert.equal(state.store.has("users/patient"), true);
  assert.deepEqual(state.authUsers, new Set(["other", "patient"]));

  assert.deepEqual(await deleteAccount({auth: freshAuth(), data: {}}), {deleted: true});
});

test("deleteAccount finishes when a concurrent call deletes a circle first", async () => {
  setupAccounts();
  const {deleteAccount} = callableModule({firestore: storedFirestore()});
  deleteCircleBeforeWriting("careCircles/alone/media/photo", "careCircles/alone");

  assert.deepEqual(await deleteAccount({auth: freshAuth(), data: {}}), {deleted: true});

  assert.equal(state.store.has("users/gone"), false);
});

test("deleteAccount splits a large circle over several batches", async () => {
  setupAccounts();
  const count = 900;
  for (let index = 0; index < count; index += 1) {
    state.store.set(`careCircles/shared/media/many-${index}`, storedMedia(`many-${index}`, {
      shownIds: ["gone"],
    }));
  }
  const {deleteAccount} = callableModule({firestore: storedFirestore()});

  await deleteAccount({auth: freshAuth(), data: {}});

  assertNoReferenceTo("gone");
  // the fake refuses a batch above 500 writes: this checks the media are split, not just that they fit
  const manyPerBatch = state.batchWrites
    .map((paths) => paths.filter((documentPath) => documentPath.includes("/media/many-")).length)
    .filter((written) => written > 0);
  assert.equal(manyPerBatch.reduce((sum, written) => sum + written, 0), count);
  assert.ok(manyPerBatch.length > 1, `${manyPerBatch} writes per batch`);
});

test("deleteAccount refreshes the Storage rules of the buckets it goes through", async (t) => {
  await t.test("before deleting any file", async () => {
    setupAccounts();
    const released = [];
    const filesWhenReleased = [];
    state.securityRules.releaseStorageRuleset = async (_ruleset, bucketName) => {
      released.push(bucketName);
      filesWhenReleased.push(state.buckets.get(bucketName).size);
    };
    const {deleteAccount} = callableModule({firestore: storedFirestore()});

    await deleteAccount({auth: freshAuth(), data: {}});

    // not the bucket of the circle they are alone in: it is deleted anyway
    assert.deepEqual(released, [BUCKET]);
    // all 3 files of the shared bucket are still there
    assert.deepEqual(filesWhenReleased, [3]);
  });

  await t.test("without letting a failure stop the deletion", async () => {
    setupAccounts();
    state.securityRules.releaseStorageRuleset = async () => {
      throw new Error("Rules API unavailable");
    };
    t.mock.method(console, "warn", () => {});
    const {deleteAccount} = callableModule({firestore: storedFirestore()});

    await deleteAccount({auth: freshAuth(), data: {}});

    assertNoReferenceTo("gone");
  });
});

test("deleteAccount marks the profile as being deleted before anything else", async () => {
  setupAccounts();
  const {deleteAccount} = callableModule({firestore: storedFirestore()});
  state.failWrite = (action, target) => action === "delete" && target === "invitations/used";
  await assert.rejects(deleteAccount({auth: freshAuth(), data: {}}), {message: "Failed delete invitations/used"});

  assert.equal(state.store.get("users/gone").deletionRequested, true);
});

/** Deletes "gone", set up by setupAccounts, and returns how to read what stays of "shared". */
async function deleteTheSharedCaregiver() {
  const {deleteAccount} = callableModule({firestore: storedFirestore()});
  await deleteAccount({auth: freshAuth(), data: {}});
  return (documentPath) => state.store.get(`careCircles/shared/${documentPath}`);
}

test("deleteAccount leaves no trace of a caregiver and changes nobody else's data", async () => {
  setupAccounts();
  const untouched = [
    "users/other",
    "users/patient",
    "careCircles/unrelated",
    "careCircles/unrelated/media/photo",
    "careCircles/shared/appointments/physio",
    "invitations/unrelated",
  ];
  const before = untouched.map((documentPath) => state.store.get(documentPath));

  await deleteTheSharedCaregiver();

  assertNoReferenceTo("gone");
  assert.deepEqual(untouched.map((documentPath) => state.store.get(documentPath)), before);
  assert.equal(state.buckets.get("amber-other").size, 1);
});

test("deleteAccount removes a caregiver from a circle that stays, with their nicknames", async () => {
  setupAccounts();

  await deleteTheSharedCaregiver();

  const circle = state.store.get("careCircles/shared");
  assert.deepEqual(circle.memberIds, ["other", "patient"]);
  assert.equal(circle.patientId, "patient");
  assert.equal(circle.createdBy, "");
  assert.deepEqual(circle.nicknames, {other: {patient: "Mum"}, patient: {}});
});

test("deleteAccount deletes a caregiver's media with their files, and their stories", async () => {
  setupAccounts();

  const stored = await deleteTheSharedCaregiver();

  assert.equal(stored("media/own-photo"), undefined);
  assert.equal(stored("media/own-memo"), undefined);
  assert.equal(stored("media/own-text"), undefined);
  assert.deepEqual(state.buckets.get(BUCKET), new Set(["careCircles/shared/media/tagged.jpg"]));
  assert.equal(stored("stories/own-story"), undefined);
  assert.deepEqual(stored("stories/other-story").mediaIds, ["tagged"]);
});

test("deleteAccount removes a caregiver from the others' media and appointments", async () => {
  setupAccounts();

  const stored = await deleteTheSharedCaregiver();

  assert.deepEqual(stored("media/tagged").shownIds, ["patient"]);
  assert.deepEqual(stored("media/two-allowed").authorizedIds, ["patient"]);
  assert.deepEqual(stored("appointments/doctor"), {createdBy: "", attendeeIds: ["other"]});
});

test("deleteAccount keeps a media restricted to the deleted user visible to its author only", async () => {
  setupAccounts();

  const stored = await deleteTheSharedCaregiver();

  // an empty list would show it to the whole circle
  assert.deepEqual(stored("media/restricted").authorizedIds, ["other"]);
});

test("deleteAccount deletes the circle and the bucket of a last member", async () => {
  setupAccounts();

  await deleteTheSharedCaregiver();

  assert.deepEqual(
    [...state.store.keys()].filter((key) => key.startsWith("careCircles/alone")),
    [],
  );
  assert.equal(state.buckets.has(EMPTY_BUCKET), false);
});

test("deleteAccount frees the patient slot when the patient is deleted", async () => {
  setupAccounts();
  const {deleteAccount} = callableModule({firestore: storedFirestore()});

  await deleteAccount({auth: freshAuth("patient"), data: {}});

  assertNoReferenceTo("patient");
  const circle = state.store.get("careCircles/shared");
  assert.deepEqual(circle.memberIds, ["gone", "other"]);
  assert.equal(circle.patientId, "");
  assert.equal(circle.createdBy, "gone");
});

test("deleteAccount keeps the circle of a last caregiver while its patient remains", async () => {
  setupAccounts();
  const {nicknames: _nicknames, ...circle} = state.store.get("careCircles/shared");
  state.store.set("careCircles/shared", {...circle, memberIds: ["gone", "patient"]});
  const {deleteAccount} = callableModule({firestore: storedFirestore()});

  await deleteAccount({auth: freshAuth(), data: {}});

  assertNoReferenceTo("gone");
  assert.deepEqual(state.store.get("careCircles/shared").memberIds, ["patient"]);
  assert.equal("nicknames" in state.store.get("careCircles/shared"), false);
  assert.equal(state.buckets.has(BUCKET), true);
});

test("deleteAccount doesn't touch buckets in the emulator", async (t) => {
  setupAccounts();
  let rulesReleases = 0;
  state.securityRules.releaseStorageRuleset = async () => {
    rulesReleases += 1;
  };
  process.env.FUNCTIONS_EMULATOR = "true";
  t.after(() => delete process.env.FUNCTIONS_EMULATOR);
  const {deleteAccount} = callableModule({firestore: storedFirestore()});

  await deleteAccount({auth: freshAuth(), data: {}});

  assert.equal(state.store.has("careCircles/alone"), false);
  assert.equal(state.buckets.has(EMPTY_BUCKET), true);
  assert.equal(state.buckets.get(BUCKET).size, 3);
  assert.equal(rulesReleases, 0);
});

test("deleteAccount finishes the job when retried after any partial failure", async (t) => {
  const failures = [
    ["a Storage file", "deleteFile", "careCircles/shared/media/own-photo.jpg"],
    ["the media documents", "delete", "careCircles/shared/media/own-photo"],
    ["the other members' stories", "update", "careCircles/shared/stories/other-story"],
    ["the remaining media", "update", "careCircles/shared/media/restricted"],
    ["an appointment", "update", "careCircles/shared/appointments/doctor"],
    ["leaving the shared circle", "update", "careCircles/shared"],
    ["the bucket", "deleteBucket", EMPTY_BUCKET],
    ["the last member's circle", "recursiveDelete", "careCircles/alone"],
    ["an invitation", "delete", "invitations/used"],
    ["the profile", "delete", "users/gone"],
    ["the Auth user", "deleteUser", "gone"],
  ];

  for (const [name, failingAction, failingPath] of failures) {
    await t.test(`after failing on ${name}`, async () => {
      setupAccounts();
      const {deleteAccount} = callableModule({firestore: storedFirestore()});
      state.failWrite = (action, target) => action === failingAction && target === failingPath;
      await assert.rejects(
        deleteAccount({auth: freshAuth(), data: {}}),
        {message: `Failed ${failingAction} ${failingPath}`},
      );

      state.failWrite = null;
      await deleteAccount({auth: freshAuth(), data: {}});

      assertNoReferenceTo("gone");
      assert.equal(state.buckets.has(EMPTY_BUCKET), false);
      assert.equal(state.buckets.get(BUCKET).has("careCircles/shared/media/own-photo.jpg"), false);
      assert.deepEqual(state.store.get("careCircles/shared/stories/other-story").mediaIds, ["tagged"]);
    });
  }
});

test("deleteAccount leaves the profile listing only the circles that still list the user", async (t) => {
  // the app can only read a circle that both list: its deletion screen would refuse a retry
  const failures = [
    ["leaving the shared circle", "update", "careCircles/shared"],
    ["the last member's circle", "recursiveDelete", "careCircles/alone"],
    ["the profile", "delete", "users/gone"],
  ];

  for (const [name, failingAction, failingPath] of failures) {
    await t.test(`after failing on ${name}`, async () => {
      setupAccounts();
      const {deleteAccount} = callableModule({firestore: storedFirestore()});
      state.failWrite = (action, target) => action === failingAction && target === failingPath;
      await assert.rejects(deleteAccount({auth: freshAuth(), data: {}}));

      const listing = [...state.store.entries()]
        .filter(([key, data]) => /^careCircles\/[^/]+$/.test(key) && data.memberIds?.includes("gone"))
        .map(([key]) => key.split("/")[1]);
      assert.deepEqual([...state.store.get("users/gone").circleIds].sort(), listing.sort());
    });
  }
});
