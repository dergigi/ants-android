# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.23.2] - 2026-10-03

### Fixed

- Limit decoded feed images to 1536 px per dimension, use a small shared image cache and two concurrent decoders, and clear cached images on memory pressure. Gallery images use a separate 2048 px request without retaining full-size gallery bitmaps in the feed cache; downloads keep original files.

- Bound relay result queues, retained search payloads, thread/quote context, back navigation, and profile caches by estimated bytes as well as counts. Reduce buffered relay events and cap cached profile text to limit heap growth during large searches.

## [0.23.1] - 2026-10-03

### Fixed

- Prioritize relevant name matches for fallback profile verification and activity checks instead of metadata recency. Treat unavailable NIP-05 lookups as unknown rather than identity mismatches.

- Preserve successful Vertex rankings when outbox discovery and profile downloads take longer than the Vertex request timeout. Give profile hydration its own relay budgets and keep Vertex order.

## [0.23.0] - 2026-10-03

### Added

- Cache Vertex results independently of relay settings, direct profile metadata, and resolved author names for five minutes. Coalesce concurrent identical lookups, expire empty results after 30 seconds, bound memory use, and clear caches on `/clear` or account changes.

- Add cached NIP-65 relay discovery and per-relay query routing: authors use advertised write relays, mentions use read relays, and NIP-50 branches stay on search relays. Retain fallback coverage, signed-event verification, bounded connections, and cancellation.

## [0.22.0] - 2026-10-03

### Added

- Replace result-order text with a clock/sort icon toggle for newest or oldest first. Sort the loaded results immediately, keep single-profile cards first, preserve order through refresh and back navigation, and default new searches to newest first.

- Make result-card kind icons search their corresponding `is:` keyword (or numeric kind for comments), with accessible touch targets and query tooltips. Reply/reaction header icons search by kind while the rest of the header still loads the parent.

- Show an author’s feed beneath their profile card when a completed profile search has exactly one match. Keep the latest profile card first, load up to 500 events across supported kinds, and retain stop, refresh, and back-navigation behavior.

## [0.21.0] - 2026-10-03

### Fixed

- Prepare report targets off the main thread, bound embedded reference lookups, and open unsupported addressable targets such as Git repositories in the browser.

- Require 48 dp of deliberate scroll movement before hiding or restoring the search controls. Small direction reversals and separate drags no longer make the header flicker.

### Added

- Render zap receipts and nutzaps with published amounts, sender/recipient profiles, comments, mint links, and embedded targets. Validate included zap requests before attributing their sender; identify amounts as unverified rather than claiming payment settlement. Enable both search shortcuts.

- Render Git patches with colored diffs, issues with Markdown, and reports with their reasons and clickable targets. Paginate long patches and enable patch, issue, and report searches.

- Render reposts as embedded original notes, validating included note signatures and loading missing originals from relays with retry and bounded nesting.

- Render public mute lists, pinned notes, bookmarks, and follow packs with clickable entries, resolved profile avatars, cover images, and paged member lists. Keep encrypted entries private and enable their search shortcuts.

## [0.20.1] - 2026-10-03

### Changed

- Use the same keyword-only search shortcut list in account and profile menus, matching the web app’s `is:` aliases and ordering. Scope searches to the selected profile or `@me`, use `mentions:` for mention searches and `/logout` for sign-out, and disable kinds without native renderers.

## [0.20.0] - 2026-10-03

### Changed

- Hide the toolbar and search controls when scrolling down through results; reveal them immediately when scrolling upward. Keep controls visible during query editing and reset visibility for each search.

- Show up to two lines of collapsed query translations. Align the translation chevron and stop/refresh controls to the same 48 dp row in collapsed and expanded states, keeping refresh in the stop button’s position when a search finishes.

## [0.19.0] - 2026-10-03

### Changed

- Collapse long query translations to one line with an ellipsis and expand chevron. Tap to expand or collapse; expanded text remains selectable and scrollable, and each new search starts collapsed.

### Fixed

- Request up to 500 candidates per relay for non-text queries such as author, kind, and hashtag filters, and prevent fast relay result bursts from being silently dropped by a full delivery channel.

- Keep incoming results visible and included in the result count after scrolling, rather than hiding the rest of the search behind the jump-to-newest button. Preserve reading position with stable note keys and pause top anchoring only on a user drag, not keyboard-driven scrolling.

## [0.18.0] - 2026-10-03

### Changed

- Combine the loading spinner and stop button into one control beside the query translation, removing the empty results row while awaiting results. Keep stop available while editing a running query.

- Open profile-card pictures and banners in the image gallery, starting at the tapped image and allowing swiping between both, zooming, and downloading.

## [0.17.0] - 2026-10-03

### Changed

- Show query translations immediately on submission, expanding local aliases and dates before network lookups and updating names as they resolve. Show a small spinner beside the translation while searching instead of “Searching…” text and a full-width progress bar.

- Collapse profile card footers to one row. Move author/mention searches, all supported content-kind searches, domain search, website, browser, and event details into a three-dot overflow menu; keep identity and Lightning indicators and the native-app shortcut visible.

## [0.16.0] - 2026-10-03

### Added

- Show the resolved query below the search field with a muted equals sign and monospace text, including numeric kinds, resolved public keys, dates, and OR branches. Reuse the executed query’s resolution and restore translations with navigation history.

- Match the web profile indicators: verified NIP-05 identities in green, mismatches in red, missing identities in yellow, and root-domain double checks. Add domain-search, Lightning, and website shortcuts with long-press labels. Lightning bolts indicate sent zaps (yellow), sent nutzaps (purple), or both (green), using bounded background lookups cached in memory.

### Changed

- Make timestamps in result cards and event details open a search for the event’s `nostr:note` identifier.

## [0.15.1] - 2026-10-02

### Fixed

- Fix the immediate crash when rendering highlights (including `is:highlight "proof of work"`). Standalone highlight cards now supply their Markdown styles explicitly instead of reading missing Markdown composition locals.

## [0.15.0] - 2026-10-02

### Added

- Offer crash reports on the next launch, with review, copy, dismiss, and explicit Send actions. Reports go to @ants.sh as encrypted NIP-17 messages from a one-time key, using the recipient’s inbox relays. No login is required and nothing is sent automatically.

### Fixed

- Harden highlight rendering against Markdown and annotation failures with a readable fallback. Use native underline spans instead of manual text-layout offset drawing when search results appear or profile mentions update.

## [0.14.1] - 2026-10-02

### Fixed

- Hide the ants logo and wordmark whenever the search toolbar shows a back button.

## [0.14.0] - 2026-10-02

### Added

- Render profile search results as native profile cards with avatars, optional banners, names, bios, website and Lightning details, and icon actions for posts, mentions, copying the public key, and opening profiles in other apps or the browser. Author taps, profile mentions, account-menu profiles, and pasted public keys open profile cards.
- Render article footnotes as numbered references and formatted notes, with tap-to-jump navigation and a return-to-reference action. Preserve code examples and support multi-paragraph footnotes.

### Changed

- Use the /help terminal-panel style for all slash-command output: examples, history, kinds, login/logout, cache clearing, tutorial content, and command errors. Keep long lists lazy, queries tappable, and examples free of categories and descriptions.

## [0.13.1] - 2026-10-02

### Fixed

- Resolve visible npub/nprofile mentions immediately through batched metadata lookups, including highlight context and comments. Use displayName metadata as well as display_name and name, updating mentions to clickable profile names as metadata arrives.

- Keep article Markdown parsing state stable during recomposition so scrolling no longer restarts parsing, collapses the article, and resets the scroll position. Save detail scroll positions after scrolling settles instead of updating the entire app on every scroll movement.

## [0.13.0] - 2026-10-02

### Added

- Rotate search placeholders through supported commands and examples every seven seconds, starting with /examples. Show the web-style countdown ring, tap it for the next example, and submit the displayed placeholder with the search button or keyboard when the field is empty. Pause rotation during input, loading, and background activity; hide login-only examples when signed out.

### Changed

- Add a logo-only home control at the top left of the start screen and history, help, and login/account controls at the top right, keeping the search field centered.
- Show the ant logo in grayscale when logged out and its original blue when logged in, on both the start screen and search toolbar.

## [0.12.0] - 2026-10-02

### Added

- Add a top-right history button and /history command for opening, rerunning, and clearing recent searches.

### Changed

- Open to a vertically centered search field with a clickable version footer. Show commands on slash input and move the field above results after searching; remove the start-screen toolbar, examples, and inline history list.

### Fixed

- Render Markdown in highlight passages, context, and comments. Preserve link labels, emphasis, and the gold selection after formatting and profile-mention replacement; prepare content off the UI thread.

- Open long-form articles in a full-screen scrollable reader with a back button instead of a bottom-sheet overlay, including articles opened from embedded cards.

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

[Unreleased]: https://github.com/dergigi/ants-android/compare/v0.23.2...HEAD
[0.23.2]: https://github.com/dergigi/ants-android/compare/v0.23.1...v0.23.2
[0.23.1]: https://github.com/dergigi/ants-android/compare/v0.23.0...v0.23.1
[0.23.0]: https://github.com/dergigi/ants-android/compare/v0.22.0...v0.23.0
[0.22.0]: https://github.com/dergigi/ants-android/compare/v0.21.0...v0.22.0
[0.21.0]: https://github.com/dergigi/ants-android/compare/v0.20.1...v0.21.0
[0.20.1]: https://github.com/dergigi/ants-android/compare/v0.20.0...v0.20.1
[0.20.0]: https://github.com/dergigi/ants-android/compare/v0.19.0...v0.20.0
[0.19.0]: https://github.com/dergigi/ants-android/compare/v0.18.0...v0.19.0
[0.18.0]: https://github.com/dergigi/ants-android/compare/v0.17.0...v0.18.0
[0.17.0]: https://github.com/dergigi/ants-android/compare/v0.16.0...v0.17.0
[0.16.0]: https://github.com/dergigi/ants-android/compare/v0.15.1...v0.16.0
[0.15.1]: https://github.com/dergigi/ants-android/compare/v0.15.0...v0.15.1
[0.15.0]: https://github.com/dergigi/ants-android/compare/v0.14.1...v0.15.0
[0.14.1]: https://github.com/dergigi/ants-android/compare/v0.14.0...v0.14.1
[0.14.0]: https://github.com/dergigi/ants-android/compare/v0.13.1...v0.14.0
[0.13.1]: https://github.com/dergigi/ants-android/compare/v0.13.0...v0.13.1
[0.13.0]: https://github.com/dergigi/ants-android/compare/v0.12.0...v0.13.0
[0.12.0]: https://github.com/dergigi/ants-android/compare/v0.11.0...v0.12.0
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
