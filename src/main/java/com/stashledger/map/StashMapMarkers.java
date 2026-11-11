package com.stashledger.map;

import com.stashledger.model.BuildStatus;
import com.stashledger.model.ContentsStatus;
import com.stashledger.model.StashDefinition;
import com.stashledger.model.StashEntry;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;

/** Owns only the ledger's overview markers; call from the client thread. */
public final class StashMapMarkers
{
    private final WorldMapPointManager manager;
    private final List<WorldMapPoint> points = new ArrayList<>();
    private Map<String, String> visibleStates = Map.of();

    public StashMapMarkers(WorldMapPointManager manager)
    {
        this.manager = manager;
    }

    public void refresh(List<StashEntry> entries, boolean enabled, boolean hideFilled)
    {
        Map<String, String> next = new LinkedHashMap<>();
        if (enabled)
        {
            for (StashEntry entry : entries)
            {
                if (hideFilled && entry.getContentsStatus() == ContentsStatus.FILLED) { continue; }
                next.put(entry.getDefinition().getId(), status(entry));
            }
        }
        if (next.equals(visibleStates)) { return; }
        clear();
        for (StashEntry entry : entries)
        {
            StashDefinition definition = entry.getDefinition();
            if (!next.containsKey(definition.getId())) { continue; }
            for (WorldPoint location : definition.getWorldPoints())
            {
                WorldMapPoint point = new WorldMapPoint(location, icon(entry));
                point.setName("STASH: " + definition.getName());
                point.setTooltip(definition.getTier().getLabel() + " STASH: " + definition.getName()
                    + " | " + next.get(definition.getId()));
                point.setJumpOnClick(true);
                points.add(point);
                manager.add(point);
            }
        }
        visibleStates = next;
    }

    public void clear()
    {
        for (WorldMapPoint point : points) { manager.remove(point); }
        points.clear();
        visibleStates = Map.of();
    }

    private static String status(StashEntry entry)
    {
        if (entry.getBuildStatus() == BuildStatus.UNKNOWN) { return "Construction unknown"; }
        if (entry.getBuildStatus() == BuildStatus.NOT_BUILT) { return "Not built"; }
        switch (entry.getContentsStatus())
        {
            case FILLED: return "Built, filled";
            case EMPTY: return "Built, empty";
            default: return "Built, contents unknown";
        }
    }

    private static BufferedImage icon(StashEntry entry)
    {
        Color color;
        if (entry.getBuildStatus() == BuildStatus.UNKNOWN) { color = new Color(159, 164, 173); }
        else if (entry.getBuildStatus() == BuildStatus.NOT_BUILT) { color = new Color(220, 116, 105); }
        else if (entry.getContentsStatus() == ContentsStatus.FILLED) { color = new Color(133, 187, 150); }
        else if (entry.getContentsStatus() == ContentsStatus.EMPTY) { color = new Color(230, 189, 121); }
        else { color = new Color(130, 167, 207); }
        BufferedImage image = new BufferedImage(12, 12, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(25, 28, 33)); graphics.fillOval(0, 0, 12, 12);
        graphics.setColor(color); graphics.fillOval(2, 2, 8, 8);
        graphics.dispose();
        return image;
    }
}
