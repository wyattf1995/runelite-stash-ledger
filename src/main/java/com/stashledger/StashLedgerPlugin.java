package com.stashledger;

import com.google.inject.Provides;
import com.stashledger.data.StashCatalog;
import com.stashledger.map.StashMapMarkers;
import com.stashledger.model.BuildStatus;
import com.stashledger.model.ContentsStatus;
import com.stashledger.model.StashDefinition;
import com.stashledger.model.StashEntry;
import com.stashledger.model.StashTier;
import com.stashledger.state.LedgerState;
import com.stashledger.tracking.BuildScanner;
import com.stashledger.tracking.StashInteractionTracker;
import com.stashledger.tracking.WatsonBoardParser;
import com.stashledger.ui.StashLedgerPanel;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
    name = "STASH Ledger",
    description = "Browse every STASH, track construction and contents, and plan your next build",
    tags = {"clue", "stash", "construction", "collection", "treasure"}
)
public class StashLedgerPlugin extends Plugin
{
    static final String CONFIG_GROUP = "stashledger";
    static final String SAVE_KEY = "accountState";
    // HIDEY_HOLES noticeboard, verified against the current game interface and Plugin Hub source.
    static final int WATSON_GROUP = 493;
    private static final Logger LOG = LoggerFactory.getLogger(StashLedgerPlugin.class);

    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private ConfigManager configManager;
    @Inject private ClientToolbar clientToolbar;
    @Inject private WorldMapPointManager mapPoints;
    @Inject private StashLedgerConfig config;

    private StashCatalog catalog;
    private LedgerState state;
    private StashLedgerPanel panel;
    private NavigationButton navigation;
    private WorldMapPoint marker;
    private StashMapMarkers overviewMarkers;
    private final StashInteractionTracker interactions = new StashInteractionTracker(30);
    private int interactionWorldView = -1;
    private String profile;
    private boolean saveHealthy = true;
    private boolean scanPending;
    private boolean constructionReady;
    private int scanAttempts;
    private int boardAttempts;
    private boolean boardPending;
    private boolean boardOpen;
    private long accountEpoch;
    private volatile long displayedEpoch = -1;
    private volatile long lifecycle;
    private volatile boolean running;
    private String message = "Log in to scan construction. Open Watson's noticeboard to sync contents.";

    @Provides
    StashLedgerConfig provideConfig(ConfigManager manager)
    {
        return manager.getConfig(StashLedgerConfig.class);
    }

    @Override
    protected void startUp()
    {
        catalog = new StashCatalog();
        state = new LedgerState(catalog.getDefinitions());
        overviewMarkers = new StashMapMarkers(mapPoints);
        running = true;
        long currentLifecycle = ++lifecycle;
        SwingUtilities.invokeLater(() ->
        {
            if (!running || currentLifecycle != lifecycle) { return; }
            panel = new StashLedgerPanel(new PanelActions());
            navigation = NavigationButton.builder().tooltip("STASH Ledger").icon(icon())
                .priority(6).panel(panel).build();
            clientToolbar.addNavigation(navigation);
            clientThread.invokeLater(() ->
            {
                if (!running || currentLifecycle != lifecycle) { return; }
                synchronizeProfile();
                requestScan();
                publish();
            });
        });
    }

    @Override
    protected void shutDown()
    {
        running = false;
        ++lifecycle;
        displayedEpoch = -1;
        NavigationButton oldNavigation = navigation;
        navigation = null;
        SwingUtilities.invokeLater(() ->
        {
            if (oldNavigation != null) { clientToolbar.removeNavigation(oldNavigation); }
        });
        WorldMapPoint oldMarker = marker;
        marker = null;
        if (oldMarker != null) { mapPoints.remove(oldMarker); }
        if (overviewMarkers != null) { overviewMarkers.clear(); }
        interactions.clear();
        profile = null;
        boardPending = false;
        boardOpen = false;
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!CONFIG_GROUP.equals(event.getGroup()) || !running) { return; }
        long token = lifecycle;
        clientThread.invokeLater(() ->
        {
            if (!running || token != lifecycle) { return; }
            if (!config.autoTrackContents()) { interactions.clear(); }
            publish();
        });
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (!running) { return; }
        if (event.getGameState() == GameState.LOGGED_IN)
        {
            requestScan();
        }
        else if (event.getGameState() == GameState.LOGIN_SCREEN
            || event.getGameState() == GameState.LOGIN_SCREEN_AUTHENTICATOR
            || event.getGameState() == GameState.HOPPING
            || event.getGameState() == GameState.CONNECTION_LOST)
        {
            clearAccount();
        }
    }

    @Subscribe
    public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
    {
        if (running)
        {
            // ConfigManager publishes this from more than one context. Confine all state to the client thread.
            long token = lifecycle;
            clientThread.invokeLater(() ->
            {
                if (!running || token != lifecycle) { return; }
                synchronizeProfile();
                requestScan();
            });
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (!running || client.getGameState() != GameState.LOGGED_IN) { return; }
        synchronizeProfile();
        if (profile == null) { return; }
        if (scanPending)
        {
            scanPending = false;
            try
            {
                Map<String, BuildStatus> scan = BuildScanner.scan(client, catalog.getDefinitions());
                state.applyBuildScan(scan);
                constructionReady = scan.values().stream().noneMatch(value -> value == BuildStatus.UNKNOWN);
                if (!constructionReady) { scanPending = ++scanAttempts < 3; }
                save();
                message = saveHealthy
                    ? (constructionReady ? "Construction synced. Open Watson's noticeboard to sync contents."
                        : "Some construction status is unavailable. Use Refresh to try again.")
                    : "Saved data could not be read. It has been preserved; account edits are disabled.";
            }
            catch (RuntimeException ex)
            {
                LOG.debug("STASH construction scan unavailable", ex);
                scanPending = ++scanAttempts < 3;
                message = "Construction scan unavailable. Use Refresh to try again.";
            }
            publish();
        }
        if (boardPending && boardOpen)
        {
            if (syncWatsonBoard()) { boardPending = false; }
            else if (++boardAttempts >= 5)
            {
                boardPending = false;
                message = "Noticeboard could not be matched completely. Saved contents are unchanged.";
                publish();
            }
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (!running || (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)) { return; }
        String text = Text.removeTags(event.getMessage());
        if ("You build a STASH unit.".equals(text)) { requestScan(); }
        if (!config.autoTrackContents() || !activeAccount()) { interactions.clear(); return; }
        // Exact core success messages confirm only the explicitly matched Search target.
        interactions.acceptSuccessMessage(text, client.getTickCount()).ifPresent(update ->
        {
            StashDefinition definition = catalog.getById(update.getStashId());
            if (definition == null || !nearInteractedStash(definition)) { return; }
            if (state.markContents(update.getStashId(), update.getContentsStatus()))
            {
                save();
                message = "Contents updated from your STASH interaction.";
                publish();
            }
        });
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        interactions.clear();
        if (!running || !config.autoTrackContents() || !activeAccount() || event.isConsumed()
            || !"Search".equals(event.getMenuOption()) || !isObjectOption(event.getMenuAction())) { return; }
        WorldView view = client.getWorldView(event.getMenuEntry().getWorldViewId());
        if (view == null || view.getScene() == null || event.getParam0() < 0 || event.getParam1() < 0
            || event.getParam0() >= 104 || event.getParam1() >= 104) { return; }
        LocalPoint local = LocalPoint.fromScene(event.getParam0(), event.getParam1(), view);
        WorldPoint clicked = WorldPoint.fromLocalInstance(view.getScene(), local, view.getPlane());
        for (StashDefinition definition : catalog.getDefinitions())
        {
            if (definition.getObjectId() != event.getId()) { continue; }
            interactions.recordInteraction(definition, event.getId(), clicked.getX(), clicked.getY(),
                clicked.getPlane(), StashInteractionTracker.Action.SEARCH, client.getTickCount());
            interactionWorldView = view.getId();
            return;
        }
    }

    private boolean activeAccount()
    {
        return running && saveHealthy && profile != null && client.getGameState() == GameState.LOGGED_IN
            && Objects.equals(profile, configManager.getRSProfileKey());
    }

    private boolean nearInteractedStash(StashDefinition definition)
    {
        if (client.getLocalPlayer() == null || client.getLocalPlayer().getLocalLocation() == null) { return false; }
        LocalPoint local = client.getLocalPlayer().getLocalLocation();
        if (local.getWorldView() != interactionWorldView) { return false; }
        WorldView view = client.getWorldView(interactionWorldView);
        if (view == null || view.getScene() == null) { return false; }
        WorldPoint player = WorldPoint.fromLocalInstance(view.getScene(), local, view.getPlane());
        WorldPoint stash = new WorldPoint(definition.getX(), definition.getY(), definition.getPlane());
        return player.distanceTo(stash) <= 2;
    }

    private static boolean isObjectOption(MenuAction action)
    {
        return action == MenuAction.GAME_OBJECT_FIRST_OPTION || action == MenuAction.GAME_OBJECT_SECOND_OPTION
            || action == MenuAction.GAME_OBJECT_THIRD_OPTION || action == MenuAction.GAME_OBJECT_FOURTH_OPTION
            || action == MenuAction.GAME_OBJECT_FIFTH_OPTION;
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event)
    {
        if (running && event.getGroupId() == WATSON_GROUP)
        {
            boardOpen = true;
            boardPending = true;
            boardAttempts = 0;
            requestScan();
        }
    }

    @Subscribe
    public void onWidgetClosed(WidgetClosed event)
    {
        if (event.getGroupId() == WATSON_GROUP)
        {
            boardOpen = false;
            boardPending = false;
        }
    }

    private void synchronizeProfile()
    {
        String current = client.getGameState() == GameState.LOGGED_IN ? configManager.getRSProfileKey() : null;
        if (Objects.equals(current, profile)) { return; }
        clearAccount();
        if (current == null) { return; }
        profile = current;
        try
        {
            state.load(configManager.getConfiguration(CONFIG_GROUP, current, SAVE_KEY));
        }
        catch (RuntimeException ex)
        {
            saveHealthy = false;
            message = "Saved data could not be read. It has been preserved; account edits are disabled.";
            LOG.warn("STASH Ledger save could not be read; retaining the original configuration", ex);
        }
        requestScan();
        publish();
    }

    private void clearAccount()
    {
        interactions.clear();
        ++accountEpoch;
        profile = null;
        state = new LedgerState(catalog.getDefinitions());
        saveHealthy = true;
        scanPending = false;
        constructionReady = false;
        boardOpen = false;
        boardPending = false;
        message = "Log in to scan construction. Open Watson's noticeboard to sync contents.";
        if (marker != null) { mapPoints.remove(marker); marker = null; }
        if (overviewMarkers != null) { overviewMarkers.clear(); }
        publish();
    }

    private void requestScan()
    {
        scanPending = true;
        constructionReady = false;
        scanAttempts = 0;
    }

    private void save()
    {
        if (profile == null || !saveHealthy || !Objects.equals(profile, configManager.getRSProfileKey())
            || client.getGameState() != GameState.LOGGED_IN) { return; }
        String json = state.serialize();
        if (!json.equals(configManager.getConfiguration(CONFIG_GROUP, profile, SAVE_KEY)))
        {
            // Explicit profile identity prevents a delayed callback from writing another account's key.
            configManager.setConfiguration(CONFIG_GROUP, profile, SAVE_KEY, json);
        }
    }

    private boolean syncWatsonBoard()
    {
        if (!saveHealthy || !constructionReady) { return false; }
        Map<StashTier, List<WatsonBoardParser.Cell>> cells = new EnumMap<>(StashTier.class);
        Map<StashTier, Map<String, String>> aliases = new EnumMap<>(StashTier.class);
        for (StashTier tier : StashTier.values())
        {
            Widget root = client.getWidget(WATSON_GROUP, 4 + tier.ordinal() * 2);
            if (root == null || root.getChildren() == null) { return false; }
            List<WatsonBoardParser.Cell> tierCells = new ArrayList<>();
            for (Widget child : root.getChildren())
            {
                // The noticeboard reader uses text (4) and check-mark sprites (5);
                // layout/decorative children are not data rows.
                if (child != null && (child.getType() == 4 || child.getType() == 5))
                {
                    tierCells.add(new WatsonBoardParser.Cell(child.getType(), child.getText()));
                }
            }
            cells.put(tier, tierCells);
            aliases.put(tier, catalog.getWatsonAliases(tier));
        }
        Optional<Map<String, Boolean>> parsed = WatsonBoardParser.parse(cells, aliases, catalog.getDefinitions());
        if (!parsed.isPresent()) { return false; }
        Map<String, BuildStatus> builds = state.buildSnapshot();
        for (Map.Entry<String, Boolean> entry : parsed.get().entrySet())
        {
            if (builds.get(entry.getKey()) == BuildStatus.BUILT)
            {
                state.markContents(entry.getKey(), entry.getValue() ? ContentsStatus.FILLED : ContentsStatus.EMPTY);
            }
        }
        save();
        message = "Contents synced from Watson's noticeboard.";
        publish();
        return true;
    }

    private void publish()
    {
        if (!running || state == null) { return; }
        long currentLifecycle = lifecycle;
        long epoch = accountEpoch;
        List<StashEntry> snapshot = state.snapshot();
        boolean loggedIn = profile != null && client.getGameState() == GameState.LOGGED_IN
            && Objects.equals(profile, configManager.getRSProfileKey());
        if (overviewMarkers != null)
        {
            overviewMarkers.refresh(snapshot, loggedIn && config.showAllMapMarkers(), config.hideFilledMapMarkers());
        }
        String name = loggedIn && client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : "";
        String status = message;
        SwingUtilities.invokeLater(() ->
        {
            if (!running || currentLifecycle != lifecycle || panel == null) { return; }
            displayedEpoch = epoch;
            panel.update(snapshot, name, loggedIn);
            panel.setStatusMessage(status);
        });
    }

    private void accountAction(Runnable action)
    {
        long expectedEpoch = displayedEpoch;
        long expectedLifecycle = lifecycle;
        clientThread.invokeLater(() ->
        {
            if (!running || expectedLifecycle != lifecycle || expectedEpoch != accountEpoch || profile == null
                || !saveHealthy || client.getGameState() != GameState.LOGGED_IN
                || !Objects.equals(profile, configManager.getRSProfileKey())) { return; }
            action.run();
            save();
            publish();
        });
    }

    private final class PanelActions implements StashLedgerPanel.Actions
    {
        @Override public void togglePinned(String id) { accountAction(() -> state.togglePinned(id)); }
        @Override public void markContents(String id, ContentsStatus status)
        {
            accountAction(() ->
            {
                if (state.markContents(id, status)) { message = "Contents updated from your confirmation."; }
            });
        }
        @Override public void refresh()
        {
            accountAction(() ->
            {
                requestScan();
                boardPending = boardOpen;
                boardAttempts = 0;
                message = "Scanning construction…";
            });
        }
        @Override public void showOnMap(StashDefinition definition)
        {
            long token = lifecycle;
            long mapEpoch = displayedEpoch;
            clientThread.invokeLater(() ->
            {
                if (!running || token != lifecycle || mapEpoch != accountEpoch || profile == null
                    || !Objects.equals(profile, configManager.getRSProfileKey())
                    || client.getGameState() != GameState.LOGGED_IN) { return; }
                if (marker != null) { mapPoints.remove(marker); }
                WorldPoint point = new WorldPoint(definition.getX(), definition.getY(), definition.getPlane());
                marker = new WorldMapPoint(point, icon());
                marker.setName("STASH: " + definition.getName());
                marker.setJumpOnClick(true);
                mapPoints.add(marker);
                if (client.getWorldMap() != null) { client.getWorldMap().setWorldMapPositionTarget(point); }
                message = "Marked " + definition.getName() + ". Open the world map to view it.";
                publish();
            });
        }
        @Override public void openWiki(StashDefinition definition)
        {
            LinkBrowser.browse("https://oldschool.runescape.wiki/w/STASH");
        }
    }

    private static BufferedImage icon()
    {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(63, 49, 32)); g.fillRoundRect(1, 3, 14, 12, 2, 2);
        g.setColor(new Color(230, 189, 121)); g.drawRect(1, 3, 13, 11);
        g.drawLine(1, 7, 14, 7); g.drawLine(5, 3, 5, 14); g.drawLine(10, 3, 10, 14);
        g.setColor(new Color(245, 216, 163)); g.fillRect(6, 6, 4, 3);
        g.dispose();
        return image;
    }
}
