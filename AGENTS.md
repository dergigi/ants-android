# Project conventions

- Build a native Kotlin / Jetpack Compose Android app for ants.
- Commit after each implementation step or change. Use Conventional Commits 1.0.0.
- Maintain CHANGELOG.md using Keep a Changelog 1.1.0 with an Unreleased section and dated releases.
- Use Semantic Versioning 2.0.0; increment Android versionCode for every published APK.
- Reuse the boris-android publishing setup. Never commit signing secrets, keystores, .env, or local.properties.
- For the initial MVP, the user requested publishing directly for phone testing: do not run local tests or an emulator.

# UI and writing

- Keep copy short and specific. Use plain language; cut filler, canned contrasts, and promotional phrasing. See the user's [writing reference](https://en.wikipedia.org/wiki/Wikipedia:Signs_of_AI_writing).
- Use icons for familiar actions, with tooltips and accessibility labels. Put explanations and related actions inside popovers.
- Describe zaps and nutzaps as monetary transactions.
