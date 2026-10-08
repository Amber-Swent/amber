// Written by GitHub Copilot.
const assert = require("node:assert/strict");
const {createHash} = require("node:crypto");
const Module = require("node:module");
const path = require("node:path");
const test = require("node:test");

class MockHttpsError extends Error {
  constructor(code, message) {
    super(message);
    this.code = code;
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
  state.circleSnapshots = [];
  state.circleReadIndex = 0;
  state.storage = {
    bucket: () => {
      throw new Error("Storage bucket was not configured");
    },
  };
  state.securityRules = {
    getStorageRuleset: async () => {
      throw Object.assign(new Error("Missing ruleset"), {code: "not-found"});
    },
    createRulesFileFromSource: (name, source) => ({name, source}),
    createRuleset: async (rulesFile) => ({source: [rulesFile]}),
    releaseStorageRuleset: async () => {},
  };
}

function callableModule() {
  const originalLoad = Module._load;
  Module._load = function mockModuleLoad(request, parent, isMain) {
    if (request === "firebase-admin/app") return {initializeApp: () => {}};
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
    role: "CAREGIVER",
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
  const {user} = options;
  state.documents.set("invitations/code-1", invitationData);
  if (circle !== undefined) state.documents.set("careCircles/circle-1", circle);
  if (user !== undefined) state.documents.set("users/user-1", user);
}

async function expectHttpsError(promise, code, message) {
  await assert.rejects(promise, (error) => error instanceof MockHttpsError
    && error.code === code && error.message === message);
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

test("returns the original redemption result for the same user without writes", async () => {
  setupRedeem({invitationData: invitation({usedBy: "user-1", role: "PATIENT"})});
  const {redeemInvitation} = callableModule();

  assert.deepEqual(
    await redeemInvitation({auth: {uid: "user-1"}, data: {code: "code-1"}}),
    {circleId: "circle-1", role: "PATIENT"},
  );
  assert.deepEqual(state.updates, []);
  assert.deepEqual(state.creates, []);
});

test("rejects unavailable, consumed, and structurally invalid invitations", async (t) => {
  const cases = [
    ["missing", undefined, "not-found", "Invitation was not found"],
    ["redeemed by another user", invitation({usedBy: "other-user"}), "failed-precondition",
      "Invitation has already been redeemed"],
    ["invalid role", invitation({role: "ADMIN"}), "failed-precondition",
      "Invitation has an invalid role"],
    ["expired numeric timestamp", invitation({expiresAt: Date.now() - 1}), "failed-precondition",
      "Invitation has expired"],
    ["invalid expiration", invitation({expiresAt: {}}), "failed-precondition",
      "Invitation has expired"],
    ["blank circle", invitation({circleId: ""}), "failed-precondition",
      "Invitation has an invalid circle"],
  ];

  for (const [name, invitationData, code, message] of cases) {
    await t.test(name, async () => {
      setupRedeem({invitationData});
      const {redeemInvitation} = callableModule();
      await expectHttpsError(
        redeemInvitation({auth: {uid: "user-1"}, data: {code: "code-1"}}),
        code,
        message,
      );
    });
  }
});

test("rejects invalid circle and user membership states", async (t) => {
  const cases = [
    ["missing circle", undefined, undefined, "Invitation circle no longer exists"],
    ["invalid member ids", validCircle({memberIds: "user-1"}), undefined,
      "User is already a circle member"],
    ["already a member", validCircle({memberIds: ["user-1"]}), undefined,
      "User is already a circle member"],
    ["patient already assigned", validCircle({patientId: "patient-1"}), undefined,
      "Circle already has a patient", invitation({role: "PATIENT"})],
    ["role mismatch", validCircle(), {role: "PATIENT", circleIds: []},
      "Invitation role does not match user profile"],
    ["invalid profile memberships", validCircle(), {role: "CAREGIVER", circleIds: "circle-1"},
      "User profile has invalid circle membership"],
  ];

  for (const [name, circle, user, message, invitationData] of cases) {
    await t.test(name, async () => {
      setupRedeem({circle, user, invitationData: invitationData ?? invitation()});
      const {redeemInvitation} = callableModule();
      await expectHttpsError(
        redeemInvitation({auth: {uid: "user-1"}, data: {code: "code-1"}}),
        "failed-precondition",
        message,
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

test("creates a patient profile with supplied person data", async () => {
  setupRedeem({
    invitationData: invitation({role: "PATIENT", expiresAt: {toMillis: () => Date.now() + 60_000}}),
  });
  const {redeemInvitation} = callableModule();

  await redeemInvitation({
    auth: {uid: "user-1"},
    data: {
      code: "code-1",
      person: {firstName: "Ada", lastName: "Lovelace", nickname: null},
    },
  });

  assert.deepEqual(state.updates[1], {
    reference: {path: "careCircles/circle-1"},
    data: {memberIds: {arrayUnion: "user-1"}, patientId: "user-1"},
  });
  assert.deepEqual(state.creates, [{
    reference: {path: "users/user-1"},
    data: {
      uid: "user-1",
      role: "PATIENT",
      person: {id: "user-1", firstName: "Ada", lastName: "Lovelace", nickname: null},
      circleIds: ["circle-1"],
    },
  }]);
});

test("validates new profile person fields", async (t) => {
  const invalidPeople = [
    ["array", [] , "Person must be an object"],
    ["unsupported field", {middleName: "Byron"}, "Person contains unsupported fields"],
    ["non-string first name", {firstName: 1}, "Person fields have invalid types"],
    ["non-string last name", {lastName: false}, "Person fields have invalid types"],
    ["non-string nickname", {nickname: {}}, "Person fields have invalid types"],
  ];

  for (const [name, person, message] of invalidPeople) {
    await t.test(name, async () => {
      setupRedeem();
      const {redeemInvitation} = callableModule();
      await expectHttpsError(
        redeemInvitation({auth: {uid: "user-1"}, data: {code: "code-1", person}}),
        "invalid-argument",
        message,
      );
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
