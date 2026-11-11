package com.stashledger.items;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.Objects;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/** Renders one passive planner dot for candidate items in inventory, bank, and equipment widgets. */
public final class StashNeededItemOverlay extends WidgetItemOverlay
{
    static final Color PLANNER_COLOR = new Color(230, 189, 121, 220);
    private final NeededItemLookup neededItems;

    public StashNeededItemOverlay(NeededItemLookup neededItems)
    {
        this.neededItems = Objects.requireNonNull(neededItems);
        showOnInventory();
        showOnBank();
        showOnEquipment();
    }

    @Override
    public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
    {
        if (!neededItems.isNeeded(itemId))
        {
            return;
        }
        Rectangle bounds = widgetItem.getCanvasBounds();
        int diameter = Math.max(4, Math.min(7, Math.min(bounds.width, bounds.height) / 4));
        int x = bounds.x + bounds.width - diameter - 2;
        int y = bounds.y + 2;
        graphics.setColor(PLANNER_COLOR);
        graphics.fillOval(x, y, diameter, diameter);
    }
}
