// Written by GitHub Copilot.
// Modified by Neha Chakraborty and Copilot, with assistance from
// Claude (Anthropic) via Claude Code.
const {state} = require("../support/fakes");

/** An unused invitation to circle-1 that expires in a minute. */
function invitation(overrides = {}) {
  return {
    circleId: "circle-1",
    usedBy: null,
    expiresAt: Date.now() + 60_000,
    ...overrides,
  };
}

/** A circle with one caregiver and no patient. */
function validCircle(overrides = {}) {
  return {memberIds: ["caregiver-1"], patientId: "", ...overrides};
}

/**
 * Stores invitations/code-1, careCircles/circle-1 and users/user-1 for redeemInvitation. Each can be
 * overridden in options, or left out by passing undefined.
 */
function setupRedeem(options = {}) {
  const invitationData = "invitationData" in options ? options.invitationData : invitation();
  const circle = "circle" in options ? options.circle : validCircle();
  const user = "user" in options ? options.user : {role: "CAREGIVER", circleIds: []};
  state.documents.set("invitations/code-1", invitationData);
  if (circle !== undefined) state.documents.set("careCircles/circle-1", circle);
  if (user !== undefined) state.documents.set("users/user-1", user);
}

module.exports = {invitation, validCircle, setupRedeem};
