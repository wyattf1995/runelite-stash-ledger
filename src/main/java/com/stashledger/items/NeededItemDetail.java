package com.stashledger.items;

import com.stashledger.model.StashEntry;
import java.util.List;

/**
 * A deliberately count-free explanation for an item marker. It says only that
 * the item can help an unfinished STASH plan; it does not claim a deficit or
 * prove fulfillment of a composite equipment requirement.
 */
public final class NeededItemDetail
{
    private final int itemId;
    private final List<StashEntry> stashes;
    private final boolean focused;

    NeededItemDetail(int itemId, List<StashEntry> stashes, boolean focused)
    {
        this.itemId = itemId;
        this.stashes = List.copyOf(stashes);
        this.focused = focused;
    }

    public int getItemId()
    {
        return itemId;
    }

    public List<StashEntry> getStashes()
    {
        return stashes;
    }

    public boolean isFocused()
    {
        return focused;
    }
}
