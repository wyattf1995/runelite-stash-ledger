package com.stashledger.items;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.stashledger.model.BuildStatus;
import com.stashledger.model.ContentsStatus;
import com.stashledger.model.StashDefinition;
import com.stashledger.model.StashEntry;
import com.stashledger.model.StashTier;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class NeededItemProviderTest
{
    @Test
    public void keepsCandidatesForUnknownUnbuiltAndBuiltUnfilledButExcludesBuiltFilled()
    {
        NeededItemProvider provider = provider();

        provider.refresh(List.of(
            entry("filled", BuildStatus.BUILT, ContentsStatus.FILLED),
            entry("empty", BuildStatus.BUILT, ContentsStatus.EMPTY),
            entry("unbuilt", BuildStatus.NOT_BUILT, ContentsStatus.UNKNOWN),
            entry("unknown", BuildStatus.UNKNOWN, ContentsStatus.UNKNOWN)));

        assertFalse(provider.isNeeded(10));
        assertTrue(provider.isNeeded(20));
        assertTrue(provider.isNeeded(30));
        assertTrue(provider.isNeeded(40));
    }

    @Test
    public void canonicalizesWidgetVariantsAndClearsAccountState()
    {
        NeededItemProvider provider = new NeededItemProvider(id -> id == 900 ? 20 : id,
            Map.of("empty", Set.of(20)));

        provider.refresh(List.of(entry("empty", BuildStatus.BUILT, ContentsStatus.EMPTY)));

        assertTrue("A noted, placeholder, or worn variant can canonicalize to the requirement", provider.isNeeded(900));
        assertTrue(provider.detailFor(900).isPresent());
        provider.clear();
        assertFalse(provider.isNeeded(20));
        assertFalse(provider.detailFor(900).isPresent());
    }

    private static NeededItemProvider provider()
    {
        return new NeededItemProvider(id -> id, Map.of(
            "filled", Set.of(10), "empty", Set.of(20), "unbuilt", Set.of(30), "unknown", Set.of(40)));
    }

    private static StashEntry entry(String id, BuildStatus build, ContentsStatus contents)
    {
        return new StashEntry(new StashDefinition(id, 1, id, StashTier.EASY, 0, 0, 0, List.of()), build, contents, false);
    }
}
