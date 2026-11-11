package com.stashledger.tracking;

import com.stashledger.model.ContentsStatus;
import com.stashledger.model.StashDefinition;
import java.util.Objects;
import java.util.Optional;

/**
 * Correlates one explicitly verified STASH Search interaction with RuneLite's
 * exact deposit or withdraw success message.
 *
 * <p>This helper deliberately does not infer a STASH from chat or player
 * proximity. The event adapter must either use
 * {@link #recordInteraction(StashDefinition, int, int, int, int, Action, long)}
 * with the clicked object's world location, or verify that same predicate
 * before calling {@link #recordVerifiedInteraction(String, Action, long)}.
 * It must call {@link #clear()} at logout, profile/account changes, world hops,
 * and on every interaction that is not a matching STASH. Account ownership and
 * state mutation remain with the caller.</p>
 *
 * <p>The success strings are the exact equality checks used by RuneLite's
 * {@code ClueScrollPlugin}; failed and unrelated messages cannot produce an
 * update.</p>
 */
public final class StashInteractionTracker
{
    public static final String DEPOSIT_SUCCESS_MESSAGE = "You deposit your items into the STASH unit.";
    public static final String WITHDRAW_SUCCESS_MESSAGE = "You withdraw your items from the STASH unit.";

    /** The only STASH menu action verified in pinned RuneLite source. */
    public enum Action
    {
        SEARCH
    }

    /** Immutable confirmed contents change for the state owner to apply. */
    public static final class Update
    {
        private final String stashId;
        private final ContentsStatus contentsStatus;

        private Update(String stashId, ContentsStatus contentsStatus)
        {
            this.stashId = stashId;
            this.contentsStatus = contentsStatus;
        }

        public String getStashId()
        {
            return stashId;
        }

        public ContentsStatus getContentsStatus()
        {
            return contentsStatus;
        }
    }

    private final long timeoutTicks;
    private Pending pending;

    public StashInteractionTracker(long timeoutTicks)
    {
        if (timeoutTicks < 0)
        {
            throw new IllegalArgumentException("timeoutTicks must not be negative");
        }
        this.timeoutTicks = timeoutTicks;
    }

    /**
     * Records Search only when the clicked object ID and world location exactly
     * match the supplied catalog definition. A nonmatch cancels older pending
     * state rather than leaving it available for a later chat line.
     */
    public void recordInteraction(StashDefinition definition, int objectId, int x, int y, int plane,
                                  Action action, long tick)
    {
        Objects.requireNonNull(definition, "definition");
        if (definition.getObjectId() != objectId || definition.getX() != x
            || definition.getY() != y || definition.getPlane() != plane)
        {
            clear();
            return;
        }
        recordVerifiedInteraction(definition.getId(), action, tick);
    }

    /**
     * Records a Search already verified by an adapter against its catalog
     * object ID and location. A later verified interaction replaces the old
     * pending interaction.
     */
    public void recordVerifiedInteraction(String stashId, Action action, long tick)
    {
        if (stashId == null || stashId.trim().isEmpty())
        {
            throw new IllegalArgumentException("stashId must not be blank");
        }
        pending = new Pending(stashId, Objects.requireNonNull(action, "action"), tick);
    }

    /** Clears pending state at an interaction, logout, profile, or world boundary. */
    public void clear()
    {
        pending = null;
    }

    /**
     * Consumes a pending Search only for either exact core success message.
     * Arbitrary chat leaves a still-valid pending interaction intact; it cannot
     * overwrite contents. A matching success is one-shot.
     */
    public Optional<Update> acceptSuccessMessage(String message, long tick)
    {
        if (pending == null)
        {
            return Optional.empty();
        }
        if (expired(tick))
        {
            clear();
            return Optional.empty();
        }

        ContentsStatus status = statusFor(message);
        if (status == null)
        {
            return Optional.empty();
        }

        String stashId = pending.stashId;
        clear();
        return Optional.of(new Update(stashId, status));
    }

    private boolean expired(long tick)
    {
        return tick < pending.tick || tick - pending.tick > timeoutTicks;
    }

    private static ContentsStatus statusFor(String message)
    {
        if (DEPOSIT_SUCCESS_MESSAGE.equals(message))
        {
            return ContentsStatus.FILLED;
        }
        if (WITHDRAW_SUCCESS_MESSAGE.equals(message))
        {
            return ContentsStatus.EMPTY;
        }
        return null;
    }

    private static final class Pending
    {
        private final String stashId;
        private final Action action;
        private final long tick;

        private Pending(String stashId, Action action, long tick)
        {
            this.stashId = stashId;
            this.action = action;
            this.tick = tick;
        }
    }
}
