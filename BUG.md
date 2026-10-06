# Bug Log

Record confirmed bugs discovered during implementation or review. Use the next available ID in the form `BUG-001`, `BUG-002`, and so on. Mark each entry `Open` or `Fixed`, and link the relevant source file, test, or PR when available. Do not add speculative issues.

| ID | Description | Status | Evidence / Fix |
| --- | --- | --- | --- |
| BUG-001 | Firestore rules allowed unrestricted reads and writes, so user roles could not enforce access levels. | Fixed | Added immutable user roles and caregiver-only care-circle mutations in `firebase/firestore/firestore.rules`. |
