package com.stashledger.map;

import com.stashledger.data.StashCatalog;
import com.stashledger.model.BuildStatus;
import com.stashledger.model.ContentsStatus;
import com.stashledger.model.StashDefinition;
import com.stashledger.model.StashEntry;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class StashMapMarkersTest
{
    private final List<StashDefinition> definitions = new StashCatalog().getDefinitions();

    @Test public void hidesOnlyConfirmedFilledAndKeepsUnknown()
    {
        WorldMapPointManager manager = mock(WorldMapPointManager.class);
        StashMapMarkers markers = new StashMapMarkers(manager);
        List<StashEntry> entries = entries();
        markers.refresh(entries, true, true);
        ArgumentCaptor<WorldMapPoint> points = ArgumentCaptor.forClass(WorldMapPoint.class);
        verify(manager, times(3)).add(points.capture());
        List<WorldMapPoint> visible = points.getAllValues();
        assertFalse(visible.stream().anyMatch(point -> point.getWorldPoint().equals(location(0))));
        assertTrue(visible.stream().anyMatch(point -> point.getTooltip().endsWith("Built, contents unknown")));
        assertTrue(visible.stream().anyMatch(point -> point.getTooltip().endsWith("Not built")));
        assertTrue(visible.stream().anyMatch(point -> point.getTooltip().endsWith("Construction unknown")));
    }

    @Test public void disablingRemovesOnlyOwnedMarkers()
    {
        WorldMapPointManager manager = mock(WorldMapPointManager.class);
        WorldMapPoint otherPluginMarker = new WorldMapPoint(new WorldPoint(1, 1, 0), null);
        manager.add(otherPluginMarker);
        StashMapMarkers markers = new StashMapMarkers(manager);
        markers.refresh(entries(), true, false);
        clearInvocations(manager);
        markers.refresh(entries(), false, false);
        verify(manager, times(4)).remove(any(WorldMapPoint.class));
        verify(manager, never()).remove(same(otherPluginMarker));
        verifyNoMoreInteractions(manager);
    }

    @Test public void clearingAccountAllowsNewAccountToPopulateIdenticalState()
    {
        WorldMapPointManager manager = mock(WorldMapPointManager.class);
        StashMapMarkers markers = new StashMapMarkers(manager);
        markers.refresh(entries(), true, false);
        clearInvocations(manager);
        markers.clear();
        verify(manager, times(4)).remove(any(WorldMapPoint.class));
        markers.refresh(entries(), true, false);
        verify(manager, times(4)).add(any(WorldMapPoint.class));
    }

    @Test public void unchangedStateDoesNotRecreateOverviewMarkers()
    {
        WorldMapPointManager manager = mock(WorldMapPointManager.class);
        StashMapMarkers markers = new StashMapMarkers(manager);
        markers.refresh(entries(), true, false);
        clearInvocations(manager);
        markers.refresh(entries(), true, false);
        verifyNoInteractions(manager);
    }

    @Test public void everyPhysicalLocationSharesOneUnitsStateAndLifecycle()
    {
        StashDefinition definition = new StashCatalog().getById("LUMBRIDGE_SWAMP_CAVES");
        WorldMapPointManager manager = mock(WorldMapPointManager.class);
        StashMapMarkers markers = new StashMapMarkers(manager);
        markers.refresh(List.of(new StashEntry(definition, BuildStatus.BUILT, ContentsStatus.EMPTY, false)), true, true);
        ArgumentCaptor<WorldMapPoint> points = ArgumentCaptor.forClass(WorldMapPoint.class);
        verify(manager, times(2)).add(points.capture());
        assertEquals(new WorldPoint(3222, 9584, 0), points.getAllValues().get(0).getWorldPoint());
        assertEquals(new WorldPoint(3167, 9570, 0), points.getAllValues().get(1).getWorldPoint());
        assertTrue(points.getAllValues().stream().allMatch(point -> point.getTooltip().endsWith("Built, empty")));
        clearInvocations(manager);
        markers.refresh(List.of(new StashEntry(definition, BuildStatus.BUILT, ContentsStatus.FILLED, false)), true, true);
        for (WorldMapPoint point : points.getAllValues()) { verify(manager).remove(same(point)); }
        verify(manager, never()).add(any());
        verifyNoMoreInteractions(manager);
    }

    private List<StashEntry> entries()
    {
        return List.of(
            new StashEntry(definitions.get(0), BuildStatus.BUILT, ContentsStatus.FILLED, false),
            new StashEntry(definitions.get(1), BuildStatus.BUILT, ContentsStatus.UNKNOWN, false),
            new StashEntry(definitions.get(2), BuildStatus.NOT_BUILT, ContentsStatus.UNKNOWN, false),
            new StashEntry(definitions.get(3), BuildStatus.UNKNOWN, ContentsStatus.UNKNOWN, false));
    }

    private WorldPoint location(int index)
    {
        StashDefinition definition = definitions.get(index);
        return new WorldPoint(definition.getX(), definition.getY(), definition.getPlane());
    }
}
