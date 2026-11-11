# STASH Ledger

A native RuneLite sidebar for finding every STASH unit and tracking what remains
to build. Location cards show clue requirements, construction materials, contents
status and a personal shortlist.

## What it does

- Catalog of 119 STASH units across all six clue tiers, with search and filters.
- Automatic construction scan after login, construction and manual refresh.
- Separate built/not-built/unknown and stocked/empty/contents-unknown states.
- Contents sync when you open Watson's STASH noticeboard. A partial or unmatched
  board leaves saved contents unchanged. You can also explicitly confirm a
  built unit's contents in its expanded card.
- Account-scoped shortlist and confirmed contents. Account switching and logout
  clear the visible account state; one account's callbacks cannot update another.
- Map markers and Wiki links. Map adds a marker and targets the location; open
  the in-game world map to view it. This does not navigate or move your character.

Construction data comes from the game's Watson STASH check script. Contents are
last confirmed data, not a live inventory: reopen the noticeboard after changing
items. Unknown contents are never treated as empty. Confirmation and shortlist
data use RuneLite's profile configuration; the plugin makes no network uploads.

## Try the development build

This project has not been submitted to the Plugin Hub. Development launch uses
RuneLite's official example-plugin approach; it opens a separate RuneLite client.
Close that client normally when finished. It shares your normal RuneLite profile
settings, and requires you to log in normally. No credentials are supplied here.

With Java 11 or newer:

```sh
java -ea -jar stash-ledger-0.1.0-all.jar --developer-mode --debug
```

On a Mac with RuneLite installed, its bundled Java can be used without installing
a JDK:

```sh
/Applications/RuneLite.app/Contents/Resources/jre/bin/java \
  -ea -jar stash-ledger-0.1.0-all.jar --developer-mode --debug
```

Enable **STASH Ledger** in RuneLite's plugin settings if needed, then use the
gold crate icon in the sidebar. Log in for the construction scan. Open Watson's
noticeboard once to populate contents, or confirm individual units manually.

The packaged `run-stash-ledger.command` does this on macOS. Keep it beside the
`-all.jar`. The `-ea` option is required by RuneLite's development plugin loader.

The small `stash-ledger-0.1.0.jar` is the plugin artifact; the `-all.jar` includes
the development launcher and its RuneLite dependencies. Dropping the small JAR
into a normal client's plugin directory does not install it as a Plugin Hub entry.

## Build and test

The Gradle wrapper is pinned to 8.10 and targets Java 11. The RuneLite dependency
is pinned to 1.13.6, the verified Plugin Hub version at implementation time.

```sh
./gradlew --no-daemon --max-workers=2 test jar shadowJar
```

`scripts/fleet-validate.sh` additionally performs six source-only negative checks
for account callbacks, map callbacks, construction/contents guards and noticeboard
name normalization and detail text width, restores exact source
bytes, then repeats the complete suite and builds the JARs. Tests run headlessly
and produce the actual sidebar capture in `build/ui/stash-ledger.png`.

The fleet wrapper collects diagnostics even when validation fails. Its exit code
reports evidence collection; `build/validation/summary.json` must contain
`passed: true` before a candidate is accepted. A failed candidate contains its real
logs and any real partial outputs, never substitute JARs. The underlying
`scripts/validate-candidate.sh` returns the actual validation failure code.

On Wyatt's machines, use the documented guarded build fleet with runtime
`android` (its pinned Java 17 supports this Java 11 plugin). Do not launch a
local client or heavy build while gaming mode is enabled.

## Data and distribution

See [catalog provenance](docs/catalog-provenance.md), [third-party notices](THIRD_PARTY_NOTICES)
and [license](LICENSE). Item descriptions retain complete source clue wording,
including alternatives and restrictions, rather than guessing interchangeable
items. Catalog updates must regenerate and validate all mappings together.

Plugin Hub submission requires a public source repository and a manifest pointing
to a reviewed immutable commit, followed by RuneLite's review. This repository
includes `runelite-plugin.properties` for that future submission. No publication
or Plugin Hub acceptance is implied by a local build.
