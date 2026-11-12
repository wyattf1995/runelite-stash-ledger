package com.stashledger;

import com.stashledger.items.ItemHighlightStyle;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(StashLedgerPlugin.CONFIG_GROUP)
public interface StashLedgerConfig extends Config
{
    @ConfigItem(keyName = "highlightItems", name = "Highlight needed items",
        description = "Mark items needed by STASH units that are not confirmed filled in your inventory, bank and equipment.", position = 0)
    default boolean highlightItems() { return true; }

    @ConfigItem(keyName = "highlightStyle", name = "Item highlight style",
        description = "Use a clear icon outline or a small gold planner dot for relevant bank, inventory and equipment items.", position = 1)
    default ItemHighlightStyle highlightStyle() { return ItemHighlightStyle.OUTLINE; }

    @ConfigItem(keyName = "itemHoverDetails", name = "Show item hover details",
        description = "Show the STASH locations that a highlighted item can help fill. This never estimates item quantities.", position = 2)
    default boolean itemHoverDetails() { return true; }

    @ConfigItem(keyName = "showAllMapMarkers", name = "Show all STASH map markers",
        description = "Show every STASH on the world map, coloured by its known construction and contents state.", position = 3)
    default boolean showAllMapMarkers() { return true; }

    @ConfigItem(keyName = "hideFilledMapMarkers", name = "Hide filled map markers",
        description = "Hide confirmed filled STASH units from the all-units map. A location selected in the sidebar stays visible.", position = 4)
    default boolean hideFilledMapMarkers() { return true; }

    @ConfigItem(keyName = "autoTrackContents", name = "Track STASH interactions",
        description = "Update contents after a confirmed deposit or withdrawal at the STASH unit you interacted with.", position = 5)
    default boolean autoTrackContents() { return true; }
}
