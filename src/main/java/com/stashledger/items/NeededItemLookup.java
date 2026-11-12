package com.stashledger.items;

import java.util.Optional;

/** Immutable-read view used by the item widget overlay. */
public interface NeededItemLookup
{
    boolean isNeeded(int itemId);

    /** A count-free, immutable explanation for a visible planner marker. */
    default Optional<NeededItemDetail> detailFor(int itemId)
    {
        return Optional.empty();
    }
}
