# Bug Log

Record confirmed bugs discovered during implementation or review. Use the next available ID in the form `BUG-001`, `BUG-002`, and so on. Mark each entry `Open` or `Fixed`, and link the relevant source file, test, or PR when available. Do not add speculative issues.

| ID | Description | Status | Evidence / Fix |
| --- | --- | --- | --- |
| BUG-001 | Storage rule source path did not match the deployed/configured location. | Fixed | `firebase.json` and `functions/index.js` both load `functions/storage.rules`. |
| BUG-002 | Invitation redemption was denied by client rules but had no callable backend. | Fixed | Added the authenticated, transactional `redeemInvitation` callable in `functions/index.js`. |
| BUG-003 | JaCoCo read Kotlin classes from the obsolete AGP output path, producing an empty SonarCloud coverage report. | Fixed | `app/build.gradle.kts` now uses the `compileDebugKotlin` task's declared output directory. |
| BUG-004 | Storage rules rejected PNG uploads and prevented caregivers from deleting another member's media. | Fixed | `functions/storage.rules` now accepts `image/png` files with a `.png` extension and lets care-circle caregivers delete media. |
