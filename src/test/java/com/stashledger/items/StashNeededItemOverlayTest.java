package com.stashledger.items;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stashledger.StashLedgerConfig;
import com.stashledger.model.BuildStatus;
import com.stashledger.model.ContentsStatus;
import com.stashledger.model.StashDefinition;
import com.stashledger.model.StashEntry;
import com.stashledger.model.StashTier;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import org.junit.Test;

public class StashNeededItemOverlayTest
{
    @Test
    public void rendersOnePlannerDotWithoutQuantityOrDeficitText()
    {
        StashLedgerConfig config = config(ItemHighlightStyle.DOT, false);
        StashNeededItemOverlay overlay = new StashNeededItemOverlay(itemId -> itemId == 42,
            mock(Client.class), mock(ItemManager.class), mock(TooltipManager.class), config);
        BufferedImage image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try
        {
            overlay.renderItemOverlay(graphics, 42, new WidgetItem(42, 999, new Rectangle(4, 4, 24, 24), null, null));
        }
        finally
        {
            graphics.dispose();
        }

        assertEquals(StashNeededItemOverlay.PLANNER_COLOR.getRGB(), image.getRGB(22, 8));
    }

    @Test
    public void outlineUsesOriginalWidgetIdAndQuantity()
    {
        NeededItemLookup lookup = itemId -> itemId == 42;
        ItemManager itemManager = mock(ItemManager.class);
        BufferedImage outline = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
        outline.setRGB(5, 6, StashNeededItemOverlay.PLANNER_COLOR.getRGB());
        when(itemManager.getItemOutline(42, 999, StashNeededItemOverlay.PLANNER_COLOR)).thenReturn(outline);
        StashNeededItemOverlay overlay = new StashNeededItemOverlay(lookup, mock(Client.class), itemManager,
            mock(TooltipManager.class), config(ItemHighlightStyle.OUTLINE, false));
        BufferedImage image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);

        Graphics2D graphics = image.createGraphics();
        try
        {
            overlay.renderItemOverlay(graphics, 42, new WidgetItem(42, 999, new Rectangle(4, 4, 24, 24), null, null));
        }
        finally
        {
            graphics.dispose();
        }

        verify(itemManager).getItemOutline(42, 999, StashNeededItemOverlay.PLANNER_COLOR);
        assertEquals("The cached icon outline is rendered at the widget origin",
            StashNeededItemOverlay.PLANNER_COLOR.getRGB(), image.getRGB(9, 10));
        assertEquals("An outline must preserve transparent pixels around the item", 0, image.getRGB(4, 4));
    }

    @Test
    public void tooltipExplainsFocusedLocationsWithoutClaimingQuantity()
    {
        NeededItemDetail detail = new NeededItemDetail(42, List.of(
            entry("Bob's <axes>", StashTier.BEGINNER, BuildStatus.BUILT, ContentsStatus.EMPTY)), true);

        String text = StashNeededItemOverlay.tooltipText(detail);

        assertTrue(text.contains("Fill this STASH"));
        assertTrue(text.contains("Bob's [axes] - Beginner - empty"));
        assertFalse(text.toLowerCase().contains("need "));
        assertFalse(text.toLowerCase().contains("missing"));
    }

    @Test
    public void hoverAddsTooltipOnlyForCandidateUnderMouseWhenMenusAreClosed()
    {
        Client client = mock(Client.class);
        when(client.getMouseCanvasPosition()).thenReturn(new Point(10, 10));
        NeededItemDetail detail = new NeededItemDetail(42, List.of(
            entry("One", StashTier.EASY, BuildStatus.BUILT, ContentsStatus.EMPTY)), false);
        NeededItemLookup lookup = new NeededItemLookup()
        {
            @Override public boolean isNeeded(int itemId) { return itemId == 42; }
            @Override public java.util.Optional<NeededItemDetail> detailFor(int itemId)
            {
                return itemId == 42 ? java.util.Optional.of(detail) : java.util.Optional.empty();
            }
        };
        TooltipManager tooltips = mock(TooltipManager.class);
        StashNeededItemOverlay overlay = new StashNeededItemOverlay(lookup, client, mock(ItemManager.class), tooltips,
            config(ItemHighlightStyle.DOT, true));
        BufferedImage image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try
        {
            overlay.renderItemOverlay(graphics, 42, new WidgetItem(42, 1, new Rectangle(4, 4, 24, 24), null, null));
        }
        finally
        {
            graphics.dispose();
        }

        verify(tooltips).add(any());
        when(client.isMenuOpen()).thenReturn(true);
        graphics = image.createGraphics();
        try
        {
            overlay.renderItemOverlay(graphics, 42, new WidgetItem(42, 1, new Rectangle(4, 4, 24, 24), null, null));
        }
        finally
        {
            graphics.dispose();
        }
        verify(tooltips, times(1)).add(any());
    }

    @Test
    public void hoverRespectsPointerDraggingDisabledDetailsAndUnrelatedItems()
    {
        Client client = mock(Client.class);
        StashLedgerConfig config = config(ItemHighlightStyle.DOT, true);
        TooltipManager tooltips = mock(TooltipManager.class);
        NeededItemDetail detail = new NeededItemDetail(42, List.of(
            entry("One", StashTier.EASY, BuildStatus.BUILT, ContentsStatus.EMPTY)), false);
        NeededItemLookup lookup = new NeededItemLookup()
        {
            @Override public boolean isNeeded(int itemId) { return itemId == 42; }
            @Override public java.util.Optional<NeededItemDetail> detailFor(int itemId)
            {
                return java.util.Optional.of(detail);
            }
        };
        StashNeededItemOverlay overlay = new StashNeededItemOverlay(lookup, client, mock(ItemManager.class), tooltips, config);
        WidgetItem item = new WidgetItem(42, 1, new Rectangle(4, 4, 24, 24), null, null);
        when(client.getMouseCanvasPosition()).thenReturn(null);
        render(overlay, 42, item);
        when(client.getMouseCanvasPosition()).thenReturn(new Point(35, 35));
        render(overlay, 42, item);
        when(client.getMouseCanvasPosition()).thenReturn(new Point(10, 10));
        render(overlay, 99, item);
        when(client.isDraggingWidget()).thenReturn(true);
        render(overlay, 42, item);
        when(client.isDraggingWidget()).thenReturn(false);
        render(overlay, 42, new WidgetItem(42, 1, new Rectangle(4, 4, 24, 24), null,
            new Rectangle(4, 4, 24, 24)));
        when(config.itemHoverDetails()).thenReturn(false);
        render(overlay, 42, item);
        verify(tooltips, never()).add(any());

        when(config.itemHoverDetails()).thenReturn(true);
        render(overlay, 42, item);
        verify(tooltips).add(any());
    }

    @Test
    public void globalTooltipExplainsUncertaintyAndBoundsSharedDestinations()
    {
        NeededItemDetail detail = new NeededItemDetail(42, List.of(
            entry("First", StashTier.EASY, BuildStatus.UNKNOWN, ContentsStatus.UNKNOWN),
            entry("Second", StashTier.MEDIUM, BuildStatus.NOT_BUILT, ContentsStatus.UNKNOWN),
            entry("Third", StashTier.HARD, BuildStatus.BUILT, ContentsStatus.UNKNOWN),
            entry("Fourth", StashTier.ELITE, BuildStatus.BUILT, ContentsStatus.EMPTY),
            entry("Fifth", StashTier.MASTER, BuildStatus.BUILT, ContentsStatus.EMPTY),
            entry("Sixth", StashTier.EASY, BuildStatus.BUILT, ContentsStatus.EMPTY),
            entry("Seventh", StashTier.EASY, BuildStatus.BUILT, ContentsStatus.EMPTY)), false);

        String text = StashNeededItemOverlay.tooltipText(detail);

        assertTrue(text.contains("STASH planner"));
        assertTrue(text.contains("First - Easy - construction unknown"));
        assertTrue(text.contains("Second - Medium - not built"));
        assertTrue(text.contains("Third - Hard - contents unknown"));
        assertTrue(text.contains("Sixth - Easy - empty"));
        assertFalse(text.contains("Seventh"));
        assertTrue(text.contains("+1 more locations"));
    }

    private static void render(StashNeededItemOverlay overlay, int itemId, WidgetItem item)
    {
        Graphics2D graphics = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try { overlay.renderItemOverlay(graphics, itemId, item); }
        finally { graphics.dispose(); }
    }

    private static StashLedgerConfig config(ItemHighlightStyle style, boolean details)
    {
        StashLedgerConfig config = mock(StashLedgerConfig.class);
        when(config.highlightStyle()).thenReturn(style);
        when(config.itemHoverDetails()).thenReturn(details);
        return config;
    }

    private static StashEntry entry(String name, StashTier tier, BuildStatus build, ContentsStatus contents)
    {
        return new StashEntry(new StashDefinition(name, 1, name, tier, 0, 0, 0, List.of()), build, contents, false);
    }
}
