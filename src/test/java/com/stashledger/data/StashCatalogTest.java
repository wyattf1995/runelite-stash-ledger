package com.stashledger.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.stashledger.model.StashDefinition;
import com.stashledger.model.StashTier;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class StashCatalogTest
{
    @Test
    public void loadsTheCompleteCurrentCatalogWithRequirements()
    {
        StashCatalog catalog = new StashCatalog();

        assertEquals(119, catalog.getDefinitions().size());
        Set<String> ids = new HashSet<>();
        Map<StashTier, Integer> counts = new EnumMap<>(StashTier.class);
        for (StashDefinition definition : catalog.getDefinitions())
        {
            assertTrue("duplicate id " + definition.getId(), ids.add(definition.getId()));
            assertTrue("missing object id " + definition.getId(), definition.getObjectId() > 0);
            assertFalse("missing clue text " + definition.getId(), definition.getItems().isEmpty());
            assertTrue("blank clue text " + definition.getId(), definition.getItems().stream().noneMatch(String::isEmpty));
            counts.merge(definition.getTier(), 1, Integer::sum);
        }

        assertEquals(Integer.valueOf(3), counts.get(StashTier.BEGINNER));
        assertEquals(Integer.valueOf(31), counts.get(StashTier.EASY));
        assertEquals(Integer.valueOf(25), counts.get(StashTier.MEDIUM));
        assertEquals(Integer.valueOf(16), counts.get(StashTier.HARD));
        assertEquals(Integer.valueOf(19), counts.get(StashTier.ELITE));
        assertEquals(Integer.valueOf(25), counts.get(StashTier.MASTER));
    }

    @Test
    public void indexesDefinitionsAndDoesNotInventMissingIds()
    {
        StashCatalog catalog = new StashCatalog();
        StashDefinition barrows = catalog.getById("BARROWS_CHEST");

        assertEquals(StashTier.MASTER, barrows.getTier());
        assertTrue(barrows.getItems().get(0).contains("full barrows set"));
        assertSame(barrows, catalog.getById("BARROWS_CHEST"));
        assertNull(catalog.getById("NOT_A_STASH"));
    }

    @Test
    public void keepsWatsonAliasesScopedToTheirClueTier()
    {
        StashCatalog catalog = new StashCatalog();

        assertEquals("WARRIORS_GUILD_BANK",
            catalog.getWatsonAliases(StashTier.ELITE).get("warriors guild bank"));
        assertEquals("WARRIORS_GUILD_BANK_29047",
            catalog.getWatsonAliases(StashTier.MASTER).get("warriors guild bank"));
        assertEquals(119, catalog.getWatsonAliases(StashTier.BEGINNER).size()
            + catalog.getWatsonAliases(StashTier.EASY).size()
            + catalog.getWatsonAliases(StashTier.MEDIUM).size()
            + catalog.getWatsonAliases(StashTier.HARD).size()
            + catalog.getWatsonAliases(StashTier.ELITE).size()
            + catalog.getWatsonAliases(StashTier.MASTER).size());
    }
}
