# Candidate validation

Verified 2026-11-11 against RuneLite 1.13.6. This candidate is awaiting Plugin
Hub review; development installation is separate from Hub availability.

Validated code and test commit: `26d962299fcdeca21e1659efd2c728f95cbdeb34`.
The following documentation/preview update does not change executable inputs.

- 69 Java tests passed with zero failures, errors or skipped tests on Java 17,
  targeting Java 11 bytecode. Six Python catalog-generator tests passed.
- 16 named source-only checks failed after removing the relevant guard, then
  passed after restoring the exact source bytes. These cover complete scans,
  account/session boundaries, Watson parsing, built-only contents edits, sidebar
  geometry and font support, filters, map visibility, confirmed interactions,
  item highlighting and source-declared item exclusions.
- Four additional generator guards were independently proved with source-only
  removal and byte-identical restoration. The escaped-quote parser regression
  also passed its committed test after restoration.
- Both plugin and runnable JARs built successfully. The runnable JAR reached
  RuneLite's `--help` handler with assertions enabled in a headless process.
- Packaged license, third-party notices and metadata match source files exactly.
- The actual Swing sidebar rendered with RuneLite's look and feel at 225 x 680
  logical pixels, captured at 2x scale. Its geometry and font tests passed and
  the resulting image was visually inspected. The preview uses demo account data.
- Catalog/Watson checks cover all 119 units and all six clue tiers, including
  the two Warriors' Guild bank entries. Item tests preserve explicit alternatives
  and exclusions, including excluding the inactive Bow of Faerdhinen.

Artifact SHA-256 values:

```text
959a4254d7de4eab8753ccf7d5f9e18bad5109203565609322988e0243656223  stash-ledger-0.1.0.jar
bc602a8f0064435bff94fe04456384109d509f727baf5b7168a0164bcb68a65b  stash-ledger-0.1.0-all.jar
```

The retained validation bundle includes test XML, logs, source hashes for every
red/green proof, both JARs, the rendered sidebar and an archive/checksum receipt.
The earlier integration run failed in seven new Mockito fixtures before behavior;
those fixtures were corrected in a separate test-only commit without weakening
assertions, and the complete suite above then passed.

## Live validation scope

The prior development client was successfully installed and signed in on macOS.
A user screenshot confirms the active sidebar and a completed construction scan
showing 50 of 119 built, with 50 contents unknown. The count has not been
independently reconciled against the account.

The updated candidate's live UI, interaction tracking, item/map markers and
account switching still need an in-game pilot. Watson noticeboard synchronization
has automated coverage but its live check was deferred because the user could not
reach Watson. These are explicit gaps, not claimed live passes.
