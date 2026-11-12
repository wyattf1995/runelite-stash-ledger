# Version 0.1.1 validation

Validated on 2026-11-11 local (2026-11-12 UTC) against RuneLite 1.13.6.
Executable and test candidate: `3fee9c790ccbe55cf539df481cae4cb249c69141`.
Subsequent documentation commits do not change executable inputs.

- 96 Java tests passed, with zero failures, errors or skips. Six Python catalog
  tests passed. Java 17 built Java 11 bytecode.
- All 35 named source-only regression proofs failed when their guard was removed
  and passed after exact byte restoration. Each source hash was checked against
  the candidate commit. The final complete suite ran again after all proofs.
- New coverage verifies single-STASH eligibility and account boundaries, queued
  stale clicks, completion/logout cleanup, destination deduplication across item
  variants, real outline pixels, hover menu/drag/pointer restrictions, bounded
  hover details, and sidebar selection/stop callbacks.
- Both plugin and runnable JARs built; the runnable JAR passed headless launcher
  help with assertions enabled. Packaged notices and plugin metadata match source.
- The actual Swing sidebar rendered at 225px width in its normal and selected
  fill states. The longest real catalog name and full fill instructions fit,
  including the Stop control. Both final renders were visually inspected.

A speculative status-height change was withdrawn after two actual negative
checks disproved the concern. The stronger preferred-height and containment test
remains; the unsupported mutation was removed with an explicit commit reason.
No test assertions were weakened. Those first two runs were not release passes.

Verified fleet ticket: `vps-f17c10edb8264cdaa53ce2ab62ed3a35`.
The exported source receipt, artifact hashes and completed archive/retirement
were checked. Source and guarding tests are in separate commits.

```text
e7cc5f17cd44e9873678c03958080603049f7ed6fa09c0ead2d57e3f03e8eb45  stash-ledger-0.1.1.jar
53e187c3098aa0105e1f5cfa93723ae92655d8acb8345afff4b249b51cba03de  stash-ledger-0.1.1-all.jar
```

Version 0.1.1's Plugin Hub review and official distribution are pending. The
new bank outlines, hover and fill mode have automated coverage; a live in-game
bank/contents/account-switch pilot has not been performed. Existing live checks
listed below apply to the earlier candidate only.

![Selected fill mode with demo progress](fill-mode-preview.png)

---

# Version 0.1.0 historical validation

Validated 2026-11-11 against RuneLite 1.13.6. Official Plugin Hub distribution
was verified on 2026-11-12 UTC after the submission was merged.

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

## Official distribution

[Plugin Hub PR 20516](https://github.com/runelite/plugin-hub/pull/20516) merged
at 2026-11-12 00:36:41 UTC. Its descriptor pins reviewed source
`c579b19a5cab11595127b4a0197cb893ac419903`, which differs from the validated
candidate only by documentation and merge history. The
[production build and upload](https://github.com/runelite/plugin-hub/actions/runs/43128542665)
succeeded, completing upload at 00:37:39 UTC.

At 04:12 UTC, the official RuneLite 1.13.6 manifest contained exactly one
`stash-ledger` display entry and one JAR entry, version `0.1.0`. Its RSA/SHA-256
signature was verified with RuneLite's pinned certificate. The downloaded JAR
matched the signed manifest and production CI manifest diff in hash and size:

```text
8fed51321c2d9b37c5bc3f4576f65751398824257b07f41d99c14a2a5d5278da  published JAR (101281 bytes)
```

All compiled classes and other resources match the reviewed pull-request build.
Only generated `runelite_plugin.json` creation/update timestamps differ. The
development launcher is excluded. Publication does not imply that the remaining
live checks below have been performed.

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
