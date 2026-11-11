package com.stashledger.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.stashledger.model.BuildStatus;
import com.stashledger.model.ContentsStatus;
import com.stashledger.model.StashDefinition;
import com.stashledger.model.StashEntry;
import com.stashledger.model.StashTier;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.laf.RuneLiteLAF;
import org.junit.Before;
import org.junit.Test;

public class StashLedgerPanelTest
{
    private StashLedgerPanel panel;
    private RecordingActions actions;
    private List<StashEntry> entries;

    @Before
    public void setUp() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            RuneLiteLAF.setup();
            actions = new RecordingActions();
            panel = new StashLedgerPanel(actions);
            entries = Arrays.asList(
                entry("BARROWS", "Barrows chest", StashTier.MASTER, BuildStatus.BUILT, ContentsStatus.FILLED, true, "Any full Barrows set"),
                entry("CANIFIS", "Canifis", StashTier.MEDIUM, BuildStatus.BUILT, ContentsStatus.EMPTY, false),
                entry("FISHING", "Fishing Guild bank", StashTier.HARD, BuildStatus.BUILT, ContentsStatus.UNKNOWN, false),
                entry("DRAYNOR", "Draynor market", StashTier.EASY, BuildStatus.NOT_BUILT, ContentsStatus.UNKNOWN, false),
                entry("DUNGEON", "Dungeon entrance", StashTier.ELITE, BuildStatus.UNKNOWN, ContentsStatus.UNKNOWN, false, "Steel platebody"));
            panel.update(entries, "Demo account", true);
        });
    }

    @Test
    public void builtFilterExcludesUnbuiltAndUnknownConstruction() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> click(panel, "Built"));
        assertEquals(3, panel.visibleEntries().size());
        assertTrue(panel.visibleEntries().stream().allMatch(entry -> entry.getBuildStatus() == BuildStatus.BUILT));
    }

    @Test
    public void unknownFilterIncludesUnknownContentsAndConstruction() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> click(panel, "Unknown"));
        List<StashEntry> visible = panel.visibleEntries();
        assertEquals(2, visible.size());
        assertTrue(visible.stream().anyMatch(entry -> entry.getDefinition().getId().equals("FISHING")));
        assertTrue(visible.stream().anyMatch(entry -> entry.getDefinition().getId().equals("DUNGEON")));
        assertFalse(visible.stream().anyMatch(entry -> entry.getDefinition().getId().equals("CANIFIS")));
    }

    @Test
    public void filledAndEmptyFiltersOnlyMatchBuiltUnitsWithThatContentsState() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> click(panel, "Filled"));
        assertEquals(1, panel.visibleEntries().size());
        assertEquals("BARROWS", panel.visibleEntries().get(0).getDefinition().getId());

        SwingUtilities.invokeAndWait(() -> click(panel, "Empty"));
        assertEquals(1, panel.visibleEntries().size());
        assertEquals("CANIFIS", panel.visibleEntries().get(0).getDefinition().getId());
        assertTrue(panel.visibleEntries().stream().allMatch(entry -> entry.getBuildStatus() == BuildStatus.BUILT));
    }

    @Test
    public void hideFilledComposesWithShortlistBuildTierAndSearchFilters() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            click(panel, "Hide filled");
            click(panel, "My list");
        });
        assertTrue("The sole shortlisted entry is filled and should be hidden", panel.visibleEntries().isEmpty());

        SwingUtilities.invokeAndWait(() -> {
            click(panel, "My list");
            click(panel, "Built");
            find(panel, JComboBox.class).setSelectedIndex(4); // Hard
            find(panel, JTextField.class).setText("Fishing");
        });
        assertEquals(1, panel.visibleEntries().size());
        assertEquals("FISHING", panel.visibleEntries().get(0).getDefinition().getId());
        assertEquals(ContentsStatus.UNKNOWN, panel.visibleEntries().get(0).getContentsStatus());
    }

    @Test
    public void searchIncludesKnownItemAndStateIsPreservedAcrossUpdate() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            JTextField search = find(panel, JTextField.class);
            search.setText("steel");
        });
        assertEquals(1, panel.visibleEntries().size());
        assertEquals("DUNGEON", panel.visibleEntries().get(0).getDefinition().getId());

        SwingUtilities.invokeAndWait(() -> panel.update(entries, "Demo account", true));
        assertEquals(1, panel.visibleEntries().size());
    }

    @Test
    public void mapPinAndManualContentsCallbacksAreWired() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            clickAccessible(panel, "Expand Barrows chest");
            click(panel, "Map");
            click(panel, "★ Listed");
            List<JComboBox> boxes = findAll(panel, JComboBox.class);
            boxes.get(boxes.size() - 1).setSelectedIndex(1);
        });
        assertEquals("BARROWS", actions.mapId);
        assertEquals("BARROWS", actions.pinId);
        assertEquals("BARROWS", actions.contentsId);
        assertEquals(ContentsStatus.EMPTY, actions.contentsStatus);
    }

    @Test
    public void rendersNarrowSidebarEvidence() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            clickAccessible(panel, "Expand Barrows chest");
            panel.setSize(new Dimension(225, 680));
            layoutTree(panel);
            JScrollPane scrollPane = find(panel, JScrollPane.class);
            scrollPane.getViewport().setViewPosition(new java.awt.Point(0, 0));
            layoutTree(panel);
            JLabel barrows = findLabel(panel, "Barrows chest");
            assertTrue("Expanded card title should have positive rendered width", barrows.getWidth() > 0);
            assertTrue("Expanded card title should be inside the narrow sidebar", barrows.getY() >= 0);
            assertEquals("Sidebar content must track the viewport width", scrollPane.getViewport().getWidth(),
                scrollPane.getViewport().getView().getWidth());
            assertInsideViewport(panel, scrollPane, find(panel, JTextField.class));
            assertInsideViewport(panel, scrollPane, findAll(panel, JComboBox.class).get(0));
            for (String filter : Arrays.asList("All", "Not built", "Built", "Unknown", "Filled", "Empty"))
            {
                assertInsideViewport(panel, scrollPane, findButton(panel, filter));
            }
            assertInsideViewport(panel, scrollPane, findButton(panel, "Hide filled"));
            assertInsideViewport(panel, scrollPane, barrows);
            assertInsideViewport(panel, scrollPane, findButton(panel, "★ Listed"));
            BufferedImage image = new BufferedImage(225, 680, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            panel.paint(graphics);
            graphics.dispose();
            try
            {
                File target = new File("build/ui/stash-ledger.png");
                target.getParentFile().mkdirs();
                ImageIO.write(image, "png", target);
            }
            catch (Exception exception)
            {
                throw new AssertionError("Could not write sidebar evidence", exception);
            }
        });
        assertTrue(new File("build/ui/stash-ledger.png").isFile());
    }

    @Test
    public void longStatusMessageUsesAVisibleWrappedLabel() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            panel.setStatusMessage("Watson noticeboard sync found 6 built units whose contents have not been checked yet.");
            panel.setSize(new Dimension(225, 680));
            layoutTree(panel);
            JLabel status = findLabelContaining(panel, "Watson noticeboard sync");
            assertDetailTextFits(status);
            assertTrue(status.getToolTipText().startsWith("Watson noticeboard sync"));
        });
    }

    @Test
    public void contentsSummaryFitsAtTheRuneLiteSidebarWidth() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            panel.setSize(new Dimension(225, 680));
            layoutTree(panel);
            JLabel contents = findLabelContaining(panel, "contents unknown");
            assertTrue("Wrapped contents summary must fit its allocated sidebar width",
                contents.getPreferredSize().width <= contents.getWidth());
            assertTrue("Contents summary should use the deliberate compact text width",
                contents.getPreferredSize().width <= 168);
        });
    }

    @Test
    public void longDetailMaterialsAndClueRequirementsFitAtSidebarWidth() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            StashEntry longDetail = entry("LONG", "Long clue location", StashTier.MASTER,
                BuildStatus.BUILT, ContentsStatus.FILLED, false,
                "Any combination of a completed god book, a ring of wealth, a heraldic rune shield, and climbing boots");
            panel.update(Arrays.asList(longDetail), "Demo account", true);
            clickAccessible(panel, "Expand Long clue location");
            panel.setSize(new Dimension(225, 680));
            layoutTree(panel);
            assertDetailTextFits(findLabelContaining(panel, "2 mahogany planks"));
            assertDetailTextFits(findLabelContaining(panel, "Any combination of a completed god book"));
        });
    }

    private static StashEntry entry(String id, String name, StashTier tier, BuildStatus build,
                                    ContentsStatus contents, boolean pinned, String... items)
    {
        return new StashEntry(new StashDefinition(id, 1, name, tier, 3200, 3200, 0,
            Arrays.asList(items)), build, contents, pinned);
    }

    private static void click(JComponent root, String text)
    {
        AbstractButton button = findButton(root, text);
        if (button == null)
        {
            throw new AssertionError("Missing button: " + text);
        }
        button.doClick();
    }

    private static AbstractButton findButton(Component component, String text)
    {
        if (component instanceof AbstractButton && text.equals(((AbstractButton) component).getText()))
        {
            return (AbstractButton) component;
        }
        if (component instanceof java.awt.Container)
        {
            for (Component child : ((java.awt.Container) component).getComponents())
            {
                AbstractButton found = findButton(child, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void clickAccessible(JComponent root, String accessibleName)
    {
        AbstractButton button = findAccessibleButton(root, accessibleName);
        if (button == null)
        {
            throw new AssertionError("Missing accessible button: " + accessibleName);
        }
        button.doClick();
    }

    private static AbstractButton findAccessibleButton(Component component, String accessibleName)
    {
        if (component instanceof AbstractButton
            && accessibleName.equals(((AbstractButton) component).getAccessibleContext().getAccessibleName()))
        {
            return (AbstractButton) component;
        }
        if (component instanceof java.awt.Container)
        {
            for (Component child : ((java.awt.Container) component).getComponents())
            {
                AbstractButton found = findAccessibleButton(child, accessibleName);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static <T extends Component> T find(Component component, Class<T> type)
    {
        T found = findOrNull(component, type);
        if (found != null) return found;
        throw new AssertionError("Missing component " + type.getSimpleName());
    }

    private static <T extends Component> T findOrNull(Component component, Class<T> type)
    {
        if (type.isInstance(component)) return type.cast(component);
        if (component instanceof java.awt.Container)
        {
            for (Component child : ((java.awt.Container) component).getComponents())
            {
                T found = findOrNull(child, type);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static <T extends Component> List<T> findAll(Component component, Class<T> type)
    {
        List<T> found = new ArrayList<>();
        collect(component, type, found);
        return found;
    }

    private static <T extends Component> void collect(Component component, Class<T> type, List<T> found)
    {
        if (type.isInstance(component)) found.add(type.cast(component));
        if (component instanceof java.awt.Container)
        {
            for (Component child : ((java.awt.Container) component).getComponents()) collect(child, type, found);
        }
    }

    private static JLabel findLabel(Component component, String text)
    {
        return findLabelContaining(component, text, true);
    }

    private static JLabel findLabelContaining(Component component, String text)
    {
        return findLabelContaining(component, text, false);
    }

    private static JLabel findLabelContaining(Component component, String text, boolean exact)
    {
        if (component instanceof JLabel)
        {
            String value = ((JLabel) component).getText();
            if (value != null && (exact ? text.equals(value) : value.contains(text))) return (JLabel) component;
        }
        if (component instanceof java.awt.Container)
        {
            for (Component child : ((java.awt.Container) component).getComponents())
            {
                JLabel found = findLabelContainingOrNull(child, text, exact);
                if (found != null) return found;
            }
        }
        throw new AssertionError("Missing label: " + text);
    }

    private static JLabel findLabelContainingOrNull(Component component, String text, boolean exact)
    {
        if (component instanceof JLabel)
        {
            String value = ((JLabel) component).getText();
            if (value != null && (exact ? text.equals(value) : value.contains(text))) return (JLabel) component;
        }
        if (component instanceof java.awt.Container)
        {
            for (Component child : ((java.awt.Container) component).getComponents())
            {
                JLabel found = findLabelContainingOrNull(child, text, exact);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void layoutTree(Component component)
    {
        if (component instanceof java.awt.Container)
        {
            ((java.awt.Container) component).doLayout();
            for (Component child : ((java.awt.Container) component).getComponents()) layoutTree(child);
        }
    }

    private static void assertDetailTextFits(JLabel label)
    {
        assertTrue("Wrapped detail text must fit its allocated sidebar width",
            label.getPreferredSize().width <= label.getWidth());
        assertTrue("Detail text must use the deliberate compact text width",
            label.getPreferredSize().width <= 168);
    }

    private static void assertInsideViewport(JComponent root, JScrollPane scrollPane, Component component)
    {
        assertTrue("Expected component", component != null);
        Rectangle bounds = SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), root);
        int viewportRight = scrollPane.getX() + scrollPane.getViewport().getX() + scrollPane.getViewport().getWidth();
        assertTrue("Component should start inside the sidebar viewport", bounds.x >= scrollPane.getX());
        assertTrue("Component should finish inside the sidebar viewport", bounds.x + bounds.width <= viewportRight);
    }

    private static final class RecordingActions implements StashLedgerPanel.Actions
    {
        private String pinId;
        private String mapId;
        private String contentsId;
        private ContentsStatus contentsStatus;
        @Override public void togglePinned(String id) { pinId = id; }
        @Override public void markContents(String id, ContentsStatus status) { contentsId = id; contentsStatus = status; }
        @Override public void showOnMap(StashDefinition definition) { mapId = definition.getId(); }
        @Override public void openWiki(StashDefinition definition) { }
        @Override public void refresh() { }
    }
}
