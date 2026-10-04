# Android search syntax

Android uses the action-free ANTLR grammar and aliases from [ants PR #313](https://github.com/dergigi/ants/pull/313).

| Query | Meaning |
| --- | --- |
| `by:@contacts` | Events by the accounts you follow (login required) |
| `mentions:@contacts` | Events mentioning any account you follow |
| `kind:10002` | Relay-list events, using the generic event view |
| `(bitcoin OR lightning) by:dergigi` | Either text search, restricted to one author |
| `by:(dergigi OR fiatjaf) kind:(1 OR 30023)` | Either author and either kind |
| `(by:dergigi kind:1) OR (by:fiatjaf kind:30023)` | Preserve each author's associated kind |
| `p:"Alice Smith"` | Profile search |
| `"(cats OR dogs)"` | Literal phrase, including parentheses and OR |
| `since:2w (bitcoin OR nostr)` | Same date restriction on both alternatives |

Parentheses may nest. AND binds before OR; spaces also mean AND. Operators are case-insensitive whole tokens. Quote literal operators. Within quotes, `\"` and `\\` escape a quote and backslash. Invalid escapes, unmatched quotes/parentheses, and NOT are rejected. Scoped groups cannot contain other field names.

Supported fields: `by:`/`from:`, `mentions:`, `kind:`, `since:`, `until:`, `p:`, `a:`, `license:`, `site:`, `is:`, `has:`, and `nip:`. Hashtags use `#tag`. Shared aliases expand after parsing, including site domains, media extensions, and kinds. Android also retains `is:note` and `is:notes`. NIP-50 extensions `domain:`, `language:`, `sentiment:`, `nsfw:`, and `include:spam` are passed to relays, which may ignore them.

Use commas or scoped OR for author, mention, and kind alternatives. Repeated authors and kinds intersect; contradictory constraints fail. Repeated since/until bounds narrow the interval. Dates must exist and use UTC; relative units are h/d/w/m/y. Distinct required hashtags and repeated mentions clauses are rejected: use OR instead.

Use `p:example.com` for profiles; bare domains are text. Unquoted HTTP/HTTPS/FTP URLs become domain/path text; quote URLs containing parentheses. Standalone note/nevent/naddr, hex event IDs, and npub/nprofile retain Android direct lookup behavior and relay hints. `@me` and `@contacts` require login. Unknown modifiers, including relay modifiers, fail visibly.

`by:@contacts`, `from:@contacts`, and `mentions:@contacts` expand the latest verified kind-3 public follow list found on your relays. They work in comma lists, scoped OR, and author intersections. Missing or empty lists produce an error, never an unrestricted query. Lookups are cached per account and relay settings for five minutes (empty lists for 30 seconds), with at most 5,000 contacts; larger lists fail explicitly. `/clear` and account changes invalidate the cache. The preview keeps `@contacts` readable instead of printing thousands of keys.

All web `is:` shortcuts are available. Explicit `kind:` values from 0 to 65535 and direct event/address lookups preserve every requested kind. Events without a specialized renderer use paged content and tags, with clickable references and safe web links. Known encrypted payloads remain labeled rather than displayed as plaintext; raw inspection remains available. NIP-94 files expose open links, MIME type, size, and published hash; images include file and imeta URLs. Follow-list events display their public profiles. Broad queries retain native readable defaults; use explicit kinds for protocol/metadata events.

Preview and submission share parsing and planning. Author resolution updates the preview through the existing cached Vertex/profile resolver. Quoted modifier-looking text is never resolved. Android keeps its native-renderable default kinds instead of the web fixtures' kind-1 default.

Limits: 2,000 UTF-16 code units, 16 nested groups, 256 syntax nodes, and 32 branches after alias expansion and safe same-field compaction. Ordinary relay plans run four branches concurrently, with eight-second subscriptions and a 30-second deadline including route discovery. Profile/identifier resolution retains its existing transport budgets. Results are checked against the originating subscription's structured filters and deduplicated by event ID; existing 500-event and memory limits remain. Text/phrase matching is relay-dependent.

Shared fixture checks live in `app/src/test`. They have not been run under the project's device-only validation policy.
