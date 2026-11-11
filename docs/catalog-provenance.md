# Catalog provenance

`GeneratedStashDefinitions.java` is generated from the pinned RuneLite master
snapshot `bb0fa42b3d9329ffe31115f1f20042c455def2e3`.

The pinned source inputs are:

- RuneLite `STASHUnit.java`, `EmoteClue.java`, and `ItemRequirement.java` at
  [`bb0fa42b3d9329ffe31115f1f20042c455def2e3`](https://github.com/runelite/runelite/tree/bb0fa42b3d9329ffe31115f1f20042c455def2e3)
- `https://github.com/larsvansoest/emote-clue-items/blob/2ab1220cbccba512c2f6744b217c266524e70be9/src/main/java/com/larsvansoest/runelite/clueitems/data/StashUnit.java`

Regenerate after replacing those snapshots:

```sh
python3 scripts/generate-catalog.py \
  --stash-source /path/to/STASHUnit.java \
  --clue-source /path/to/EmoteClue.java \
  --watson-source /path/to/EmoteClueItemsStashUnit.java \
  --output src/main/java/com/stashledger/data/GeneratedStashDefinitions.java \
  --item-output src/main/java/com/stashledger/data/GeneratedStashItemRequirements.java
```

The generator rejects a catalog unless it has exactly 119 unique units and every
unit has at least one exact source-derived equipment restriction and a
tier-scoped Watson noticeboard location alias. Aliases remove tags and
punctuation, lowercase, and fold whitespace; collisions within a tier fail
generation. Generated data
uses the installed RuneLite `STASHUnit` enum for object IDs and world points, so
an incompatible client fails at catalog construction instead of silently using
stale object IDs.

`GeneratedStashItemRequirements.java` is extracted from RuneLite 1.13.6's
`EmoteClue.java`. It resolves reusable requirement expressions and retains the
source's inclusive item ranges. It never looks up an item ID from a display
name. Any/all alternatives are flattened only into planner-marker candidates;
the original requirement wording remains on the card. No owned quantity or
fulfilled equipment group is inferred from these IDs. Runtime item
canonicalization and RuneLite's variation mapping cover noted and related
item forms for highlighting.

The exact deposit/withdraw success strings and the `Search` menu action follow
the same pinned RuneLite `ClueScrollPlugin`, rather than a loose text matcher:
[confirmed messages and interaction](https://github.com/runelite/runelite/blob/bb0fa42b3d9329ffe31115f1f20042c455def2e3/runelite-client/src/main/java/net/runelite/client/plugins/cluescrolls/ClueScrollPlugin.java#L324-L381).
