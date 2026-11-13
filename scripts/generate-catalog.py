#!/usr/bin/env python3
"""Generate the licensed STASH catalog from pinned RuneLite source snapshots.

The generated Java intentionally resolves STASH object IDs and coordinates from
RuneLite's public STASHUnit enum at runtime. It fails early if the installed
client no longer has the pinned 119-unit catalog.
"""
import argparse
import re
from pathlib import Path

EXPECTED_COUNT = 119
TIERS = ("BEGINNER", "EASY", "MEDIUM", "HARD", "ELITE", "MASTER")
# Exact live noticeboard labels that differ from the pinned reference wrapper.
# Keep these corrections evidence-backed; see docs/catalog-provenance.md.
WATSON_LOCATION_OVERRIDES = {
    "PANDEMONIUM_BAR": "The bar on the Pandemonium",
    "WINTUMBER_ISLAND": "On Wintumber Island",
}


def split_top_level(value):
    parts, start, depth, quote, escaped = [], 0, 0, None, False
    for index, char in enumerate(value):
        if quote:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == quote:
                quote = None
        elif char in ("'", '"'):
            quote = char
        elif char in "([{":
            depth += 1
        elif char in ")]}":
            depth -= 1
        elif char == "," and depth == 0:
            parts.append(value[start:index].strip())
            start = index + 1
    parts.append(value[start:].strip())
    return parts


def calls(source):
    marker = "new EmoteClue("
    offset = 0
    while True:
        start = source.find(marker, offset)
        if start < 0:
            return
        index, depth, quote, escaped = start + len(marker), 1, None, False
        while depth:
            char = source[index]
            if quote:
                if escaped:
                    escaped = False
                elif char == "\\":
                    escaped = True
                elif char == quote:
                    quote = None
            elif char == '"':
                quote = char
            elif char == "(":
                depth += 1
            elif char == ")":
                depth -= 1
            index += 1
        yield source[start + len(marker):index - 1]
        offset = index


def java_string(value):
    value = value.replace("\\n", " ").replace("\\\"", '"').replace("\\\\", "\\")
    return value.replace("\\", "\\\\").replace('"', '\\"')


def display_name(identifier):
    return identifier.replace("_", " ").title()


def requirement(text):
    # The full source clue text is the only stable rendering that preserves
    # variants such as "equip", "wear", "wield", and empty-slot rules.
    return text.replace("\\n", " ")


def parse_stashes(source):
    pattern = re.compile(r"^\s*([A-Z0-9_]+)\(ObjectID\.(HH_(BEGINNER|EASY|MEDIUM|HARD|ELITE|MASTER)[A-Z0-9_]*)", re.M)
    rows = [(identifier, tier) for identifier, _object_id, tier in pattern.findall(source)]
    if len(rows) != EXPECTED_COUNT or len({identifier for identifier, _ in rows}) != EXPECTED_COUNT:
        raise ValueError("expected %d unique STASH units, found %d" % (EXPECTED_COUNT, len(rows)))
    return rows


def parse_requirements(source, identifiers):
    known = set(identifiers)
    result = {identifier: [] for identifier in identifiers}
    for call in calls(source):
        args = split_top_level(call)
        if len(args) < 4 or not args[1].startswith('"'):
            continue
        stash_id = args[3]
        if stash_id not in known:
            continue
        text = args[1][1:-1]
        item_requirement = requirement(text)
        if item_requirement not in result[stash_id]:
            result[stash_id].append(item_requirement)
    missing = [identifier for identifier, values in result.items() if not values]
    if missing:
        raise ValueError("STASH entries without parsed equipment requirements: " + ", ".join(missing))
    return result


def static_requirement_expressions(source):
    """Return the source expression for named reusable equipment requirements."""
    result = {}
    marker = re.compile(r"(?:private\s+)?static\s+final\s+[^=;]+\s+([A-Z][A-Z0-9_]*)\s*=")
    for match in marker.finditer(source):
        start, index, depth, quote, escaped = match.end(), match.end(), 0, None, False
        while index < len(source):
            char = source[index]
            if quote:
                if escaped:
                    escaped = False
                elif char == "\\":
                    escaped = True
                elif char == quote:
                    quote = None
            elif char in ("'", '"'):
                quote = char
            elif char in "([{":
                depth += 1
            elif char in ")]}":
                depth -= 1
            elif char == ";" and depth == 0:
                result[match.group(1)] = source[start:index]
                break
            index += 1
    return result


def parse_item_ids(source, identifiers):
    """Extract only source-declared equipment IDs; no display-name lookup is used.

    The overlay is deliberately a candidate marker, not a fulfillment checker, so
    nested any/all alternatives are flattened only for rendering. Ranges retain
    their source endpoints and are expanded at runtime exactly as RuneLite's
    ItemRequirements.range does.
    """
    known = set(identifiers)
    static = static_requirement_expressions(source)
    result = {identifier: {"items": set(), "ranges": set(), "variations": set()} for identifier in identifiers}

    def add_expression(target, expression, seen_symbols):
        variation_bases = set(re.findall(
            r"ItemVariationMapping\.getVariations\(ItemID\.([A-Z0-9_]+)\)", expression))
        exclusions = set(re.findall(
            r"\.filter\(itemId\s*->\s*itemId\s*!=\s*ItemID\.([A-Z0-9_]+)\)", expression))
        target["items"].update(
            set(re.findall(r"ItemID\.([A-Z0-9_]+)", expression)) - variation_bases - exclusions)
        for base in variation_bases:
            # Keep each source expression group distinct: an exclusion on one
            # group must not suppress a variant required by another STASH clause.
            target["variations"].add((base, tuple(sorted(exclusions))))
        target["ranges"].update(re.findall(
            r"range\s*\(\s*(?:\"[^\"]*\"\s*,\s*)?ItemID\.([A-Z0-9_]+)\s*,\s*ItemID\.([A-Z0-9_]+)",
            expression))
        for symbol in re.findall(r"\b[A-Z][A-Z0-9_]+\b", expression):
            if symbol in static and symbol not in seen_symbols:
                add_expression(target, static[symbol], seen_symbols | {symbol})

    for call in calls(source):
        args = split_top_level(call)
        stash_index = next((index for index, value in enumerate(args) if value in known), None)
        if stash_index is None:
            continue
        add_expression(result[args[stash_index]], ",".join(args[stash_index + 1:]), set())

    missing = [identifier for identifier, values in result.items()
               if not values["items"] and not values["ranges"] and not values["variations"]]
    # A few valid STASH locations have no equipment requirement. Keep them
    # present with an empty set so coverage remains exhaustive and they cannot
    # create a false highlight.
    return result, missing


def normalize_watson_location(value):
    value = re.sub(r"<[^>]*>", "", value)
    value = value.replace("_", " ")
    value = re.sub(r"[^\w\s]", "", value).lower()
    return " ".join(value.split())


def parse_watson_aliases(source, identifiers):
    known = set(identifiers)
    aliases = {}
    entry_pattern = re.compile(r"^\s*([A-Z0-9_]+)\(", re.M)
    for entry in entry_pattern.finditer(source):
        start, index, depth, quote, escaped = entry.end(), entry.end(), 1, None, False
        while depth:
            char = source[index]
            if quote:
                if escaped:
                    escaped = False
                elif char == "\\":
                    escaped = True
                elif char == quote:
                    quote = None
            elif char == '"':
                quote = char
            elif char == "(":
                depth += 1
            elif char == ")":
                depth -= 1
            index += 1
        call = source[start:index - 1]
        args = split_top_level(call)
        match = re.search(r"STASHUnit\.([A-Z0-9_]+)", call)
        if not match or match.group(1) not in known or not args or not args[0].startswith('"'):
            continue
        stash_id = match.group(1)
        # The three-argument constructor uses the wrapper enum's this.name(),
        # not its friendly display-name argument or the linked STASHUnit ID.
        location = args[1][1:-1] if len(args) > 1 and args[1].startswith('"') else entry.group(1)
        # fromWatsonLocation explicitly resolves these identical board labels
        # by difficulty; the numeric suffix is not part of the displayed name.
        if stash_id in {"WARRIORS_GUILD_BANK", "WARRIORS_GUILD_BANK_29047"}:
            location = "Warriors' Guild bank"
        location = WATSON_LOCATION_OVERRIDES.get(stash_id, location)
        normalized = normalize_watson_location(location)
        previous = aliases.get(stash_id)
        if previous is not None and previous != normalized:
            raise ValueError("conflicting Watson aliases for " + stash_id)
        aliases[stash_id] = normalized
    missing = sorted(known - set(aliases))
    if missing:
        raise ValueError("STASH entries without Watson alias: " + ", ".join(missing))
    return aliases


def generate(rows, requirements, aliases):
    tiers_by_id = dict(rows)
    seen_aliases = {}
    for identifier, alias in aliases.items():
        key = (tiers_by_id[identifier], alias)
        previous = seen_aliases.get(key)
        if previous is not None and previous != identifier:
            raise ValueError("Watson alias collision for %s: %s and %s" % (alias, previous, identifier))
        seen_aliases[key] = identifier
    lines = [
        "/*",
        " * GENERATED from RuneLite source snapshots by scripts/generate-catalog.py.",
        " * Do not edit manually. See docs/catalog-provenance.md and THIRD_PARTY_NOTICES.",
        " */",
        "package com.stashledger.data;",
        "",
        "import com.stashledger.model.StashDefinition;",
        "import com.stashledger.model.StashTier;",
        "import java.util.ArrayList;",
        "import java.util.Arrays;",
        "import java.util.Collections;",
        "import java.util.List;",
        "import net.runelite.api.coords.WorldPoint;",
        "import net.runelite.client.plugins.cluescrolls.clues.emote.STASHUnit;",
        "",
        "final class GeneratedStashDefinitions",
        "{",
        "    private GeneratedStashDefinitions() { }",
        "",
        "    static List<StashDefinition> load()",
        "    {",
        "        List<StashDefinition> definitions = new ArrayList<>();",
    ]
    for identifier, tier in rows:
        args = ", ".join('"%s"' % java_string(value) for value in requirements[identifier])
        lines.append("        add(definitions, \"%s\", \"%s\", StashTier.%s, List.of(%s));" % (identifier, display_name(identifier), tier, args))
    lines += [
        "        if (definitions.size() != %d)" % EXPECTED_COUNT,
        "        {",
        "            throw new IllegalStateException(\"Expected %d STASH definitions but loaded \" + definitions.size());" % EXPECTED_COUNT,
        "        }",
        "        return Collections.unmodifiableList(definitions);",
        "    }",
        "",
        "    static java.util.Map<StashTier, java.util.Map<String, String>> watsonAliases()",
        "    {",
        "        java.util.Map<StashTier, java.util.Map<String, String>> aliases = new java.util.EnumMap<>(StashTier.class);",
    ]
    for tier in TIERS:
        lines.append("        aliases.put(StashTier.%s, new java.util.HashMap<>());" % tier)
    for identifier, tier in rows:
        lines.append("        aliases.get(StashTier.%s).put(\"%s\", \"%s\");" % (tier, java_string(aliases[identifier]), identifier))
    lines += [
        "        java.util.Map<StashTier, java.util.Map<String, String>> immutable = new java.util.EnumMap<>(StashTier.class);",
        "        for (java.util.Map.Entry<StashTier, java.util.Map<String, String>> entry : aliases.entrySet())",
        "        {",
        "            immutable.put(entry.getKey(), Collections.unmodifiableMap(entry.getValue()));",
        "        }",
        "        return Collections.unmodifiableMap(immutable);",
        "    }",
        "",
        "    private static void add(List<StashDefinition> definitions, String id, String name, StashTier tier, List<String> requirements)",
        "    {",
        "        STASHUnit unit = STASHUnit.valueOf(id);",
        "        WorldPoint[] points = unit.getWorldPoints();",
        "        if (points.length == 0)",
        "        {",
        "            throw new IllegalStateException(\"STASH unit has no world point: \" + id);",
        "        }",
        "        definitions.add(new StashDefinition(id, unit.getObjectId(), name, tier, Arrays.asList(points), requirements));",
        "    }",
        "}",
        "",
    ]
    return "\n".join(lines)


def generate_item_requirements(rows, item_ids):
    lines = [
        "/*",
        " * GENERATED from RuneLite EmoteClue source by scripts/generate-catalog.py.",
        " * Candidate IDs are source-declared equipment requirements only; this is not a fulfillment model.",
        " * Do not edit manually. See docs/catalog-provenance.md and THIRD_PARTY_NOTICES.",
        " */",
        "package com.stashledger.data;",
        "",
        "import java.util.Collections;",
        "import java.util.LinkedHashSet;",
        "import java.util.LinkedHashMap;",
        "import java.util.Map;",
        "import java.util.Set;",
        "import net.runelite.api.gameval.ItemID;",
        "import net.runelite.client.game.ItemVariationMapping;",
        "",
        "final class GeneratedStashItemRequirements",
        "{",
        "    private GeneratedStashItemRequirements() { }",
        "",
        "    static Map<String, Set<Integer>> load()",
        "    {",
        "        Map<String, Set<Integer>> requirements = new LinkedHashMap<>();",
    ]
    for identifier, _tier in rows:
        values = item_ids[identifier]
        names = ", ".join("ItemID." + value for value in sorted(values["items"]))
        if names:
            lines.append("        add(requirements, \"%s\", %s);" % (identifier, names))
        else:
            lines.append("        add(requirements, \"%s\");" % identifier)
        for start, end in sorted(values["ranges"]):
            lines.append("        addRange(requirements.get(\"%s\"), ItemID.%s, ItemID.%s);" % (identifier, start, end))
        for base, excluded in sorted(values["variations"]):
            exclusions = ", ".join("ItemID." + item_id for item_id in excluded)
            suffix = ", " + exclusions if exclusions else ""
            lines.append("        addVariations(requirements.get(\"%s\"), ItemID.%s%s);" % (identifier, base, suffix))
    lines += [
        "        if (requirements.size() != %d)" % EXPECTED_COUNT,
        "        {",
        "            throw new IllegalStateException(\"Expected %d STASH item requirement sets but loaded \" + requirements.size());" % EXPECTED_COUNT,
        "        }",
        "        Map<String, Set<Integer>> immutable = new LinkedHashMap<>();",
        "        for (Map.Entry<String, Set<Integer>> entry : requirements.entrySet())",
        "        {",
        "            immutable.put(entry.getKey(), Collections.unmodifiableSet(entry.getValue()));",
        "        }",
        "        return Collections.unmodifiableMap(immutable);",
        "    }",
        "",
        "    private static void add(Map<String, Set<Integer>> requirements, String id, int... itemIds)",
        "    {",
        "        Set<Integer> values = new LinkedHashSet<>();",
        "        for (int itemId : itemIds)",
        "        {",
        "            values.add(itemId);",
        "        }",
        "        requirements.put(id, values);",
        "    }",
        "",
        "    private static void addRange(Set<Integer> values, int start, int end)",
        "    {",
        "        // Mirrors RangeItemRequirement: only the inclusive source direction matches.",
        "        for (int itemId = start; itemId <= end; itemId++)",
        "        {",
        "            values.add(itemId);",
        "        }",
        "    }",
        "",
        "    private static void addVariations(Set<Integer> values, int sourceId, int... excludedIds)",
        "    {",
        "        Set<Integer> excluded = new LinkedHashSet<>();",
        "        for (int itemId : excludedIds)",
        "        {",
        "            excluded.add(itemId);",
        "        }",
        "        for (int itemId : ItemVariationMapping.getVariations(sourceId))",
        "        {",
        "            if (!excluded.contains(itemId))",
        "            {",
        "                values.add(itemId);",
        "            }",
        "        }",
        "    }",
        "}",
        "",
    ]
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--stash-source", type=Path, required=True)
    parser.add_argument("--clue-source", type=Path, required=True)
    parser.add_argument("--watson-source", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--item-output", type=Path)
    parser.add_argument("--item-only", action="store_true",
        help="generate only exact item requirement candidates; does not require Watson aliases")
    args = parser.parse_args()
    rows = parse_stashes(args.stash_source.read_text())
    requirements = parse_requirements(args.clue_source.read_text(), [identifier for identifier, _ in rows])
    item_ids, _empty_requirement_ids = parse_item_ids(args.clue_source.read_text(), [identifier for identifier, _ in rows])
    if not args.item_only:
        aliases = parse_watson_aliases(args.watson_source.read_text(), [identifier for identifier, _ in rows])
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(generate(rows, requirements, aliases))
    if args.item_output:
        args.item_output.parent.mkdir(parents=True, exist_ok=True)
        args.item_output.write_text(generate_item_requirements(rows, item_ids))


if __name__ == "__main__":
    main()
