package com.stashledger;

import com.stashledger.data.StashCatalog;
import com.stashledger.model.BuildStatus;
import com.stashledger.model.ContentsStatus;
import com.stashledger.model.StashDefinition;
import com.stashledger.model.StashTier;
import com.stashledger.ui.StashLedgerPanel;
import com.stashledger.state.LedgerState;
import com.stashledger.map.StashMapMarkers;
import com.stashledger.tracking.StashInteractionTracker;
import com.stashledger.items.NeededItemProvider;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class StashLedgerPluginTest
{
    private StashLedgerPlugin plugin;
    private Client client;
    private ConfigManager config;
    private List<Runnable> queued;

    @Before public void setUp() throws Exception
    {
        plugin = new StashLedgerPlugin();
        client = mock(Client.class);
        config = mock(ConfigManager.class);
        ClientThread thread = mock(ClientThread.class);
        queued = Collections.synchronizedList(new ArrayList<>());
        doAnswer(invocation -> { queued.add(invocation.getArgument(0)); return null; }).when(thread).invokeLater(any(Runnable.class));
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(config.getRSProfileKey()).thenReturn("account-a");
        StashCatalog catalog = new StashCatalog();
        set("client", client); set("clientThread", thread); set("configManager", config);
        set("config", new StashLedgerConfig() {});
        set("tooltipManager", mock(TooltipManager.class));
        set("mapPoints", mock(WorldMapPointManager.class));
        set("catalog", catalog); set("state", new LedgerState(catalog.getDefinitions()));
        set("profile", "account-a"); set("running", true); set("lifecycle", 1L);
        set("accountEpoch", 3L); set("displayedEpoch", 3L);
    }

    @Test public void callbackCapturedForOldProfileCannotWriteNewAccount() throws Exception
    {
        AtomicBoolean changed = new AtomicBoolean();
        action(() -> changed.set(true));
        when(config.getRSProfileKey()).thenReturn("account-b");
        queued.remove(0).run();
        assertFalse(changed.get());
        verify(config, never()).setConfiguration(anyString(), anyString(), anyString(), anyString());
    }

    @Test public void oldRenderedSidebarCannotMutateANewerSession() throws Exception
    {
        AtomicBoolean changed = new AtomicBoolean();
        action(() -> changed.set(true));
        set("accountEpoch", 4L);
        queued.remove(0).run();
        assertFalse(changed.get());
        verifyNoInteractionsExceptProfileReads();
    }

    @Test public void shutdownInvalidatesPendingCallbacks() throws Exception
    {
        AtomicBoolean changed = new AtomicBoolean();
        action(() -> changed.set(true));
        plugin.shutDown();
        queued.remove(0).run();
        assertFalse(changed.get());
    }

    @Test public void validActionWritesOnlyTheExplicitActiveProfile() throws Exception
    {
        String id = new StashCatalog().getDefinitions().get(0).getId();
        action(() -> ((LedgerState) get("state")).togglePinned(id));
        queued.remove(0).run();
        verify(config).setConfiguration(eq("stashledger"), eq("account-a"), eq("accountState"), contains("\"pinned\":true"));
    }

    @Test public void logoutClearsVisibleStateAndDoesNotSaveToAnotherProfile() throws Exception
    {
        LedgerState old = (LedgerState) get("state");
        old.togglePinned(old.snapshot().get(0).getDefinition().getId());
        GameStateChanged event = new GameStateChanged();
        event.setGameState(GameState.LOGIN_SCREEN);
        plugin.onGameStateChanged(event);
        assertNull(get("profile"));
        LedgerState cleared = (LedgerState) get("state");
        assertTrue(cleared.snapshot().stream().noneMatch(e -> e.isPinned() || e.getBuildStatus() != BuildStatus.UNKNOWN));
        verify(config, never()).setConfiguration(anyString(), anyString(), anyString(), anyString());
    }

    @Test public void invalidSavedStateIsPreservedAndFurtherWritesAreBlocked() throws Exception
    {
        set("profile", null);
        when(config.getConfiguration("stashledger", "account-a", "accountState")).thenReturn("invalid json");
        call("synchronizeProfile");
        assertEquals(false, get("saveHealthy"));
        set("displayedEpoch", get("accountEpoch"));
        AtomicBoolean changed = new AtomicBoolean();
        action(() -> changed.set(true));
        queued.remove(0).run();
        assertFalse(changed.get());
        verify(config, never()).setConfiguration(anyString(), anyString(), anyString(), anyString());
    }

    @Test public void incompleteConstructionCannotReportContentsSyncedOrReviveStaleContents() throws Exception
    {
        StashCatalog catalog = (StashCatalog) get("catalog");
        LedgerState state = (LedgerState) get("state");
        Map<String, BuildStatus> builds = new HashMap<>();
        catalog.getDefinitions().forEach(d -> builds.put(d.getId(), BuildStatus.BUILT));
        state.applyBuildScan(builds);
        String id = catalog.getDefinitions().get(0).getId();
        state.markContents(id, ContentsStatus.FILLED);
        builds.put(id, BuildStatus.UNKNOWN);
        state.applyBuildScan(builds);
        installEmptyBoard(catalog);
        set("constructionReady", false);
        assertEquals(false, call("syncWatsonBoard"));
        verify(config, never()).setConfiguration(anyString(), anyString(), anyString(), anyString());
        assertFalse(String.valueOf(get("message")).startsWith("Contents synced"));
        builds.put(id, BuildStatus.BUILT);
        state.applyBuildScan(builds);
        set("constructionReady", true);
        assertEquals(true, call("syncWatsonBoard"));
        assertEquals(ContentsStatus.EMPTY, state.snapshot().get(0).getContentsStatus());
    }

    @Test public void unmatchedNoticeboardRowCannotPartiallyReplaceSavedContents() throws Exception
    {
        StashCatalog catalog = (StashCatalog) get("catalog");
        LedgerState state = (LedgerState) get("state");
        Map<String, BuildStatus> builds = new HashMap<>();
        catalog.getDefinitions().forEach(definition -> builds.put(definition.getId(), BuildStatus.BUILT));
        state.applyBuildScan(builds);
        String savedId = catalog.getDefinitions().get(0).getId();
        state.markContents(savedId, ContentsStatus.FILLED);
        String saved = state.serialize();
        installEmptyBoard(catalog);
        installUnknownBoard(StashTier.MASTER);
        set("constructionReady", true);

        assertEquals(false, call("syncWatsonBoard"));

        assertEquals("A failed board must preserve every prior contents observation", saved, state.serialize());
        verify(config, never()).setConfiguration(anyString(), anyString(), anyString(), anyString());
    }

    @Test public void finalNoticeboardRetryExplainsTheFailureWithoutChangingSavedContents() throws Exception
    {
        StashCatalog catalog = (StashCatalog) get("catalog");
        LedgerState state = (LedgerState) get("state");
        Map<String, BuildStatus> builds = new HashMap<>();
        catalog.getDefinitions().forEach(definition -> builds.put(definition.getId(), BuildStatus.BUILT));
        state.applyBuildScan(builds);
        String savedId = catalog.getDefinitions().get(0).getId();
        state.markContents(savedId, ContentsStatus.FILLED);
        String saved = state.serialize();
        installEmptyBoard(catalog);
        installUnknownBoard(StashTier.MASTER);
        set("constructionReady", true);
        set("boardOpen", true);
        set("boardPending", true);
        set("boardAttempts", 4);

        plugin.onGameTick(new GameTick());

        assertEquals(false, get("boardPending"));
        String status = String.valueOf(get("message")).toLowerCase(java.util.Locale.ROOT);
        assertTrue(status.contains("master"));
        assertTrue(status.contains("not a stash label"));
        assertTrue(status.contains("unchanged"));
        assertEquals(saved, state.serialize());
        verify(config, never()).setConfiguration(anyString(), anyString(), anyString(), anyString());
    }

    @Test public void noticeboardDiagnosticsClearForANewBoardAndAccount() throws Exception
    {
        set("latestBoardDiagnostic", "Unknown location in beginner: stale value");
        WidgetLoaded loaded = new WidgetLoaded();
        loaded.setGroupId(493);
        plugin.onWidgetLoaded(loaded);
        assertNull(get("latestBoardDiagnostic"));

        set("latestBoardDiagnostic", "Unknown location in beginner: stale value");
        call("clearAccount");
        assertNull(get("latestBoardDiagnostic"));
    }

    @Test public void finalNoticeboardRetryReportsUnreadableSaveInsteadOfAStaleBoardFailure() throws Exception
    {
        set("latestBoardDiagnostic", "Master tier has an unknown location: stale.");
        set("saveHealthy", false);
        set("constructionReady", true);
        set("scanPending", false);
        set("boardOpen", true);
        set("boardPending", true);
        set("boardAttempts", 4);

        plugin.onGameTick(new GameTick());

        String status = String.valueOf(get("message")).toLowerCase(java.util.Locale.ROOT);
        assertTrue(status.contains("saved data"));
        assertTrue(status.contains("disabled"));
        assertFalse(status.contains("master"));
        assertEquals(false, get("boardPending"));
        verify(config, never()).setConfiguration(anyString(), anyString(), anyString(), anyString());
        verify(client, never()).getWidget(anyInt(), anyInt());
    }

    @Test public void finalNoticeboardRetryReportsConstructionScanInsteadOfAStaleBoardFailure() throws Exception
    {
        set("latestBoardDiagnostic", "Master tier has an unknown location: stale.");
        set("saveHealthy", true);
        set("constructionReady", false);
        set("scanPending", false);
        set("boardOpen", true);
        set("boardPending", true);
        set("boardAttempts", 4);

        plugin.onGameTick(new GameTick());

        String status = String.valueOf(get("message")).toLowerCase(java.util.Locale.ROOT);
        assertTrue(status.contains("construction"));
        assertTrue(status.contains("refresh"));
        assertFalse(status.contains("master"));
        assertEquals(false, get("boardPending"));
        verify(config, never()).setConfiguration(anyString(), anyString(), anyString(), anyString());
        verify(client, never()).getWidget(anyInt(), anyInt());
    }

    @Test public void staleMapCallbackCannotCrossAccountEpoch() throws Exception
    {
        Class<?> type = Class.forName("com.stashledger.StashLedgerPlugin$PanelActions");
        Constructor<?> constructor = type.getDeclaredConstructor(StashLedgerPlugin.class);
        constructor.setAccessible(true);
        StashLedgerPanel.Actions actions = (StashLedgerPanel.Actions) constructor.newInstance(plugin);
        actions.showOnMap(new StashCatalog().getDefinitions().get(0));
        set("accountEpoch", 4L);
        queued.remove(0).run();
        verifyNoInteractions((WorldMapPointManager) get("mapPoints"));
        assertTrue(((List<?>) get("selectedMarkers")).isEmpty());
    }

    @Test public void confirmedSearchUpdatesOnlyItsStashAndActiveProfile() throws Exception
    {
        StashDefinition definition = installInteractionScene();
        plugin.onMenuOptionClicked(search(definition, 50, MenuAction.GAME_OBJECT_FIRST_OPTION));
        plugin.onChatMessage(chat(StashInteractionTracker.DEPOSIT_SUCCESS_MESSAGE));
        LedgerState state = (LedgerState) get("state");
        assertEquals(ContentsStatus.FILLED, state.snapshot().get(0).getContentsStatus());
        assertTrue(state.snapshot().subList(1, state.snapshot().size()).stream()
            .allMatch(entry -> entry.getContentsStatus() == ContentsStatus.UNKNOWN));
        verify(config).setConfiguration(eq("stashledger"), eq("account-a"), eq("accountState"), anyString());
    }

    @Test public void exactChatWithoutMatchingObjectAndLocationCannotChangeContents() throws Exception
    {
        StashDefinition definition = installInteractionScene();
        plugin.onChatMessage(chat(StashInteractionTracker.DEPOSIT_SUCCESS_MESSAGE));
        plugin.onMenuOptionClicked(search(definition, 51, MenuAction.GAME_OBJECT_FIRST_OPTION));
        plugin.onChatMessage(chat(StashInteractionTracker.DEPOSIT_SUCCESS_MESSAGE));
        plugin.onMenuOptionClicked(search(definition, 50, MenuAction.NPC_FIRST_OPTION));
        plugin.onChatMessage(chat(StashInteractionTracker.DEPOSIT_SUCCESS_MESSAGE));
        assertEquals(ContentsStatus.UNKNOWN, ((LedgerState) get("state")).snapshot().get(0).getContentsStatus());
        verify(config, never()).setConfiguration(anyString(), anyString(), anyString(), anyString());
    }

    @Test public void nextMenuActionCancelsPendingContentsConfirmation() throws Exception
    {
        StashDefinition definition = installInteractionScene();
        plugin.onMenuOptionClicked(search(definition, 50, MenuAction.GAME_OBJECT_FIRST_OPTION));
        plugin.onMenuOptionClicked(search(definition, 50, MenuAction.WALK));
        plugin.onChatMessage(chat(StashInteractionTracker.DEPOSIT_SUCCESS_MESSAGE));
        assertEquals(ContentsStatus.UNKNOWN, ((LedgerState) get("state")).snapshot().get(0).getContentsStatus());
    }

    @Test public void contentsConfirmationCannotCrossAccountOrProximityBoundary() throws Exception
    {
        StashDefinition definition = installInteractionScene();
        plugin.onMenuOptionClicked(search(definition, 50, MenuAction.GAME_OBJECT_FIRST_OPTION));
        when(config.getRSProfileKey()).thenReturn("account-b");
        plugin.onChatMessage(chat(StashInteractionTracker.DEPOSIT_SUCCESS_MESSAGE));
        when(config.getRSProfileKey()).thenReturn("account-a");
        plugin.onChatMessage(chat(StashInteractionTracker.DEPOSIT_SUCCESS_MESSAGE));
        assertEquals(ContentsStatus.UNKNOWN, ((LedgerState) get("state")).snapshot().get(0).getContentsStatus());

        plugin.onMenuOptionClicked(search(definition, 50, MenuAction.GAME_OBJECT_FIRST_OPTION));
        LocalPoint farAway = LocalPoint.fromScene(60, 50, client.getWorldView(-1));
        when(client.getLocalPlayer().getLocalLocation()).thenReturn(farAway);
        plugin.onChatMessage(chat(StashInteractionTracker.DEPOSIT_SUCCESS_MESSAGE));
        assertEquals(ContentsStatus.UNKNOWN, ((LedgerState) get("state")).snapshot().get(0).getContentsStatus());
        verify(config, never()).setConfiguration(anyString(), anyString(), anyString(), anyString());
    }

    @Test public void logoutClearsOverviewMarkersAndPendingConfirmation() throws Exception
    {
        StashDefinition definition = installInteractionScene();
        StashMapMarkers markers = new StashMapMarkers((WorldMapPointManager) get("mapPoints"));
        set("overviewMarkers", markers);
        call("publish");
        verify((WorldMapPointManager) get("mapPoints"), times(121)).add(any());
        plugin.onMenuOptionClicked(search(definition, 50, MenuAction.GAME_OBJECT_FIRST_OPTION));
        GameStateChanged logout = new GameStateChanged(); logout.setGameState(GameState.LOGIN_SCREEN);
        plugin.onGameStateChanged(logout);
        verify((WorldMapPointManager) get("mapPoints"), times(121)).remove(any());
        assertFalse(((StashInteractionTracker) get("interactions"))
            .acceptSuccessMessage(StashInteractionTracker.DEPOSIT_SUCCESS_MESSAGE, 100).isPresent());
    }

    @Test public void overviewCannotExposeStateToAnotherActiveProfile() throws Exception
    {
        installInteractionScene();
        WorldMapPointManager manager = (WorldMapPointManager) get("mapPoints");
        set("overviewMarkers", new StashMapMarkers(manager));
        when(config.getRSProfileKey()).thenReturn("account-b");
        call("publish");
        verifyNoInteractions(manager);
    }

    @Test public void itemHighlightsClearAcrossProfilesAndWhenDisabled() throws Exception
    {
        StashDefinition definition = installInteractionScene();
        ItemManager itemManager = mock(ItemManager.class);
        when(itemManager.canonicalize(anyInt())).thenAnswer(invocation -> invocation.getArgument(0));
        NeededItemProvider items = new NeededItemProvider(itemManager, Map.of(definition.getId(), Set.of(20)));
        set("neededItems", items);
        call("publish");
        assertTrue(items.isNeeded(20));

        when(config.getRSProfileKey()).thenReturn("account-b");
        call("publish");
        assertFalse(items.isNeeded(20));
        when(config.getRSProfileKey()).thenReturn("account-a");
        call("publish");
        assertTrue(items.isNeeded(20));

        set("config", new StashLedgerConfig() { @Override public boolean highlightItems() { return false; } });
        call("publish");
        assertFalse(items.isNeeded(20));
    }

    @Test public void alternateLocationsConfirmTheSameLogicalStash() throws Exception
    {
        StashCatalog catalog = (StashCatalog) get("catalog");
        Map<String, WorldPoint> alternatives = Map.of(
            "LUMBRIDGE_SWAMP_CAVES", new WorldPoint(3167, 9570, 0),
            "ENTRANCE_OF_THE_CAVERN_UNDER_THE_WHIRLPOOL", new WorldPoint(1636, 5367, 1));
        for (Map.Entry<String, WorldPoint> alternative : alternatives.entrySet())
        {
            StashDefinition definition = catalog.getById(alternative.getKey());
            installInteractionScene(definition, alternative.getValue());
            plugin.onMenuOptionClicked(search(definition, 50, MenuAction.GAME_OBJECT_FIRST_OPTION));
            plugin.onChatMessage(chat(StashInteractionTracker.DEPOSIT_SUCCESS_MESSAGE));
            LedgerState state = (LedgerState) get("state");
            assertEquals(ContentsStatus.FILLED, state.snapshot().stream()
                .filter(entry -> entry.getDefinition().getId().equals(definition.getId()))
                .findFirst().get().getContentsStatus());
            assertEquals(119, state.snapshot().size());
            assertEquals(1, state.snapshot().stream().filter(entry -> entry.getContentsStatus() == ContentsStatus.FILLED).count());
            verify(config).setConfiguration(eq("stashledger"), eq("account-a"), eq("accountState"), contains(definition.getId()));
            clearInvocations(config);
        }
    }

    @Test public void selectedMapActionOwnsAndClearsEveryLocation() throws Exception
    {
        Class<?> type = Class.forName("com.stashledger.StashLedgerPlugin$PanelActions");
        Constructor<?> constructor = type.getDeclaredConstructor(StashLedgerPlugin.class);
        constructor.setAccessible(true);
        StashLedgerPanel.Actions actions = (StashLedgerPanel.Actions) constructor.newInstance(plugin);
        StashDefinition definition = ((StashCatalog) get("catalog")).getById("LUMBRIDGE_SWAMP_CAVES");
        actions.showOnMap(definition);
        queued.remove(0).run();
        WorldMapPointManager manager = (WorldMapPointManager) get("mapPoints");
        ArgumentCaptor<WorldMapPoint> points = ArgumentCaptor.forClass(WorldMapPoint.class);
        verify(manager, times(2)).add(points.capture());
        assertEquals(new WorldPoint(3222, 9584, 0), points.getAllValues().get(0).getWorldPoint());
        assertEquals(new WorldPoint(3167, 9570, 0), points.getAllValues().get(1).getWorldPoint());
        plugin.shutDown();
        queued.remove(0).run();
        for (WorldMapPoint point : points.getAllValues()) { verify(manager).remove(same(point)); }
        assertTrue(((List<?>) get("selectedMarkers")).isEmpty());
    }

    @Test public void shutdownDuringRefreshCannotLeaveOverviewMarkersBehind() throws Exception
    {
        WorldMapPointManager manager = (WorldMapPointManager) get("mapPoints");
        Set<WorldMapPoint> visible = new HashSet<>();
        AtomicBoolean stopped = new AtomicBoolean();
        doAnswer(invocation ->
        {
            visible.add(invocation.getArgument(0));
            if (stopped.compareAndSet(false, true))
            {
                SwingUtilities.invokeAndWait(plugin::shutDown);
            }
            return null;
        }).when(manager).add(any(WorldMapPoint.class));
        doAnswer(invocation -> { visible.remove(invocation.getArgument(0)); return null; })
            .when(manager).remove(any(WorldMapPoint.class));
        set("overviewMarkers", new StashMapMarkers(manager));
        call("publish");
        drainClientCallbacks();
        assertTrue(stopped.get());
        assertTrue("all markers from the in-flight refresh must be retired", visible.isEmpty());
        assertEquals(false, get("running"));
    }

    @Test public void rapidRestartDoesNotClearTheNewSessionsOverlay() throws Exception
    {
        Set<Overlay> visible = installLifecycleDependencies();
        SwingUtilities.invokeAndWait(plugin::startUp);
        drainClientCallbacks();
        SwingUtilities.invokeAndWait(() -> { });
        drainClientCallbacks();
        assertEquals(1, visible.size());
        Overlay oldOverlay = visible.iterator().next();

        SwingUtilities.invokeAndWait(() -> { plugin.shutDown(); plugin.startUp(); });
        drainClientCallbacks();
        SwingUtilities.invokeAndWait(() -> { });
        drainClientCallbacks();
        assertEquals(true, get("running"));
        assertEquals(1, visible.size());
        assertFalse(visible.contains(oldOverlay));
        assertTrue(visible.contains(get("itemOverlay")));
        SwingUtilities.invokeAndWait(plugin::shutDown);
        drainClientCallbacks();
        assertTrue(visible.isEmpty());
    }

    @Test public void stopDuringInitializationCannotReactivateThePlugin() throws Exception
    {
        Set<Overlay> visible = installLifecycleDependencies();
        OverlayManager manager = (OverlayManager) get("overlayManager");
        doAnswer(invocation ->
        {
            visible.add(invocation.getArgument(0));
            SwingUtilities.invokeAndWait(plugin::shutDown);
            return true;
        }).when(manager).add(any(Overlay.class));
        SwingUtilities.invokeAndWait(plugin::startUp);
        queued.remove(0).run();
        assertEquals(false, get("running"));
        drainClientCallbacks();
        SwingUtilities.invokeAndWait(() -> { });
        assertTrue(visible.isEmpty());
        verifyNoInteractions((ClientToolbar) get("clientToolbar"));
    }

    @Test public void fillModeRestrictsHighlightsWithoutChangingContentsAndCanBeStopped() throws Exception
    {
        StashCatalog catalog = (StashCatalog) get("catalog");
        String first = catalog.getDefinitions().get(0).getId();
        String second = catalog.getDefinitions().get(1).getId();
        LedgerState state = (LedgerState) get("state");
        state.applyBuildScan(Map.of(first, BuildStatus.BUILT, second, BuildStatus.BUILT));
        state.markContents(first, ContentsStatus.EMPTY);
        state.markContents(second, ContentsStatus.EMPTY);
        ItemManager manager = mock(ItemManager.class);
        when(manager.canonicalize(anyInt())).thenAnswer(invocation -> invocation.getArgument(0));
        NeededItemProvider items = new NeededItemProvider(manager, Map.of(first, Set.of(10), second, Set.of(20)));
        set("neededItems", items);
        String saved = state.serialize();

        panelActions().fillStash(first);
        drainClientCallbacks();

        assertEquals(first, get("fillStashId"));
        assertTrue(items.isNeeded(10));
        assertFalse(items.isNeeded(20));
        assertEquals("Selecting a target never changes saved contents", saved, state.serialize());
        assertTrue(items.detailFor(10).orElseThrow().isFocused());

        panelActions().clearFillStash();
        drainClientCallbacks();
        assertNull(get("fillStashId"));
        assertTrue(items.isNeeded(10));
        assertTrue(items.isNeeded(20));
        assertEquals(saved, state.serialize());
    }

    @Test public void fillModeRejectsUnknownUnbuiltFilledAndStaleEligibility() throws Exception
    {
        String id = ((StashCatalog) get("catalog")).getDefinitions().get(0).getId();
        LedgerState state = (LedgerState) get("state");
        for (BuildStatus build : List.of(BuildStatus.UNKNOWN, BuildStatus.NOT_BUILT, BuildStatus.BUILT))
        {
            state.applyBuildScan(Map.of(id, build));
            set("message", "Original status");
            panelActions().fillStash(id);
            drainClientCallbacks();
            assertNull("Unknown contents are never assumed empty", get("fillStashId"));
            assertEquals("A rejected selection must not announce fill mode", "Original status", get("message"));
        }
        state.markContents(id, ContentsStatus.FILLED);
        set("message", "Original status");
        panelActions().fillStash(id);
        drainClientCallbacks();
        assertNull(get("fillStashId"));
        assertEquals("Original status", get("message"));

        state.markContents(id, ContentsStatus.EMPTY);
        panelActions().fillStash(id);
        state.markContents(id, ContentsStatus.FILLED);
        drainClientCallbacks();
        assertNull("Eligibility is rechecked after the queued click", get("fillStashId"));
        assertEquals("A stale click must not announce fill mode", "Original status", get("message"));
    }

    @Test public void fillModeEndsWhenSelectedStashIsFilledOrNoLongerKnownBuilt() throws Exception
    {
        String id = ((StashCatalog) get("catalog")).getDefinitions().get(0).getId();
        LedgerState state = (LedgerState) get("state");
        state.applyBuildScan(Map.of(id, BuildStatus.BUILT));
        state.markContents(id, ContentsStatus.EMPTY);
        panelActions().fillStash(id);
        drainClientCallbacks();
        assertEquals(id, get("fillStashId"));

        state.markContents(id, ContentsStatus.FILLED);
        call("publish");
        assertNull("Completion must leave selected mode", get("fillStashId"));

        state.markContents(id, ContentsStatus.EMPTY);
        panelActions().fillStash(id);
        drainClientCallbacks();
        state.applyBuildScan(Map.of(id, BuildStatus.UNKNOWN));
        call("publish");
        assertNull("A stale build result must leave selected mode", get("fillStashId"));
    }

    @Test public void fillModeCannotCrossAccountBoundaryAndClearsOnLogout() throws Exception
    {
        String id = ((StashCatalog) get("catalog")).getDefinitions().get(0).getId();
        LedgerState state = (LedgerState) get("state");
        state.applyBuildScan(Map.of(id, BuildStatus.BUILT));
        state.markContents(id, ContentsStatus.EMPTY);
        panelActions().fillStash(id);
        set("accountEpoch", 4L);
        drainClientCallbacks();
        assertNull("Old sidebar callback cannot select for a new session", get("fillStashId"));

        set("displayedEpoch", 4L);
        panelActions().fillStash(id);
        when(config.getRSProfileKey()).thenReturn("account-b");
        drainClientCallbacks();
        assertNull("Profile identity is checked as well as epoch", get("fillStashId"));

        when(config.getRSProfileKey()).thenReturn("account-a");
        panelActions().fillStash(id);
        drainClientCallbacks();
        assertEquals(id, get("fillStashId"));
        GameStateChanged event = new GameStateChanged();
        event.setGameState(GameState.LOGIN_SCREEN);
        plugin.onGameStateChanged(event);
        assertNull(get("fillStashId"));
    }

    @Test public void shutdownClearsFillSelectionOnClientQueue() throws Exception
    {
        set("fillStashId", "BOB_AXES_ENTRANCE");
        plugin.shutDown();
        drainClientCallbacks();
        assertNull(get("fillStashId"));
    }

    private StashLedgerPanel.Actions panelActions() throws Exception
    {
        Class<?> type = Class.forName("com.stashledger.StashLedgerPlugin$PanelActions");
        Constructor<?> constructor = type.getDeclaredConstructor(StashLedgerPlugin.class);
        constructor.setAccessible(true);
        return (StashLedgerPanel.Actions) constructor.newInstance(plugin);
    }

    private Set<Overlay> installLifecycleDependencies() throws Exception
    {
        Set<Overlay> visible = new HashSet<>();
        OverlayManager manager = mock(OverlayManager.class);
        doAnswer(invocation -> { visible.add(invocation.getArgument(0)); return true; })
            .when(manager).add(any(Overlay.class));
        doAnswer(invocation -> { visible.remove(invocation.getArgument(0)); return true; })
            .when(manager).remove(any(Overlay.class));
        ItemManager items = mock(ItemManager.class);
        when(items.canonicalize(anyInt())).thenAnswer(invocation -> invocation.getArgument(0));
        set("itemManager", items);
        set("overlayManager", manager);
        set("clientToolbar", mock(ClientToolbar.class));
        return visible;
    }

    private void drainClientCallbacks()
    {
        while (!queued.isEmpty()) { queued.remove(0).run(); }
    }

    private StashDefinition installInteractionScene() throws Exception
    {
        StashDefinition definition = ((StashCatalog) get("catalog")).getDefinitions().get(0);
        return installInteractionScene(definition, new WorldPoint(definition.getX(), definition.getY(), definition.getPlane()));
    }

    private StashDefinition installInteractionScene(StashDefinition definition, WorldPoint location) throws Exception
    {
        Scene scene = mock(Scene.class);
        when(scene.getBaseX()).thenReturn(location.getX() - 50);
        when(scene.getBaseY()).thenReturn(location.getY() - 50);
        WorldView view = mock(WorldView.class);
        when(view.getId()).thenReturn(-1);
        when(view.getPlane()).thenReturn(location.getPlane());
        when(view.getScene()).thenReturn(scene);
        when(client.getWorldView(-1)).thenReturn(view);
        when(client.getTickCount()).thenReturn(100);
        Player player = mock(Player.class);
        LocalPoint playerLocation = LocalPoint.fromScene(50, 50, view);
        when(player.getLocalLocation()).thenReturn(playerLocation);
        when(client.getLocalPlayer()).thenReturn(player);
        ((LedgerState) get("state")).applyBuildScan(Map.of(definition.getId(), BuildStatus.BUILT));
        return definition;
    }

    private MenuOptionClicked search(StashDefinition definition, int sceneX, MenuAction action)
    {
        MenuEntry entry = mock(MenuEntry.class);
        when(entry.getOption()).thenReturn("Search"); when(entry.getType()).thenReturn(action);
        when(entry.getIdentifier()).thenReturn(definition.getObjectId());
        when(entry.getWorldViewId()).thenReturn(-1);
        when(entry.getParam0()).thenReturn(sceneX); when(entry.getParam1()).thenReturn(50);
        return new MenuOptionClicked(entry);
    }

    private ChatMessage chat(String message)
    {
        ChatMessage event = mock(ChatMessage.class);
        when(event.getType()).thenReturn(ChatMessageType.GAMEMESSAGE);
        when(event.getMessage()).thenReturn(message);
        return event;
    }

    private void installEmptyBoard(StashCatalog catalog)
    {
        for (StashTier tier : StashTier.values())
        {
            List<Widget> children = new ArrayList<>();
            Widget decorative = mock(Widget.class);
            when(decorative.getType()).thenReturn(3);
            children.add(decorative);
            for (String name : catalog.getWatsonAliases(tier).keySet())
            {
                Widget label = mock(Widget.class);
                when(label.getType()).thenReturn(4);
                when(label.getText()).thenReturn(name);
                children.add(label);
                Widget builtTick = mock(Widget.class);
                when(builtTick.getType()).thenReturn(5);
                children.add(builtTick);
            }
            Widget root = mock(Widget.class);
            when(root.getChildren()).thenReturn(children.toArray(new Widget[0]));
            when(client.getWidget(493, 4 + tier.ordinal() * 2)).thenReturn(root);
        }
    }

    private void installUnknownBoard(StashTier tier)
    {
        Widget label = mock(Widget.class);
        when(label.getType()).thenReturn(4);
        when(label.getText()).thenReturn("<col=ff00ff>not a STASH\nlabel</col>");
        Widget root = mock(Widget.class);
        when(root.getChildren()).thenReturn(new Widget[] { label });
        when(client.getWidget(493, 4 + tier.ordinal() * 2)).thenReturn(root);
    }

    private void verifyNoInteractionsExceptProfileReads()
    {
        verify(config, never()).setConfiguration(anyString(), anyString(), anyString(), anyString());
    }
    private void action(Runnable value) throws Exception
    {
        Method method = StashLedgerPlugin.class.getDeclaredMethod("accountAction", Runnable.class);
        method.setAccessible(true); method.invoke(plugin, value);
    }
    private Object call(String name) throws Exception
    {
        Method method = StashLedgerPlugin.class.getDeclaredMethod(name);
        method.setAccessible(true); return method.invoke(plugin);
    }
    private void set(String name, Object value) throws Exception
    {
        Field field = StashLedgerPlugin.class.getDeclaredField(name);
        field.setAccessible(true); field.set(plugin, value);
    }
    private Object get(String name)
    {
        try
        {
            Field field = StashLedgerPlugin.class.getDeclaredField(name);
            field.setAccessible(true); return field.get(plugin);
        }
        catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }
}
