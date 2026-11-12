package com.stashledger.items;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import net.runelite.client.ui.overlay.TooltipManager;
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
