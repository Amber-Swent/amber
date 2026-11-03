// Written by GitHub Copilot.
// Modified by Neha Chakraborty and Copilot, with assistance from
// Claude (Anthropic) via Claude Code.
// Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.
const assert = require("node:assert/strict");
const {MockHttpsError, state} = require("./fakes");

/** Asserts that promise rejects with exactly this HttpsError code, message and details.reason. */
async function expectHttpsError(promise, code, message, reason) {
  await assert.rejects(promise, (error) => error instanceof MockHttpsError
    && error.code === code && error.message === message && error.details?.reason === reason);
}

/** Asserts that no stored document mentions uid, and that uid is no Auth user any more. */
function assertNoReferenceTo(uid) {
  const stored = JSON.stringify([...state.store.entries()]);
  assert.equal(stored.includes(`"${uid}"`), false, stored);
  assert.equal(stored.includes(`users/${uid}`), false, stored);
  assert.equal(state.authUsers.has(uid), false);
}

module.exports = {expectHttpsError, assertNoReferenceTo};
