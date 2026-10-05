# Result filters

The filter icon beside the result sort control opens a native menu. Changes apply to the retained results without another relay search. Counts show visible / fetched results when they differ. Clear and reset actions use icons with tooltips; switches and numeric limits have labels.

The implementation follows the web app at commit `23dc652870926ed622a74d9c179dade6a210d86b`: `src/lib/contentAnalysis.ts`, `src/hooks/useResultPipeline.ts`, `src/components/ClientFilters.tsx`, and their URL and identifier helpers.

| Setting | Web and Android behavior |
| --- | --- |
| Smart (default) | Apply filters at 69 or more fetched results |
| Always / Never | Apply filters at any count / bypass them |
| Emojis | Default maximum 3; count whole emoji sequences using emoji-regex 9.2.2 |
| Emoji searches | In Smart mode, two or more emojis in the search query bypass the emoji limit |
| Hashtags | Default maximum 3; count `#[A-Za-z0-9_]+` occurrences in content, including repeats |
| Mentions | Default maximum 6; count ASCII `@username` occurrences plus distinct NIP-19 identifiers, including identifiers in decoded URLs |
| Bridged accounts | Hidden by default if the author's NIP-05 contains `mostr.pub`, `mastodon`, `bluesky`, `bsky.app`, or `bsky.social`, ignoring case |
| Bots | Optional; hide metadata with boolean `bot`/`is_bot`, or the whole words `bot`, `automated`, or `autopost` in the author's about text |
| NSFW | Optional; hide content containing `nsfw` or `nude`, ignoring case, as the web does |
| External links | Optional; exclude HTTP(S) links except the web's image/audio/video filename extensions, including `filename` and `name` URL parameters |
| Valid NIP-05 | Optional; hide unverified authors. Verification is bounded to authors in the first 50 results, matching the web's lookup bound |
| Text | Optional fuzzy content match using Fuse's Bitap algorithm, threshold 0.35, ignoring match location |

Limits exclude counts strictly greater than the configured maximum. Each limit can be disabled separately. Bot, NSFW, external-link, and verified-only switches start off. Text filtering starts enabled with an empty query. Clear disables everything; reset restores these defaults.

Settings last for the app session and apply when navigating between searches. Slash-command output is not filtered. Author-based filters update as profile metadata arrives; unknown profiles are retained unless verified-only is enabled. Android verifies NIP-05 claims through its resolver instead of trusting a `verified` value supplied inside profile metadata. It only starts the bounded verification requests when the verified-only filter is active.

The raw event set remains bounded by the existing result and memory limits. Content analysis runs off the main thread and caches counts for up to 600 event IDs. Filter changes do not fetch more results to replace hidden events. Fuzzy matches retain chronological sorting for notes; matching profiles use Fuse's score and field-length normalization.

## Shared dependencies

`app/src/main/resources/emoji-regex-9.2.2.txt` comes from the web checkout's `emoji-regex/es2015/index.js`. Only JavaScript `\\u{...}` code-point escapes were changed to Java `\\x{...}` escapes. This is a committed resource, not a dependency downloaded during builds.

`ResultFuzzyFilter.kt` ports the matching subset of Fuse.js 7.1.0 with the web's options. License notices for both dependencies are in `licenses/` and packaged in the APK under `META-INF/`.

Boundary and regression checks are in `ContentFiltersTest.kt`. They have not been run under the project's no-local-tests policy. Device validation should cover toggling filters, streaming past 69 results, metadata arriving after notes, all results hidden, navigation, and restoring defaults.

Encrypted content is hidden by default in Smart and Always modes, including below 69 results. Never mode or disabling Hide encrypted content reveals placeholders; raw event inspection remains available. Detection covers known encrypted kinds and kind-1 content consisting entirely of at least 128 Base64 characters decoding to non-UTF-8 binary. This is a payload heuristic, not proof of encryption; mixed prose and encoded plain text are left alone.
