# Android search syntax

Android uses the action-free ANTLR grammar and aliases from [ants PR #313](https://github.com/dergigi/ants/pull/313).

| Query | Meaning |
| --- | --- |
| `(bitcoin OR lightning) by:dergigi` | Either text search, restricted to one author |
| `by:(dergigi OR fiatjaf) kind:(1 OR 30023)` | Either author and either kind |
| `(by:dergigi kind:1) OR (by:fiatjaf kind:30023)` | Preserve each author's associated kind |
| `p:"Alice Smith"` | Profile search |
| `"(cats OR dogs)"` | Literal phrase, including parentheses and OR |
| `since:2w (bitcoin OR nostr)` | Same date restriction on both alternatives |

Parentheses may nest. AND binds before OR; spaces also mean AND. Operators are case-insensitive whole tokens. Quote literal operators. Within quotes, `\"` and `\\` escape a quote and backslash. Invalid escapes, unmatched quotes/parentheses, and NOT are rejected. Scoped groups cannot contain other field names.

Supported fields: `by:`/`from:`, `mentions:`, `kind:`, `since:`, `until:`, `p:`, `a:`, `license:`, `site:`, `is:`, `has:`, and `nip:`. Hashtags use `#tag`. Shared aliases expand after parsing, including site domains, media extensions, and kinds. Android also retains `is:note` and `is:notes`. NIP-50 extensions `domain:`, `language:`, `sentiment:`, `nsfw:`, and `include:spam` are passed to relays, which may ignore them.

Use commas or scoped OR for author, mention, and kind alternatives. Repeated authors and kinds intersect; contradictory constraints fail. Repeated since/until bounds narrow the interval. Dates must exist and use UTC; relative units are h/d/w/m/y. Distinct required hashtags and repeated mentions clauses are rejected: use OR instead.

Use `p:example.com` for profiles; bare domains are text. Unquoted HTTP/HTTPS/FTP URLs become domain/path text; quote URLs containing parentheses. Standalone note/nevent/naddr, hex event IDs, and npub/nprofile retain Android direct lookup behavior and relay hints. `@me` requires login; `@contacts` is not yet supported. Unknown modifiers, including relay modifiers, fail visibly.

Preview and submission share parsing and planning. Author resolution updates the preview through the existing cached Vertex/profile resolver. Quoted modifier-looking text is never resolved. Android keeps its native-renderable default kinds instead of the web fixtures' kind-1 default.

Limits: 2,000 UTF-16 code units, 16 nested groups, 256 syntax nodes, and 32 branches after alias expansion and safe same-field compaction. Ordinary relay plans run four branches concurrently, with eight-second subscriptions and a 30-second deadline including route discovery. Profile/identifier resolution retains its existing transport budgets. Results are checked against the originating subscription's structured filters and deduplicated by event ID; existing 500-event and memory limits remain. Text/phrase matching is relay-dependent.

Shared fixture checks live in `app/src/test`. They have not been run under the project's device-only validation policy.
