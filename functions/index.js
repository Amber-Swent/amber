// Written by GitHub Copilot.
// Modified by Neha Chakraborty and Copilot, with assistance from
// Claude (Anthropic) via Claude Code.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Each function lives in its own module under src/: this only exports them for Firebase.
const { deleteAccount } = require("./src/deleteAccount");
const { provisionCareCircleBucket } = require("./src/provisionCareCircleBucket");
const { redeemInvitation } = require("./src/redeemInvitation");

module.exports = { deleteAccount, provisionCareCircleBucket, redeemInvitation };
