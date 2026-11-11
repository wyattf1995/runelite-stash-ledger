package com.stashledger.state;

import com.stashledger.model.BuildStatus;
import com.stashledger.model.ContentsStatus;
import com.stashledger.model.StashDefinition;
import com.stashledger.model.StashEntry;
import com.stashledger.model.StashTier;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class LedgerStateTest
{
    private LedgerState newState()
    {
        return new LedgerState(List.of(
            new StashDefinition("a", 1, "A", StashTier.EASY, 1, 2, 0, List.of("Gold ring")),
            new StashDefinition("b", 2, "B", StashTier.MASTER, 3, 4, 0, List.of("Any full Barrows set"))));
    }

    @Test public void unseenIsUnknownRatherThanNotBuiltOrEmpty()
    {
        LedgerState state = newState();
        for (StashEntry entry : state.snapshot())
        {
            assertEquals(BuildStatus.UNKNOWN, entry.getBuildStatus());
            assertEquals(ContentsStatus.UNKNOWN, entry.getContentsStatus());
        }
    }

    @Test public void onlyBuiltUnitsAcceptContentsConfirmation()
    {
        LedgerState state = newState();
        assertFalse(state.markContents("a", ContentsStatus.FILLED));
        state.applyBuildScan(Map.of("a", BuildStatus.NOT_BUILT, "b", BuildStatus.BUILT));
        assertFalse(state.markContents("a", ContentsStatus.FILLED));
        assertTrue(state.markContents("b", ContentsStatus.EMPTY));
        assertFalse(state.markContents("missing", ContentsStatus.FILLED));
        assertEquals(ContentsStatus.EMPTY, state.snapshot().get(1).getContentsStatus());
    }

    @Test public void savedContentsAndPinsRoundTripWithoutTrustingOldConstruction()
    {
        LedgerState first = newState();
        first.applyBuildScan(Map.of("a", BuildStatus.BUILT));
        first.markContents("a", ContentsStatus.FILLED);
        first.togglePinned("a");
        LedgerState restored = newState();
        restored.load(first.serialize());
        assertEquals(BuildStatus.UNKNOWN, restored.snapshot().get(0).getBuildStatus());
        assertEquals(ContentsStatus.UNKNOWN, restored.snapshot().get(0).getContentsStatus());
        assertTrue(restored.snapshot().get(0).isPinned());
        restored.applyBuildScan(Map.of("a", BuildStatus.BUILT));
        assertEquals(ContentsStatus.FILLED, restored.snapshot().get(0).getContentsStatus());
    }

    @Test public void confirmedUnbuiltClearsContentsButKeepsShortlist()
    {
        LedgerState state = newState();
        state.applyBuildScan(Map.of("a", BuildStatus.BUILT));
        state.markContents("a", ContentsStatus.FILLED);
        state.togglePinned("a");
        state.applyBuildScan(Map.of("a", BuildStatus.NOT_BUILT));
        state.applyBuildScan(Map.of("a", BuildStatus.BUILT));
        assertEquals(ContentsStatus.UNKNOWN, state.snapshot().get(0).getContentsStatus());
        assertTrue(state.snapshot().get(0).isPinned());
    }

    @Test public void unknownScanDoesNotDestroyConfirmedContents()
    {
        LedgerState state = newState();
        state.applyBuildScan(Map.of("a", BuildStatus.BUILT));
        state.markContents("a", ContentsStatus.FILLED);
        state.applyBuildScan(Map.of());
        assertEquals(ContentsStatus.UNKNOWN, state.snapshot().get(0).getContentsStatus());
        state.applyBuildScan(Map.of("a", BuildStatus.BUILT));
        assertEquals(ContentsStatus.FILLED, state.snapshot().get(0).getContentsStatus());
    }

    @Test public void loadingAnotherAccountClearsOldPinsAndContents()
    {
        LedgerState state = newState();
        state.togglePinned("a");
        state.applyBuildScan(Map.of("a", BuildStatus.BUILT));
        state.markContents("a", ContentsStatus.FILLED);
        state.load(null);
        assertFalse(state.snapshot().get(0).isPinned());
        assertEquals(BuildStatus.UNKNOWN, state.snapshot().get(0).getBuildStatus());
        state.applyBuildScan(Map.of("a", BuildStatus.BUILT));
        assertEquals(ContentsStatus.UNKNOWN, state.snapshot().get(0).getContentsStatus());
    }

    @Test public void malformedSaveDoesNotPartiallyReplaceCurrentState()
    {
        LedgerState state = newState();
        state.togglePinned("a");
        String original = state.serialize();
        assertThrows(RuntimeException.class, () -> state.load("{\"version\":1,\"units\":{\"a\":{\"pinned\":false},\"b\":{\"contents\":\"broken\"}}}"));
        assertEquals(original, state.serialize());
        assertThrows(RuntimeException.class, () -> state.load("{\"version\":2,\"units\":{}}"));
        assertEquals(original, state.serialize());
    }

    @Test public void snapshotsAndDefinitionsAreImmutable()
    {
        LedgerState state = newState();
        List<StashEntry> old = state.snapshot();
        state.togglePinned("a");
        assertFalse(old.get(0).isPinned());
        assertThrows(UnsupportedOperationException.class, () -> old.clear());
        assertThrows(UnsupportedOperationException.class, () -> old.get(0).getDefinition().getItems().clear());
    }
}
