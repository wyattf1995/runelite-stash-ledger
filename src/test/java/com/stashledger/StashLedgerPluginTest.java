package com.stashledger;

import com.stashledger.data.StashCatalog;
import com.stashledger.model.BuildStatus;
import com.stashledger.state.LedgerState;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
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

    private void verifyNoInteractionsExceptProfileReads()
    {
        verify(config, never()).setConfiguration(anyString(), anyString(), anyString(), anyString());
    }
    private void action(Runnable value) throws Exception
    {
        Method method = StashLedgerPlugin.class.getDeclaredMethod("accountAction", Runnable.class);
        method.setAccessible(true); method.invoke(plugin, value);
    }
    private void call(String name) throws Exception
    {
        Method method = StashLedgerPlugin.class.getDeclaredMethod(name);
        method.setAccessible(true); method.invoke(plugin);
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
