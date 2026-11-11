package com.stashledger.items;

/** Immutable-read view used by the item widget overlay. */
public interface NeededItemLookup
{
    boolean isNeeded(int itemId);
}
