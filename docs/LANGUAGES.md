# Language filtering

## Bundled detector

fastText v0.9.2 sources are vendored unmodified under `app/src/main/cpp/fasttext`, excluding the CLI entry point. Source: https://github.com/facebookresearch/fastText/releases/tag/v0.9.2 (MIT).

Release archive SHA-256: `7ea4edcdb64bfc6faaaec193ef181bdc108ee62bb6a04e48b2e80b639a99e27e`.

The unmodified `lid.176.ftz` model is from https://dl.fbaipublicfiles.com/fasttext/supervised-models/lid.176.ftz . SHA-256: `8f3472cfe8738a7b6099e8e999c3cbfae0dcd15696aac7d7738a8039db603e83`. Model attribution: Facebook fastText, trained on Wikipedia, Tatoeba and SETimes; distributed under CC-BY-SA 3.0. See https://fasttext.cc/docs/en/language-identification.html and the license copies in `licenses/` and APK `META-INF/`. The APK also includes `META-INF/fasttext-NOTICE.txt` with model attribution and source links.

Reference: Armand Joulin, Edouard Grave, Piotr Bojanowski and Tomas Mikolov, *Bag of Tricks for Efficient Text Classification*, 2016; Armand Joulin et al., *FastText.zip: Compressing text classification models*, 2016.

The C++ model is loaded lazily once per process under a mutex, only from the checksum-verified bundled asset. JNI input uses UTF-8 bytes. Detection runs off the main thread; no content, detection requests or metrics are sent to a detection service. Model/library failures return unknown. Native build dependencies are pinned to Android NDK 27.2.12479018 and CMake 3.22.1; shared libraries use 16 KB page alignment.

## Result policy and UI

Analyze text kinds 1, 20, 21, 22, 1111, 9802 and 30023. Profile metadata, protocol records and recognized encrypted payloads are not classified or hidden by language. Strip code, URLs, Nostr references and non-text symbols before detection. Inspect at most 16,000 input characters and three 400-character samples (beginning, middle, end), each with at least 12 letters. Require a top score of 0.80 and a lead of 0.20 over the runner-up. These conservative thresholds are implementation choices, not an accuracy guarantee; short and code-switched notes need phone evaluation.

When detection is inconclusive, use self-reported NIP-32 `l` tags in the `ISO-639-1` namespace. If `L` tags are present they must include that namespace. Validate codes against ISO-639-1. Confident text detection takes precedence over conflicting self-labels. Third-party kind-1985 label events are not fetched or trusted as labels of their own content.

Retain a bounded cache of 600 event classifications. Compute language counts from all fetched, classifiable events **before any display filters**, so filtering does not remove picker options. A multilingual event can contribute to multiple buckets. Unknown includes uncertain portions as well as wholly unclassified notes.

Show the Languages row only when at least two known languages are present. Initially all languages are enabled. The first language toggle establishes the preferred set from currently detected languages; future unselected languages are filtered too. Preferred languages and the Unknown choice persist in SharedPreferences across searches and app restarts. Toggling a visible language preserves preferences for languages absent from the current results. Saved preferences also apply to single-language results. Unknown stays enabled unless explicitly unchecked. Never and Clear bypass/reset language filtering; Restore defaults enables all languages. Language filtering applies below the usual Smart-mode threshold.

The compact dialog lists only detected language names, without counts, with Show all and Close icons. An active language preference also exposes Show all languages in the main filter menu, even when there is no language picker. The all-results-hidden state offers a language-only clear action when languages have hidden results; it preserves other filters. Clearing also clears the saved preference. Checkbox changes filter retained results immediately. The optional Search icon reruns the query and asks for enabled languages using NIP-50. It is available when a language preference is active and at least one detected language is enabled.

## Relay requests

Every search snapshots the saved language selection. For text-search branches, add separate `language:xx` NIP-50 requests for up to four selected two-letter codes, capped at eight extra branches and 32 total. Keep original broad branches first, under the existing 30-second search deadline, concurrency and event/memory limits. This preserves unknown/unlabeled results and supports relays that ignore language extensions. No extra hints are added to explicit language branches, direct event lookups, addressable lookups or profile-only branches. The visible query and history are not rewritten. Three-letter detector languages remain locally filterable but cannot use NIP-50's two-letter extension.

NIP-32 labels do not become mandatory relay `#l` filters: doing so would lose unlabeled notes. Supplemental requests do not guarantee more matching results if the relay ignores the extension or existing budgets are exhausted.

Regression cases in `ResultLanguagesTest.kt` cover label namespaces, short/uncertain text, code/URL cleanup, preferred-language selections and clearing, and relay branch preservation/bounds. They are authored but not run under the project's no-local-tests policy. Device evaluation is still needed for accuracy, mixed languages and interaction.
