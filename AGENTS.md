# Project conventions

- Build a native Kotlin / Jetpack Compose Android app for ants.
- Commit after each implementation step or change. Use Conventional Commits 1.0.0.
- Maintain CHANGELOG.md using Keep a Changelog 1.1.0 with an Unreleased section and dated releases.
- Use Semantic Versioning 2.0.0; increment Android versionCode for every published APK.
- Reuse the boris-android publishing setup. Never commit signing secrets, keystores, .env, or local.properties.
- For the initial MVP, the user requested publishing directly for phone testing: do not run local tests or an emulator.
