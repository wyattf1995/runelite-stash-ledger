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
        for (StashTier tier : StashTier.values())
        {
            aliases.put(tier, catalog.getWatsonAliases(tier));
            List<WatsonBoardParser.Cell> cells = new ArrayList<>();
            for (Map.Entry<String, String> entry : aliases.get(tier).entrySet())
            {
                String name = entry.getKey();
                if (name.equals("fishing guild outside")) { name = "Fishing Guild (Outside)"; }
                if (name.equals("trollheim mountain top")) { name = "Trollheim Mountain (top)"; }
                if (name.equals("trollheim mountain halfway")) { name = "Trollheim Mountain (half-way)"; }
                if (name.equals("warriors guild bank")) { name = "Warriors’ Guild bank"; }
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
