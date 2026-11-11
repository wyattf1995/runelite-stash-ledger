package com.stashledger;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(StashLedgerPlugin.CONFIG_GROUP)
public interface StashLedgerConfig extends Config
{
    @ConfigItem(keyName = "highlightItems", name = "Highlight needed items",
        description = "Mark items needed by STASH units that are not confirmed filled in your inventory, bank and equipment.", position = 0)
    default boolean highlightItems() { return true; }

    @ConfigItem(keyName = "showAllMapMarkers", name = "Show all STASH map markers",
        description = "Show every STASH on the world map, coloured by its known construction and contents state.", position = 1)
    default boolean showAllMapMarkers() { return true; }

    @ConfigItem(keyName = "hideFilledMapMarkers", name = "Hide filled map markers",
        description = "Hide confirmed filled STASH units from the all-units map. A location selected in the sidebar stays visible.", position = 2)
    default boolean hideFilledMapMarkers() { return true; }

    @ConfigItem(keyName = "autoTrackContents", name = "Track STASH interactions",
        description = "Update contents after a confirmed deposit or withdrawal at the STASH unit you interacted with.", position = 3)
    default boolean autoTrackContents() { return true; }
}
