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
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
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
            actions = new RecordingActions();
            panel = new StashLedgerPanel(actions);
            entries = Arrays.asList(
                entry("BARROWS", "Barrows chest", StashTier.MASTER, BuildStatus.BUILT, ContentsStatus.FILLED, true, "Any full Barrows set"),
                entry("CANIFIS", "Canifis", StashTier.MEDIUM, BuildStatus.BUILT, ContentsStatus.EMPTY, false),
                entry("FISHING", "Fishing Guild bank", StashTier.HARD, BuildStatus.BUILT, ContentsStatus.UNKNOWN, false),
                entry("DRAYNOR", "Draynor market", StashTier.EASY, BuildStatus.NOT_BUILT, ContentsStatus.UNKNOWN, false),
                entry("DUNGEON", "Dungeon entrance", StashTier.ELITE, BuildStatus.UNKNOWN, ContentsStatus.UNKNOWN, false, "Steel platebody"));
            panel.update(entries, "Bonnie", true);
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
    public void searchIncludesKnownItemAndStateIsPreservedAcrossUpdate() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            JTextField search = find(panel, JTextField.class);
            search.setText("steel");
        });
        assertEquals(1, panel.visibleEntries().size());
        assertEquals("DUNGEON", panel.visibleEntries().get(0).getDefinition().getId());

        SwingUtilities.invokeAndWait(() -> panel.update(entries, "Bonnie", true));
        assertEquals(1, panel.visibleEntries().size());
    }

    @Test
    public void mapPinAndManualContentsCallbacksAreWired() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            clickAccessible(panel, "Expand Barrows chest");
            click(panel, "Map");
            click(panel, "☆ My list");
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
            panel.setSize(new Dimension(225, 680));
            panel.doLayout();
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
        if (type.isInstance(component)) return type.cast(component);
        if (component instanceof java.awt.Container)
        {
            for (Component child : ((java.awt.Container) component).getComponents())
            {
                T found = find(child, type);
                if (found != null) return found;
            }
        }
        throw new AssertionError("Missing component " + type.getSimpleName());
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
