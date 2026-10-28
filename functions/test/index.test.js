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
};

// the only HTTP call the functions make: linking a bucket to Firebase Storage
globalThis.fetch = async (url, init) => {
  state.linkRequests.push({url, init});
  return {ok: state.linkStatus < 300, status: state.linkStatus, text: async () => "link refused"};
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
    if (request === "firebase-admin/firestore") {
      return {
        FieldValue: {arrayUnion: (value) => ({arrayUnion: value})},
        getFirestore: () => ({
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
      return {HttpsError: MockHttpsError, onCall: (handler) => handler};
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
