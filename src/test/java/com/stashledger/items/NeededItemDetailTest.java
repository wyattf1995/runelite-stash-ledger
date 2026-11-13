package com.stashledger.items;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.stashledger.model.BuildStatus;
import com.stashledger.model.ContentsStatus;
import com.stashledger.model.StashDefinition;
import com.stashledger.model.StashEntry;
import com.stashledger.model.StashTier;
import java.util.List;
import org.junit.Test;

public class NeededItemDetailTest
{
    @Test
    public void tiersAreDistinctImmutableAndAlwaysUseClueTierOrder()
    {
        NeededItemDetail detail = new NeededItemDetail(42, List.of(
            entry("Master", StashTier.MASTER), entry("Easy", StashTier.EASY),
            entry("Other easy", StashTier.EASY), entry("Beginner", StashTier.BEGINNER)) , false);

        assertEquals(List.of(StashTier.BEGINNER, StashTier.EASY, StashTier.MASTER), detail.getTiers());
        assertThrows(UnsupportedOperationException.class, () -> detail.getTiers().add(StashTier.HARD));
    }

    private static StashEntry entry(String name, StashTier tier)
    {
        return new StashEntry(new StashDefinition(name, 1, name, tier, 0, 0, 0, List.of()),
            BuildStatus.BUILT, ContentsStatus.EMPTY, false);
    }
}
