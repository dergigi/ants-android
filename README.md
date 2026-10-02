# ants for Android

Follow your curiosity. A native Android search and discovery app for Nostr, based on [ants](https://github.com/dergigi/ants). Kotlin, Jetpack Compose, direct relay connections. No account required.

[Get it on Zapstore](https://zapstore.dev/apps/org.dergigi.ants) · [Download APK](https://github.com/dergigi/ants-android/releases)

## MVP

- Text search across configurable NIP-50 relays, with deduplicated, signature-verified results. Only supported native content types are shown; encrypted/protocol events, raw JSON payloads (except intentional code snippets), and empty media cards are excluded.
- Hashtags, author and mention filters, NIP-05 resolution, kinds, date ranges, simple OR queries, and image/video filters.
- Direct lookup of `npub`, `nprofile`, `note`, `nevent`, `naddr`, and event hex IDs.
- Custom emoji images in reactions and note text, including animated GIF and SVG assets, with shortcode fallback on loading failure.
- Profile names and avatars, inline images without duplicate URLs, full event text, raw JSON, copying and sharing.
- Linked videos play inline with native play/pause and seeking controls. MP4, WebM, MOV/M4V, Matroska, and other supported file formats are detected, including video media tags. Playback starts on tap, pauses offscreen or in the background, and offers retry/browser fallback; codec support depends on your phone. Rendered video URLs are hidden, with up to four players per result and twenty in details.
- Tap images for a Boris-style gallery: swipe, pinch/double-tap zoom, previous/next, background switching, save one/all, share the image file, or copy/open its URL.
- Replies and reactions show a compact parent bar above their content. Tap to prepend the verified parent note, replacing its bar. Keep tapping the new top bar to load earlier notes toward the root; unavailable notes can be retried. Supports text-note reply markers, legacy replies, and event-ID parents in comments. Loaded ancestors form a flat timeline above the original result, without nested expand/collapse controls.
- Tappable hashtags, Nostr mentions, quoted-note references, web links, and highlight sources that lead to native searches.
- Back navigation restores results, scroll position, and event details within the current session; tap the ants logo to return home.
- New searches stay at the top while results arrive. Scrolling pauses updates to the visible list; tap the new-results icon to reveal queued results.
- Type `/` in the search box to browse all commands, narrow by prefix, and tap to execute.
- All web slash commands: `/help`, `/examples`, `/kinds`, `/login`, `/logout`, `/clear`, and `/tutorial`, with tappable help examples and kind shortcuts.
- Top-right account avatar on every screen, with a native profile panel, own posts, mentions, public-key copying, and logout. Account profile metadata loads on startup and login.
- Optional external Android signer connection (such as Amber) for `by:@me` and `mentions:@me`. No secret-key entry or import.
- Device-local saved searches and recent history.
- Receive shared text, `nostr:` links, and `ants.sh` links.
- Relay status, cancellation, retry, and editable search relays.

Try `ants`, `#asknostr`, `GM by:dergigi`, `p:fiatjaf`, `is:highlight`, `by:name@example.com`, `kind:30023 since:2026-01-01`, or `bitcoin OR lightning`.

The app remains read-only. Signer connection enables account-relative searches; it does not request event signing or authenticate to relays. It does not include posting, zaps, grouped boolean expressions, reverse image search, persistent offline result caching, or pagination. Navigation history is held in memory (up to 20 prior screens, further bounded by retained content size) and does not survive process termination. Up to 100 candidates are requested per query per relay, and results are capped at 500. Media filters operate on returned candidates. Search semantics and coverage depend on the relay. Dates use UTC, with `until:` including the specified day. Profile metadata and reaction previews can arrive after results. Up to 100 distinct reaction targets are fetched per search; any target can also be tapped for a direct lookup. Galleries include up to 100 images per event.

## Privacy

Search queries go directly to configured relays. Author aliases / NIP-05 addresses are resolved over HTTPS. Direct lookups also use Damus, nos.lol, and Primal; profile metadata is fetched from purplepag.es and Damus. Images load from their hosts; videos stream from their hosts when you tap play. Saved searches, history, and settings remain on the device; no analytics or private keys are collected. Optional signer connection stores only the public key and signer package locally; `/logout` removes both. `/clear` clears cached results, profiles, and images while preserving the account, saved searches, history, settings, and downloaded pictures. Clear recent searches from the home screen. Saved images go to Pictures/ants on Android 10+ (Pictures on older versions, which request storage permission). Image sharing downloads a temporary file into app cache and grants the receiving app access to that file. Android backups are disabled.

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

Publishing follows [boris-android](https://github.com/dergigi/boris-android): `zsp`, the same publisher npub, APK signing certificate identity linking, and repository metadata in `zapstore.yaml`. The script publishes the local APK using the YAML config, preserving changelog release notes. Requires `zsp`, `nak`, and `gh` on PATH (or `zsp` in `~/bin`). Credentials and keystores are never committed. For a PKCS#12 keystore, use a `.p12` extension (or place a local copy at `keystore/upload.p12`); `zsp` selects its keystore parser by extension.

## License

[MIT](LICENSE). Branding and the original search concept come from [ants](https://github.com/dergigi/ants); Nostr event and identifier helpers are adapted from [Boris](https://github.com/dergigi/boris-android).
