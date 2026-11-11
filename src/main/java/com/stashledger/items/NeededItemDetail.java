package com.stashledger.items;

/**
 * A deliberately count-free explanation for an item marker. It says only that
 * the item can help an unfinished STASH plan; it does not claim a deficit or
 * prove fulfillment of a composite equipment requirement.
 */
public final class NeededItemDetail
{
    private final int itemId;

    NeededItemDetail(int itemId)
    {
        this.itemId = itemId;
    }

    public int getItemId()
    {
        return itemId;
    }
}
