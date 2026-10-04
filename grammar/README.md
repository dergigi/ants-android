# Shared ANTLR query language

Grammar, portable fixtures, and alias table imported from dergigi/ants PR #313,
commit `7baa60e5933b5286326e43246c4cf17726d77596`, language version 1.
The alias table is packaged as `app/src/main/resources/query-replacements.txt`.
Generator and Android runtime are pinned to ANTLR **4.13.2**.

Regenerate Java sources using Java 17:

```sh
python3 scripts/generate-query-parser.py
```

The generator verifies the upstream jar SHA-256. Generated Java is committed;
normal builds do not download or run the generator. Do not edit generated files.
Update the shared grammar, fixtures, aliases, adapter, and syntax documentation together.

The Kotlin adapter rejects lexer/parser errors, uses UTF-16 source positions,
checks depth and node limits before planning, then compiles bounded branch-local
filters. Shared fixture tests are in the JVM test source set. Device-only validation
is the current project policy; do not claim fixture conformance without running them.
