# STASH Ledger implementation contract

Approved UI: Location cards in the mockup at
`/Users/wyattfleming/.codex/visualizations/2026/11/11/01a1c8d9-bd32-7832-8dbc-5a17bd760ef0/stash-ledger.html`.

This is a new local project on `develop`, with no remote or origin/staging.
Source and tests are separate draft commits until integrated fleet validation.
Do not launch RuneLite or another visible GUI on the Mac. Gaming mode is on.

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
`StashTier`, `BuildStatus`, `ContentsStatus`. Do not edit these in worker branches.

Catalog lane owns `com.stashledger.data.*`, catalog resources, generator and
attribution. Entry point: `new StashCatalog().getDefinitions()` returns an
immutable `List<StashDefinition>`. `getById(String)` returns a matching definition
or null. Avoid needing a Client to load the catalog. Full item groups must be
accurate; alternatives remain alternatives. Use pinned licensed upstream data.

UI lane owns `com.stashledger.ui.*`. Entry point:
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

Each worker commits source first, tests separately with justification. Return
branch/SHA, changed files, evidence, known risks. No remote push or publication.
