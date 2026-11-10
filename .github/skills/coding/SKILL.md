---
name: coding
description: 'Implement Kotlin Android and Jetpack Compose features using MVVM on the developer's feature branch; return results for a separate developer-launched testing session.'
---

# Coding Skill

Use this workflow when implementing a feature or fixing a bug. The developer launches and orchestrates the other roles separately.

1. Read `AGENTS.md`, inspect the relevant implementation and tests, and identify the smallest owning layer.
2. Work directly in the developer's checkout on the current feature branch. Do not switch branches, commit, or push unless the developer asks.
3. Follow MVVM and Compose conventions. Keep composables focused on rendering, state/events in ViewModels, and external services in repositories/data sources. A ViewModel must never import Firebase.
4. Never hardcode user-visible text in Kotlin. Put it in `strings.xml` with its French version in `values-fr/strings.xml`, and have ViewModels expose `@StringRes` or `UiText`, following "Text and translation" in `UI_CONVENTIONS_AGENT.md`.
5. Use camelCase for variables, properties, and parameters. Do not edit generated files or anything under the repository-root `/generated` directory.
6. Keep the change focused and within the sizing guidance in `AGENTS.md`. Include the required contributor acknowledgement at the top of each changed source file; preserve existing attributions and do not invent identities.
7. Make the implementation compile where practical. Do not write tests in this role or invoke another agent.
8. Return the branch name, concise implementation summary, relevant files, compile/check results, known risks, and a suggested prompt for the separate testing session. The developer inspects the code before that session begins.

Do not claim checks passed unless you ran them and observed success.
