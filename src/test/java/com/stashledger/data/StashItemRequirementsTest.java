package com.stashledger.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class StashItemRequirementsTest
{
    @Test
    public void generatedRequirementSetsCoverEveryCatalogUnitAndKeepExactSourceAlternatives()
    {
        StashCatalog catalog = new StashCatalog();
        Set<String> catalogIds = catalog.getDefinitions().stream()
            .map(definition -> definition.getId()).collect(Collectors.toSet());

        assertEquals(119, catalog.getItemRequirementIds().size());
        assertEquals(catalogIds, new HashSet<>(catalog.getItemRequirementIds().keySet()));
        assertTrue(catalog.getItemRequirementIds().get("GYPSY_TENT_ENTRANCE").contains(ItemID.GOLD_RING));
        assertTrue(catalog.getItemRequirementIds().get("GYPSY_TENT_ENTRANCE").contains(ItemID.GOLD_NECKLACE));
        assertTrue(catalog.getItemRequirementIds().get("BARBARIAN_OUTPOST_OBSTACLE_COURSE")
            .contains(ItemID.WILDERNESS_CAPE_1));
        assertTrue(catalog.getItemRequirementIds().get("BARROWS_CHEST")
            .contains(ItemID.BARROWS_AHRIM_HEAD));
    }
}
