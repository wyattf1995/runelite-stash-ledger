package com.stashledger.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class StashDefinitionTest
{
    @Test
    public void retainsAnImmutableDefensiveCopyOfEveryWorldPoint()
    {
        List<WorldPoint> supplied = new ArrayList<>();
        supplied.add(new WorldPoint(3200, 3201, 0));
        supplied.add(new WorldPoint(3202, 3203, 1));

        StashDefinition definition = new StashDefinition("stash", 1, "Stash", StashTier.EASY,
            supplied, List.of("item"));
        supplied.clear();

        assertEquals(List.of(new WorldPoint(3200, 3201, 0), new WorldPoint(3202, 3203, 1)),
            definition.getWorldPoints());
        assertEquals(3200, definition.getX());
        assertEquals(3201, definition.getY());
        assertEquals(0, definition.getPlane());
        try
        {
            definition.getWorldPoints().add(new WorldPoint(1, 1, 0));
            fail("world point list must be immutable");
        }
        catch (UnsupportedOperationException expected)
        {
            // Expected.
        }
    }
}
