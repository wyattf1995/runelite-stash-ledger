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
import java.awt.Color;
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

    @Test
    public void tierDotsUseOneDistinctConfiguredColorPerTierInEnumOrder()
    {
        Color beginner = new Color(11, 21, 31);
        Color easy = new Color(41, 51, 61);
        Color hard = new Color(71, 81, 91);
        StashLedgerConfig config = tierConfig();
        when(config.beginnerDotColor()).thenReturn(beginner);
        when(config.easyDotColor()).thenReturn(easy);
        when(config.hardDotColor()).thenReturn(hard);
        NeededItemDetail detail = new NeededItemDetail(42, List.of(
            entry("Hard", StashTier.HARD, BuildStatus.BUILT, ContentsStatus.EMPTY),
            entry("Easy", StashTier.EASY, BuildStatus.BUILT, ContentsStatus.EMPTY),
            entry("Another easy", StashTier.EASY, BuildStatus.UNKNOWN, ContentsStatus.UNKNOWN),
            entry("Beginner", StashTier.BEGINNER, BuildStatus.NOT_BUILT, ContentsStatus.UNKNOWN)), false);
        StashNeededItemOverlay overlay = overlay(detail, config);

        BufferedImage image = renderImage(overlay, 42, new Rectangle(4, 4, 24, 24));

        assertEquals(List.of(StashTier.BEGINNER, StashTier.EASY, StashTier.HARD), detail.getTiers());
        assertEquals("Dots are ordered by tier instead of input or map order", List.of(beginner, easy, hard),
            dotColors(image, new Rectangle(4, 4, 24, 24)));
    }

    @Test
    public void singleColorModeShowsOneConfiguredDotForAllRelevantTiers()
    {
        Color single = new Color(101, 111, 121);
        StashLedgerConfig config = tierConfig();
        when(config.dotColorMode()).thenReturn(DotColorMode.SINGLE_COLOR);
        when(config.singleDotColor()).thenReturn(single);
        NeededItemDetail detail = new NeededItemDetail(42, List.of(
            entry("Easy", StashTier.EASY, BuildStatus.BUILT, ContentsStatus.EMPTY),
            entry("Master", StashTier.MASTER, BuildStatus.BUILT, ContentsStatus.EMPTY)), false);

        BufferedImage image = renderImage(overlay(detail, config), 42, new Rectangle(4, 4, 24, 24));

        assertEquals(List.of(single), dotColors(image, new Rectangle(4, 4, 24, 24)));
    }

    @Test
    public void tierDotColorsReadTheCurrentConfigurationOnEveryRender()
    {
        Color first = new Color(23, 33, 43);
        Color changed = new Color(53, 63, 73);
        StashLedgerConfig config = tierConfig();
        when(config.easyDotColor()).thenReturn(first);
        NeededItemDetail detail = new NeededItemDetail(42, List.of(
            entry("Easy", StashTier.EASY, BuildStatus.BUILT, ContentsStatus.EMPTY)), false);
        StashNeededItemOverlay overlay = overlay(detail, config);
        Rectangle bounds = new Rectangle(4, 4, 24, 24);

        assertEquals(List.of(first), dotColors(renderImage(overlay, 42, bounds), bounds));
        when(config.easyDotColor()).thenReturn(changed);
        assertEquals(List.of(changed), dotColors(renderImage(overlay, 42, bounds), bounds));
    }

    @Test
    public void sixTierDotsFitWithinTheItemBoundsWithoutSharingPixels()
    {
        StashLedgerConfig config = tierConfig();
        Color[] colors = {new Color(10, 20, 30), new Color(40, 50, 60), new Color(70, 80, 90),
            new Color(100, 110, 120), new Color(130, 140, 150), new Color(160, 170, 180)};
        when(config.beginnerDotColor()).thenReturn(colors[0]);
        when(config.easyDotColor()).thenReturn(colors[1]);
        when(config.mediumDotColor()).thenReturn(colors[2]);
        when(config.hardDotColor()).thenReturn(colors[3]);
        when(config.eliteDotColor()).thenReturn(colors[4]);
        when(config.masterDotColor()).thenReturn(colors[5]);
        NeededItemDetail detail = new NeededItemDetail(42, List.of(
            entry("B", StashTier.BEGINNER, BuildStatus.BUILT, ContentsStatus.EMPTY),
            entry("E", StashTier.EASY, BuildStatus.BUILT, ContentsStatus.EMPTY),
            entry("M", StashTier.MEDIUM, BuildStatus.BUILT, ContentsStatus.EMPTY),
            entry("H", StashTier.HARD, BuildStatus.BUILT, ContentsStatus.EMPTY),
            entry("L", StashTier.ELITE, BuildStatus.BUILT, ContentsStatus.EMPTY),
            entry("X", StashTier.MASTER, BuildStatus.BUILT, ContentsStatus.EMPTY)), false);
        for (int size : List.of(24, 32))
        {
            Rectangle bounds = new Rectangle(4, 4, size, size);
            BufferedImage image = renderImage(overlay(detail, config), 42, bounds);

            assertEquals(List.of(colors), dotColors(image, bounds));
            assertNoColorOutside(image, bounds, List.of(colors));
            List<Rectangle> dots = new java.util.ArrayList<>();
            for (Color color : colors)
            {
                List<Rectangle> components = componentBounds(image, bounds, color);
                assertEquals("Each configured tier has exactly one dot at " + size + "px", 1, components.size());
                Rectangle dot = components.get(0);
                assertTrue("A dot remains compact", dot.width <= 7 && dot.height <= 7);
                assertTrue("A dot has visible pixels", dot.width > 0 && dot.height > 0);
                dots.add(dot);
            }
            for (int first = 0; first < dots.size(); first++)
            {
                for (int second = first + 1; second < dots.size(); second++)
                {
                    assertFalse("Tier dots cannot overlap", dots.get(first).intersects(dots.get(second)));
                }
            }
        }
    }

    @Test
    public void itemWithoutDetailAndTinyBoundsNeverPaintTierDots()
    {
        StashLedgerConfig config = tierConfig();
        StashNeededItemOverlay overlay = new StashNeededItemOverlay(itemId -> itemId == 42,
            mock(Client.class), mock(ItemManager.class), mock(TooltipManager.class), config);
        NeededItemDetail multiTier = new NeededItemDetail(42, List.of(
            entry("Easy", StashTier.EASY, BuildStatus.BUILT, ContentsStatus.EMPTY),
            entry("Hard", StashTier.HARD, BuildStatus.BUILT, ContentsStatus.EMPTY)), false);

        BufferedImage unrelated = renderImage(overlay, 99, new Rectangle(4, 4, 24, 24));
        BufferedImage tiny = renderImage(overlay, 42, new Rectangle(4, 4, 2, 2));
        BufferedImage tinyTiered = renderImage(overlay(multiTier, config), 42, new Rectangle(4, 4, 2, 2));

        assertEquals(0, paintedPixels(unrelated));
        assertEquals(0, paintedPixels(tiny));
        assertEquals(0, paintedPixels(tinyTiered));
    }

    @Test
    public void tooltipListsAllDotTiersEvenWhenLocationsAreCapped()
    {
        NeededItemDetail detail = new NeededItemDetail(42, List.of(
            entry("One", StashTier.EASY, BuildStatus.BUILT, ContentsStatus.EMPTY),
            entry("Two", StashTier.EASY, BuildStatus.BUILT, ContentsStatus.EMPTY),
            entry("Three", StashTier.EASY, BuildStatus.UNKNOWN, ContentsStatus.UNKNOWN),
            entry("Four", StashTier.EASY, BuildStatus.NOT_BUILT, ContentsStatus.UNKNOWN),
            entry("Five", StashTier.EASY, BuildStatus.BUILT, ContentsStatus.EMPTY),
            entry("Six", StashTier.EASY, BuildStatus.BUILT, ContentsStatus.EMPTY),
            entry("Seventh master", StashTier.MASTER, BuildStatus.BUILT, ContentsStatus.EMPTY)), false);

        String text = StashNeededItemOverlay.tooltipText(detail);

        assertTrue(text.contains("Tiers: Easy, Master"));
        assertTrue(text.contains("+1 more locations"));
        assertFalse(text.contains("Seventh master"));
    }

    private static void render(StashNeededItemOverlay overlay, int itemId, WidgetItem item)
    {
        Graphics2D graphics = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try { overlay.renderItemOverlay(graphics, itemId, item); }
        finally { graphics.dispose(); }
    }

    private static StashNeededItemOverlay overlay(NeededItemDetail detail, StashLedgerConfig config)
    {
        NeededItemLookup lookup = new NeededItemLookup()
        {
            @Override public boolean isNeeded(int itemId) { return itemId == 42; }
            @Override public java.util.Optional<NeededItemDetail> detailFor(int itemId)
            {
                return itemId == 42 ? java.util.Optional.of(detail) : java.util.Optional.empty();
            }
        };
        return new StashNeededItemOverlay(lookup, mock(Client.class), mock(ItemManager.class), mock(TooltipManager.class), config);
    }

    private static BufferedImage renderImage(StashNeededItemOverlay overlay, int itemId, Rectangle bounds)
    {
        BufferedImage image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try { overlay.renderItemOverlay(graphics, itemId, new WidgetItem(itemId, 1, bounds, null, null)); }
        finally { graphics.dispose(); }
        return image;
    }

    private static StashLedgerConfig tierConfig()
    {
        StashLedgerConfig config = config(ItemHighlightStyle.DOT, false);
        when(config.dotColorMode()).thenReturn(DotColorMode.CLUE_TIER);
        return config;
    }

    private static List<Color> dotColors(BufferedImage image, Rectangle bounds)
    {
        java.util.LinkedHashSet<Integer> values = new java.util.LinkedHashSet<>();
        for (int y = bounds.y; y < bounds.y + bounds.height; y++)
        {
            for (int x = bounds.x; x < bounds.x + bounds.width; x++)
            {
                int value = image.getRGB(x, y);
                if ((value >>> 24) != 0) { values.add(value); }
            }
        }
        return values.stream().map(Color::new).collect(java.util.stream.Collectors.toList());
    }

    private static void assertNoColorOutside(BufferedImage image, Rectangle bounds, List<Color> colors)
    {
        java.util.Set<Integer> values = colors.stream().map(Color::getRGB).collect(java.util.stream.Collectors.toSet());
        for (int y = 0; y < image.getHeight(); y++)
        {
            for (int x = 0; x < image.getWidth(); x++)
            {
                if (!bounds.contains(x, y)) { assertFalse(values.contains(image.getRGB(x, y))); }
            }
        }
    }

    private static List<Rectangle> componentBounds(BufferedImage image, Rectangle bounds, Color color)
    {
        boolean[][] visited = new boolean[bounds.height][bounds.width];
        List<Rectangle> result = new java.util.ArrayList<>();
        for (int startY = 0; startY < bounds.height; startY++)
        {
            for (int startX = 0; startX < bounds.width; startX++)
            {
                if (visited[startY][startX] || image.getRGB(bounds.x + startX, bounds.y + startY) != color.getRGB())
                {
                    continue;
                }
                java.util.ArrayDeque<Integer> pending = new java.util.ArrayDeque<>();
                pending.add(startY * bounds.width + startX);
                visited[startY][startX] = true;
                int minX = startX, maxX = startX, minY = startY, maxY = startY;
                while (!pending.isEmpty())
                {
                    int point = pending.remove();
                    int x = point % bounds.width;
                    int y = point / bounds.width;
                    minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                    for (int[] delta : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}})
                    {
                        int nextX = x + delta[0];
                        int nextY = y + delta[1];
                        if (nextX >= 0 && nextX < bounds.width && nextY >= 0 && nextY < bounds.height
                            && !visited[nextY][nextX]
                            && image.getRGB(bounds.x + nextX, bounds.y + nextY) == color.getRGB())
                        {
                            visited[nextY][nextX] = true;
                            pending.add(nextY * bounds.width + nextX);
                        }
                    }
                }
                result.add(new Rectangle(bounds.x + minX, bounds.y + minY, maxX - minX + 1, maxY - minY + 1));
            }
        }
        return result;
    }

    private static int paintedPixels(BufferedImage image)
    {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++)
        {
            for (int x = 0; x < image.getWidth(); x++)
            {
                if ((image.getRGB(x, y) >>> 24) != 0) { count++; }
            }
        }
        return count;
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
