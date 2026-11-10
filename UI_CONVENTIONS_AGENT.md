# UI Conventions — Agent Instructions

Audience: an AI coding agent working on the Amber Android app (Kotlin, Jetpack Compose).
Goal: a simple, consistent UI that is easy to review and easy to change. Prefer consistency over visual creativity.

Keywords: MUST / MUST NOT are mandatory. SHOULD is the default unless there is a justified reason.

## Before writing any UI code
1. Reuse before creating: look in `Theme.kt`, `Constant.kt` files, `Typography`, `strings.xml` and the `*Utils.kt` helper files first.
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

## Text and translation

The app is in English and French. Android picks `res/values-fr/strings.xml` when the phone is set to French and falls back to `res/values/strings.xml` (English) otherwise. Text written in Kotlin is never translated.

- MUST NOT hardcode user-visible text in Kotlin. This covers labels, titles, messages, errors and `contentDescription`. Test tags and log messages are not user-visible and stay in Kotlin.
- Every key MUST exist in both files: English in `values/strings.xml`, French in `values-fr/strings.xml`. Lint's `MissingTranslation` fails `./gradlew check` otherwise.
- Composables read text with `stringResource(R.string.key)`.

### Keys and files
- Keys are camelCase, `screenPurpose`: `caregiverChoiceCreateCircle`, `createProfileErrorOffline`. The older `app_name` and `title_activity_*` keep their names.
- Group keys with one `<!-- ScreenName -->` comment per screen, in the same order in both files.
- MUST NOT build a sentence by joining pieces (`WELCOME + ", $name"`): French word order differs. Use numbered placeholders: `<string name="caregiverChoiceWelcomeName">Welcome, %1$s</string>` and `stringResource(R.string.caregiverChoiceWelcomeName, name)`. `%1$s` is the first argument as a string, `%2$d` the second as an integer; the numbers let a translation reorder them.
- French typography: escape apostrophes (`l\'invitation`) and put a non-breaking space (`&#160;`) before `:`, `?` and `!`.

### Text from ViewModels
ViewModels have no `Context`, so they MUST NOT hold translated text. They expose what to show and the composable resolves it:
- A fixed message: a `@StringRes Int`, e.g. `errorMsgRes = R.string.createProfileErrorExists`. The composable calls `stringResource(it)`.
- Text that depends on data only the ViewModel has: a `UiText` (`ui/utils/UiText.kt`).
  - `UiText.Resource(id, args)` is **app text**: a key from `strings.xml`, plus the values for its placeholders, if any. It is translated.
  - `UiText.Plural(id, count)` is **app text with a number**: a `<plurals>` key from `strings.xml`. The number picks the wording ("1 character", "3 characters"). Use it whenever a number is followed by a word that changes with it. In a composable, the same key is read with `pluralStringResource(R.plurals.key, count, count)`. French needs the `one`, `many` and `other` items.
  - `UiText.Dynamic(value)` is **user data** shown exactly as typed, such as a name or a nickname. It is never translated.
  - ⚠️ `Dynamic` is ONLY for user data that fills the whole text. Data inside a sentence is an argument of `Resource`. A string literal or a constant in `Dynamic` is hardcoded English.

  Example: each picture shows who uploaded it. That is the member's name when it is known, otherwise "Unknown member 3".
  ```xml
  <!-- values/strings.xml -->
  <string name="unknownMember">Unknown member %1$d</string>
  <!-- values-fr/strings.xml -->
  <string name="unknownMember">Membre inconnu %1$d</string>
  ```
  ```kotlin
  // ViewModel: the field is a UiText, because it holds either user data or app text.
  val uploader: UiText =
      if (name != null) UiText.Dynamic(name) // "Mamie Jo", shown as is
      else UiText.Resource(R.string.unknownMember, listOf(number)) // "Unknown member 3" / "Membre inconnu 3"

  // Composable
  Text(uiState.uploader.asString(LocalResources.current))
  ```
  | Text to show                              | Correct                                               | Wrong                              |
  |-------------------------------------------|-------------------------------------------------------|------------------------------------|
  | A name alone: "Mamie Jo"                  | `UiText.Dynamic(name)`                                | -                                  |
  | Fixed app text: "Anonymous"               | `UiText.Resource(R.string.anonymous)`                 | `UiText.Dynamic("Anonymous")`      |
  | A name in a sentence: "Welcome, Mamie Jo" | `UiText.Resource(R.string.welcomeName, listOf(name))` | `UiText.Dynamic("Welcome, $name")` |
- Resolve a `UiText` with its one function, `asString(resources)`. Always take the resources from `LocalResources.current`, so the text follows the app language. MUST NOT use `context.resources` or `context.getString(...)`.
  - In a composable: `text.asString(LocalResources.current)`.
  - In a `LaunchedEffect`, for example for a `Toast`: read `val resources = LocalResources.current` in the composable, then use `text.asString(resources)` (or `resources.getString(res)` for a plain `@StringRes`) inside the effect.
- Errors: data-layer exceptions keep their English messages, which are for logs. The ViewModel maps the error type to a resource and MUST NOT show `e.message` to the user.

### Dates
- Format dates with the app locale (`LocalConfiguration.current.locales[0]`), never `Locale.ENGLISH`, so month names are translated.

### Tests
- Find text by `testTag` or `context.getString(R.string.key)`, never by hardcoded English.
- ViewModel tests compare resource IDs or `UiText` values, not English text.
- Check French with Robolectric `@Config(qualifiers = "fr")`.

## Screen structure and naming

Replace `Name` with the screen name.

| Thing     | File               | Identifier                                                                       |
|-----------|--------------------|----------------------------------------------------------------------------------|
| Screen    | `Name.kt`          | `fun NameScreen(...)`                                                            |
| ViewModel | `NameViewModel.kt` | `class NameViewModel(...)`                                                       |
| UI state  | `NameViewModel.kt` | `data class NameUIState(...)`                                                    |
| Test tags | in the screen file | `object NameOfTheScreenTestTags { const val NAME = "name_of_the_screen_name"  }` |

An example of a valid testTag is: `object UploadPictureTestTags { const val button = "upload_picture_button"  }`.

- Screens shared by caregiver and patient and needing separate versions: `CaregiverNameScreen` / `PatientNameScreen`.
- Every screen MUST have a ViewModel with the basic layout (UI state + class), even if empty at first. It simplifies later implementation.
- Do NOT split a screen into a "Screen" (calls the ViewModel) and a "Content" part. One composable `NameScreen`.

- Preview: even if there is no Content split, add a preview. They can be run through the emulator. All screens and all helper function place inside a *Utils.kt must have a preview.
- Collect ViewModel state with `collectAsStateWithLifecycle()`, NEVER `collectAsState()`.
- ALL functions (composables and helpers) MUST have default values for every parameter.
- Apply MVVM principle. The UI is never in charge of the logic. 

## Helpers and reuse

- Extract repeated UI into helper composables. MUST NOT copy-paste styling between screens.
- MUST use the helper function already implemented in the *Utils.kt file
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
- [ ] No user-visible text hardcoded in Kotlin; use `strings.xml` plus `values-fr`.
- [ ] Widths use `fillMaxWidth(fraction)`; fixed heights only with scrolling.
- [ ] No useless `Spacer`.
- [ ] Reusable code is in a `*Utils.kt` file and reused.
- [ ] Every function parameter has a default value.
- [ ] Naming follows `NameScreen` / `NameViewModel` / `NameUIState` / `NameTestTags`.
- [ ] A ViewModel exists for the screen (even if minimal).
- [ ] `collectAsStateWithLifecycle()` is used.
- [ ] No top bar on `TopLevelDestination` screens.
- [ ] Figma status mentioned to the user if the screen changed.
