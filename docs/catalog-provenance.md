# Catalog provenance

`GeneratedStashDefinitions.java` is generated from the pinned RuneLite master
snapshot `bb0fa42b3d9329ffe31115f1f20042c455def2e3`.

Inputs stored outside this repository for the implementation task:

- `/Users/wyattfleming/reports/runelite-stash-ledger-20261110/reference/STASHUnit.java`
- `/Users/wyattfleming/reports/runelite-stash-ledger-20261110/reference/EmoteClue.java`
- `/Users/wyattfleming/reports/runelite-stash-ledger-20261110/reference/ItemRequirement.java`

Regenerate after replacing those snapshots:

```sh
python3 scripts/generate-catalog.py \
  --stash-source /path/to/STASHUnit.java \
  --clue-source /path/to/EmoteClue.java \
  --output src/main/java/com/stashledger/data/GeneratedStashDefinitions.java
```

The generator rejects a catalog unless it has exactly 119 unique units and every
unit has at least one exact source-derived equipment restriction. Generated data
uses the installed RuneLite `STASHUnit` enum for object IDs and world points, so
an incompatible client fails at catalog construction instead of silently using
stale object IDs.
