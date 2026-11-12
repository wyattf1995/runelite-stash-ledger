# STASH Ledger

STASH Ledger is a compact RuneLite sidebar for planning every STASH unit. It
keeps construction progress and confirmed contents separate, so an unavailable
scan never becomes a false “empty” result.

![STASH Ledger sidebar preview](docs/sidebar-preview.png)

The preview uses a small demo account, not live player progress.

## Features

- Browse all 119 STASH units across Beginner through Master clue tiers.
- Filter by construction state, filled/empty contents, tier, location, or clue
  item; hide filled units while keeping unknown contents visible.
- Expand a location card for construction materials and exact clue requirements.
- Scan construction after login, a build, or a manual refresh.
- Read saved contents from Watson's noticeboard or explicitly confirm contents
  for a built unit. Confirmed STASH Search interactions also update filled/empty
  state automatically. Unknown contents are distinct from empty.
- Highlight needed clue items with a clear gold icon outline in the bank,
  inventory and equipment. A compact gold-dot style remains available. Hover an
  item for its STASH destinations, clue tiers and last confirmed states.
- Select **Fill this STASH** on a built, confirmed-empty unit to highlight only
  its items. A persistent banner identifies the selection; **Stop filling**
  returns to all unfinished units. Selection ends on completion, an unknown or
  unbuilt state, logout, account change or plugin shutdown.
- Item guidance preserves accepted variants and exclusions. It does not count
  owned items or claim that an alternative equipment requirement is satisfied.
- Keep a profile-scoped shortlist; logout and account changes clear visible
  account state.
- Show all STASH locations on the world map, optionally hiding confirmed filled
  units, or select one location from its card. Map tooltips explain unknown,
  not-built, empty and filled states. Open RuneLite's general STASH Wiki page.

Settings let you choose outline or dot highlights, disable item hover details,
and turn item highlighting, automatic interaction tracking and map markers on or off. The plugin never moves a character or uploads account data.

Construction uses RuneLite's Watson STASH check script. Contents are the last
confirmed noticeboard, interaction or manual state, not a live inventory scan.
Interaction updates require a matching STASH object and location, the exact
game success message, the same account, and a player still near that STASH.
Open Watson's noticeboard to reconcile contents you changed outside this client.

## Using the sidebar

1. Open RuneLite's **Plugin Hub**, search for **STASH Ledger**, and install it.
   Select the gold crate on the sidebar. The development client already includes
   the plugin and enables it by default.
2. Log in and select **Refresh** to scan construction for the current account.
3. Open Watson's noticeboard in game to sync filled/empty contents across units.
   Later confirmed STASH interactions update individual units automatically.
4. Expand a card for materials and clue items. Use **My list** to focus on pinned
   units, or **Hide filled** to work through unfinished storage.
5. For a built unit confirmed empty, select **Fill this STASH**, open your bank,
   and gather its highlighted items. Hover an item to check the destination.
   The selection guides you; it does not withdraw items or change contents.
   Complete the deposit yourself. If item highlighting is disabled, enable
   **Highlight needed items** in the plugin settings.

An unknown contents state means it has not been confirmed. It does not mean the
unit is empty. Filled/empty is tracked per unit, not per individual stored item.

## Development

The project targets Java 11 bytecode and pins RuneLite `1.13.6`. A development
launcher is available in the `-all.jar` artifact:

```sh
java -ea -jar build/libs/stash-ledger-0.1.3-all.jar --developer-mode --debug
```

On macOS, the development launcher also needs this JVM option:

```text
--add-opens=java.desktop/com.apple.eawt=ALL-UNNAMED
```

`-ea` is required by RuneLite's development-plugin loader. The small JAR is the
plugin artifact; it is not a Plugin Hub installation mechanism.

To build and test in a suitable development environment:

```sh
./gradlew test jar shadowJar
```

The test suite covers account boundaries, tracking, catalog data, filters and
the real 225px Swing layout. Source-only negative checks prove key guards fail
when removed and pass after exact restoration. See [validation evidence](docs/validation.md)
for the tested commit and remaining live checks.

## Catalog, licensing, and Hub status

The catalog retains exact source clue wording, alternatives, and restrictions;
it does not guess interchangeable items. See [catalog provenance](docs/catalog-provenance.md),
[third-party notices](THIRD_PARTY_NOTICES), and [license](LICENSE).

Version `0.1.2` is available in RuneLite's Plugin Hub, including the corrected
Watson location aliases, item outlines, hover details and selected-STASH fill mode.
The [update submission](https://github.com/runelite/plugin-hub/pull/20592) merged on
2026-11-12 UTC. The official signed RuneLite 1.13.6 manifest and downloadable
plugin were verified against the reviewed source. Install or update STASH Ledger
through RuneLite's Plugin Hub.

The [initial 0.1.0 submission](https://github.com/runelite/plugin-hub/pull/20516)
is retained in the validation history.

### Watson noticeboard patch

Version 0.1.2 corrects catalog aliases, including shortened labels such as
“Canifis” versus the board's “Centre of Canifis.” It preserves existing contents
unless the complete board matches. Publication is verified, but a user reports
continued matching failure after updating. Successful live sync is not yet
verified; further diagnostics are prepared in the 0.1.3 draft.
See [validation evidence](docs/validation.md).

### Version 0.1.3 candidate (not released)

The local 0.1.3 candidate adds a configurable color for each clue tier and a
single-color mode under **Dot colors**. With **Item highlight style → Dot**, an
item needed by several tiers shows one dot per relevant tier, in Beginner-to-Master
order. Filled destinations stay excluded and **Fill this STASH** still narrows
the plan. Hover text names all relevant tiers even when destination rows are capped.

The draft also preserves word boundaries in noticeboard line-break markup and
reports the failing tier and reason when a complete scan is rejected. Line breaks
are a confirmed normalization gap, not yet the confirmed cause of the live error.

Compilation, tests and live rendering of this candidate remain pending; the build
fleet is blocked by an earlier job's NAS archive recovery. This is not a published
Plugin Hub update. The broader [hardening roadmap](docs/hardening-plan.md) is a
proposal, separate from this authorized tier-dot implementation.
