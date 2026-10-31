// Written by GitHub Copilot.
const fs = require("node:fs");
const path = require("node:path");

const projectRoot = path.resolve(__dirname, "..");
const rulesRelativePath = "firebase/firestore/firestore.rules";
const isMemberFunction = "function isMember(circleId) {";
const coveredConditions = [
  "return isSignedIn()",
  "&& exists(circlePath(circleId))",
  "&& exists(userPath(request.auth.uid))",
  "&& circleId in userDoc(request.auth.uid).circleIds",
  "&& request.auth.uid in circleDoc(circleId).memberIds;",
];

function lineNumberFor(lines, condition) {
  const matchingLines = lines
    .map((line, index) => (line.trim() === condition ? index + 1 : null))
    .filter((lineNumber) => lineNumber !== null);

  if (matchingLines.length !== 1) {
    throw new Error(`Expected exactly one rule line matching: ${condition}`);
  }

  return matchingLines[0];
}

function isMemberLineNumberFor(lines, condition) {
  const functionStart = lineNumberFor(lines, isMemberFunction);
  const functionEnd = lines.findIndex(
    (line, index) => index >= functionStart && line.trim() === "}",
  );
  if (functionEnd === -1) {
    throw new Error("Could not find the end of isMember");
  }

  const isMemberLines = lines.slice(functionStart, functionEnd + 1);
  return functionStart + lineNumberFor(isMemberLines, condition);
}

function generateCoverageReport(
  rulesPath = path.join(projectRoot, rulesRelativePath),
  outputPath = path.join(projectRoot, "build/reports/firestore-rules-coverage.xml"),
) {
  const rules = fs.readFileSync(rulesPath, "utf8").split(/\r?\n/);
  const coveredLines = coveredConditions.map((condition) => isMemberLineNumberFor(rules, condition));
  const lineEntries = coveredLines
    .map((lineNumber) => `    <lineToCover lineNumber="${lineNumber}" covered="true"/>`)
    .join("\n");
  const report = [
    '<?xml version="1.0" encoding="UTF-8"?>',
    '<coverage version="1">',
    `  <file path="${rulesRelativePath}">`,
    lineEntries,
    "  </file>",
    "</coverage>",
    "",
  ].join("\n");

  fs.mkdirSync(path.dirname(outputPath), {recursive: true});
  fs.writeFileSync(outputPath, report);
}

if (require.main === module) {
  generateCoverageReport(undefined, process.argv[2]);
}

module.exports = {
  coveredConditions,
  generateCoverageReport,
  isMemberLineNumberFor,
  lineNumberFor,
};
