# Development build validation

Verified 2026-11-10 on the guarded Linux build fleet, using Java 17 with Java 11
bytecode targets and RuneLite 1.13.6. This is a development candidate, not a
Plugin Hub release.

Validated source: `793527bac85afe6c5f85a9686724835127cb60bf`.

- 36 tests passed; zero failures, errors, or skipped tests.
- Six source-only regression proofs failed their named test with the guard
  removed, then passed after restoring the exact original source bytes.
  They cover incomplete construction scans, stale map/account callbacks,
  noticeboard punctuation, built-only contents edits, and wrapped detail text.
- Both JARs were built, and the runnable JAR reached RuneLite's `--help` handler
  with assertions enabled in a headless process.
- The real Swing sidebar was rendered at 225 × 680 using RuneLite's look and
  feel. Its viewport and control bounds passed checks; the PNG was visually
  inspected. Displayed account states in that capture are synthetic demo data.
- The catalog and Watson integration checks cover all 119 units and all six
  clue tiers, including the two Warriors' Guild bank entries.

Artifact SHA-256 values:

```text
8701d8550ff403da1260ef652ca889be542316023e5b79acf49f0a282600707b  stash-ledger-0.1.0.jar
538586434e4ede699f3a8c639d7eb3224e6042b38bd7b7e9d104d2b79e902412  stash-ledger-0.1.0-all.jar
```

Evidence is retained at
`/Users/wyattfleming/reports/runelite-stash-ledger-20261110/validation-4`.
The fleet archive is `vpsq-4557f32f153244a29da5ef72ba858900`; export hashes,
source identity, receipt, archive verification, and workspace retirement were
verified. An interrupted download was recovered from that same ticket.

Live login, real game-script results, Watson's live widgets, and in-game map
interaction still need an in-game pilot. Gaming mode remained enabled, so no
RuneLite client was launched. The plugin has not been installed in the normal
client, published, or submitted to the Plugin Hub.
