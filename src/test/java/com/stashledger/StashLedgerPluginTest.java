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
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Client;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import org.junit.Before;
import org.junit.Test;
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
        queued = new ArrayList<>();
        doAnswer(invocation -> { queued.add(invocation.getArgument(0)); return null; }).when(thread).invokeLater(any(Runnable.class));
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(config.getRSProfileKey()).thenReturn("account-a");
        StashCatalog catalog = new StashCatalog();
        set("client", client); set("clientThread", thread); set("configManager", config);
        set("config", new StashLedgerConfig() {});
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
        assertNull(get("marker"));
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
        verify((WorldMapPointManager) get("mapPoints"), times(119)).add(any());
        plugin.onMenuOptionClicked(search(definition, 50, MenuAction.GAME_OBJECT_FIRST_OPTION));
        GameStateChanged logout = new GameStateChanged(); logout.setGameState(GameState.LOGIN_SCREEN);
        plugin.onGameStateChanged(logout);
        verify((WorldMapPointManager) get("mapPoints"), times(119)).remove(any());
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

    private StashDefinition installInteractionScene() throws Exception
    {
        StashDefinition definition = ((StashCatalog) get("catalog")).getDefinitions().get(0);
        Scene scene = mock(Scene.class);
        when(scene.getBaseX()).thenReturn(definition.getX() - 50);
        when(scene.getBaseY()).thenReturn(definition.getY() - 50);
        WorldView view = mock(WorldView.class);
        when(view.getId()).thenReturn(-1);
        when(view.getPlane()).thenReturn(definition.getPlane());
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
