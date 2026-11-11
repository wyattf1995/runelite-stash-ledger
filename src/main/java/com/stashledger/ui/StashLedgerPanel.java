package com.stashledger.ui;

import com.stashledger.model.BuildStatus;
import com.stashledger.model.ContentsStatus;
import com.stashledger.model.StashDefinition;
import com.stashledger.model.StashEntry;
import com.stashledger.model.StashTier;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * Native RuneLite sidebar for one account's STASH construction and contents
 * ledger. This class intentionally receives immutable snapshots and knows
 * nothing about the game client or configuration implementation.
 */
public final class StashLedgerPanel extends PluginPanel
{
    private static final Color GOLD = new Color(230, 189, 121);
    private static final Color GOLD_DIM = new Color(92, 74, 49);
    private static final Color SURFACE = new Color(45, 45, 49);
    private static final Color SURFACE_DARK = new Color(38, 38, 42);
    private static final Color SURFACE_DETAIL = new Color(41, 41, 45);
    private static final Color BORDER = new Color(65, 65, 70);
    private static final Color MUTED = new Color(174, 171, 179);
    private static final Color GREEN = new Color(164, 215, 182);
    private static final Color GREEN_SURFACE = new Color(43, 64, 52);
    private static final Color EMPTY = new Color(233, 199, 136);
    private static final Color EMPTY_SURFACE = new Color(68, 59, 43);
    private static final Border CARD_BORDER = BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(BORDER), BorderFactory.createEmptyBorder(0, 0, 0, 0));

    private final Actions actions;
    private final JPanel cards = new JPanel();
    private final JScrollPane scrollPane;
    private final JLabel builtValue = new JLabel("—");
    private final JLabel builtCaption = new JLabel("STASH units built");
    private final JLabel stockCaption = new JLabel("Contents not scanned");
    private final JLabel resultCount = new JLabel("0 locations");
    private final JLabel statusLabel = new JLabel("Sign in to inspect your STASH units.");
    private final JTextField search = new JTextField();
    private final JComboBox<TierOption> tier = new JComboBox<>(TierOption.values());
    private final Map<Filter, JToggleButton> filters = new HashMap<>();
    private final JToggleButton shortlistButton = toggleButton("My list");
    private final Set<String> expandedIds = new LinkedHashSet<>();
    private final Set<String> pinnedIds = new LinkedHashSet<>();
    private final List<StashEntry> entries = new ArrayList<>();

    private Filter filter = Filter.ALL;
    private boolean shortlistOnly;
    private boolean loggedIn;
    private String accountName = "";
    private String statusMessage = "";
    private int previousScrollValue;
    private boolean rebuilding;

    public StashLedgerPanel(Actions actions)
    {
        super(false);
        this.actions = actions;
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ColorScheme.DARK_GRAY_COLOR);
        content.setBorder(BorderFactory.createEmptyBorder(12, 10, 10, 10));

        content.add(header());
        content.add(Box.createVerticalStrut(10));
        content.add(summary());
        content.add(Box.createVerticalStrut(9));
        content.add(controls());
        content.add(Box.createVerticalStrut(7));
        content.add(statusRow());
        content.add(Box.createVerticalStrut(7));

        cards.setLayout(new BoxLayout(cards, BoxLayout.Y_AXIS));
        cards.setBackground(ColorScheme.DARK_GRAY_COLOR);
        content.add(cards);
        content.add(Box.createVerticalGlue());

        scrollPane = new JScrollPane(content, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);

        installListeners();
        rebuild();
    }

    public void update(List<StashEntry> snapshot, String newAccountName, boolean newLoggedIn)
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(() -> update(snapshot, newAccountName, newLoggedIn));
            return;
        }
        previousScrollValue = scrollPane.getVerticalScrollBar().getValue();
        entries.clear();
        if (snapshot != null)
        {
            entries.addAll(snapshot);
        }
        pinnedIds.clear();
        entries.stream().filter(StashEntry::isPinned).map(entry -> entry.getDefinition().getId()).forEach(pinnedIds::add);
        expandedIds.retainAll(entries.stream().map(entry -> entry.getDefinition().getId()).collect(Collectors.toSet()));
        accountName = newAccountName == null ? "" : newAccountName;
        loggedIn = newLoggedIn;
        rebuild();
    }

    public void setStatusMessage(String text)
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(() -> setStatusMessage(text));
            return;
        }
        statusMessage = text == null ? "" : text;
        updateStatusText();
    }

    // Package-visible so focused UI tests can verify the actual filter path.
    List<StashEntry> visibleEntries()
    {
        String query = search.getText().trim().toLowerCase(Locale.ROOT);
        TierOption selectedTier = (TierOption) tier.getSelectedItem();
        return entries.stream()
            .filter(entry -> filter.matches(entry))
            .filter(entry -> selectedTier == null || selectedTier.matches(entry.getDefinition().getTier()))
            .filter(entry -> !shortlistOnly || entry.isPinned())
            .filter(entry -> query.isEmpty() || matchesQuery(entry, query))
            .sorted(Comparator.comparing((StashEntry entry) -> !entry.isPinned())
                .thenComparing(entry -> entry.getDefinition().getTier().ordinal())
                .thenComparing(entry -> entry.getDefinition().getName()))
            .collect(Collectors.toList());
    }

    private boolean matchesQuery(StashEntry entry, String query)
    {
        StashDefinition definition = entry.getDefinition();
        if (definition.getName().toLowerCase(Locale.ROOT).contains(query)
            || definition.getTier().getLabel().toLowerCase(Locale.ROOT).contains(query)
            || definition.getId().toLowerCase(Locale.ROOT).contains(query))
        {
            return true;
        }
        return definition.getItems().stream().anyMatch(item -> item.toLowerCase(Locale.ROOT).contains(query));
    }

    private Component header()
    {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setOpaque(false);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 39));

        JLabel icon = new JLabel("▣", SwingConstants.CENTER);
        icon.setOpaque(true);
        icon.setForeground(GOLD);
        icon.setBackground(new Color(56, 48, 37));
        icon.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(GOLD_DIM),
            BorderFactory.createEmptyBorder(5, 8, 5, 8)));
        panel.add(icon, BorderLayout.WEST);

        JPanel title = new JPanel();
        title.setOpaque(false);
        title.setLayout(new BoxLayout(title, BoxLayout.Y_AXIS));
        JLabel name = new JLabel("STASH Ledger");
        name.setFont(FontManager.getRunescapeBoldFont());
        name.setForeground(Color.WHITE);
        title.add(name);
        JLabel subtitle = new JLabel("Every hideaway, accounted for.");
        subtitle.setForeground(MUTED);
        subtitle.setFont(subtitle.getFont().deriveFont(11f));
        title.add(subtitle);
        panel.add(title, BorderLayout.CENTER);

        JButton refresh = iconButton("↻", "Refresh construction scan");
        refresh.addActionListener(event -> actions.refresh());
        panel.add(refresh, BorderLayout.EAST);
        return panel;
    }

    private Component summary()
    {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(SURFACE);
        panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER),
            BorderFactory.createEmptyBorder(9, 10, 9, 10)));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 86));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.anchor = GridBagConstraints.WEST;
        constraints.gridx = 0;
        constraints.gridy = 0;
        builtValue.setFont(builtValue.getFont().deriveFont(Font.BOLD, 25f));
        builtValue.setForeground(GOLD);
        panel.add(builtValue, constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.anchor = GridBagConstraints.EAST;
        builtCaption.setForeground(new Color(208, 204, 197));
        builtCaption.setFont(builtCaption.getFont().deriveFont(11f));
        panel.add(builtCaption, constraints);
        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.gridwidth = 2;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(7, 0, 7, 0);
        panel.add(new ProgressLine(), constraints);
        constraints.gridy = 2;
        constraints.insets = new Insets(0, 0, 0, 0);
        stockCaption.setForeground(MUTED);
        stockCaption.setFont(stockCaption.getFont().deriveFont(11f));
        panel.add(stockCaption, constraints);
        return panel;
    }

    private Component controls()
    {
        JPanel panel = verticalPanel();
        search.setToolTipText("Search location, clue tier, or known item");
        search.getAccessibleContext().setAccessibleName("Search STASH locations");
        search.setMaximumSize(new Dimension(Integer.MAX_VALUE, 29));
        panel.add(search);
        panel.add(Box.createVerticalStrut(6));

        JPanel statusFilters = new JPanel(new GridLayout(1, 4, 3, 0));
        statusFilters.setOpaque(false);
        ButtonGroup group = new ButtonGroup();
        for (Filter option : Filter.values())
        {
            JToggleButton button = toggleButton(option.label);
            button.getAccessibleContext().setAccessibleName(option.label + " STASH units");
            filters.put(option, button);
            group.add(button);
            statusFilters.add(button);
            if (option == Filter.ALL)
            {
                button.setSelected(true);
            }
            button.addActionListener(event -> {
                filter = option;
                rebuild();
            });
        }
        panel.add(statusFilters);
        panel.add(Box.createVerticalStrut(6));

        JPanel selectLine = new JPanel(new BorderLayout(5, 0));
        selectLine.setOpaque(false);
        tier.setToolTipText("Filter by clue tier");
        tier.getAccessibleContext().setAccessibleName("Clue tier");
        selectLine.add(tier, BorderLayout.CENTER);
        shortlistButton.setToolTipText("Show only locations on my list");
        shortlistButton.setPreferredSize(new Dimension(67, 26));
        shortlistButton.addActionListener(event -> {
            shortlistOnly = shortlistButton.isSelected();
            rebuild();
        });
        selectLine.add(shortlistButton, BorderLayout.EAST);
        panel.add(selectLine);
        return panel;
    }

    private Component statusRow()
    {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        resultCount.setForeground(new Color(211, 207, 199));
        resultCount.setFont(resultCount.getFont().deriveFont(11f));
        statusLabel.setForeground(MUTED);
        statusLabel.setFont(statusLabel.getFont().deriveFont(11f));
        statusLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        row.add(resultCount, BorderLayout.WEST);
        row.add(statusLabel, BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        return row;
    }

    private void installListeners()
    {
        search.getDocument().addDocumentListener(new SimpleDocumentListener(this::rebuild));
        tier.addActionListener(event -> rebuild());
    }

    private void rebuild()
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(this::rebuild);
            return;
        }
        if (rebuilding)
        {
            return;
        }
        rebuilding = true;
        try
        {
            updateSummary();
            List<StashEntry> visible = visibleEntries();
            cards.removeAll();
            if (!loggedIn)
            {
                cards.add(emptyState("Sign in to view your STASH Ledger", "Construction scans and your list are saved per account."));
            }
            else if (visible.isEmpty())
            {
                cards.add(emptyState("No matching STASH units", "Try another location, tier, or status."));
            }
            else
            {
                for (StashEntry entry : visible)
                {
                    cards.add(card(entry));
                    cards.add(Box.createVerticalStrut(6));
                }
            }
            resultCount.setText(visible.size() + (visible.size() == 1 ? " location" : " locations"));
            updateStatusText();
            cards.revalidate();
            cards.repaint();
            SwingUtilities.invokeLater(() -> scrollPane.getVerticalScrollBar().setValue(previousScrollValue));
        }
        finally
        {
            rebuilding = false;
        }
    }

    private void updateSummary()
    {
        int built = (int) entries.stream().filter(entry -> entry.getBuildStatus() == BuildStatus.BUILT).count();
        int filled = (int) entries.stream().filter(entry -> entry.getContentsStatus() == ContentsStatus.FILLED).count();
        int empty = (int) entries.stream().filter(entry -> entry.getBuildStatus() == BuildStatus.BUILT
            && entry.getContentsStatus() == ContentsStatus.EMPTY).count();
        int unknown = built - filled - empty;
        builtValue.setText(loggedIn ? built + " / " + entries.size() : "—");
        stockCaption.setText(loggedIn
            ? filled + " stocked  •  " + empty + " empty  •  " + unknown + " contents unknown"
            : "Contents are never inferred from construction.");
    }

    private void updateStatusText()
    {
        if (!statusMessage.isEmpty())
        {
            statusLabel.setText(statusMessage);
        }
        else if (!loggedIn)
        {
            statusLabel.setText("Account state unavailable");
        }
        else
        {
            statusLabel.setText(accountName.isEmpty() ? "Construction scan" : accountName);
        }
    }

    private Component emptyState(String heading, String message)
    {
        JPanel panel = verticalPanel();
        panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER),
            BorderFactory.createEmptyBorder(17, 12, 17, 12)));
        JLabel title = new JLabel(heading, SwingConstants.CENTER);
        title.setForeground(new Color(224, 220, 212));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel detail = new JLabel("<html><div style='text-align:center; width:180px'>" + message + "</div></html>");
        detail.setForeground(MUTED);
        detail.setFont(detail.getFont().deriveFont(11f));
        detail.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(title);
        panel.add(Box.createVerticalStrut(5));
        panel.add(detail);
        return panel;
    }

    private Component card(StashEntry entry)
    {
        StashDefinition definition = entry.getDefinition();
        boolean expanded = expandedIds.contains(definition.getId());
        JPanel card = verticalPanel();
        card.setBackground(SURFACE);
        card.setOpaque(true);
        card.setBorder(expanded
            ? BorderFactory.createLineBorder(GOLD_DIM)
            : CARD_BORDER);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton header = new JButton();
        header.setLayout(new BorderLayout(7, 0));
        header.setBackground(SURFACE);
        header.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        header.setHorizontalAlignment(SwingConstants.LEFT);
        header.setToolTipText((expanded ? "Collapse " : "Expand ") + definition.getName());
        header.getAccessibleContext().setAccessibleName((expanded ? "Collapse " : "Expand ") + definition.getName());
        header.addActionListener(event -> {
            previousScrollValue = scrollPane.getVerticalScrollBar().getValue();
            if (!expandedIds.add(definition.getId()))
            {
                expandedIds.remove(definition.getId());
            }
            rebuild();
        });
        header.add(statusBadge(entry), BorderLayout.WEST);
        JPanel text = verticalPanel();
        text.setOpaque(false);
        JLabel title = new JLabel(definition.getName());
        title.setForeground(new Color(240, 237, 231));
        title.setFont(FontManager.getRunescapeBoldFont());
        text.add(title);
        JLabel caption = new JLabel(definition.getTier().getLabel() + "  •  " + stateText(entry));
        caption.setForeground(stateColor(entry));
        caption.setFont(caption.getFont().deriveFont(11f));
        text.add(caption);
        header.add(text, BorderLayout.CENTER);
        JLabel disclosure = new JLabel(expanded ? "⌃" : "⌄");
        disclosure.setForeground(MUTED);
        header.add(disclosure, BorderLayout.EAST);
        card.add(header);

        if (expanded)
        {
            card.add(new JSeparator());
            card.add(detail(entry));
        }
        return card;
    }

    private Component statusBadge(StashEntry entry)
    {
        JLabel badge = new JLabel(stateGlyph(entry), SwingConstants.CENTER);
        badge.setPreferredSize(new Dimension(25, 25));
        badge.setOpaque(true);
        badge.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
        badge.setForeground(stateColor(entry));
        badge.setBackground(stateSurface(entry));
        badge.getAccessibleContext().setAccessibleName(stateText(entry));
        return badge;
    }

    private Component detail(StashEntry entry)
    {
        StashDefinition definition = entry.getDefinition();
        JPanel detail = verticalPanel();
        detail.setBackground(SURFACE_DETAIL);
        detail.setOpaque(true);
        detail.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        detail.add(sectionLabel(entry.getBuildStatus() == BuildStatus.NOT_BUILT ? "To build" : "Construction"));
        JLabel construction = new JLabel(entry.getBuildStatus() == BuildStatus.BUILT
            ? "✓ Built • " + definition.getTier().getConstructionLevel() + " Construction"
            : definition.getTier().getConstructionLevel() + " Construction");
        construction.setForeground(entry.getBuildStatus() == BuildStatus.BUILT ? GREEN : GOLD);
        detail.add(construction);
        JLabel materials = new JLabel(definition.getTier().getMaterials() + " • bring hammer and saw");
        materials.setForeground(MUTED);
        materials.setFont(materials.getFont().deriveFont(11f));
        detail.add(materials);

        if (!definition.getItems().isEmpty())
        {
            detail.add(Box.createVerticalStrut(8));
            detail.add(sectionLabel("Clue items"));
            for (String item : definition.getItems())
            {
                JLabel row = new JLabel((entry.getContentsStatus() == ContentsStatus.FILLED ? "✓ " : "• ") + item);
                row.setForeground(entry.getContentsStatus() == ContentsStatus.FILLED ? GREEN : new Color(220, 215, 207));
                row.setFont(row.getFont().deriveFont(11f));
                detail.add(row);
            }
        }
        detail.add(Box.createVerticalStrut(8));
        detail.add(contentsControls(entry));
        detail.add(Box.createVerticalStrut(8));
        detail.add(actionRow(entry));
        return detail;
    }

    private Component contentsControls(StashEntry entry)
    {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        JLabel label = new JLabel("Contents");
        label.setForeground(MUTED);
        label.setFont(label.getFont().deriveFont(11f));
        row.add(label, BorderLayout.WEST);
        JComboBox<ContentsOption> choice = new JComboBox<>(ContentsOption.values());
        choice.setSelectedItem(ContentsOption.forStatus(entry.getContentsStatus()));
        choice.setEnabled(loggedIn && entry.getBuildStatus() == BuildStatus.BUILT);
        choice.setToolTipText(choice.isEnabled()
            ? "Confirm contents for this built STASH unit"
            : "Build this STASH before confirming its contents");
        choice.addActionListener(event -> {
            if (!rebuilding && choice.isEnabled())
            {
                actions.markContents(entry.getDefinition().getId(), ((ContentsOption) choice.getSelectedItem()).status);
            }
        });
        row.add(choice, BorderLayout.CENTER);
        return row;
    }

    private Component actionRow(StashEntry entry)
    {
        JPanel row = new JPanel(new GridLayout(1, 3, 5, 0));
        row.setOpaque(false);
        String id = entry.getDefinition().getId();
        JButton pin = compactButton(entry.isPinned() ? "★ Listed" : "☆ My list");
        pin.setToolTipText(entry.isPinned() ? "Remove from my list" : "Add to my list");
        pin.setEnabled(loggedIn);
        pin.addActionListener(event -> actions.togglePinned(id));
        JButton map = compactButton("Map");
        map.addActionListener(event -> actions.showOnMap(entry.getDefinition()));
        JButton wiki = compactButton("Wiki");
        wiki.addActionListener(event -> actions.openWiki(entry.getDefinition()));
        row.add(pin);
        row.add(map);
        row.add(wiki);
        return row;
    }

    private JLabel sectionLabel(String text)
    {
        JLabel label = new JLabel(text.toUpperCase(Locale.ROOT));
        label.setForeground(MUTED);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 10f));
        return label;
    }

    private Color stateColor(StashEntry entry)
    {
        if (entry.getBuildStatus() == BuildStatus.UNKNOWN)
        {
            return new Color(191, 193, 208);
        }
        if (entry.getBuildStatus() == BuildStatus.NOT_BUILT)
        {
            return MUTED;
        }
        if (entry.getContentsStatus() == ContentsStatus.FILLED)
        {
            return GREEN;
        }
        if (entry.getContentsStatus() == ContentsStatus.EMPTY)
        {
            return EMPTY;
        }
        return new Color(191, 193, 208);
    }

    private Color stateSurface(StashEntry entry)
    {
        if (entry.getBuildStatus() == BuildStatus.BUILT && entry.getContentsStatus() == ContentsStatus.FILLED)
        {
            return GREEN_SURFACE;
        }
        if (entry.getBuildStatus() == BuildStatus.BUILT && entry.getContentsStatus() == ContentsStatus.EMPTY)
        {
            return EMPTY_SURFACE;
        }
        return new Color(53, 54, 64);
    }

    private String stateText(StashEntry entry)
    {
        if (entry.getBuildStatus() == BuildStatus.UNKNOWN) return "Construction unknown";
        if (entry.getBuildStatus() == BuildStatus.NOT_BUILT) return "Not built";
        if (entry.getContentsStatus() == ContentsStatus.FILLED) return "Built • stocked";
        if (entry.getContentsStatus() == ContentsStatus.EMPTY) return "Built • empty";
        return "Built • contents unknown";
    }

    private String stateGlyph(StashEntry entry)
    {
        if (entry.getBuildStatus() == BuildStatus.NOT_BUILT) return "⌂";
        if (entry.getBuildStatus() == BuildStatus.UNKNOWN || entry.getContentsStatus() == ContentsStatus.UNKNOWN) return "?";
        return entry.getContentsStatus() == ContentsStatus.FILLED ? "✓" : "□";
    }

    private static JPanel verticalPanel()
    {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        return panel;
    }

    private static JToggleButton toggleButton(String text)
    {
        JToggleButton button = new JToggleButton(text);
        button.setFont(button.getFont().deriveFont(10f));
        button.setForeground(MUTED);
        button.setBackground(SURFACE_DARK);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createLineBorder(BORDER));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private static JButton compactButton(String text)
    {
        JButton button = new JButton(text);
        button.setForeground(new Color(227, 223, 214));
        button.setBackground(new Color(56, 55, 60));
        button.setFont(button.getFont().deriveFont(10f));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createLineBorder(new Color(76, 75, 81)));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private static JButton iconButton(String text, String tooltip)
    {
        JButton button = new JButton(text);
        button.setToolTipText(tooltip);
        button.getAccessibleContext().setAccessibleName(tooltip);
        button.setForeground(GOLD);
        button.setBackground(SURFACE_DARK);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private final class ProgressLine extends JPanel
    {
        private ProgressLine()
        {
            setOpaque(false);
            setPreferredSize(new Dimension(1, 5));
            getAccessibleContext().setAccessibleName("STASH construction progress");
        }

        @Override
        protected void paintComponent(java.awt.Graphics graphics)
        {
            super.paintComponent(graphics);
            graphics.setColor(BORDER);
            graphics.fillRoundRect(0, 0, getWidth(), getHeight(), 5, 5);
            int total = entries.size();
            int built = (int) entries.stream().filter(entry -> entry.getBuildStatus() == BuildStatus.BUILT).count();
            if (total > 0)
            {
                graphics.setColor(GOLD);
                graphics.fillRoundRect(0, 0, Math.round(getWidth() * (built / (float) total)), getHeight(), 5, 5);
            }
        }
    }

    private enum Filter
    {
        ALL("All") { @Override boolean matches(StashEntry entry) { return true; } },
        NOT_BUILT("Not built") { @Override boolean matches(StashEntry entry) { return entry.getBuildStatus() == BuildStatus.NOT_BUILT; } },
        BUILT("Built") { @Override boolean matches(StashEntry entry) { return entry.getBuildStatus() == BuildStatus.BUILT; } },
        UNKNOWN("Unknown") { @Override boolean matches(StashEntry entry) { return entry.getBuildStatus() == BuildStatus.UNKNOWN || (entry.getBuildStatus() == BuildStatus.BUILT && entry.getContentsStatus() == ContentsStatus.UNKNOWN); } };

        private final String label;
        Filter(String label) { this.label = label; }
        abstract boolean matches(StashEntry entry);
    }

    private enum TierOption
    {
        ALL("All clue tiers", null), BEGINNER("Beginner", StashTier.BEGINNER), EASY("Easy", StashTier.EASY),
        MEDIUM("Medium", StashTier.MEDIUM), HARD("Hard", StashTier.HARD), ELITE("Elite", StashTier.ELITE), MASTER("Master", StashTier.MASTER);
        private final String label;
        private final StashTier tier;
        TierOption(String label, StashTier tier) { this.label = label; this.tier = tier; }
        boolean matches(StashTier value) { return tier == null || tier == value; }
        @Override public String toString() { return label; }
    }

    private enum ContentsOption
    {
        UNKNOWN("Unknown / not checked", ContentsStatus.UNKNOWN),
        EMPTY("Built, empty", ContentsStatus.EMPTY),
        FILLED("Built, stocked", ContentsStatus.FILLED);
        private final String label;
        private final ContentsStatus status;
        ContentsOption(String label, ContentsStatus status) { this.label = label; this.status = status; }
        static ContentsOption forStatus(ContentsStatus status)
        {
            for (ContentsOption option : values()) if (option.status == status) return option;
            return UNKNOWN;
        }
        @Override public String toString() { return label; }
    }

    public interface Actions
    {
        void togglePinned(String id);
        void markContents(String id, ContentsStatus status);
        void showOnMap(StashDefinition definition);
        void openWiki(StashDefinition definition);
        void refresh();
    }
}
