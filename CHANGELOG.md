# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed

- Open to a vertically centered search field with a clickable version footer. Show commands on slash input and move the field above results after searching; remove the start-screen toolbar, examples, and history.

### Fixed

- Focus gallery double-tap zoom on the tapped image area, retaining double-tap zoom-out and recentering.

## [0.11.0] - 2026-10-02

### Added

- Render quoted note and article references as embedded cards, with deduplication, author metadata, retry, and full-note navigation. Fetch only displayed embeds, limit concurrent requests and nesting, and honor secure relay hints.
- Render long-form articles with Markdown headings, lists, quotes, code, tables, links, cover images, and gallery-enabled inline images. Use compact feed previews and open the full article in details.
- Link the footer version to its matching GitHub release.

### Changed

- Bring the terminal-style help panel closer to the web app while retaining the Android search field and its inline search button.
- Make the toolbar help icon execute /help directly. Help shows commands and version information, without random example searches.

### Fixed

- Give footer action icons equal-width slots and consistent separation from the timestamp.

## [0.10.2] - 2026-10-02

### Added

- Add 78 supported search examples from the web app, expanding /examples from 41 to 119 tappable queries. Adapt grouped OR queries and site aliases to Android syntax while preserving login-only filtering and the minimal list layout.

## [0.10.1] - 2026-10-02

### Fixed

- Reduce UI stalls during busy searches by batching relay updates and moving result sorting, profile parsing, linked-profile discovery, and signer-response verification off the main thread.
- Prepare note media and links in the background and bound feed preview text before Android lays it out. Full note text remains available in details.
- Avoid repeated full-URL scans when trimming trailing punctuation.

## [0.10.0] - 2026-10-02

### Added

- Vertex profile discovery with personalized PageRank through the connected external signer, used by p:, by:, from:, and mentions: searches. Relay fallback ranks name matches, verified NIP-05 addresses, follows, and zap activity; profile results retain relevance order.

- Relative since:/until: dates using h, d, w, m, and y, matching web UTC date boundaries and calendar arithmetic. All branches in a search share one reference time; refreshing recalculates it.

### Removed

- Home-screen Search Nostr heading and its extra spacing.

### Changed

- Open web links and highlight source URLs directly instead of searching for them. Nostr mentions and hashtags continue to navigate within ants.

## [0.9.0] - 2026-10-02

### Added

- Resolve linked npub/nprofile mentions and profile URLs into compact, tappable @names across notes, highlights, details, and loaded thread parents, with shortened-key fallbacks.

### Changed

- Replace the home-screen tagline with Search Nostr and remove promotional copy from the app and description.
- Use a single magnifying-glass search button instead of a submit arrow and decorative search icon.

### Removed

- Duplicate slash-command headings beneath the search box.
- Device-only saved searches, including bookmark controls, the saved-search dialog, and previously stored saved-search data.

## [0.8.1] - 2026-10-02

### Changed

- Present examples and command shortcuts as flat, tappable text lists without categories, explanatory subtitles, card backgrounds, or trailing arrows.
- Make /examples the first home-screen suggestion and remove its duplicate shortcut.

## [0.8.0] - 2026-10-02

### Added

- Pull down at the top of search results to rerun the current search, including empty results and tutorial searches.
- Open notes, profiles, and loaded thread parents in installed Nostr apps through Android’s app chooser, excluding ants itself.

## [0.7.0] - 2026-10-02

### Added

- Render NIP-30 custom emoji images in reactions and note text, including animated GIF and SVG assets. Preserve ordinary emoji, links, and shortcode text when an image is unavailable.

### Changed

- Remove explanatory filler from command pages and shorten account/cache status messages.
- Use a simple ants text search as the first home-screen example.
- Limit search results and loaded thread parents to supported native content types. Exclude encrypted/protocol events, raw JSON payloads, and empty or unsupported media cards while preserving reactions and intentional code snippets.
- Request supported kinds from relays so unsupported events do not consume result limits. Show only supported shortcuts and examples, and explain unsupported explicit kind searches.

### Fixed

- Never fall back to raw profile JSON when a profile has no bio.

## [0.6.0] - 2026-10-02

### Added

- Inline, tap-to-play video players for linked MP4, WebM, MOV/M4V, Matroska, and other supported file formats, plus videos declared in event media tags. Show native playback and seeking controls, retry, and browser fallback without duplicate video URLs.
- Pause videos when leaving the screen or app, release inactive players, and allow only one video to play at a time. Video decoding depends on the device's supported codecs.


### Changed

- Load thread ancestors progressively above the search result: each tapped bar is replaced by its parent note, with the next load bar above the oldest visible note. Remove expand/collapse controls and nested depth limits.

## [0.5.0] - 2026-10-02

### Added

- Persistent top-right account avatar with profile, own posts, mentions, and logout actions; signed-out users can connect their external signer from the same control.
- Native account profile panel with name, bio, public key copying, and icon actions. Fetch verified account metadata on startup and login, with an offline avatar fallback.
- Expandable parent bars above replies and reactions. Follow nested ancestors inline, collapse them, retry unavailable notes, or open a parent as its own search.
- Recognize marked and legacy text-note replies and event-ID parents in NIP-22 comments, without treating explicit mentions as replies.
- Slash-command suggestions while typing: enter / to browse all seven commands, narrow by prefix, and tap to execute.

### Changed

- Replace the separate reaction-target card below reactions with a compact context bar above the content.

## [0.4.0] - 2026-10-02

### Added

- All seven web slash commands: /help, /examples, /kinds, /login, /logout, /clear, and /tutorial, handled natively without sending commands as relay search terms.
- Tappable help examples, grouped executable searches, and complete web kind shortcuts, including multi-kind video and media searches.
- Minimal account icon and external Android signer connection, including Amber, for by:@me and mentions:@me searches. Only the public key and signer package are stored; logout removes them.
- Cache clearing that preserves the account, saved searches, history, settings, and downloaded pictures, plus native tutorial-event lookup.
- Reject pasted nsec/ncryptsec values before storing searches or sending queries to relays. No secret-key entry or import.

### Fixed

- Clear stale connection messages when navigating back to the account page.

## [0.3.0] - 2026-10-02

### Added

- Boris-style fullscreen image gallery with swiping, pinch/double-tap zoom, previous/next controls, background switching, save/share actions, download-all, URL copying, and loading retry.
- Image downloads to Pictures and binary image sharing using temporary content URIs.
- Reaction results show the reaction above a verified preview of its target, with native links to the post and author. Missing targets can be opened for a direct lookup.

## [0.2.0] - 2026-10-02

### Added

- Follow hashtags, Nostr mentions, quoted-note references, web links, and highlight sources into native searches. Source authors also open inside ants.
- In-session search navigation history: Back restores cached results, list position, and open event details without fetching again. The ants logo still returns directly home.

### Changed

- Use njump.to for outbound Nostr links while continuing to recognize links from both njump domains.

### Fixed

- Keep new searches at the newest result as relays stream in. Queue incoming results while reading so the visible list stays unchanged, with an icon to reveal them and jump to the newest result.
- Hide URLs for rendered images in cards and details while preserving captions and ordinary links; show multiple attached images and an open-image fallback on loading failure.
- Handle source URLs containing parentheses and Nostr addresses with empty identifiers.

## [0.1.1] - 2026-10-02

### Changed

- Match web ants with charcoal cards, subtle borders, compact kind icons, author footers, and blue source links.
- Prefer icon actions with accessibility labels and long-press tooltips for copying, opening, sharing, stopping searches, and inspecting events.

- Render highlights as warm yellow marked passages with surrounding context, source links, and source-author attribution in results and details.

### Fixed

- Return to the start screen by tapping the ant / ants wordmark or pressing Back from results; cancel the active search and reset scroll without losing saved searches or history.

- Draw highlight underlines only on the lines they touch, keeping long passages responsive.

- Stop publishing if certificate proof generation fails, preventing empty signer output from reaching the relay publisher.

## [0.1.0] - 2026-10-02

### Added

- Initial native Android MVP for Nostr search and discovery.
- Nostr relay search with signature verification, OR queries, hashtags, author and kind filters, date ranges, and direct event lookup.

- Native dark interface with result details, images, profile names, sharing, raw event inspection, saved searches, and local search history.
- Editable search relays, connection status, search cancellation, and incoming shared text / Nostr links.

- Signed APK release and Zapstore publishing using the Boris publishing setup.

[Unreleased]: https://github.com/dergigi/ants-android/compare/v0.11.0...HEAD
[0.11.0]: https://github.com/dergigi/ants-android/compare/v0.10.2...v0.11.0
[0.10.2]: https://github.com/dergigi/ants-android/compare/v0.10.1...v0.10.2
[0.10.1]: https://github.com/dergigi/ants-android/compare/v0.10.0...v0.10.1
[0.10.0]: https://github.com/dergigi/ants-android/compare/v0.9.0...v0.10.0
[0.9.0]: https://github.com/dergigi/ants-android/compare/v0.8.1...v0.9.0
[0.8.1]: https://github.com/dergigi/ants-android/compare/v0.8.0...v0.8.1
[0.8.0]: https://github.com/dergigi/ants-android/compare/v0.7.0...v0.8.0
[0.7.0]: https://github.com/dergigi/ants-android/compare/v0.6.0...v0.7.0
[0.6.0]: https://github.com/dergigi/ants-android/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/dergigi/ants-android/compare/v0.4.0...v0.5.0
[0.4.0]: https://github.com/dergigi/ants-android/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/dergigi/ants-android/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/dergigi/ants-android/compare/v0.1.1...v0.2.0
[0.1.1]: https://github.com/dergigi/ants-android/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/dergigi/ants-android/releases/tag/v0.1.0
