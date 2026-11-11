package com.stashledger.items;

import static org.junit.Assert.assertEquals;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import net.runelite.api.widgets.WidgetItem;
import org.junit.Test;

public class StashNeededItemOverlayTest
{
    @Test
    public void rendersOnePlannerDotWithoutQuantityOrDeficitText()
    {
        StashNeededItemOverlay overlay = new StashNeededItemOverlay(itemId -> itemId == 42);
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
}
