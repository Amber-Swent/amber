# Bug Log

Record confirmed bugs discovered during implementation or review. Use the next available ID in the form `BUG-001`, `BUG-002`, and so on. Mark each entry `Open` or `Fixed`, and link the relevant source file, test, or PR when available. Do not add speculative issues.

| ID      | Description                                                                                      | Status | Evidence / Fix |
|---------|--------------------------------------------------------------------------------------------------| --- | --- |
| BUG-001 | Firebase configuration belonged to a different Android application due to package name mismatch. | Fixed | Created a new Firebase project and registered an Android app with package name `com.github.se.amber`. Replaced `app/google-services.json` with the new configuration and updated the default project ID in `.firebaserc` to `amber-34abf`. Verified: `./gradlew.bat check` passed, including Google Services processing, compilation, formatting, existing unit tests, and lint. |
