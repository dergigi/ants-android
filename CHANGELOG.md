# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

[Unreleased]: https://github.com/dergigi/ants-android/compare/v0.3.0...HEAD
[0.3.0]: https://github.com/dergigi/ants-android/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/dergigi/ants-android/compare/v0.1.1...v0.2.0
[0.1.1]: https://github.com/dergigi/ants-android/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/dergigi/ants-android/releases/tag/v0.1.0
