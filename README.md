# STASH Ledger

STASH Ledger is a compact RuneLite sidebar for planning every STASH unit. It
keeps construction progress and confirmed contents separate, so an unavailable
scan never becomes a false “empty” result.

![STASH Ledger sidebar preview](docs/sidebar-preview.png)

## Features

- Browse all 119 STASH units across Beginner through Master clue tiers.
- Filter by construction state, tier, location, or clue item; surface unknown
  contents separately from confirmed contents.
- Expand a location card for construction materials and exact clue requirements.
- Scan construction after login, a build, or a manual refresh.
- Read saved contents from Watson's noticeboard or explicitly confirm contents
  for a built unit. Unknown contents are distinct from empty.
- Keep a profile-scoped shortlist; logout and account changes clear visible
  account state.
- Open a location on the world map or RuneLite's general STASH Wiki page. The
  plugin never moves a character or uploads account data.

Construction uses RuneLite's Watson STASH check script. Contents are the last
confirmed noticeboard or manual state, not a live inventory scan.

## Development

The project targets Java 11 bytecode and pins RuneLite `1.13.6`. A development
launcher is available in the `-all.jar` artifact:

```sh
java -ea -jar build/libs/stash-ledger-0.1.0-all.jar --developer-mode --debug
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

The latest verified development candidate passed 36 tests with no skips and six
named source-removal/red-restore-green proofs. It also produced the 225px sidebar
preview above. See [validation evidence](docs/validation.md).

## Catalog, licensing, and Hub status

The catalog retains exact source clue wording, alternatives, and restrictions;
it does not guess interchangeable items. See [catalog provenance](docs/catalog-provenance.md),
[third-party notices](THIRD_PARTY_NOTICES), and [license](LICENSE).

`runelite-plugin.properties` declares version `0.1.0` for a future Plugin Hub
submission. The plugin is not installed, published, or accepted by Plugin Hub.
Submission still requires a public source repository, a reviewed immutable
commit, and RuneLite review.
