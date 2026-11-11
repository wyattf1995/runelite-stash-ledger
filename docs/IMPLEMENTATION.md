# STASH Ledger architecture

The native sidebar uses compact dark and gold location cards. See the README
for the rendered interface and development setup.

## Scope

- Full current RuneLite STASH catalog, tiers, construction requirements, clue items.
- Native Swing sidebar matching the compact dark/gold location-card concept.
- All / Not built / Built / Unknown / Filled / Empty filters, Hide filled, tier and text filters, expandable
  cards, overall built/filled/empty/contents-unknown counts, account-scoped shortlist.
- Automatically scan construction using the existing Watson check script on
  the RuneLite client thread. Never treat unavailable data as false.
- Separate construction and contents states. Contents can be synced from
  Watson's noticeboard, an exact confirmed STASH interaction or the user.
- Needed-item dots in inventory/bank/equipment; optional all-unit map markers
  and per-card map and Wiki actions. No gameplay automation or remote uploads.
- Log out clears visible account state; switching profile never mixes accounts.

## Shared Java contracts (Java 11)

Package `com.stashledger.model`: immutable `StashDefinition`, `StashEntry`, enums
`StashTier`, `BuildStatus`, `ContentsStatus`.

`com.stashledger.data` contains the catalog and generated, attributed data.
Entry point: `new StashCatalog().getDefinitions()` returns an
immutable `List<StashDefinition>`. `getById(String)` returns a matching definition
or null. Avoid needing a Client to load the catalog. Full item groups must be
accurate; alternatives remain alternatives. Use pinned licensed upstream data.

`com.stashledger.ui` contains the Swing sidebar. Entry point:
`new StashLedgerPanel(Actions actions)`, extending RuneLite `PluginPanel`.
`void update(List<StashEntry> entries, String accountName, boolean loggedIn)`
is called on Swing EDT; `void setStatusMessage(String text)` is optional.
Nested public interface `Actions` defines:
`void togglePinned(String id)`;
`void markContents(String id, ContentsStatus status)`;
`void showOnMap(StashDefinition definition)`;
`void openWiki(StashDefinition definition)`;
`void refresh()`.

UI actions must preserve search/filter/scroll/expanded card state on updates.
Disable account-changing actions when logged out. Manual contents choice should
be labeled as confirmation, and only enabled for built units. Display unknown
contents distinctly from empty. Main UI fits the normal 225px RuneLite sidebar.
No Swing access to Client. No client-thread work on EDT.

State mutation and game access stay on RuneLite's client thread. Immutable
snapshots cross to the Swing EDT. Account identity and lifecycle checks reject
callbacks captured by a previous session, and a complete construction scan is
required before applying Watson contents.

`StashInteractionTracker` correlates a single Search target with the two exact
success strings used by RuneLite's pinned `ClueScrollPlugin`. The event adapter
checks the object ID and instance-aware coordinates, cancels on a later menu
action, expires after 30 game ticks, and rechecks account identity and proximity
before applying contents. Login, logout, world hopping and disabling tracking
clear pending interactions. Construction remains separately script-derived.

`NeededItemProvider` publishes an immutable set of source-declared item candidates
for units not confirmed filled. Item alternatives are flattened only for passive
highlighting; this is not an equipment-satisfaction or possession-count model.
`StashNeededItemOverlay` draws a gold planner dot without modifying widgets or
items. Profile changes and disabling highlights clear the snapshot.

`StashMapMarkers` owns overview markers separately from the selected card marker.
It preserves unknown states, can hide filled units, and removes only its own
markers at shutdown or account changes. Settings updates run on the client
thread; markers do not rebuild when the visible states are unchanged.
