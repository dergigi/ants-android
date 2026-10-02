# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed

- Match web ants with charcoal cards, subtle borders, compact kind icons, author footers, and blue source links.
- Prefer icon actions with accessibility labels and long-press tooltips for copying, opening, sharing, stopping searches, and inspecting events.

- Render highlights as warm yellow marked passages with surrounding context, source links, and source-author attribution in results and details.

### Fixed

- Stop publishing if certificate proof generation fails, preventing empty signer output from reaching the relay publisher.

## [0.1.0] - 2026-10-02

### Added

- Initial native Android MVP for Nostr search and discovery.
- Nostr relay search with signature verification, OR queries, hashtags, author and kind filters, date ranges, and direct event lookup.

- Native dark interface with result details, images, profile names, sharing, raw event inspection, saved searches, and local search history.
- Editable search relays, connection status, search cancellation, and incoming shared text / Nostr links.

- Signed APK release and Zapstore publishing using the Boris publishing setup.

[Unreleased]: https://github.com/dergigi/ants-android/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/dergigi/ants-android/releases/tag/v0.1.0
