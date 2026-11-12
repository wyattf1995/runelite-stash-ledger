package com.stashledger.items;

import com.stashledger.StashLedgerConfig;
import com.stashledger.model.StashTier;
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
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
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
            drawDots(graphics, bounds, detail.orElse(null));
        }
        if (detail.isPresent())
        {
            addTooltipIfHovered(detail.get(), widgetItem, bounds);
        }
    }

    private void drawDots(Graphics2D graphics, Rectangle bounds, NeededItemDetail detail)
    {
        List<StashTier> tiers = detail == null ? List.of() : detail.getTiers();
        if (config.dotColorMode() != DotColorMode.CLUE_TIER || tiers.isEmpty())
        {
            drawDot(graphics, bounds, configuredColor(config.singleDotColor()));
            return;
        }

        int diameter = dotDiameter(bounds);
        if (diameter < 3) { return; }
        int gap = 1;
        int columns = Math.min(3, tiers.size());
        int width = columns * diameter + (columns - 1) * gap;
        int rows = (tiers.size() + 2) / 3;
        int height = rows * diameter + (rows - 1) * gap;
        int x = bounds.x + bounds.width - width - 2;
        int y = bounds.y + 2;
        if (x < bounds.x || y + height > bounds.y + bounds.height) { return; }
        for (int index = 0; index < tiers.size(); index++)
        {
            int dotX = x + (index % 3) * (diameter + gap);
            int dotY = y + (index / 3) * (diameter + gap);
            graphics.setColor(tierColor(tiers.get(index)));
            graphics.fillOval(dotX, dotY, diameter, diameter);
        }
    }

    private static int dotDiameter(Rectangle bounds)
    {
        int diameter = Math.min(7, Math.min(bounds.width, bounds.height) / 4);
        return diameter >= 3 ? diameter : 0;
    }

    private static void drawDot(Graphics2D graphics, Rectangle bounds, Color color)
    {
        int diameter = dotDiameter(bounds);
        if (diameter < 3) { return; }
        int x = bounds.x + bounds.width - diameter - 2;
        int y = bounds.y + 2;
        if (x < bounds.x || y + diameter > bounds.y + bounds.height) { return; }
        graphics.setColor(color);
        graphics.fillOval(x, y, diameter, diameter);
    }

    private Color tierColor(StashTier tier)
    {
        if (tier == null) { return PLANNER_COLOR; }
        switch (tier)
        {
            case BEGINNER: return configuredColor(config.beginnerDotColor());
            case EASY: return configuredColor(config.easyDotColor());
            case MEDIUM: return configuredColor(config.mediumDotColor());
            case HARD: return configuredColor(config.hardDotColor());
            case ELITE: return configuredColor(config.eliteDotColor());
            case MASTER: return configuredColor(config.masterDotColor());
            default: return PLANNER_COLOR;
        }
    }

    private static Color configuredColor(Color configured)
    {
        return configured == null ? PLANNER_COLOR : configured;
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
        if (!detail.getTiers().isEmpty())
        {
            text.append("Tiers: ");
            for (int index = 0; index < detail.getTiers().size(); index++)
            {
                if (index > 0) { text.append(", "); }
                text.append(detail.getTiers().get(index).getLabel());
            }
            text.append("<br>");
        }
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
