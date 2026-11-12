package com.stashledger;

import com.stashledger.data.StashCatalog;
import com.stashledger.model.StashTier;
import com.stashledger.tracking.WatsonBoardParser;
import java.util.ArrayList;
import java.util.EnumMap;
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
        for (StashTier tier : StashTier.values())
        {
            aliases.put(tier, catalog.getWatsonAliases(tier));
            List<WatsonBoardParser.Cell> cells = new ArrayList<>();
            for (Map.Entry<String, String> entry : aliases.get(tier).entrySet())
            {
                String name = "(" + entry.getKey() + ")";
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
}
