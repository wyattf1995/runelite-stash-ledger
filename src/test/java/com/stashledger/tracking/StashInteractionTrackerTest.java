package com.stashledger.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.stashledger.model.ContentsStatus;
import com.stashledger.model.StashDefinition;
import com.stashledger.model.StashTier;
import java.util.Collections;
import java.util.Optional;
import org.junit.Test;

public class StashInteractionTrackerTest
{
    @Test
    public void exactDepositAfterMatchingSearchProducesOneFilledUpdate()
    {
        StashInteractionTracker tracker = new StashInteractionTracker(2);
        tracker.recordInteraction(definition("one", 100, 3200, 3201, 0), 100, 3200, 3201, 0,
            StashInteractionTracker.Action.SEARCH, 40);

        StashInteractionTracker.Update update = tracker
            .acceptSuccessMessage(StashInteractionTracker.DEPOSIT_SUCCESS_MESSAGE, 42).get();

        assertEquals("one", update.getStashId());
        assertEquals(ContentsStatus.FILLED, update.getContentsStatus());
        assertFalse(tracker.acceptSuccessMessage(StashInteractionTracker.DEPOSIT_SUCCESS_MESSAGE, 42).isPresent());
    }

    @Test
    public void exactWithdrawAfterMatchingSearchProducesEmptyUpdate()
    {
        StashInteractionTracker tracker = new StashInteractionTracker(2);
        tracker.recordVerifiedInteraction("one", StashInteractionTracker.Action.SEARCH, 40);

        Optional<StashInteractionTracker.Update> update = tracker
            .acceptSuccessMessage(StashInteractionTracker.WITHDRAW_SUCCESS_MESSAGE, 41);

        assertTrue(update.isPresent());
        assertEquals(ContentsStatus.EMPTY, update.get().getContentsStatus());
    }

    @Test
    public void spuriousOrLooselySimilarMessagesCannotChangeContents()
    {
        StashInteractionTracker tracker = new StashInteractionTracker(2);
        tracker.recordVerifiedInteraction("one", StashInteractionTracker.Action.SEARCH, 40);

        assertFalse(tracker.acceptSuccessMessage("You filled the STASH unit.", 41).isPresent());
        assertFalse(tracker.acceptSuccessMessage("You deposit your items into the stash unit.", 41).isPresent());
        assertFalse(tracker.acceptSuccessMessage("You deposit your items into the STASH unit.", 43).isPresent());
    }

    @Test
    public void expiredPendingSearchCannotApplyAnOtherwiseExactSuccessMessage()
    {
        StashInteractionTracker tracker = new StashInteractionTracker(1);
        tracker.recordVerifiedInteraction("one", StashInteractionTracker.Action.SEARCH, 40);

        assertFalse(tracker.acceptSuccessMessage(StashInteractionTracker.DEPOSIT_SUCCESS_MESSAGE, 42).isPresent());
        assertFalse(tracker.acceptSuccessMessage(StashInteractionTracker.DEPOSIT_SUCCESS_MESSAGE, 42).isPresent());
    }

    @Test
    public void wrongObjectOrLocationCancelsTheEarlierPendingSearch()
    {
        StashInteractionTracker tracker = new StashInteractionTracker(2);
        StashDefinition definition = definition("one", 100, 3200, 3201, 0);
        tracker.recordVerifiedInteraction("other", StashInteractionTracker.Action.SEARCH, 40);

        tracker.recordInteraction(definition, 101, 3200, 3201, 0, StashInteractionTracker.Action.SEARCH, 41);
        assertFalse(tracker.acceptSuccessMessage(StashInteractionTracker.DEPOSIT_SUCCESS_MESSAGE, 41).isPresent());

        tracker.recordVerifiedInteraction("other", StashInteractionTracker.Action.SEARCH, 42);
        tracker.recordInteraction(definition, 100, 3201, 3201, 0, StashInteractionTracker.Action.SEARCH, 42);
        assertFalse(tracker.acceptSuccessMessage(StashInteractionTracker.WITHDRAW_SUCCESS_MESSAGE, 42).isPresent());
    }

    @Test
    public void laterMatchingSearchReplacesEarlierOne()
    {
        StashInteractionTracker tracker = new StashInteractionTracker(2);
        tracker.recordVerifiedInteraction("one", StashInteractionTracker.Action.SEARCH, 40);
        tracker.recordVerifiedInteraction("two", StashInteractionTracker.Action.SEARCH, 41);

        StashInteractionTracker.Update update = tracker
            .acceptSuccessMessage(StashInteractionTracker.DEPOSIT_SUCCESS_MESSAGE, 42).get();

        assertEquals("two", update.getStashId());
    }

    @Test
    public void profileOrWorldBoundaryClearPreventsCrossAccountApplication()
    {
        StashInteractionTracker tracker = new StashInteractionTracker(2);
        tracker.recordVerifiedInteraction("one", StashInteractionTracker.Action.SEARCH, 40);

        tracker.clear();

        assertFalse(tracker.acceptSuccessMessage(StashInteractionTracker.DEPOSIT_SUCCESS_MESSAGE, 40).isPresent());
    }

    private static StashDefinition definition(String id, int objectId, int x, int y, int plane)
    {
        return new StashDefinition(id, objectId, id, StashTier.BEGINNER, x, y, plane, Collections.emptyList());
    }
}
