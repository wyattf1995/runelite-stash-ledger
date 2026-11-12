package com.stashledger.items;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.stashledger.model.BuildStatus;
import com.stashledger.model.ContentsStatus;
import com.stashledger.model.StashDefinition;
import com.stashledger.model.StashEntry;
import com.stashledger.model.StashTier;
import com.stashledger.data.StashCatalog;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.gameval.ItemID;
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

    @Test
    public void preservesItemManagerWornCanonicalizationWithoutRuntimeVariationExpansion()
    {
        // Pinned ItemManager.WORN_ITEMS maps this worn equipment ID to its inventory ID.
        NeededItemProvider provider = new NeededItemProvider(id -> id == ItemID.IKOV_BOOTSOFLIGHTNESSWORN
            ? ItemID.IKOV_BOOTSOFLIGHTNESS : id, Map.of("unfinished", Set.of(ItemID.IKOV_BOOTSOFLIGHTNESS)));

        provider.refresh(List.of(entry("unfinished", BuildStatus.NOT_BUILT, ContentsStatus.UNKNOWN)));

        assertTrue("Worn equipment remains discoverable through ItemManager canonicalization",
            provider.isNeeded(ItemID.IKOV_BOOTSOFLIGHTNESSWORN));
    }

    @Test
    public void doesNotReintroduceAnExcludedVariationDuringRefresh()
    {
        NeededItemProvider provider = new NeededItemProvider(id -> id,
            new StashCatalog().getItemRequirementIds());

        provider.refresh(List.of(entry("TENT_IN_LORD_IORWERTHS_ENCAMPMENT",
            BuildStatus.NOT_BUILT, ContentsStatus.UNKNOWN)));

        assertFalse(provider.isNeeded(ItemID.BOW_OF_FAERDHINEN_INACTIVE));
        assertTrue(provider.isNeeded(ItemID.BOW_OF_FAERDHINEN));
    }

    @Test
    public void doesNotCanonicalizeAfterAccountStateIsCleared()
    {
        AtomicInteger canonicalizationCalls = new AtomicInteger();
        NeededItemProvider provider = new NeededItemProvider(id ->
        {
            canonicalizationCalls.incrementAndGet();
            return id;
        }, Map.of("unfinished", Set.of(88)));

        provider.clear();

        assertFalse(provider.isNeeded(89));
        assertFalse(provider.detailFor(89).isPresent());
        assertTrue("An empty snapshot must avoid an ItemManager lookup after logout", canonicalizationCalls.get() == 0);
    }

    @Test
    public void focusedPlanIncludesOnlyOneBuiltEmptyStashAndItsImmutableDetails()
    {
        NeededItemProvider provider = provider();
        StashEntry empty = entry("empty", BuildStatus.BUILT, ContentsStatus.EMPTY);
        StashEntry unbuilt = entry("unbuilt", BuildStatus.NOT_BUILT, ContentsStatus.UNKNOWN);

        StashEntry otherEmpty = entry("filled", BuildStatus.BUILT, ContentsStatus.EMPTY);
        provider.refresh(List.of(empty, unbuilt, otherEmpty), "empty");

        NeededItemDetail detail = provider.detailFor(20).orElseThrow(AssertionError::new);
        assertTrue(detail.isFocused());
        assertEquals(20, detail.getItemId());
        assertEquals(List.of(empty), detail.getStashes());
        assertFalse(provider.isNeeded(30));
        assertFalse("Other eligible units remain outside the selected plan", provider.isNeeded(10));
        assertThrows(UnsupportedOperationException.class, () -> detail.getStashes().add(unbuilt));
    }

    @Test
    public void invalidOrNonEmptyFocusNeverFallsBackToTheGlobalPlan()
    {
        NeededItemProvider provider = provider();
        provider.refresh(List.of(
            entry("empty", BuildStatus.BUILT, ContentsStatus.EMPTY),
            entry("unbuilt", BuildStatus.NOT_BUILT, ContentsStatus.UNKNOWN),
            entry("filled", BuildStatus.BUILT, ContentsStatus.FILLED)), "unbuilt");

        assertFalse(provider.isNeeded(20));
        assertFalse(provider.isNeeded(30));
        assertFalse(provider.detailFor(20).isPresent());
    }

    @Test
    public void globalDetailsIncludeEveryUnfinishedLocationForTheSameRequirement()
    {
        NeededItemProvider provider = new NeededItemProvider(id -> id, Map.of("one", Set.of(44), "two", Set.of(44)));
        StashEntry one = entry("one", BuildStatus.BUILT, ContentsStatus.EMPTY);
        StashEntry two = entry("two", BuildStatus.UNKNOWN, ContentsStatus.UNKNOWN);

        provider.refresh(List.of(one, two));

        NeededItemDetail detail = provider.detailFor(44).orElseThrow(AssertionError::new);
        assertFalse(detail.isFocused());
        assertEquals(List.of(one, two), detail.getStashes());
    }

    @Test
    public void dotTiersOnlyDescribeDestinationsThatRemainEligibleForTheItem()
    {
        NeededItemProvider provider = new NeededItemProvider(id -> id,
            Map.of("filled", Set.of(44), "easy", Set.of(44), "master", Set.of(44)));
        StashEntry filled = tierEntry("filled", StashTier.BEGINNER, BuildStatus.BUILT, ContentsStatus.FILLED);
        StashEntry easy = tierEntry("easy", StashTier.EASY, BuildStatus.BUILT, ContentsStatus.EMPTY);
        StashEntry master = tierEntry("master", StashTier.MASTER, BuildStatus.NOT_BUILT, ContentsStatus.UNKNOWN);

        provider.refresh(List.of(filled, easy, master));

        NeededItemDetail detail = provider.detailFor(44).orElseThrow(AssertionError::new);
        assertEquals(List.of(easy, master), detail.getStashes());
        assertEquals(List.of(StashTier.EASY, StashTier.MASTER), detail.getTiers());
    }

    @Test
    public void canonicalRequirementVariantsListEachDestinationOnlyOnce()
    {
        NeededItemProvider provider = new NeededItemProvider(id -> id == 900 ? 10 : id,
            Map.of("one", Set.of(10, 900), "two", Set.of(10)));
        StashEntry one = entry("one", BuildStatus.BUILT, ContentsStatus.EMPTY);
        StashEntry two = entry("two", BuildStatus.NOT_BUILT, ContentsStatus.UNKNOWN);

        provider.refresh(List.of(one, two));

        assertEquals(List.of(one, two), provider.detailFor(900).orElseThrow(AssertionError::new).getStashes());
        provider.refresh(List.of(one, two), "one");
        assertEquals(List.of(one), provider.detailFor(10).orElseThrow(AssertionError::new).getStashes());
    }

    private static NeededItemProvider provider()
    {
        return new NeededItemProvider(id -> id, Map.of(
            "filled", Set.of(10), "empty", Set.of(20), "unbuilt", Set.of(30), "unknown", Set.of(40)));
    }

    private static StashEntry entry(String id, BuildStatus build, ContentsStatus contents)
    {
        return tierEntry(id, StashTier.EASY, build, contents);
    }

    private static StashEntry tierEntry(String id, StashTier tier, BuildStatus build, ContentsStatus contents)
    {
        return new StashEntry(new StashDefinition(id, 1, id, tier, 0, 0, 0, List.of()), build, contents, false);
    }
}
