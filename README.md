# ants for Android

Follow your curiosity. A native Android search and discovery app for Nostr, based on [ants](https://github.com/dergigi/ants). Kotlin, Jetpack Compose, direct relay connections. No account required.

[Get it on Zapstore](https://zapstore.dev/apps/org.dergigi.ants) · [Download APK](https://github.com/dergigi/ants-android/releases)

## MVP

- Text search across configurable NIP-50 relays, with deduplicated, signature-verified results.
- Hashtags, author and mention filters, NIP-05 resolution, kinds, date ranges, simple OR queries, and image/video filters.
- Direct lookup of `npub`, `nprofile`, `note`, `nevent`, `naddr`, and event hex IDs.
- Profile names and avatars, inline images, full event text, raw JSON, copying and sharing.
- Device-local saved searches and recent history.
- Receive shared text, `nostr:` links, and `ants.sh` links.
- Relay status, cancellation, retry, and editable search relays.

Try `bitcoin`, `#asknostr`, `GM by:dergigi`, `p:fiatjaf`, `is:highlight`, `by:name@example.com`, `kind:30023 since:2026-01-01`, or `bitcoin OR lightning`.

The initial release is read-only. It does not include login, posting, zaps, grouped boolean expressions, reverse image search, offline result caching, or pagination. Up to 100 candidates are requested per query per relay, and results are capped at 500. Media filters operate on returned candidates. Search semantics and coverage depend on the relay. Dates use UTC, with `until:` including the specified day. Profile metadata can arrive after results.

## Privacy

Search queries go directly to configured relays. Author aliases / NIP-05 addresses are resolved over HTTPS. Direct lookups also use Damus, nos.lol, and Primal; profile metadata is fetched from purplepag.es and Damus. Images load from their hosts. Saved searches, history, and settings remain on the device; no analytics or account keys are collected. Clear recent searches from the home screen. Android backups are disabled.

## Build

Requires JDK 17 and Android SDK 35. Set `sdk.dir` in gitignored `local.properties`.

```sh
./gradlew :app:assembleDebug
```

For a signed release, set these in `local.properties` (or environment variables):

```properties
OEM_STORE_FILE=/absolute/path/to/upload.jks
OEM_STORE_PASSWORD=…
OEM_KEY_ALIAS=upload
OEM_KEY_PASSWORD=…
```

```sh
./gradlew :app:assembleRelease
```

The first release was compiled without running local tests or an emulator, as requested. Runtime feedback comes from installation on a physical phone.

## Release

Use [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/) after every implementation step. Maintain [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) entries in `CHANGELOG.md` and [Semantic Versioning](https://semver.org/) versions in `app/build.gradle.kts`. Increment `versionCode` for every published APK.

1. Move Unreleased changes into a dated version section; update `versionName` and `versionCode`.
2. Commit the release preparation and build the signed APK.
3. Tag `vX.Y.Z`, push the source and tag, and create a GitHub release with the APK and that version's changelog notes.
4. Run `./scripts/zapstore-publish.sh` with `SIGN_WITH` configured in the environment or gitignored `.env`.

Publishing follows [boris-android](https://github.com/dergigi/boris-android): `zsp`, the same publisher npub, APK signing certificate identity linking, and repository metadata in `zapstore.yaml`. The script publishes the local APK using the YAML config, preserving changelog release notes. Requires `zsp`, `nak`, and `gh` on PATH (or `zsp` in `~/bin`). Credentials and keystores are never committed.

## License

[MIT](LICENSE). Branding and the original search concept come from [ants](https://github.com/dergigi/ants); Nostr event and identifier helpers are adapted from [Boris](https://github.com/dergigi/boris-android).
