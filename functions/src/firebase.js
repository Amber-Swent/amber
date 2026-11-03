// Written by GitHub Copilot.
// Modified by Neha Chakraborty and Copilot, with assistance from
// Claude (Anthropic) via Claude Code.
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");

const app = initializeApp();
const db = getFirestore();

module.exports = { app, db };
