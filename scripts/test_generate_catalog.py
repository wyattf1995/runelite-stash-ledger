#!/usr/bin/env python3
"""Focused, dependency-free checks for STASH item-candidate generation."""
import importlib.util
import unittest
from pathlib import Path


SPEC = importlib.util.spec_from_file_location(
    "generate_catalog", Path(__file__).with_name("generate-catalog.py"))
generate_catalog = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(generate_catalog)


class GenerateCatalogTest(unittest.TestCase):
    def test_escaped_quote_with_parenthesis_preserves_static_requirement(self):
        source = r'''
            private static final ItemRequirement ESCAPED = any("A \"quoted)\" requirement", item(ItemID.CANDIDATE));
            new EmoteClue(ItemID.CLUE, "text", "where", STASH_ONE, ESCAPED);
        '''

        parsed, missing = generate_catalog.parse_item_ids(source, ["STASH_ONE"])

        self.assertEqual({"CANDIDATE"}, parsed["STASH_ONE"]["items"])
        self.assertEqual([], missing)

    def test_escaped_static_expression_and_empty_slot_do_not_create_false_ids(self):
        source = r'''
            private static final ItemRequirement ESCAPED = any("A \"quoted\" requirement", item(ItemID.CANDIDATE));
            new EmoteClue(ItemID.CLUE, "text", "where", STASH_ONE, ESCAPED,
                emptySlot("Nothing equipped", EquipmentInventorySlot.HEAD));
        '''

        parsed, missing = generate_catalog.parse_item_ids(source, ["STASH_ONE"])

        self.assertEqual({"CANDIDATE"}, parsed["STASH_ONE"]["items"])
        self.assertEqual(set(), parsed["STASH_ONE"]["ranges"])
        self.assertEqual([], missing)

    def test_range_uses_the_same_inclusive_direction_as_runelite(self):
        source = '''
            new EmoteClue(ItemID.CLUE, "text", "where", STASH_ONE,
                range(ItemID.FIRST, ItemID.LAST));
        '''

        parsed, _missing = generate_catalog.parse_item_ids(source, ["STASH_ONE"])
        generated = generate_catalog.generate_item_requirements(
            [("STASH_ONE", "EASY")], parsed)

        self.assertIn("for (int itemId = start; itemId <= end; itemId++)", generated)
        self.assertNotIn("Math.min(start, end)", generated)

    def test_empty_candidate_set_emits_a_valid_zero_argument_varargs_call(self):
        generated = generate_catalog.generate_item_requirements(
            [("STASH_EMPTY", "EASY")],
            {"STASH_EMPTY": {"items": set(), "ranges": set(), "variations": set()}},
        )

        self.assertIn('add(requirements, "STASH_EMPTY");', generated)
        self.assertNotIn('add(requirements, "STASH_EMPTY", );', generated)

    def test_definition_generator_has_no_unused_item_id_parameter(self):
        generated = generate_catalog.generate(
            [("STASH_ONE", "EASY")],
            {"STASH_ONE": ["required item"]},
            {"STASH_ONE": "somewhere"},
        )

        self.assertIn('add(definitions, "STASH_ONE"', generated)

    def test_watson_aliases_use_wrapper_identifier_unless_the_source_overrides_it(self):
        source = '''
            CENTRE_OF_CANIFIS("Canifis", STASHUnit.CENTRE_OF_CANIFIS, Type.Rock),
            BARBARIAN_OUTPOST_OBSTACLE_COURSE("Barbarian Outpost Course",
                "Barbarian Outpost obstacle course", STASHUnit.BARBARIAN_OUTPOST_OBSTACLE_COURSE, Type.Bush),
            WARRIORS_GUILD_BANK("Warriors Guild Bank", STASHUnit.WARRIORS_GUILD_BANK, Type.Hole),
            WARRIORS_GUILD_BANK_29047("Warriors Guild Bank", STASHUnit.WARRIORS_GUILD_BANK_29047, Type.Crate);
        '''

        aliases = generate_catalog.parse_watson_aliases(source, [
            "CENTRE_OF_CANIFIS", "BARBARIAN_OUTPOST_OBSTACLE_COURSE",
            "WARRIORS_GUILD_BANK", "WARRIORS_GUILD_BANK_29047",
        ])

        self.assertEqual("centre of canifis", aliases["CENTRE_OF_CANIFIS"])
        self.assertEqual("barbarian outpost obstacle course",
                         aliases["BARBARIAN_OUTPOST_OBSTACLE_COURSE"])
        self.assertEqual("warriors guild bank", aliases["WARRIORS_GUILD_BANK"])
        self.assertEqual("warriors guild bank", aliases["WARRIORS_GUILD_BANK_29047"])

    def test_source_declared_variations_keep_local_exclusions(self):
        source = '''
            static final ItemRequirement ACTIVE_BOW = any("active bow",
                Stream.of(
                    ItemVariationMapping.getVariations(ItemID.BOW_INACTIVE).stream(),
                    Stream.of(ItemID.CRYSTAL_BOW))
                .reduce(Stream::concat)
                .orElseGet(Stream::empty)
                .filter(itemId -> itemId != ItemID.BOW_INACTIVE)
                .map(ItemRequirements::item)
                .toArray(SingleItemRequirement[]::new));
            new EmoteClue(ItemID.CLUE, "text", "where", STASH_FILTERED, ACTIVE_BOW);
            new EmoteClue(ItemID.CLUE, "text", "where", STASH_DIRECT, item(ItemID.BOW_INACTIVE));
        '''

        parsed, _missing = generate_catalog.parse_item_ids(
            source, ["STASH_FILTERED", "STASH_DIRECT"])
        generated = generate_catalog.generate_item_requirements(
            [("STASH_FILTERED", "EASY"), ("STASH_DIRECT", "EASY")], parsed)

        self.assertEqual({"CRYSTAL_BOW"}, parsed["STASH_FILTERED"]["items"])
        self.assertEqual({("BOW_INACTIVE", ("BOW_INACTIVE",))},
                         parsed["STASH_FILTERED"]["variations"])
        self.assertEqual({"BOW_INACTIVE"}, parsed["STASH_DIRECT"]["items"])
        self.assertIn('addVariations(requirements.get("STASH_FILTERED"), ItemID.BOW_INACTIVE, ItemID.BOW_INACTIVE);', generated)
        self.assertIn('add(requirements, "STASH_DIRECT", ItemID.BOW_INACTIVE);', generated)


if __name__ == "__main__":
    unittest.main()
