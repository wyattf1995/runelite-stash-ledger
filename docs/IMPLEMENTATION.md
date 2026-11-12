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
- Needed-item outlines or dots in inventory/bank/equipment, destination hover
  details and session-only single-STASH fill focus; optional all-unit map markers
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
`void update(List<StashEntry> entries, String accountName, boolean loggedIn, String fillStashId)`
is called on Swing EDT; the three-argument overload clears fill focus; `void setStatusMessage(String text)` is optional.
Nested public interface `Actions` defines:
`void togglePinned(String id)`;
`void markContents(String id, ContentsStatus status)`;
`void showOnMap(StashDefinition definition)`;
`void openWiki(StashDefinition definition)`;
`void refresh()`;
`void fillStash(String id)`;
`void clearFillStash()`.

UI actions must preserve search/filter/scroll/expanded card state on updates.
Disable account-changing actions when logged out. Manual contents choice should
be labeled as confirmation, and only enabled for built units. Display unknown
contents distinctly from empty. Main UI fits the normal 225px RuneLite sidebar.
No Swing access to Client. No client-thread work on EDT.

State mutation and game access stay on RuneLite's client thread. Immutable
snapshots cross to the Swing EDT. Account identity and lifecycle checks reject
callbacks captured by a previous session, and a complete construction scan is
required before applying Watson contents.

RuneLite starts and stops plugins on the EDT. Lifecycle invalidation happens
immediately there, while client-owned initialization and cleanup are queued in
order on the client thread. An event already in progress finishes before cleanup;
a rapid restart initializes only after the retired session's resources are gone.
The initializer checks its generation before and after creating resources.

`StashInteractionTracker` correlates a single Search target with the two exact
success strings used by RuneLite's pinned `ClueScrollPlugin`. The event adapter
checks the object ID and instance-aware coordinates, cancels on a later menu
action, expires after 30 game ticks, and rechecks account identity and proximity
before applying contents. Login, logout, world hopping and disabling tracking
clear pending interactions. Construction remains separately script-derived.

`NeededItemProvider` publishes an immutable map of source-declared item candidates
and their STASH destinations. Global mode includes units not both built and
filled; selected mode includes only the chosen built, confirmed-empty unit.
Canonicalized alternatives are deduplicated by logical STASH ID, preserving
source-declared exclusions. This is not an equipment-satisfaction or
possession-count model.

`StashNeededItemOverlay` uses ItemManager's cached icon outline (original widget
ID and quantity), or the configurable compact dot. Hover details show at most
six destinations plus a remaining count and explain unknown construction or
contents. Hover tooltips are suppressed for open menus, dragging, disabled
hover details and pointers outside the item. Profile changes and disabling
highlights clear the item snapshot. No widgets or items are modified.

Fill focus lives only on the client thread and is never persisted. The sidebar
callback passes the existing lifecycle/account guards, then rechecks built and
empty eligibility when the queued action executes. Publishing clears invalid
focus before updating highlights and the sidebar. Logout, profile changes and
shutdown clear it. Selection changes neither contents nor game inventory.
The status area grows with wrapped text so its banner and Stop control remain
available even when the selected card is filtered out. Disabled highlights are
respected; the sidebar tells the user to enable them in settings.

`StashMapMarkers` owns overview markers separately from the selected card marker.
It preserves unknown states, can hide filled units, and removes only its own
markers at shutdown or account changes. Settings updates run on the client
thread; markers do not rebuild when the visible states are unchanged.

Each definition retains every world point supplied by RuneLite. The current 119
logical units have 121 physical locations: Lumbridge Swamp Caves and the cavern
under the whirlpool each have two positions. Overview and selected markers cover
both positions, and interaction/proximity checks accept either. Progress and
saved contents remain keyed by the logical unit, so alternate locations never
increase the progress denominator or create duplicate saved state.
