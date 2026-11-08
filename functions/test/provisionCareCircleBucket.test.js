// Written by GitHub Copilot.
// Modified by Neha Chakraborty and Copilot, with assistance from
// Claude (Anthropic) via Claude Code.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
const assert = require("node:assert/strict");
const {createHash} = require("node:crypto");
const path = require("node:path");
const test = require("node:test");
const {callableModule, resetState, state} = require("./support/fakes");
const {configuredBucket, provisionSetup} = require("./fixtures/provisioning");

test.beforeEach(resetState);

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
