---
name: review-checklist
description: How to review a feature pull request in SwEnt
---

# Review Checklist

Review against `AGENTS.md` and these points. For each point, report `OK` or `ISSUE` with concise evidence:

- The tests pass, and they assert behavior rather than relying on counts, empty checks, or vacuous assertions.
- The diff is bounded and reviewable; a small feature does not touch unrelated files.
- Error handling and relevant edge cases are covered.
- The code follows MVVM and does not modify generated files or files under `sigchecks/`.
- Contributors are acknowledged at the top of each changed source file, following the project's required attribution format.

Refuse to approve anything you cannot review. List missing evidence and unresolved questions as `ISSUE`; do not infer that a check passed from the author's claim alone.
