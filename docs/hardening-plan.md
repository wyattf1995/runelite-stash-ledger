# STASH Ledger reliability and usability roadmap

Proposed after the live Watson failure and version-identification support on
2026-11-12. These items are not implemented merely by appearing here. The
configurable tier-dot work is separately authorized; the broader roadmap is a
recommendation for subsequent releases.

Existing protections to retain: account-scoped writes, lifecycle/epoch guards,
transactional save parsing, explicit unknown contents, exact interaction
confirmation, and refusal to replace contents from an incomplete noticeboard.

| Priority | Change | User benefit | Acceptance evidence |
|---|---|---|---|
| 1 | Visible loaded plugin version and separate construction/contents sync status | Answer “which version am I using?” and “what is current?” without logs | Version comes from the loaded plugin artifact; login/logout, no scan, failed scan and successful sync show truthful independent states |
| 1 | Typed sync results with actionable recovery | Distinguish loading, unavailable construction scan, missing tier, changed labels, duplicate rows and incompatible widget layout; offer retry when useful | Independent widget fixtures exercise every reason; no failed/partial snapshot changes any saved contents; diagnostic copy excludes profile IDs, names and unrelated client data |
| 1 | Reproducible catalog generation and compatibility canary | Detect new STASH units, renamed labels and RuneLite changes before users encounter them | Lock source URLs and SHA256 hashes; regenerate from verified inputs and compare checked-in outputs; run a separate scheduled/manual check against the latest supported RuneLite version without changing the release pin or publishing automatically |
| 2 | Per-unit last-confirmed time and source | Show “Filled — Watson — 12 minutes ago” or “Manually marked”; make old observations visible | Migrate v1 saves without dropping pins/contents; preserve original observations and never convert age into empty; no provenance or timestamps cross accounts |
| 2 | Safe recovery and save evolution | Recover malformed data without losing the original; undo accidental manual edits | Versioned migrations, bounded backups, validated preview before restore, unsupported future schemas remain protected; undo is guarded against account changes and intervening updates |
| 3 | Stronger bank-to-STASH packing guidance | Tier colors, clearer focus, item checklist and eventually “ready to fill” | Tier markers reflect eligible destinations only; quantity/checklist claims require a structured requirement evaluator that handles alternatives, complete sets, accepted variants and exclusions |

The current Watson adapter exposes only a boolean result and hard-codes group493,
tier child offsets, and text/sprite cell types in StashLedgerPlugin. Isolate those
assumptions in one versioned adapter and validate captured widget contracts.
Prefer stable IDs when available; keep strict, tier-scoped aliases otherwise.
Do not introduce fuzzy matches or silently accept a partial noticeboard.

Current save schema v1 stores only contents and pins in LedgerState. Preserve
unknown future unit records where safe, protect unsupported schemas, and test
upgrade/downgrade paths before introducing provenance or backups. Recovery must
not silently reset a damaged account or import into the wrong profile.

Current CI compiles/tests against the pinned client and runs Python generator
tests. Add clean regeneration, artifact metadata/notices, runnable-JAR headless
help, and the independent board fixture to the regular gate. Keep actual
source-only red/restore/green proofs in the release lane. A compatibility canary
must report the exact resolved version and readable differences; it must not
unpin production dependencies or automatically accept changed data.

Keep a short live release charter: load the exact version, sync Watson, inspect
bank highlights/hover, use a selected fill plan, observe a confirmed deposit and
withdrawal, switch profiles, and disable/re-enable. Account-boundary, interrupted
sync and malformed-save checks belong in deterministic tests too. Screenshot or
unit-test success alone does not establish that the real widget adapter works.

For usability, retain the location-card design. Add a compact health summary,
first-use guidance, persistent filter choices and a clear empty-filter reset.
Use both labels and color. A later true packing checklist needs richer requirement
semantics: the current NeededItemProvider deliberately supplies candidate IDs,
not owned quantities or proof that interchangeable equipment requirements are met.

Recommended sequence: finish the Watson correction; ship the separately requested
configurable tier dots once validated; then implement the priority1 reliability
work, followed by provenance/recovery, before promising quantity-aware packing.

Review basis: StashLedgerPlugin, LedgerState, WatsonBoardParser, NeededItemProvider,
StashLedgerPanel, catalog generation, CI and the 0.1.2 independent board fixture.
For API/data research, prefer RuneLite core references and official documentation;
external plugin implementations are useful secondary evidence, not a substitute
for inspecting their semantics or verifying the live game interface:
https://github.com/runelite/runelite/wiki/Developer-Guide
