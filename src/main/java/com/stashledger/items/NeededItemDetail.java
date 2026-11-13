package com.stashledger.items;

import com.stashledger.model.StashEntry;
import com.stashledger.model.StashTier;
import java.util.ArrayList;
import java.util.EnumSet;
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
    private final List<StashTier> tiers;
    private final boolean focused;

    NeededItemDetail(int itemId, List<StashEntry> stashes, boolean focused)
    {
        this.itemId = itemId;
        this.stashes = List.copyOf(stashes);
        EnumSet<StashTier> found = EnumSet.noneOf(StashTier.class);
        for (StashEntry stash : this.stashes)
        {
            if (stash != null && stash.getDefinition() != null)
            {
                found.add(stash.getDefinition().getTier());
            }
        }
        this.tiers = List.copyOf(new ArrayList<>(found));
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

    /** Relevant clue tiers in stable enum order, deduplicated for compact dots. */
    public List<StashTier> getTiers()
    {
        return tiers;
    }

    public boolean isFocused()
    {
        return focused;
    }
}
