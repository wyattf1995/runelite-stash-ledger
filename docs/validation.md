# Candidate validation

Verified 2026-11-11 against RuneLite 1.13.6. This candidate is awaiting Plugin
Hub review; development installation is separate from Hub availability.

Validated code and test commit: `a26d83be33e426264e2e1349ea713c2833984a05`.
The following documentation update does not change executable inputs.

- 78 Java tests passed with zero failures, errors or skipped tests on Java 17,
  targeting Java 11 bytecode. Six Python catalog-generator tests passed.
- 24 named source-only checks failed after removing the relevant guard, then
  passed after restoring the exact source bytes. These cover complete scans,
  account/session boundaries, Watson parsing, built-only contents edits, sidebar
  geometry and font support, filters, map visibility, confirmed interactions,
  item highlighting, source-declared item exclusions, every physical STASH
  location, and stop/restart interleavings across the EDT and client thread.
- Six generator tests cover source parsing and catalog output. Four additional
  generator guards were independently proved on the previous candidate; those
  historical proofs are retained separately from this candidate's 24 checks.
- Both plugin and runnable JARs built successfully. The runnable JAR reached
  RuneLite's `--help` handler with assertions enabled in a headless process.
- Packaged license, third-party notices and metadata match source files exactly.
- The actual Swing sidebar rendered with RuneLite's look and feel at 225 x 680
  logical pixels, captured at 2x scale. Its geometry and font tests passed and
  the resulting image was visually inspected. The preview uses demo account data.
- Catalog/Watson checks cover all 119 logical units, 121 physical coordinates,
  and all six clue tiers, including
  the two Warriors' Guild bank entries. Item tests preserve explicit alternatives
  and exclusions, including excluding the inactive Bow of Faerdhinen.

Artifact SHA-256 values:

```text
37db3083e89b957da34a1a947e263bf54f5a7408343c4fa57ba8a2a1871d4f6c  stash-ledger-0.1.0.jar
eb3ed99a835bd16be263face495b383d79de35720e282b62ee513dc7b38693d6  stash-ledger-0.1.0-all.jar
```

The retained validation bundle includes test XML, logs, source hashes for every
red/green proof, both JARs, the rendered sidebar and an archive/checksum receipt.
Evidence collection passed on the original fleet ticket
`vps-1097480b6acb4c5083907b2491493a28`. The source receipt and exported checksums
were independently verified. Source and guarding tests were committed separately.

## Comprehensive review fixes

The review covered account isolation and persistence, construction/Watson scans,
interaction confirmation, catalog/item data, overlays, map ownership, Swing/EDT
behavior and the build/packaging pipeline. Two confirmed defects were fixed:

- The generated catalog discarded alternate positions for Lumbridge Swamp Caves
  and the cavern under the whirlpool. All source-defined positions now participate
  in marker ownership, exact interaction matching and proximity checks; progress
  remains one entry per logical unit.
- Plugin shutdown on the EDT could clear markers while an already-running client
  callback continued adding them. Initialization and cleanup now run in client
  queue order, with immediate lifecycle invalidation and a stop-during-init guard.
  Deterministic tests cover refresh/stop, rapid restart and stop during startup.

## Live validation scope

The prior development client was successfully installed and signed in on macOS.
A user screenshot confirms the active sidebar and a completed construction scan
showing 50 of 119 built, with 50 contents unknown. The count has not been
independently reconciled against the account.

The previous candidate (`26d9622`) was restarted and its corrected live UI was
confirmed by the user and a private screenshot: clean icons, wrapped titles,
search prompt and Filled/Empty controls. The latest review candidate still needs
a live restart. Interaction tracking, alternate positions, item/map markers and
account switching need an in-game pilot. Watson noticeboard synchronization has
automated coverage but its live check was deferred because the user could not
reach Watson. These are explicit gaps, not claimed live passes.
