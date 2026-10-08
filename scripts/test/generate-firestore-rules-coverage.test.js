// Written by GitHub Copilot.
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const test = require("node:test");

const {
  coveredConditions,
  generateCoverageReport,
  isMemberLineNumberFor,
  lineNumberFor,
} = require("../generate-firestore-rules-coverage");

test("generates generic coverage for every tested family-membership condition", () => {
  const outputDirectory = fs.mkdtempSync(path.join(os.tmpdir(), "amber-rules-coverage-"));
  const outputPath = path.join(outputDirectory, "coverage.xml");

  try {
    generateCoverageReport(undefined, outputPath);

    const report = fs.readFileSync(outputPath, "utf8");
    const rules = fs
      .readFileSync(path.join(__dirname, "..", "..", "firebase/firestore/firestore.rules"), "utf8")
      .split(/\r?\n/);

    assert.match(report, /<file path="firebase\/firestore\/firestore.rules">/);
    for (const condition of coveredConditions) {
      const lineNumber = isMemberLineNumberFor(rules, condition);
      assert.match(report, new RegExp(`lineNumber="${lineNumber}" covered="true"`));
    }
  } finally {
    fs.rmSync(outputDirectory, {recursive: true, force: true});
  }
});

test("rejects missing or ambiguous covered conditions", () => {
  assert.throws(
    () => lineNumberFor(["return isSignedIn()"], "&& missing"),
    /Expected exactly one rule line matching/,
  );
  assert.throws(
    () => lineNumberFor(["return isSignedIn()", "return isSignedIn()"], "return isSignedIn()"),
    /Expected exactly one rule line matching/,
  );
});
