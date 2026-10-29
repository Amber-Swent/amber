# UI Conventions — Agent Instructions

Audience: an AI coding agent working on the Amber Android app (Kotlin, Jetpack Compose).
Goal: a simple, consistent UI that is easy to review and easy to change. Prefer consistency over visual creativity.

Keywords: MUST / MUST NOT are mandatory. SHOULD is the default unless there is a justified reason.

## Before writing any UI code
1. Reuse before creating: look in `Theme.kt`, `Constant.kt` files, `Typography` and the `*Utils.kt` helper files first.
2. If a rule below conflicts with an explicit request from the user, follow the user and mention the conflict.

## Colors and theme

- MUST NOT write raw colors (`Color(0xFF...)` in screens or components.
- MUST use `MaterialTheme.colorScheme.<role>`. Use the description to decide the role.
- MUST NOT add a new color to `Color.kt` if an existing one fits. A new color needs a specific, written justification.
- Light theme uses orange/red tones. Dark theme uses cyan tones. Dark mode is automatic through `Theme.kt`: do not write theme-specific branches in screens.

### Role mapping - current palette

| Role                          | Light               | Dark                 | Use for                                                            |
|-------------------------------|---------------------|----------------------|--------------------------------------------------------------------|
| `background` / `onBackground` | White / Black       | darkCyan / White     | Screen background and text on it                                   |
| `primary` / `onPrimary`       | darkOrange / Black  | cyan / Black         | Main title                                                         |
| `secondary` / `onSecondary`   | orange / Black      | lightCyan / darkCyan | Secondary title, selected icon                                     |
| `tertiary` / `onTertiary`     | lightOrange / Black | lightGray / darkCyan | Button, unselected icon                                            |
| `surface` / `onSurface`       | White / Black       | darkCyan / White     | Surfaces and text on them                                          |
| `error`                       | darkOrange          | White                | Errors                                                             |

Content color rule: a container color and its content color MUST come as a pair.
Example: `containerColor = MaterialTheme.colorScheme.primary` goes with `contentColor` / icon tint `MaterialTheme.colorScheme.onPrimary`.

#### Buttons
- All buttons of the same kind use the same container color, content color, `RoundedCornerShape` and text size. Do not restyle a button locally. Use the Helper function.
- Do not hand-build buttons in screens. Use the shared button helpers : `CaregiverButton*(...)` and `PatientButton*(...)`.

Preview note: the top bar looks purple in `@Preview`. This is expected. Do not "fix" it.

## Typography

- MUST use `MaterialTheme.typography.<style>`, for example `MaterialTheme.typography.bodySmall`.
- MUST NOT set `fontSize`, `fontWeight` or `fontFamily` directly on a `Text`.
- Pick the category by role first, then the size (small / medium / large) from Figma and the need:
  - `headline`, `title`, `body`, `label`.
  - Patient screens need larger text: use body medium/large.
- `Type.kt` was deleted as useless. Only (re)create it if a default style must be overridden, and override only that style.

## Sizes, padding and constants

- MUST NOT use magic numbers. No bare `16.dp`, `0.8f`, `maxLines = 3` and so on in screens.
- Constants live in the `Constant.kt` files. Add a constant only when NEEDED, and keep the total minimal.
- Widths MUST be relative: `Modifier.fillMaxWidth(fraction)` with a percentage. No fixed widths, because fixed sizes break on other screens.
- Heights may be fixed only if the content scrolls (`verticalScroll` / `LazyColumn`).
- Avoid `Spacer`. Prefer `Modifier.padding(top = ..., bottom = ...)` and `Alignment` / `Arrangement`.
- If a `Spacer` is unavoidable, wrap it in a reusable helper so the spacing is always identical, as much as possible.

## Screen structure and naming

Replace `Name` with the screen name.

| Thing     | File               | Identifier                                                      |
|-----------|--------------------|-----------------------------------------------------------------|
| Screen    | `Name.kt`          | `fun NameScreen(...)`                                           |
| ViewModel | `NameViewModel.kt` | `class NameViewModel(...)`                                      |
| UI state  | `NameViewModel.kt` | `data class NameUIState(...)`                                   |
| Test tags | in the screen file | `object NameTestTags { const val NAME = "Name_test_tag_name" }` |

- Screens shared by caregiver and patient and needing separate versions: `CaregiverNameScreen` / `PatientNameScreen`.
- Helper functions own their own test tag (do not reuse the screen's).
- Every screen MUST have a ViewModel with the basic layout (UI state + class), even if empty at first. It simplifies later implementation.
- Do NOT split a screen into a "Screen" (calls the ViewModel) and a "Content" part. One composable `NameScreen`.

[//]: # (Remove from convention because it can indeed be made to work)
[//]: # (- Previews: because there is no Content split, previews of screens that need a ViewModel do not work. Do not add broken previews. Preview screens with the emulator. A `@Preview` is fine on helper components and constants that do render.)
- Collect ViewModel state with `collectAsStateWithLifecycle()`, NEVER `collectAsState()`.
- ALL functions (composables and helpers) MUST have default values for every parameter.
- Apply MVVM principle. The UI is never in charge of the logic. 

## Helpers and reuse

- Extract repeated UI into helper composables. MUST NOT copy-paste styling between screens.
- Code reusable across screens goes into `<NameForAllScreensUsingIt>Utils.kt` (for example `CaregiverUtils.kt`). Screens MUST use those functions instead of rebuilding them.

## Figma

- The person who creates a screen owns its Figma file. It MUST be up to date with the implementation.
- If you change a screen, remind the user that the Figma file must be updated (deadline for the weekly review: Friday 8:45).

## Self-check before finishing

Run through this list on every UI change. Fix violations before reporting the task done.

- [ ] No raw colors; only `MaterialTheme.colorScheme`.
- [ ] No new color added without justification.
- [ ] Only `MaterialTheme.typography` styles; no direct font sizes.
- [ ] No magic numbers; values come from `Constants` files.
- [ ] Widths use `fillMaxWidth(fraction)`; fixed heights only with scrolling.
- [ ] No useless `Spacer`.
- [ ] Reusable code is in a `*Utils.kt` file and reused.
- [ ] Every function parameter has a default value.
- [ ] Naming follows `NameScreen` / `NameViewModel` / `NameUIState` / `NameTestTags`.
- [ ] A ViewModel exists for the screen (even if minimal).
- [ ] `collectAsStateWithLifecycle()` is used.
- [ ] No top bar on `TopLevelDestination` screens.
- [ ] Figma status mentioned to the user if the screen changed.