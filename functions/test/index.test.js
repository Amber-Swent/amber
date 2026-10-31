// Written by GitHub Copilot.
// Modified by Neha Chakraborty and Copilot, with assistance from
// Claude (Anthropic) via Claude Code.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.
const assert = require("node:assert/strict");
const {createHash} = require("node:crypto");
const Module = require("node:module");
const path = require("node:path");
const test = require("node:test");

class MockHttpsError extends Error {
  constructor(code, message, details) {
    super(message);
    this.code = code;
    this.details = details;
  }
}

const state = {
  documents: new Map(),
  storage: null,
  securityRules: null,
  bucketLocation: "europe-west6",
  circleSnapshots: [],
  circleReadIndex: 0,
  updates: [],
  creates: [],
  linkRequests: [],
  linkStatus: 200,
  store: null,
  buckets: null,
  authUsers: null,
  failWrite: null,
};

// the only HTTP call the functions make: linking a bucket to Firebase Storage
globalThis.fetch = async (url, init) => {
  state.linkRequests.push({url, init});
  // same rule as real fetch: ok only for 2xx
  return {ok: state.linkStatus >= 200 && state.linkStatus < 300, status: state.linkStatus, text: async () => "link refused"};
};

function snapshot(value) {
  return {
    exists: value !== undefined,
    data: () => value,
    get: (field) => value?.[field],
  };
}

function resetState() {
  state.documents = new Map();
  state.updates = [];
  state.creates = [];
  state.linkRequests = [];
  state.linkStatus = 200;
  state.circleSnapshots = [];
  state.circleReadIndex = 0;
  state.store = null;
  state.buckets = null;
  state.authUsers = null;
  state.failWrite = null;
  state.storage = {
    bucket: () => {
      throw new Error("Storage bucket was not configured");
    },
  };
  state.securityRules = {
    getStorageRuleset: async () => {
      throw Object.assign(new Error("Missing ruleset"), {code: "security-rules/not-found"});
    },
    createRulesFileFromSource: (name, source) => ({name, source}),
    createRuleset: async (rulesFile) => ({source: [rulesFile]}),
    releaseStorageRuleset: async () => {},
  };
}

// An in-memory Firestore for deleteAccount, which needs real queries and writes.
function applyField(current, value) {
  if (value?.arrayRemove) return (current ?? []).filter((item) => !value.arrayRemove.includes(item));
  return value;
}

function write(action, documentPath, data) {
  if (state.failWrite?.(action, documentPath)) throw new Error(`Failed ${action} ${documentPath}`);
  if (action === "delete") {
    state.store.delete(documentPath);
    return;
  }
  const current = state.store.get(documentPath);
  if (!current) throw Object.assign(new Error(`No document ${documentPath}`), {code: 5});
  const updated = {...current};
  for (const [field, value] of Object.entries(data)) updated[field] = applyField(current[field], value);
  state.store.set(documentPath, updated);
}

function storedSnapshot(documentPath) {
  return {
    ...snapshot(state.store.get(documentPath)),
    id: documentPath.split("/").at(-1),
    ref: storedDocument(documentPath),
  };
}

function storedCollection(collectionPath, filters = []) {
  return {
    where: (field, operator, value) => storedCollection(
      collectionPath,
      [...filters, (data) => (operator === "==" ? data[field] === value : data[field]?.includes(value))],
    ),
    get: async () => ({
      docs: [...state.store.keys()]
        .filter((key) => key.slice(0, key.lastIndexOf("/")) === collectionPath)
        .filter((key) => filters.every((filter) => filter(state.store.get(key))))
        .map(storedSnapshot),
    }),
  };
}

function storedDocument(documentPath) {
  return {
    path: documentPath,
    collection: (name) => storedCollection(`${documentPath}/${name}`),
    update: async (data) => write("update", documentPath, data),
    delete: async () => write("delete", documentPath),
  };
}

function storedFirestore() {
  return {
    doc: storedDocument,
    collection: storedCollection,
    // like the real one, nothing is written if any write of the batch fails
    batch: () => {
      const writes = [];
      return {
        update: (reference, data) => writes.push(["update", reference.path, data]),
        delete: (reference) => writes.push(["delete", reference.path]),
        commit: async () => {
          const before = new Map(state.store);
          try {
            writes.forEach((entry) => write(...entry));
          } catch (error) {
            state.store = before;
            throw error;
          }
        },
      };
    },
    runTransaction: async (callback) => {
      const writes = [];
      const result = await callback({
        get: async (reference) => storedSnapshot(reference.path),
        update: (reference, data) => writes.push(["update", reference.path, data]),
      });
      writes.forEach((entry) => write(...entry));
      return result;
    },
    recursiveDelete: async (reference) => {
      if (state.failWrite?.("recursiveDelete", reference.path)) throw new Error("Failed recursive delete");
      for (const key of [...state.store.keys()]) {
        if (key === reference.path || key.startsWith(`${reference.path}/`)) state.store.delete(key);
      }
    },
  };
}

function storedBucket(name) {
  const notFound = () => Object.assign(new Error(`No bucket ${name}`), {code: 404});
  return {
    file: (filePath) => ({
      delete: async ({ignoreNotFound}) => {
        if (state.failWrite?.("deleteFile", filePath)) throw new Error(`Failed to delete ${filePath}`);
        if (!state.buckets.get(name)?.delete(filePath) && !ignoreNotFound) throw notFound();
      },
    }),
    deleteFiles: async ({force}) => {
      assert.equal(force, true);
      if (!state.buckets.has(name)) throw notFound();
      state.buckets.get(name).clear();
    },
    delete: async ({ignoreNotFound}) => {
      if (state.failWrite?.("deleteBucket", name)) throw new Error(`Failed to delete ${name}`);
      if (!state.buckets.delete(name) && !ignoreNotFound) throw notFound();
    },
  };
}

function callableModule() {
  const originalLoad = Module._load;
  Module._load = function mockModuleLoad(request, parent, isMain) {
    if (request === "firebase-admin/app") {
      return {
        initializeApp: () => ({
          options: {credential: {getAccessToken: async () => ({access_token: "test-token"})}},
        }),
      };
    }
    if (request === "firebase-admin/auth") {
      return {
        getAuth: () => ({
          deleteUser: async (uid) => {
            if (state.failWrite?.("deleteUser", uid)) throw new Error("Failed to delete the user");
            if (!state.authUsers.delete(uid)) {
              throw Object.assign(new Error("No user"), {code: "auth/user-not-found"});
            }
          },
        }),
      };
    }
    if (request === "firebase-admin/firestore") {
      return {
        FieldValue: {
          arrayUnion: (value) => ({arrayUnion: value}),
          arrayRemove: (...values) => ({arrayRemove: values}),
        },
        getFirestore: () => (state.store ? storedFirestore() : {
          doc: (documentPath) => ({
            path: documentPath,
            get: async () => {
              const circle = state.circleSnapshots[state.circleReadIndex];
              state.circleReadIndex += 1;
              return snapshot(circle);
            },
            update: async (data) => state.updates.push({reference: "circle", data}),
          }),
          runTransaction: async (callback) =>
            callback({
              get: async (reference) => snapshot(state.documents.get(reference.path)),
              update: (reference, data) => state.updates.push({reference: {path: reference.path}, data}),
              create: (reference, data) => state.creates.push({reference: {path: reference.path}, data}),
            }),
        }),
      };
    }
    if (request === "firebase-admin/storage") return {getStorage: () => state.storage};
    if (request === "firebase-admin/security-rules") {
      return {getSecurityRules: () => state.securityRules};
    }
    if (request === "firebase-functions/params") {
      return {defineString: () => ({value: () => state.bucketLocation})};
    }
    if (request === "firebase-functions/v2/https") {
      // the handler comes last, after the options if there are any
      return {HttpsError: MockHttpsError, onCall: (...args) => args.at(-1)};
    }
    if (request === "firebase-functions/v2/firestore") {
      return {onDocumentCreated: (_options, handler) => handler};
    }
    return originalLoad.call(this, request, parent, isMain);
  };

  const modulePath = path.join(__dirname, "..", "index.js");
  delete require.cache[modulePath];
  try {
    return require(modulePath);
  } finally {
    Module._load = originalLoad;
  }
}

function invitation(overrides = {}) {
  return {
    circleId: "circle-1",
    usedBy: null,
    expiresAt: Date.now() + 60_000,
    ...overrides,
  };
}

function validCircle(overrides = {}) {
  return {memberIds: ["caregiver-1"], patientId: "", ...overrides};
}

function setupRedeem(options = {}) {
  const invitationData = "invitationData" in options ? options.invitationData : invitation();
  const circle = "circle" in options ? options.circle : validCircle();
  const user = "user" in options ? options.user : {role: "CAREGIVER", circleIds: []};
  state.documents.set("invitations/code-1", invitationData);
  if (circle !== undefined) state.documents.set("careCircles/circle-1", circle);
  if (user !== undefined) state.documents.set("users/user-1", user);
}

async function expectHttpsError(promise, code, message, reason) {
  await assert.rejects(promise, (error) => error instanceof MockHttpsError
    && error.code === code && error.message === message && error.details?.reason === reason);
}

test.beforeEach(resetState);

test("rejects unauthenticated and malformed redemption requests", async (t) => {
  const {redeemInvitation} = callableModule();
  await t.test("requires authentication", async () => {
    await expectHttpsError(
      redeemInvitation({data: {code: "code-1"}}),
      "unauthenticated",
      "Sign in before redeeming an invitation",
    );
  });

  for (const code of [undefined, "", "has space", "!", "a".repeat(129)]) {
    await t.test(`rejects invalid code ${String(code)}`, async () => {
      await expectHttpsError(
        redeemInvitation({auth: {uid: "user-1"}, data: {code}}),
        "invalid-argument",
        "A valid invitation code is required",
      );
    });
  }
});

test("returns the original result when the same user retries a redeemed invitation without writes", async () => {
  setupRedeem({
    invitationData: invitation({usedBy: "user-1"}),
    user: {role: "CAREGIVER", circleIds: ["circle-1"]},
  });
  const {redeemInvitation} = callableModule();

  assert.deepEqual(
    await redeemInvitation({auth: {uid: "user-1"}, data: {code: "code-1"}}),
    {circleId: "circle-1", role: "CAREGIVER"},
  );
  assert.deepEqual(state.updates, []);
  assert.deepEqual(state.creates, []);
});

test("rejects unavailable, consumed, and structurally invalid invitations", async (t) => {
  const cases = [
    ["missing", undefined, "not-found", "Invitation was not found"],
    ["redeemed by another user", invitation({usedBy: "other-user"}), "failed-precondition",
      "Invitation has already been redeemed", "INVITATION_ALREADY_REDEEMED"],
    ["invitation role", invitation({role: "CAREGIVER"}), "failed-precondition",
      "Invitation must not specify a role", "INVITATION_INVALID"],
    ["expired numeric timestamp", invitation({expiresAt: Date.now() - 1}), "failed-precondition",
      "Invitation has expired", "INVITATION_EXPIRED"],
    ["invalid expiration", invitation({expiresAt: {}}), "failed-precondition",
      "Invitation has expired", "INVITATION_EXPIRED"],
    ["blank circle", invitation({circleId: ""}), "failed-precondition",
      "Invitation has an invalid circle", "CIRCLE_UNAVAILABLE"],
    ["blank circle on retry", invitation({circleId: "", usedBy: "user-1"}), "failed-precondition",
      "Invitation has an invalid circle", "CIRCLE_UNAVAILABLE"],
  ];

  for (const [name, invitationData, code, message, reason] of cases) {
    await t.test(name, async () => {
      setupRedeem({invitationData});
      const {redeemInvitation} = callableModule();
      await expectHttpsError(
        redeemInvitation({auth: {uid: "user-1"}, data: {code: "code-1"}}),
        code,
        message,
        reason,
      );
    });
  }
});

test("rejects invalid circle and user membership states", async (t) => {
  const caregiverProfile = {role: "CAREGIVER", circleIds: []};
  const cases = [
    ["missing circle", undefined, caregiverProfile, "Invitation circle no longer exists",
      "CIRCLE_UNAVAILABLE"],
    ["invalid member ids", validCircle({memberIds: "user-1"}), caregiverProfile,
      "User is already a circle member", "ALREADY_CIRCLE_MEMBER"],
    ["already a member", validCircle({memberIds: ["user-1"]}), caregiverProfile,
      "User is already a circle member", "ALREADY_CIRCLE_MEMBER"],
    ["patient already assigned", validCircle({patientId: "patient-1"}),
      {role: "PATIENT", circleIds: []}, "Circle already has a patient", "PATIENT_SLOT_TAKEN"],
    ["missing profile", validCircle(), undefined, "User profile was not found", "PROFILE_INVALID"],
    ["invalid profile role", validCircle(), {role: "ADMIN", circleIds: []},
      "User profile has an invalid role", "PROFILE_INVALID"],
    ["invalid profile memberships", validCircle(), {role: "CAREGIVER", circleIds: "circle-1"},
      "User profile has invalid circle membership", "PROFILE_INVALID"],
  ];

  for (const [name, circle, user, message, reason, invitationData] of cases) {
    await t.test(name, async () => {
      setupRedeem({circle, user, invitationData: invitationData ?? invitation()});
      const {redeemInvitation} = callableModule();
      await expectHttpsError(
        redeemInvitation({auth: {uid: "user-1"}, data: {code: "code-1"}}),
        "failed-precondition",
        message,
        reason,
      );
    });
  }
});

test("redeems a caregiver invitation for an existing profile atomically", async () => {
  setupRedeem({user: {role: "CAREGIVER", circleIds: []}});
  const {redeemInvitation} = callableModule();

  assert.deepEqual(
    await redeemInvitation({auth: {uid: "user-1"}, data: {code: "code-1"}}),
    {circleId: "circle-1", role: "CAREGIVER"},
  );
  assert.deepEqual(state.updates, [
    {reference: {path: "invitations/code-1"}, data: {usedBy: "user-1"}},
    {
      reference: {path: "careCircles/circle-1"},
      data: {memberIds: {arrayUnion: "user-1"}},
    },
    {
      reference: {path: "users/user-1"},
      data: {circleIds: {arrayUnion: "circle-1"}},
    },
  ]);
  assert.deepEqual(state.creates, []);
});

test("redeems a universal invitation with an existing patient profile atomically", async () => {
  setupRedeem({
    invitationData: invitation({expiresAt: {toMillis: () => Date.now() + 60_000}}),
    user: {role: "PATIENT", circleIds: []},
  });
  const {redeemInvitation} = callableModule();

  assert.deepEqual(
    await redeemInvitation({auth: {uid: "user-1"}, data: {code: "code-1"}}),
    {circleId: "circle-1", role: "PATIENT"},
  );

  assert.deepEqual(state.updates, [
    {reference: {path: "invitations/code-1"}, data: {usedBy: "user-1"}},
    {
      reference: {path: "careCircles/circle-1"},
      data: {memberIds: {arrayUnion: "user-1"}, patientId: "user-1"},
    },
    {
      reference: {path: "users/user-1"},
      data: {circleIds: {arrayUnion: "circle-1"}},
    },
  ]);
  assert.deepEqual(state.creates, []);
});

test("accepts only the invitation code", async (t) => {
  const invalidRequests = [
    ["person", {code: "code-1", person: {firstName: "Ada"}}],
    ["role", {code: "code-1", role: "PATIENT"}],
    ["additional field", {code: "code-1", circleId: "circle-1"}],
  ];

  for (const [name, data] of invalidRequests) {
    await t.test(name, async () => {
      setupRedeem();
      const {redeemInvitation} = callableModule();
      await expectHttpsError(
        redeemInvitation({auth: {uid: "user-1"}, data}),
        "invalid-argument",
        "A valid invitation code is required",
      );
      assert.deepEqual(state.updates, []);
      assert.deepEqual(state.creates, []);
    });
  }
});

function configuredBucket({create, metadata, onSetMetadata, onDelete} = {}) {
  return {
    create: create ?? (async () => {}),
    getMetadata: async () => [metadata ?? {labels: {amber_managed: "true", circle_hash: "unused"}}],
    setMetadata: onSetMetadata ?? (async () => {}),
    delete: onDelete ?? (async () => {}),
  };
}

function provisionSetup(options = {}) {
  const {circle, bucket, securityRules} = options;
  const latestCircle = "latestCircle" in options ? options.latestCircle : circle;
  state.circleSnapshots = [circle, latestCircle];
  state.storage = {bucket: () => bucket};
  if (securityRules) state.securityRules = securityRules;
}

test("provisioning rejects absent project configuration and skips irrelevant documents", async (t) => {
  const savedProject = process.env.GCLOUD_PROJECT;
  delete process.env.GCLOUD_PROJECT;
  const {provisionCareCircleBucket} = callableModule();
  await assert.rejects(
    provisionCareCircleBucket({params: {circleId: "circle-1"}}),
    /GCLOUD_PROJECT is not available/,
  );
  process.env.GCLOUD_PROJECT = savedProject ?? "test-project";

  for (const [name, circle] of [["missing circle", undefined], ["already provisioned", {storageBucket: "bucket"}]]) {
    await t.test(name, async () => {
      resetState();
      provisionSetup({circle, bucket: configuredBucket()});
      const {provisionCareCircleBucket: provision} = callableModule();
      await provision({params: {circleId: "circle-1"}});
      assert.deepEqual(state.updates, []);
    });
  }
});

test("provisioning does nothing in the emulator, which can't create buckets", async () => {
  process.env.GCLOUD_PROJECT = "test-project";
  process.env.FUNCTIONS_EMULATOR = "true";
  resetState();
  const untouchable = () => assert.fail("no real Google API may be called in the emulator");
  // a circle that would be provisioned outside the emulator
  provisionSetup({
    circle: {storageBucket: ""},
    bucket: configuredBucket({create: untouchable, onSetMetadata: untouchable}),
    securityRules: {
      getStorageRuleset: untouchable,
      createRulesFileFromSource: untouchable,
      createRuleset: untouchable,
      releaseStorageRuleset: untouchable,
    },
  });
  const {provisionCareCircleBucket} = callableModule();

  try {
    await provisionCareCircleBucket({params: {circleId: "circle-1"}});
  } finally {
    delete process.env.FUNCTIONS_EMULATOR;
  }

  assert.equal(state.circleReadIndex, 0);
  assert.deepEqual(state.linkRequests, []);
  assert.deepEqual(state.updates, []);
});

test("provisions a bucket, creates changed rules, and records its name", async () => {
  process.env.GCLOUD_PROJECT = "test-project";
  const createdRules = [];
  const released = [];
  const bucket = configuredBucket();
  provisionSetup({
    circle: {storageBucket: ""},
    bucket,
    securityRules: {
      getStorageRuleset: async () => ({source: [{name: "storage.rules", content: "outdated"}]}),
      createRulesFileFromSource: (name, source) => ({name, source}),
      createRuleset: async (rulesFile) => {
        createdRules.push(rulesFile);
        return {source: [rulesFile]};
      },
      releaseStorageRuleset: async (ruleset, bucketName) => released.push({ruleset, bucketName}),
    },
  });
  const {provisionCareCircleBucket} = callableModule();

  await provisionCareCircleBucket({params: {circleId: "circle-1"}});

  assert.equal(createdRules.length, 1);
  assert.match(createdRules[0].source, /service firebase\.storage/);
  assert.equal(released.length, 1);
  assert.match(released[0].bucketName, /^amber-family-[a-f0-9]{24}$/);
  assert.equal(state.linkRequests.length, 1);
  assert.equal(
    state.linkRequests[0].url,
    `https://firebasestorage.googleapis.com/v1beta/projects/test-project/buckets/${released[0].bucketName}:addFirebase`,
  );
  assert.equal(state.linkRequests[0].init.method, "POST");
  assert.equal(state.linkRequests[0].init.headers.Authorization, "Bearer test-token");
  assert.deepEqual(state.updates, [{
    reference: "circle",
    data: {storageBucket: released[0].bucketName},
  }]);
});

test("reuses an owned existing bucket and unchanged ruleset", async () => {
  process.env.GCLOUD_PROJECT = "test-project";
  const metadataCalls = [];
  const bucket = configuredBucket({
    create: async () => {
      throw Object.assign(new Error("Already exists"), {code: 409});
    },
    metadata: {
      labels: {
        amber_managed: "true",
        circle_hash: createHash("sha256")
          .update("test-project:circle-1")
          .digest("hex")
          .slice(0, 24),
      },
    },
    onSetMetadata: async (value) => metadataCalls.push(value),
  });
  const ruleset = {source: [{name: "storage.rules", content: require("node:fs")
    .readFileSync(path.join(__dirname, "..", "storage.rules"), "utf8")}]};
  provisionSetup({
    circle: {storageBucket: ""},
    bucket,
    securityRules: {
      getStorageRuleset: async () => ruleset,
      createRulesFileFromSource: () => assert.fail("should not create a rules file"),
      createRuleset: async () => assert.fail("should not create a ruleset"),
      releaseStorageRuleset: async (releasedRuleset) => assert.equal(releasedRuleset, ruleset),
    },
  });
  const {provisionCareCircleBucket} = callableModule();

  await provisionCareCircleBucket({params: {circleId: "circle-1"}});

  assert.equal(metadataCalls.length, 1);
  assert.equal(state.updates.length, 1);
});

test("does not reuse an unowned bucket and propagates bucket failures", async (t) => {
  process.env.GCLOUD_PROJECT = "test-project";
  for (const [name, create, metadata, expected] of [
    ["unowned existing bucket", async () => { throw Object.assign(new Error("exists"), {code: 409}); },
      {labels: {}}, /Refusing to reuse unowned bucket/],
    ["create failure", async () => { throw new Error("storage unavailable"); }, undefined,
      /storage unavailable/],
  ]) {
    await t.test(name, async () => {
      resetState();
      provisionSetup({circle: {storageBucket: ""}, bucket: configuredBucket({create, metadata})});
      const {provisionCareCircleBucket} = callableModule();
      await assert.rejects(provisionCareCircleBucket({params: {circleId: "circle-1"}}), expected);
    });
  }
});

test("creates rules for an absent ruleset and cleans up a deleted circle", async () => {
  process.env.GCLOUD_PROJECT = "test-project";
  const deleted = [];
  const bucket = configuredBucket({onDelete: async (options) => deleted.push(options)});
  provisionSetup({
    circle: {storageBucket: ""},
    latestCircle: undefined,
    bucket,
  });
  const {provisionCareCircleBucket} = callableModule();

  await provisionCareCircleBucket({params: {circleId: "circle-1"}});

  assert.deepEqual(deleted, [{ignoreNotFound: true}]);
  assert.deepEqual(state.updates, []);
});

test("propagates unexpected rules failures and avoids overwriting concurrent provisioning", async (t) => {
  process.env.GCLOUD_PROJECT = "test-project";
  await t.test("unexpected rules error", async () => {
    const bucket = configuredBucket();
    provisionSetup({
      circle: {storageBucket: ""},
      bucket,
      securityRules: {
        getStorageRuleset: async () => { throw new Error("rules unavailable"); },
      },
    });
    const {provisionCareCircleBucket} = callableModule();
    await assert.rejects(provisionCareCircleBucket({params: {circleId: "circle-1"}}), /rules unavailable/);
  });

  await t.test("concurrent bucket assignment", async () => {
    resetState();
    provisionSetup({
      circle: {storageBucket: ""},
      latestCircle: {storageBucket: "concurrently-assigned"},
      bucket: configuredBucket(),
    });
    const {provisionCareCircleBucket} = callableModule();
    await provisionCareCircleBucket({params: {circleId: "circle-1"}});
    assert.deepEqual(state.updates, []);
  });
});

test("links a bucket already linked by an earlier run, and stops when linking fails", async (t) => {
  process.env.GCLOUD_PROJECT = "test-project";
  await t.test("already linked", async () => {
    resetState();
    state.linkStatus = 409;
    provisionSetup({circle: {storageBucket: ""}, bucket: configuredBucket()});
    const {provisionCareCircleBucket} = callableModule();
    await provisionCareCircleBucket({params: {circleId: "circle-1"}});
    assert.equal(state.linkRequests.length, 1);
    assert.equal(state.updates.length, 1);
  });

  await t.test("link refused", async () => {
    resetState();
    state.linkStatus = 403;
    const untouchable = () => assert.fail("rules must not be released for an unlinked bucket");
    provisionSetup({
      circle: {storageBucket: ""},
      bucket: configuredBucket(),
      securityRules: {
        getStorageRuleset: untouchable,
        createRulesFileFromSource: untouchable,
        createRuleset: untouchable,
        releaseStorageRuleset: untouchable,
      },
    });
    const {provisionCareCircleBucket} = callableModule();
    await assert.rejects(
      provisionCareCircleBucket({params: {circleId: "circle-1"}}),
      /Could not link bucket amber-family-[a-f0-9]{24} to Firebase: 403 link refused/,
    );
    assert.deepEqual(state.updates, []);
  });
});

test("does not redeem an invitation for a user being deleted", async (t) => {
  const deleting = {role: "CAREGIVER", circleIds: [], deletionRequested: true};
  for (const [name, invitationData, circleIds] of [
    ["an unused invitation", invitation(), []],
    ["their own redeemed invitation", invitation({usedBy: "user-1"}), ["circle-1"]],
  ]) {
    await t.test(name, async () => {
      setupRedeem({invitationData, user: {...deleting, circleIds}});
      const {redeemInvitation} = callableModule();

      await expectHttpsError(
        redeemInvitation({auth: {uid: "user-1"}, data: {code: "code-1"}}),
        "failed-precondition",
        "User is being deleted",
        "PROFILE_INVALID",
      );
      assert.deepEqual(state.updates, []);
    });
  }
});

// ---------- deleteAccount ----------

const BUCKET = "amber-family-test";
const EMPTY_BUCKET = "amber-family-alone";

function freshAuth(uid = "gone") {
  return {uid, token: {auth_time: Math.floor(Date.now() / 1000)}};
}

function storedMedia(id, overrides = {}) {
  return {
    authorId: "other",
    storagePath: `careCircles/shared/media/${id}.jpg`,
    shownIds: [],
    authorizedIds: [],
    ...overrides,
  };
}

// "gone" is deleted; "other" and "patient" stay, in a circle shared with them and one of their own
function setupAccounts() {
  state.store = new Map(Object.entries({
    "users/gone": {uid: "gone", role: "CAREGIVER", circleIds: ["shared", "alone"]},
    "users/other": {uid: "other", role: "CAREGIVER", circleIds: ["shared", "unrelated"]},
    "users/patient": {uid: "patient", role: "PATIENT", circleIds: ["shared"]},
    "careCircles/shared": {
      memberIds: ["gone", "other", "patient"],
      patientId: "patient",
      createdBy: "gone",
      storageBucket: BUCKET,
      nicknames: {gone: {other: "Bob"}, other: {gone: "Al", patient: "Mum"}, patient: {gone: "Son"}},
    },
    "careCircles/shared/media/own-photo": storedMedia("own-photo", {authorId: "gone"}),
    "careCircles/shared/media/own-text": storedMedia("own-text", {authorId: "gone", storagePath: ""}),
    "careCircles/shared/media/tagged": storedMedia("tagged", {shownIds: ["gone", "patient"]}),
    "careCircles/shared/media/restricted": storedMedia("restricted", {authorizedIds: ["gone"]}),
    "careCircles/shared/media/two-allowed": storedMedia("two-allowed", {authorizedIds: ["gone", "patient"]}),
    "careCircles/shared/stories/own-story": {createdBy: "gone", mediaIds: ["tagged"]},
    "careCircles/shared/stories/other-story": {createdBy: "other", mediaIds: ["own-photo", "tagged", "own-text"]},
    "careCircles/shared/appointments/doctor": {createdBy: "gone", attendeeIds: ["gone", "other"]},
    "careCircles/shared/appointments/physio": {createdBy: "other", attendeeIds: ["other"]},
    "careCircles/alone": {memberIds: ["gone"], patientId: "", createdBy: "gone", storageBucket: EMPTY_BUCKET},
    "careCircles/alone/media/photo": storedMedia("photo", {
      authorId: "gone",
      storagePath: "careCircles/alone/media/photo.jpg",
    }),
    "careCircles/unrelated": {memberIds: ["other"], patientId: "", createdBy: "other", storageBucket: "amber-other"},
    "careCircles/unrelated/media/photo": storedMedia("photo", {shownIds: ["other"]}),
    "invitations/created": {createdBy: "gone", usedBy: null, circleId: "shared"},
    "invitations/used": {createdBy: "other", usedBy: "gone", circleId: "shared"},
    "invitations/unrelated": {createdBy: "other", usedBy: "patient", circleId: "shared"},
  }));
  state.buckets = new Map([
    [BUCKET, new Set(["careCircles/shared/media/own-photo.jpg", "careCircles/shared/media/tagged.jpg"])],
    [EMPTY_BUCKET, new Set(["careCircles/alone/media/photo.jpg"])],
    ["amber-other", new Set(["careCircles/unrelated/media/photo.jpg"])],
  ]);
  state.storage = {bucket: storedBucket};
  state.authUsers = new Set(["gone", "other", "patient"]);
}

function assertNoReferenceTo(uid) {
  const stored = JSON.stringify([...state.store.entries()]);
  assert.equal(stored.includes(`"${uid}"`), false, stored);
  assert.equal(stored.includes(`users/${uid}`), false, stored);
  assert.equal(state.authUsers.has(uid), false);
}

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
      const {deleteAccount} = callableModule();

      await expectHttpsError(deleteAccount({auth, data: {}}), ...expected);

      assert.deepEqual(state.store, before);
      assert.equal(state.authUsers.has("gone"), true);
    });
  }
});

test("deleteAccount only deletes the caller's account, whatever uid the request names, and can run again", async () => {
  setupAccounts();
  const {deleteAccount} = callableModule();

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

test("deleteAccount blocks new redemptions from the moment it starts", async () => {
  setupAccounts();
  const {deleteAccount} = callableModule();
  state.failWrite = (action, target) => target === "invitations/used";
  await assert.rejects(deleteAccount({auth: freshAuth(), data: {}}));

  assert.equal(state.store.get("users/gone").deletionRequested, true);
});

test("deleteAccount leaves no trace of a caregiver in their circles and changes nobody else's data", async () => {
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
  const {deleteAccount} = callableModule();

  await deleteAccount({auth: freshAuth(), data: {}});

  assertNoReferenceTo("gone");
  assert.deepEqual(untouched.map((documentPath) => state.store.get(documentPath)), before);
  assert.equal(state.buckets.get("amber-other").size, 1);

  // the circle they shared
  const circle = state.store.get("careCircles/shared");
  assert.deepEqual(circle.memberIds, ["other", "patient"]);
  assert.equal(circle.patientId, "patient");
  assert.equal(circle.createdBy, "");
  assert.deepEqual(circle.nicknames, {other: {patient: "Mum"}, patient: {}});
  assert.deepEqual(state.buckets.get(BUCKET), new Set(["careCircles/shared/media/tagged.jpg"]));

  const stored = (documentPath) => state.store.get(`careCircles/shared/${documentPath}`);
  assert.equal(stored("media/own-photo"), undefined);
  assert.equal(stored("media/own-text"), undefined);
  assert.deepEqual(stored("media/tagged").shownIds, ["patient"]);
  assert.deepEqual(stored("media/two-allowed").authorizedIds, ["patient"]);
  // an empty list would show it to the whole circle
  assert.deepEqual(stored("media/restricted").authorizedIds, ["other"]);
  assert.equal(stored("stories/own-story"), undefined);
  assert.deepEqual(stored("stories/other-story").mediaIds, ["tagged"]);
  assert.deepEqual(stored("appointments/doctor"), {createdBy: "", attendeeIds: ["other"]});

  // the circle they were the last member of
  assert.deepEqual(
    [...state.store.keys()].filter((key) => key.startsWith("careCircles/alone")),
    [],
  );
  assert.equal(state.buckets.has(EMPTY_BUCKET), false);
});

test("deleteAccount frees the patient slot when the patient is deleted", async () => {
  setupAccounts();
  const {deleteAccount} = callableModule();

  await deleteAccount({auth: freshAuth("patient"), data: {}});

  assertNoReferenceTo("patient");
  const circle = state.store.get("careCircles/shared");
  assert.deepEqual(circle.memberIds, ["gone", "other"]);
  assert.equal(circle.patientId, "");
  assert.equal(circle.createdBy, "gone");
});

test("deleteAccount keeps the circle of a last caregiver while its patient remains", async () => {
  setupAccounts();
  const circle = state.store.get("careCircles/shared");
  state.store.set("careCircles/shared", {...circle, memberIds: ["gone", "patient"]});
  const {deleteAccount} = callableModule();

  await deleteAccount({auth: freshAuth(), data: {}});

  assertNoReferenceTo("gone");
  assert.deepEqual(state.store.get("careCircles/shared").memberIds, ["patient"]);
  assert.equal(state.buckets.has(BUCKET), true);
});

test("deleteAccount doesn't touch buckets in the emulator", async (t) => {
  setupAccounts();
  process.env.FUNCTIONS_EMULATOR = "true";
  t.after(() => delete process.env.FUNCTIONS_EMULATOR);
  state.store.set("careCircles/shared", {...state.store.get("careCircles/shared"), storageBucket: ""});
  state.store.delete("careCircles/alone/media/photo");
  const {deleteAccount} = callableModule();

  await deleteAccount({auth: freshAuth(), data: {}});

  assert.equal(state.store.has("careCircles/alone"), false);
  assert.equal(state.buckets.has(EMPTY_BUCKET), true);
  assert.equal(state.buckets.get(BUCKET).size, 2);
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
      const {deleteAccount} = callableModule();
      state.failWrite = (action, target) => action === failingAction && target === failingPath;
      await assert.rejects(deleteAccount({auth: freshAuth(), data: {}}));

      state.failWrite = null;
      await deleteAccount({auth: freshAuth(), data: {}});

      assertNoReferenceTo("gone");
      assert.equal(state.buckets.has(EMPTY_BUCKET), false);
      assert.equal(state.buckets.get(BUCKET).has("careCircles/shared/media/own-photo.jpg"), false);
      assert.deepEqual(state.store.get("careCircles/shared/stories/other-story").mediaIds, ["tagged"]);
    });
  }
});
