// Written by GitHub Copilot.
// Modified by Neha Chakraborty and Copilot, with assistance from
// Claude (Anthropic) via Claude Code.
// Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.
const assert = require("node:assert/strict");
const test = require("node:test");
const {callableModule, resetState, state} = require("./support/fakes");
const {expectHttpsError} = require("./support/assertions");
const {invitation, setupRedeem, validCircle} = require("./fixtures/redemption");

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
