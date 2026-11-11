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


def normalize_watson_location(value):
    value = re.sub(r"<[^>]*>", "", value)
    value = value.replace("_", " ")
    value = re.sub(r"[^\\w\\s]", "", value).lower()
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
        location = args[1][1:-1] if len(args) > 1 and args[1].startswith('"') else args[0][1:-1]
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
        "        WorldPoint point = points[0];",
        "        definitions.add(new StashDefinition(id, unit.getObjectId(), name, tier, point.getX(), point.getY(), point.getPlane(), requirements));",
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
    args = parser.parse_args()
    rows = parse_stashes(args.stash_source.read_text())
    requirements = parse_requirements(args.clue_source.read_text(), [identifier for identifier, _ in rows])
    aliases = parse_watson_aliases(args.watson_source.read_text(), [identifier for identifier, _ in rows])
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(generate(rows, requirements, aliases))


if __name__ == "__main__":
    main()
