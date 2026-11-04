// Written by GitHub Copilot.
// Modified by Neha Chakraborty and Copilot, with assistance from
// Claude (Anthropic) via Claude Code.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
const {state} = require("../support/fakes");

/** A bucket for provisioning, owned by Amber unless metadata says otherwise. Each call can be overridden. */
function configuredBucket({create, metadata, onSetMetadata, onDelete} = {}) {
  return {
    create: create ?? (async () => {}),
    getMetadata: async () => [metadata ?? {labels: {amber_managed: "true", circle_hash: "unused"}}],
    setMetadata: onSetMetadata ?? (async () => {}),
    delete: onDelete ?? (async () => {}),
  };
}

/**
 * Sets what provisioning reads: circle, then latestCircle when it reads the circle again (circle by
 * default), the bucket, and optionally the Security Rules.
 */
function provisionSetup(options = {}) {
  const {circle, bucket, securityRules} = options;
  const latestCircle = "latestCircle" in options ? options.latestCircle : circle;
  state.circleSnapshots = [circle, latestCircle];
  state.storage = {bucket: () => bucket};
  if (securityRules) state.securityRules = securityRules;
}

module.exports = {configuredBucket, provisionSetup};
