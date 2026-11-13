package com.stashledger;

import com.stashledger.data.StashCatalog;
import com.stashledger.model.StashTier;
import com.stashledger.tracking.WatsonBoardParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;
import static org.junit.Assert.*;

public class CatalogWatsonIntegrationTest
{
    @Test public void parsesAll119WithActualParenthesizedBoardNamesAndTierCollisions()
    {
        StashCatalog catalog = new StashCatalog();
        Map<StashTier, Map<String, String>> aliases = new EnumMap<>(StashTier.class);
        Map<StashTier, List<WatsonBoardParser.Cell>> board = new EnumMap<>(StashTier.class);
        assertEquals("CENTRE_OF_CANIFIS", catalog.getWatsonAliases(StashTier.MEDIUM).get("centre of canifis"));
        assertEquals("EAST_OF_THE_BARBARIAN_VILLAGE_BRIDGE",
            catalog.getWatsonAliases(StashTier.MEDIUM).get("east of the barbarian village bridge"));
        assertEquals("GNOME_STRONGHOLD_BALANCING_ROPE",
            catalog.getWatsonAliases(StashTier.MEDIUM).get("gnome stronghold balancing rope"));
        assertEquals("OUTSIDE_YANILLE_BANK",
            catalog.getWatsonAliases(StashTier.MEDIUM).get("outside yanille bank"));
        assertEquals("BARBARIAN_OUTPOST_OBSTACLE_COURSE",
            catalog.getWatsonAliases(StashTier.MEDIUM).get("barbarian outpost obstacle course"));
        assertEquals("WARRIORS_GUILD_BANK",
            catalog.getWatsonAliases(StashTier.ELITE).get("warriors guild bank"));
        assertEquals("WARRIORS_GUILD_BANK_29047",
            catalog.getWatsonAliases(StashTier.MASTER).get("warriors guild bank"));
        assertEquals("PANDEMONIUM_BAR",
            catalog.getWatsonAliases(StashTier.EASY).get("the bar on the pandemonium"));
        assertEquals("WINTUMBER_ISLAND",
            catalog.getWatsonAliases(StashTier.ELITE).get("on wintumber island"));
        assertEquals("BRITTLE_ISLE",
            catalog.getWatsonAliases(StashTier.MASTER).get("on brittle isle"));
        for (StashTier tier : StashTier.values())
        {
            aliases.put(tier, catalog.getWatsonAliases(tier));
            List<WatsonBoardParser.Cell> cells = new ArrayList<>();
            for (Map.Entry<String, String> entry : aliases.get(tier).entrySet())
            {
                String name = "(" + entry.getKey() + ")";
                if (entry.getKey().equals("warriors guild bank")) { name = "(Warriors’ Guild bank)"; }
                cells.add(new WatsonBoardParser.Cell(4, name));
                cells.add(new WatsonBoardParser.Cell(5, null));
                if (tier != StashTier.MASTER) { cells.add(new WatsonBoardParser.Cell(5, null)); }
            }
            board.put(tier, cells);
        }
        Optional<Map<String, Boolean>> result = WatsonBoardParser.parse(board, aliases, catalog.getDefinitions());
        assertTrue("All real aliases, including punctuation, must match", result.isPresent());
        assertEquals(119, result.get().size());
        assertTrue(result.get().get("WARRIORS_GUILD_BANK"));
        assertFalse(result.get().get("WARRIORS_GUILD_BANK_29047"));
        assertTrue(result.get().get("OUTSIDE_THE_FISHING_GUILD"));
    }

    @Test public void parsesAll119AgainstPinnedNoticeboardLabelFixture() throws IOException
    {
        StashCatalog catalog = new StashCatalog();
        Map<StashTier, List<WatsonBoardParser.Cell>> board = new EnumMap<>(StashTier.class);
        Map<StashTier, Integer> rowIndexes = new EnumMap<>(StashTier.class);
        Map<String, Boolean> expected = new HashMap<>();
        for (StashTier tier : StashTier.values())
        {
            board.put(tier, new ArrayList<>());
            rowIndexes.put(tier, 0);
        }

        try (InputStream resource = getClass().getResourceAsStream("/com/stashledger/watson-board-labels.tsv"))
        {
            assertNotNull("Pinned board-label fixture must be packaged with the test", resource);
            try (BufferedReader rows = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8)))
            {
                String row;
                while ((row = rows.readLine()) != null)
                {
                    if (row.isEmpty() || row.startsWith("#")) { continue; }
                    String[] values = row.split("\\t", -1);
                    assertEquals("Each fixture row has id, tier and board label", 3, values.length);
                    StashTier tier = StashTier.valueOf(values[1]);
                    int checks = rowIndexes.put(tier, rowIndexes.get(tier) + 1) % 3;
                    board.get(tier).add(new WatsonBoardParser.Cell(4, values[2]));
                    for (int check = 0; check < checks; check++)
                    {
                        board.get(tier).add(new WatsonBoardParser.Cell(5, null));
                    }
                    assertNull("Fixture IDs must be unique", expected.put(values[0], checks == 2));
                }
            }
        }

        assertEquals(119, expected.size());
        Optional<Map<String, Boolean>> result = WatsonBoardParser.parse(board, aliases(catalog), catalog.getDefinitions());
        assertTrue("Every pinned Watson board label must parse", result.isPresent());
        assertEquals(expected, result.get());
        assertTrue("The live Pandemonium Easy row must retain its filled state", result.get().get("PANDEMONIUM_BAR"));
        assertEquals("The live Wintumber Elite row must retain its fixture state",
            expected.get("WINTUMBER_ISLAND"), result.get().get("WINTUMBER_ISLAND"));
        assertEquals("The live Brittle Master row must retain its fixture state",
            expected.get("BRITTLE_ISLE"), result.get().get("BRITTLE_ISLE"));
    }

    private static Map<StashTier, Map<String, String>> aliases(StashCatalog catalog)
    {
        Map<StashTier, Map<String, String>> result = new EnumMap<>(StashTier.class);
        for (StashTier tier : StashTier.values()) { result.put(tier, catalog.getWatsonAliases(tier)); }
        return result;
    }
}
