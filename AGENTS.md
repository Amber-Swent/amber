# Agent Instructions

## Architecture
- Use MVVM: composables render UI, ViewModels own UI state/events, and repositories or data sources handle external access.
- ViewModels must never import Firebase; access Firebase only through the data layer.
- Prefer testable, state-hoisted composables and lifecycle-aware state collection.

## Changes
- Do not edit generated files or anything under the repository-root `/generated`; update their sources instead.
- Use camelCase for variables, properties, and parameters; use PascalCase for Kotlin types.
- Preserve existing attribution and add the required contributor acknowledgment to changed source files using the project handout's format.
- Keep PRs focused. Aim for commits under 500 lines, UI changes around 500 LOC or less per PR, and logic changes around 200-500 LOC; these are targets, not quotas.

## Tests
- For substantial features, aim for around 10 unit tests and 2-3 or more integration/UI tests, scaled to risk and behavior.
- Assert meaningful behavior; mock network calls or use local fixtures, never live services.
- Keep feature code and tests together in the final PR. Run `./gradlew check`; run relevant connected tests when instrumentation changes and a device is available.

## Workflow
- The developer creates a feature branch from `main` and orchestrates separate coding, testing, and review sessions. Use isolated worktrees: `agent/<feature-or-bug-id>-code` and `agent/<feature-or-bug-id>-tests`. The developer inspects and integrates each result.
- Run review in a fresh session using `.github/skills/review-checklist/SKILL.md`. The reviewer may open the code-and-tests PR when authorized, but never approves or merges; the developer owns the final decision.
- If worktree or GitHub access is unavailable, report that instead of editing the developer's checkout or claiming a PR was opened.

## PRs
- Include a brief feature/fix summary and relevant check results in each PR description.
