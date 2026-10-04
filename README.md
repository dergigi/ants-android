# ants for Android

A native Android search and discovery app for Nostr, based on [ants](https://github.com/dergigi/ants). Kotlin, Jetpack Compose, direct relay connections. No account required.

[Get it on Zapstore](https://zapstore.dev/apps/org.dergigi.ants) · [Download APK](https://github.com/dergigi/ants-android/releases)

## MVP

- Vertex results, direct profile lookups, and resolved names are cached in memory for five minutes; empty results expire after 30 seconds. Identical concurrent lookups share work. `/clear` and account changes reset discovery caches.
- Structured queries use NIP-65 author write relays and mention read relays, with secure pointer hints and fallback relays. Relay lists stay in a bounded memory cache (10 minutes; missing lists retry after 1 minute) and `/clear` clears them.
- Text search across configurable NIP-50 relays, with deduplicated, signature-verified results. Broad queries show native readable kinds. Explicit numeric kinds and direct event lookups include other event types through a bounded content-and-tags view; encrypted payloads are labeled. All web kind shortcuts are supported.
- Single-profile searches show the profile card above its author feed, newest first across supported kinds, with up to 500 events. Multi-profile searches remain profile lists.
- File attachments expose open links, MIME type, size, and published hash, including PDFs and other non-media files. Image metadata supports file URLs and imeta tags.
- Native cards for public follow lists, reposts, Git patches/issues, reports, zap receipts/nutzaps, public mute/pin/bookmark lists, and follow packs. List entries link to notes/profiles; encrypted entries remain hidden. Payment cards display published amounts without claiming independent payment verification.
- Hashtags, author and mention filters, NIP-05 resolution, kinds, date ranges, nested AND/OR queries and scoped fields, and image/video filters.
- Direct lookup of `npub`, `nprofile`, `note`, `nevent`, `naddr`, and event hex IDs.
- Custom emoji images in reactions and note text, including animated GIF and SVG assets, with shortcode fallback on loading failure.
- Profile names and avatars, inline images without duplicate URLs, full event text, raw JSON, copying and sharing.
- Linked videos play inline with native play/pause and seeking controls. MP4, WebM, MOV/M4V, Matroska, and other supported file formats are detected, including video media tags. Playback starts on tap, pauses offscreen or in the background, and offers retry/browser fallback; codec support depends on your phone. Rendered video URLs are hidden, with up to four players per result and twenty in details.
- Tap images for a Boris-style gallery: swipe, pinch/double-tap zoom, previous/next, background switching, save one/all, share the image file, or copy/open its URL.
- Replies and reactions show a compact parent bar above their content. Tap to prepend the verified parent note, replacing its bar. Keep tapping the new top bar to load earlier notes toward the root; unavailable notes can be retried. Supports text-note reply markers, legacy replies, and event-ID parents in comments. Loaded ancestors form a flat timeline above the original result, without nested expand/collapse controls.
- Tappable hashtags, Nostr mentions, and quoted-note references lead to native searches. Web links and highlight source URLs open directly. Linked profiles resolve to compact @names, with shortened public keys when metadata is unavailable.
- Back navigation restores results, scroll position, and event details within the current session; tap the ants logo to return home.
- New searches stay at the top while results arrive. Scrolling pauses updates to the visible list; tap the new-results icon to reveal queued results.
- Type `/` in the search box to browse all commands, narrow by prefix, and tap to execute.
- All web slash commands: `/help`, `/examples`, `/kinds`, `/login`, `/logout`, `/clear`, and `/tutorial`, with tappable help examples and kind shortcuts.
- Top-right account avatar on every screen, with a native profile panel, own posts, mentions, public-key copying, and logout. Account profile metadata loads on startup and login.
- Optional external Android signer connection (such as Amber) for `by:@me`, `mentions:@me`, `by:@contacts`, and `mentions:@contacts`. Contact searches use the latest verified public follow list, cache it for five minutes, and support up to 5,000 contacts; missing or empty lists fail explicitly. No secret-key entry or import.
- Recent search history.
- Receive shared text, `nostr:` links, and `ants.sh` links.
- Relay status, cancellation, retry, pull-to-refresh, and editable search relays.
- Open events in installed Nostr apps using the phone icon; Android offers compatible handlers and excludes ants from the chooser.

See [Search syntax](docs/SEARCH_SYNTAX.md) for grouping, aliases, errors, and limits.

Try `ants`, `#asknostr`, `GM by:dergigi`, `p:fiatjaf`, `is:highlight`, `by:name@example.com`, `kind:30023 since:2026-01-01`, or `bitcoin OR lightning`.

The app does not post notes. Signer connection enables account-relative searches and signed Vertex profile search requests through your external Android signer. Profile searches use personalized PageRank when Vertex is available, with relay search as a fallback. Fallback ranking combines name matching, verified NIP-05 addresses, follows, and zap activity. Username filters prefer verified matches. Profile discovery results are cached in memory for five minutes and cleared with /clear or logout; keys are never imported. It does not include posting, zaps, reverse image search, persistent offline result caching, or pagination. Navigation history is held in memory (up to 20 prior screens, further bounded by retained content size) and does not survive process termination. Text searches request up to 100 candidates per query per relay; structured queries without text search request up to 500. Results are capped at 500. Media shortcuts expand into extension text searches through the shared alias table. Search semantics and coverage depend on the relay. Dates use UTC. Both `since:` and `until:` accept `YYYY-MM-DD` or relative values such as `12h`, `3d`, `2w`, `1m`, and `1y`. Hours use an exact offset from the current time; days, weeks, months, and years resolve to calendar dates, with `since:` at the start and `until:` at the end of the day. Months and years follow the web app’s overflow behavior for short months and leap days. Refreshing recalculates relative dates. Profile metadata and reaction previews can arrive after results. Up to 100 distinct reaction targets are fetched per search; any target can also be tapped for a direct lookup. Galleries include up to 100 images per event. Visible quoted notes load automatically (up to eight references per note, two embedded levels, three concurrent lookups, and 100 cached quotes per search). Unavailable quotes can be retried or opened directly. Long-form articles use compact feed previews and formatted Markdown in details.

## Privacy

Note searches go directly to configured relays. Signed-in profile and username searches also send the search term and public key to Vertex over HTTPS. Profile fallback uses search relays and Vertex’s NIP-50 endpoint; ranking looks up public follow lists and zap activity. NIP-05 addresses are verified over HTTPS. Direct lookups also use Damus, nos.lol, and Primal; profile metadata is fetched from purplepag.es and Damus. Images load from their hosts; videos stream from their hosts when you tap play. History and settings remain on the device; no analytics or private keys are collected. Optional signer connection stores only the public key and signer package locally; `/logout` removes both. `/clear` clears cached results, profiles, and images while preserving the account, history, settings, and downloaded pictures. Open or clear recent searches with the history button or `/history`. Saved images go to Pictures/ants on Android 10+ (Pictures on older versions, which request storage permission). Image sharing downloads a temporary file into app cache and grants the receiving app access to that file. Android backups are disabled.

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

## Crash reports

After a crash, ants offers a local report containing the app version, Android/device information, stack trace, screen type, and last rendered highlight ID when available. You can review, copy, dismiss, or explicitly send it to `@ants.sh` as an encrypted NIP-17 DM from a one-time key. Reports do not include your account key, query, or search history. Nothing is sent automatically. Reports are excluded from backups and deleted after successful sending or dismissal.
