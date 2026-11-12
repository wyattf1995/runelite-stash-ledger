package com.stashledger.items;

import com.stashledger.StashLedgerConfig;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Tooltip;
import net.runelite.client.ui.overlay.TooltipManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/** Renders passive, count-free planner markers in inventory, bank, and equipment widgets. */
public final class StashNeededItemOverlay extends WidgetItemOverlay
{
    static final Color PLANNER_COLOR = new Color(230, 189, 121, 220);
    private final NeededItemLookup neededItems;
    private final Client client;
    private final ItemManager itemManager;
    private final TooltipManager tooltipManager;
    private final StashLedgerConfig config;

    public StashNeededItemOverlay(NeededItemLookup neededItems, Client client, ItemManager itemManager,
                                  TooltipManager tooltipManager, StashLedgerConfig config)
    {
        this.neededItems = Objects.requireNonNull(neededItems);
        this.client = Objects.requireNonNull(client);
        this.itemManager = Objects.requireNonNull(itemManager);
        this.tooltipManager = Objects.requireNonNull(tooltipManager);
        this.config = Objects.requireNonNull(config);
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
        Optional<NeededItemDetail> detail = neededItems.detailFor(itemId);
        Rectangle bounds = widgetItem.getCanvasBounds();
        if (config.highlightStyle() == ItemHighlightStyle.OUTLINE)
        {
            BufferedImage outline = itemManager.getItemOutline(itemId, widgetItem.getQuantity(), PLANNER_COLOR);
            if (outline != null)
            {
                graphics.drawImage(outline, bounds.x, bounds.y, null);
            }
        }
        else
        {
            drawDot(graphics, bounds);
        }
        if (detail.isPresent())
        {
            addTooltipIfHovered(detail.get(), widgetItem, bounds);
        }
    }

    private static void drawDot(Graphics2D graphics, Rectangle bounds)
    {
        int diameter = Math.max(4, Math.min(7, Math.min(bounds.width, bounds.height) / 4));
        int x = bounds.x + bounds.width - diameter - 2;
        int y = bounds.y + 2;
        graphics.setColor(PLANNER_COLOR);
        graphics.fillOval(x, y, diameter, diameter);
    }

    private void addTooltipIfHovered(NeededItemDetail detail, WidgetItem widgetItem, Rectangle bounds)
    {
        if (!config.itemHoverDetails() || client.isMenuOpen() || client.isDraggingWidget()
            || widgetItem.getDraggingCanvasBounds() != null)
        {
            return;
        }
        Point mouse = client.getMouseCanvasPosition();
        if (mouse == null || !bounds.contains(mouse.getX(), mouse.getY()))
        {
            return;
        }
        tooltipManager.add(new Tooltip(tooltipText(detail)));
    }

    static String tooltipText(NeededItemDetail detail)
    {
        StringBuilder text = new StringBuilder(detail.isFocused() ? "<col=E6BD79>Fill this STASH</col><br>" : "<col=E6BD79>STASH planner</col><br>");
        List<com.stashledger.model.StashEntry> stashes = detail.getStashes();
        int visible = Math.min(6, stashes.size());
        for (int index = 0; index < visible; index++)
        {
            com.stashledger.model.StashEntry stash = stashes.get(index);
            text.append(escape(stash.getDefinition().getName())).append(" - ")
                .append(escape(stash.getDefinition().getTier().getLabel())).append(" - ")
                .append(escape(state(stash))).append("<br>");
        }
        if (stashes.size() > visible)
        {
            text.append('+').append(stashes.size() - visible).append(" more locations");
        }
        return text.toString();
    }

    private static String state(com.stashledger.model.StashEntry entry)
    {
        if (entry.getBuildStatus() != com.stashledger.model.BuildStatus.BUILT)
        {
            return entry.getBuildStatus() == com.stashledger.model.BuildStatus.NOT_BUILT ? "not built" : "construction unknown";
        }
        switch (entry.getContentsStatus())
        {
            case FILLED: return "filled";
            case EMPTY: return "empty";
            default: return "contents unknown";
        }
    }

    private static String escape(String value)
    {
        return value.replace("<", "[").replace(">", "]");
    }
}
