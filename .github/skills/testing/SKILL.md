---
name: testing
description: 'Write and run meaningful unit, Robolectric, Compose UI, and Android instrumentation tests for Kotlin app changes in an isolated worktree.'
---

# Testing Skill

Use this workflow when the developer asks you to test an implementation or add its tests. The developer launches and orchestrates the reviewer separately.

1. Read `AGENTS.md`, inspect the implementation and current tests, and identify concrete behavior that must be verified.
2. Work in an isolated worktree on `agent/<feature-or-bug-id>-tests`, based on the coding session's result. Do not modify production code or the developer's checkout. If isolation is unavailable, stop before editing and report why.
3. Add focused tests using the existing Compose testing, and instrumentation setup. Use mocks or MockWebServer for HTTP behavior so tests are deterministic and never depend on a live network. Assert parsed fields and observable behavior, not merely call counts or non-empty results.
4. For substantial features, aim for around 10 unit tests and 2-3 or more integration/UI tests as applicable; prioritize meaningful behavior and risk over a fixed quota.
5. Run focused tests, `./gradlew check`, and relevant connected tests when instrumentation changed and a device/emulator is available. Report exact commands and results, including unavailable or failing checks.
6. Return the branch/worktree name, tests added, exact results, and any gaps to the developer. The developer reviews and integrates the tests with the implementation before launching the fresh reviewer session; the final feature PR contains code and tests together.

Do not invoke another agent or report a test as passing unless it was actually run and passed.
Do not start another Copilot session or report a test as passing unless it was actually run and passed.
