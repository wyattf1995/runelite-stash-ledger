package com.stashledger;

import com.stashledger.items.DotColorMode;
import com.stashledger.items.ItemHighlightStyle;
import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

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

    @ConfigSection(name = "Dot colors", description = "Colors used when Item highlight style is Dot.",
        position = 6, closedByDefault = true)
    String DOT_COLORS = "dotColors";

    @ConfigItem(keyName = "dotColorMode", name = "Dot color mode",
        description = "Use one dot per relevant clue tier, or one shared planner dot.",
        section = DOT_COLORS, position = 0)
    default DotColorMode dotColorMode() { return DotColorMode.CLUE_TIER; }

    @ConfigItem(keyName = "singleDotColor", name = "Single dot color",
        description = "Used only when Dot color mode is Single color.", section = DOT_COLORS, position = 1)
    default Color singleDotColor() { return new Color(230, 189, 121, 220); }

    @ConfigItem(keyName = "beginnerDotColor", name = "Beginner dot", description = "Color for Beginner STASH dots.",
        section = DOT_COLORS, position = 2)
    default Color beginnerDotColor() { return new Color(166, 171, 181, 220); }

    @ConfigItem(keyName = "easyDotColor", name = "Easy dot", description = "Color for Easy STASH dots.",
        section = DOT_COLORS, position = 3)
    default Color easyDotColor() { return new Color(133, 187, 150, 220); }

    @ConfigItem(keyName = "mediumDotColor", name = "Medium dot", description = "Color for Medium STASH dots.",
        section = DOT_COLORS, position = 4)
    default Color mediumDotColor() { return new Color(130, 167, 207, 220); }

    @ConfigItem(keyName = "hardDotColor", name = "Hard dot", description = "Color for Hard STASH dots.",
        section = DOT_COLORS, position = 5)
    default Color hardDotColor() { return new Color(220, 116, 105, 220); }

    @ConfigItem(keyName = "eliteDotColor", name = "Elite dot", description = "Color for Elite STASH dots.",
        section = DOT_COLORS, position = 6)
    default Color eliteDotColor() { return new Color(177, 139, 207, 220); }

    @ConfigItem(keyName = "masterDotColor", name = "Master dot", description = "Color for Master STASH dots.",
        section = DOT_COLORS, position = 7)
    default Color masterDotColor() { return new Color(230, 189, 121, 220); }
}
