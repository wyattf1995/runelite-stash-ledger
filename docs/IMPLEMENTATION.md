# STASH Ledger architecture

The native sidebar uses compact dark and gold location cards. See the README
for the rendered interface and development setup.

## Scope

- Full current RuneLite STASH catalog, tiers, construction requirements, clue items.
- Native Swing sidebar matching the compact dark/gold location-card concept.
- All / Not built / Built / Unknown filters, tier and text filters, expandable
  cards, overall built/filled/empty/contents-unknown counts, account-scoped shortlist.
- Automatically scan construction using the existing Watson check script on
  the RuneLite client thread. Never treat unavailable data as false.
- Separate construction and contents states. Contents can be synced from
  Watson's noticeboard or explicitly confirmed by the user. No guessed state.
- Real map and Wiki actions, no gameplay automation, no remote account uploads.
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
