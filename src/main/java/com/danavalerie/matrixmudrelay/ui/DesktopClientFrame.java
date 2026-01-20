/*
 * Lesa's Discworld MUD client.
 * Copyright (C) 2026 Dana Reese
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.danavalerie.matrixmudrelay.ui;

import com.danavalerie.matrixmudrelay.config.BotConfig;
import com.danavalerie.matrixmudrelay.config.ConfigLoader;
import com.danavalerie.matrixmudrelay.config.DeliveryRouteMappings;
import com.danavalerie.matrixmudrelay.core.MudCommandProcessor;
import com.danavalerie.matrixmudrelay.core.RoomMapService;
import com.danavalerie.matrixmudrelay.core.StoreInventoryTracker;
import com.danavalerie.matrixmudrelay.core.StatsHudRenderer;
import com.danavalerie.matrixmudrelay.core.TimerService;
import com.danavalerie.matrixmudrelay.core.RoomNoteService;
import com.danavalerie.matrixmudrelay.core.WritTracker;
import java.util.regex.Pattern;
import com.danavalerie.matrixmudrelay.mud.MudClient;
import com.danavalerie.matrixmudrelay.util.AnsiColorParser;
import com.danavalerie.matrixmudrelay.util.GrammarUtils;
import com.danavalerie.matrixmudrelay.util.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.image.BufferedImage;
import java.awt.event.KeyEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public final class DesktopClientFrame extends JFrame implements MudCommandProcessor.ClientOutput {
    private static final Logger log = LoggerFactory.getLogger(DesktopClientFrame.class);
    private static final Pattern JOB_AWARD_PATTERN = Pattern.compile("^You have been awarded .*");
    private final MudOutputPane outputPane = new MudOutputPane();
    private final ChitchatPane chitchatPane = new ChitchatPane();
    private final MapPanel mapPanel;
    private final StatsPanel statsPanel = new StatsPanel();
    private static final int RESULTS_MENU_PAGE_SIZE = 15;
    private static final Map<Integer, String> KEYPAD_DIRECTIONS = Map.of(
            KeyEvent.VK_NUMPAD8, "north",
            KeyEvent.VK_NUMPAD2, "south",
            KeyEvent.VK_NUMPAD6, "east",
            KeyEvent.VK_NUMPAD4, "west",
            KeyEvent.VK_NUMPAD9, "northeast",
            KeyEvent.VK_NUMPAD7, "northwest",
            KeyEvent.VK_NUMPAD3, "southeast",
            KeyEvent.VK_NUMPAD1, "southwest"
        );
        private final JTextField inputField = new JTextField();
    private final MudCommandProcessor commandProcessor;
    private final MudClient mud;
    private DeliveryRouteMappings routeMappings;
    private final WritTracker writTracker;
    private final StoreInventoryTracker storeInventoryTracker;
    private final TimerService timerService;
    private final BotConfig cfg;
    private final Path configPath;
    private final Path routesPath;
    private final RoomMapService routeMapService = new RoomMapService("database.db");
    private final UiFontManager fontManager;
    private final JMenuBar menuBar = new JMenuBar();
    private JMenuItem connectionItem;
    private AutoScrollScrollPane outputScroll;
    private AutoScrollScrollPane chitchatScroll;
    private com.danavalerie.matrixmudrelay.core.ContextualResultList currentResults;
    private final Set<Integer> resultsMenuVisits = new HashSet<>();
    private final List<WritTracker.WritRequirement> writRequirements = new ArrayList<>();
    private final Map<Integer, EnumSet<WritMenuAction>> writMenuVisits = new HashMap<>();
    private final List<JMenu> writMenus = new ArrayList<>();
    private final List<JMenu> resultsMenus = new ArrayList<>();
    private final StringBuilder writLineBuffer = new StringBuilder();
    private final AnsiColorParser writParser = new AnsiColorParser();
    private final StringBuilder writPendingEntity = new StringBuilder();
    private String writPendingTail = "";
    private final StringBuilder storeLineBuffer = new StringBuilder();
    private Color writCurrentColor = AnsiColorParser.defaultColor();
    private boolean writCurrentBold;
    private boolean forwardingKey;
    private boolean suppressNextKeyTyped;
    private boolean allowSplitPersist;
    private boolean suppressSplitPersist;
    private final List<String> inputHistory = new ArrayList<>();
    private int historyIndex = -1;
    private final RoomNoteService roomButtonService;
    private final RoomNotePanel roomNotePanel;
    private final TimerPanel timerPanel;
    private final RoomButtonBarPanel roomButtonBarPanel;

    private enum WritMenuAction {
        ITEM_INFO,
        LIST_STORE,
        BUY_ITEM,
        BUY_ONE_ITEM,
        BUY_TWO_ITEMS,
        NPC_INFO,
        LOCATION_INFO,
        ADD_ROUTE,
        DELIVER
    }

    public DesktopClientFrame(BotConfig cfg, Path configPath, DeliveryRouteMappings routeMappings) {
        super("Lesa's Discworld MUD Client");
        this.cfg = cfg;
        com.danavalerie.matrixmudrelay.core.TeleportRegistry.initialize(cfg.characters);
        this.configPath = configPath;
        this.routesPath = configPath.resolveSibling("delivery-routes.json");
        this.routeMappings = routeMappings;
        this.roomButtonService = new RoomNoteService(configPath.resolveSibling("room-notes.json"));
        this.roomButtonService.populateMissingNames(this.routeMapService);
        this.roomButtonBarPanel = new RoomButtonBarPanel(roomButtonService, this::submitCommand);
        this.roomNotePanel = new RoomNotePanel(roomButtonService);
        this.mapPanel = new MapPanel(
                resolveMapZoomPercent(),
                this::persistMapZoomConfig,
                resolveMapInvert(),
                this::persistMapInvertConfig
        );

        writTracker = new WritTracker();
        storeInventoryTracker = new StoreInventoryTracker();
        timerService = new TimerService(cfg, configPath);

        mud = new MudClient(
                cfg.mud,
                this::handleMudLine,
                reason -> {
                    outputPane.appendSystemText("* MUD disconnected: " + reason);
                    SwingUtilities.invokeLater(() -> updateConnectionMenuItem(false));
                }
        );
        this.timerPanel = new TimerPanel(timerService, () -> mud.getCurrentRoomSnapshot().characterName());
        outputPane.setChitchatListener((text, color) -> chitchatPane.appendChitchatLine(text, color));

        commandProcessor = new MudCommandProcessor(cfg, mud, writTracker, storeInventoryTracker, timerService, () -> routeMappings, this);
        mapPanel.setSpeedwalkHandler(
                location -> commandProcessor.speedwalkTo(location.roomId())
        );
        mud.setGmcpListener(commandProcessor);
        fontManager = new UiFontManager(this, outputPane.getFont());
        fontManager.registerListener(statsPanel);
        fontManager.registerListener(font -> updateTheme(mapPanel.isInverted()));

        statsPanel.setCharacterSelector(name -> {
            String current = statsPanel.getCurrentCharacterName();
            if (current == null) {
                submitCommand(name);
            } else {
                submitCommand("su " + name);
            }
        });
        statsPanel.setCharacterSamplesLoader(name -> {
            BotConfig.CharacterConfig c = cfg.characters.get(name);
            return c != null ? c.gpRateSamples : null;
        });
        statsPanel.setOnSamplesChanged((name, samples) -> {
            BotConfig.CharacterConfig c = cfg.characters.computeIfAbsent(name, k -> new BotConfig.CharacterConfig());
            c.gpRateSamples = new ArrayList<>(samples);
            saveConfig();
        });
        if (cfg.characters != null) {
            statsPanel.setConfigCharacters(new ArrayList<>(cfg.characters.keySet()));
        }

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(1200, 800));
        setLayout(new BorderLayout());
        setJMenuBar(buildMenuBar());
        add(buildSplitLayout(), BorderLayout.CENTER);
        add(statsPanel, BorderLayout.SOUTH);
        applyConfiguredFont();
        pack();
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setLocationRelativeTo(null);
        installInputFocusForwarding();
        updateTheme(mapPanel.isInverted());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                inputField.requestFocusInWindow();
                submitCommand("/connect");
            }

            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });
    }

    private JMenuBar buildMenuBar() {
        JMenu mainMenu = new JMenu("Menu");
        connectionItem = new JMenuItem();
        connectionItem.addActionListener(event -> {
            if (mud.isConnected()) {
                submitCommand("/disconnect");
            } else {
                submitCommand("/connect");
            }
            updateConnectionMenuItem(mud.isConnected());
        });
        updateConnectionMenuItem(mud.isConnected());
        mainMenu.addMenuListener(new javax.swing.event.MenuListener() {
            @Override
            public void menuSelected(javax.swing.event.MenuEvent e) {
                updateConnectionMenuItem(mud.isConnected());
            }

            @Override
            public void menuDeselected(javax.swing.event.MenuEvent e) {
            }

            @Override
            public void menuCanceled(javax.swing.event.MenuEvent e) {
            }
        });
        mainMenu.add(connectionItem);

        mainMenu.addSeparator();
        JMenuItem fontItem = new JMenuItem("Output Font...");
        fontItem.addActionListener(event -> showFontDialog());
        mainMenu.add(fontItem);

        JMenuItem sendPasswordItem = new JMenuItem("Send Password");
        sendPasswordItem.addActionListener(event -> submitCommand("/pw"));
        mainMenu.add(sendPasswordItem);

        mainMenu.addSeparator();
        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(event -> {
            shutdown();
            System.exit(0);
        });
        mainMenu.add(exitItem);

        menuBar.add(mainMenu);

        JMenu quickLinksMenu = new JMenu("Navigate");
        for (BotConfig.Bookmark link : cfg.bookmarks) {
            JMenuItem linkItem = new JMenuItem(link.name);
            linkItem.addActionListener(event -> {
                commandProcessor.speedwalkTo(link.roomId);
                submitCommand(null); // Just reset history index if we're not recording navigation
            });
            quickLinksMenu.add(linkItem);
        }
        menuBar.add(quickLinksMenu);
        menuBar.add(buildHelpMenu());

        return menuBar;
    }

    private JMenu buildHelpMenu() {
        JMenu helpMenu = new JMenu("Help");
        JMenuItem aboutItem = new JMenuItem("About");
        aboutItem.addActionListener(event -> {
            javax.swing.JOptionPane.showMessageDialog(this,
                "Lesa's Discworld MUD client\n" +
                "Copyright (C) 2026 Dana Reese\n\n" +
                "This program is free software: you can redistribute it and/or modify\n" +
                "it under the terms of the GNU General Public License as published by\n" +
                "the Free Software Foundation, either version 3 of the License, or\n" +
                "(at your option) any later version.\n\n" +
                "This program is distributed in the hope that it will be useful,\n" +
                "but WITHOUT ANY WARRANTY; without even the implied warranty of\n" +
                "MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the\n" +
                "GNU General Public License for more details.\n\n" +
                "You should have received a copy of the GNU General Public License\n" +
                "along with this program.  If not, see <https://www.gnu.org/licenses/>.",
                "About",
                javax.swing.JOptionPane.INFORMATION_MESSAGE);
        });
        helpMenu.add(aboutItem);
        return helpMenu;
    }

    private void showFontDialog() {
        Font current = outputPane.getFont();
        String[] fonts = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames();
        JComboBox<String> fontSelect = new JComboBox<>(filterMonospaceFonts(fonts));
        fontSelect.setSelectedItem(current.getFamily());
        JSpinner sizeSpinner = new JSpinner(new SpinnerNumberModel(current.getSize(), 8, 72, 1));

        Runnable applyFont = () -> {
            String family = (String) fontSelect.getSelectedItem();
            int size = (Integer) sizeSpinner.getValue();
            if (family == null) {
                return;
            }
            applyOutputFont(new Font(family, current.getStyle(), size), true);
        };
        fontSelect.addActionListener(event -> applyFont.run());
        sizeSpinner.addChangeListener(event -> applyFont.run());

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(4, 4, 4, 4);
        constraints.anchor = GridBagConstraints.WEST;
        constraints.gridx = 0;
        constraints.gridy = 0;
        panel.add(new JLabel("Font:"), constraints);
        constraints.gridx = 1;
        panel.add(fontSelect, constraints);

        constraints.gridx = 0;
        constraints.gridy = 1;
        panel.add(new JLabel("Size:"), constraints);
        constraints.gridx = 1;
        panel.add(sizeSpinner, constraints);

        JButton closeButton = new JButton("Close");
        JDialog dialog = new JDialog(this, "Select Output Font", false);
        dialog.setLayout(new BorderLayout(8, 8));
        dialog.add(panel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(closeButton);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        closeButton.addActionListener(event -> dialog.dispose());

        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void addCurrentRoomRoute(WritTracker.WritRequirement requirement,
                                     Consumer<DeliveryRouteMappings> onSuccess,
                                     Consumer<String> onError) {
        Thread worker = new Thread(() -> {
            ThreadUtils.checkNotEdt();
            try {
                String roomId = mud.getCurrentRoomSnapshot().roomId();
                if (roomId == null || roomId.isBlank()) {
                    SwingUtilities.invokeLater(() -> onError.accept("Error: No room info available yet."));
                    return;
                }
                RoomMapService.RoomLocation location = routeMapService.lookupRoomLocation(roomId);
                DeliveryRouteMappings updated = appendRouteMapping(requirement, location);
                ConfigLoader.saveRoutes(routesPath, updated);
                DeliveryRouteMappings reloaded = ConfigLoader.loadRoutes(routesPath);
                SwingUtilities.invokeLater(() -> {
                    routeMappings = reloaded;
                    onSuccess.accept(reloaded);
                    outputPane.appendSystemText("Saved delivery route for \"" + requirement.npc()
                            + "\" at \"" + requirement.locationDisplay() + "\".");
                });
            } catch (RoomMapService.MapLookupException e) {
                SwingUtilities.invokeLater(() -> onError.accept("Error: " + e.getMessage()));
            } catch (IllegalStateException e) {
                SwingUtilities.invokeLater(() -> onError.accept("Error: " + e.getMessage()));
            } catch (Exception e) {
                log.warn("route mapping save failed", e);
                SwingUtilities.invokeLater(() -> onError.accept("Error: Unable to save delivery route."));
            }
        }, "delivery-route-save");
        worker.setDaemon(true);
        worker.start();
    }

    private DeliveryRouteMappings appendRouteMapping(WritTracker.WritRequirement requirement,
                                                     RoomMapService.RoomLocation location) throws IOException {
        DeliveryRouteMappings existing = ConfigLoader.loadRoutes(routesPath);
        String locationDisplay = requirement.locationDisplay();
        List<DeliveryRouteMappings.RouteEntry> existingRoutes =
                existing.routes() == null ? List.of() : existing.routes();
        boolean alreadyExists = existingRoutes.stream()
                .anyMatch(entry -> requirement.npc().equals(entry.npc())
                        && locationDisplay.equals(entry.location()));
        if (alreadyExists) {
            throw new IllegalStateException("Route mapping already exists for \"" + requirement.npc()
                    + "\" at \"" + locationDisplay + "\".");
        }
        List<DeliveryRouteMappings.RouteEntry> updated = new ArrayList<>(existingRoutes);
        updated.add(new DeliveryRouteMappings.RouteEntry(
                requirement.npc(),
                locationDisplay,
                location.roomId(),
                null,
                List.of(),
                null
        ));
        return new DeliveryRouteMappings(updated);
    }

    private void applyOutputFont(Font font) {
        applyOutputFont(font, false);
    }

    private void applyOutputFont(Font font, boolean persist) {
        fontManager.setBaseFont(font);
        if (persist) {
            persistFontConfig(font);
        }
    }

    private static String[] filterMonospaceFonts(String[] fonts) {
        List<String> monospace = new ArrayList<>();
        for (String fontName : fonts) {
            if (isMonospaceFont(fontName)) {
                monospace.add(fontName);
            }
        }
        return monospace.toArray(new String[0]);
    }

    private static boolean isMonospaceFont(String fontName) {
        Font font = new Font(fontName, Font.PLAIN, 12);
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        int widthI;
        int widthW;
        try {
            var graphics = image.createGraphics();
            graphics.setFont(font);
            var metrics = graphics.getFontMetrics();
            widthI = metrics.charWidth('i');
            widthW = metrics.charWidth('W');
            graphics.dispose();
        } catch (Exception ignored) {
            return false;
        }
        return widthI == widthW;
    }

    private void applyConfiguredFont() {
        Font base = fontManager.getBaseFont();
        String family = cfg.ui.fontFamily != null ? cfg.ui.fontFamily : base.getFamily();
        int size = cfg.ui.fontSize != null && cfg.ui.fontSize > 0 ? cfg.ui.fontSize : base.getSize();
        applyOutputFont(new Font(family, base.getStyle(), size));
    }

    private void persistFontConfig(Font font) {
        cfg.ui.fontFamily = font.getFamily();
        cfg.ui.fontSize = font.getSize();
        saveConfig();
    }

    private int resolveMapZoomPercent() {
        Integer zoomPercent = cfg.ui.mapZoomPercent;
        if (zoomPercent == null || zoomPercent <= 0) {
            return 100;
        }
        return zoomPercent;
    }

    private void persistMapZoomConfig(int zoomPercent) {
        cfg.ui.mapZoomPercent = zoomPercent;
        saveConfig();
    }

    private boolean resolveMapInvert() {
        return cfg.ui.invertMap != null && cfg.ui.invertMap;
    }

    private void persistMapInvertConfig(boolean invertMap) {
        cfg.ui.invertMap = invertMap;
        updateTheme(invertMap);
        saveConfig();
    }

    private double resolveMudMapSplitRatio() {
        Double ratio = cfg.ui.mudMapSplitRatio;
        if (ratio == null || ratio <= 0 || ratio >= 1) {
            return 0.75;
        }
        return ratio;
    }

    private void persistMudMapSplitRatio(double ratio) {
        if (Double.isNaN(ratio) || ratio <= 0 || ratio >= 1) {
            return;
        }
        cfg.ui.mudMapSplitRatio = ratio;
        saveConfig();
    }

    private double resolveMapNotesSplitRatio() {
        Double ratio = cfg.ui.mapNotesSplitRatio;
        if (ratio == null || ratio <= 0 || ratio >= 1) {
            return 0.7;
        }
        return ratio;
    }

    private void persistMapNotesSplitRatio(double ratio) {
        if (Double.isNaN(ratio) || ratio <= 0 || ratio >= 1) {
            return;
        }
        cfg.ui.mapNotesSplitRatio = ratio;
        saveConfig();
    }

    private double resolveChitchatTimerSplitRatio() {
        Double ratio = cfg.ui.chitchatTimerSplitRatio;
        if (ratio == null || ratio <= 0 || ratio >= 1) {
            return 0.7;
        }
        return ratio;
    }

    private void persistChitchatTimerSplitRatio(double ratio) {
        if (Double.isNaN(ratio) || ratio <= 0 || ratio >= 1) {
            return;
        }
        cfg.ui.chitchatTimerSplitRatio = ratio;
        saveConfig();
    }

    private double resolveOutputSplitRatio() {
        Double ratio = cfg.ui.outputSplitRatio;
        if (ratio == null || ratio <= 0 || ratio >= 1) {
            return 0.2;
        }
        return ratio;
    }

    private void persistOutputSplitRatio(double ratio) {
        if (Double.isNaN(ratio) || ratio <= 0 || ratio >= 1) {
            return;
        }
        cfg.ui.outputSplitRatio = ratio;
        saveConfig();
    }

    private void saveConfig() {
        try {
            ConfigLoader.save(configPath, cfg);
        } catch (IOException e) {
            outputPane.appendSystemText("* Unable to save config: " + e.getMessage());
        }
    }


    private void updateWritMenus(List<WritTracker.WritRequirement> requirements) {
        boolean resetVisits = !Objects.equals(writRequirements, requirements);
        writRequirements.clear();
        if (requirements != null) {
            writRequirements.addAll(requirements);
        }
        if (resetVisits) {
            writMenuVisits.clear();
        }
        rebuildWritMenus();
    }

    private void rebuildWritMenus() {
        for (JMenu menu : resultsMenus) {
            menuBar.remove(menu);
        }
        for (JMenu menu : writMenus) {
            menuBar.remove(menu);
        }
        writMenus.clear();
        if (writRequirements.isEmpty()) {
            reattachResultsMenus();
            menuBar.revalidate();
            menuBar.repaint();
            return;
        }
        for (int i = 0; i < writRequirements.size(); i++) {
            WritTracker.WritRequirement req = writRequirements.get(i);
            boolean hasRoute = routeMappings.findRoutePlan(req.npc(), req.locationDisplay()).isPresent();
            boolean canWriteRoutes = Files.isWritable(routesPath);
            int index = i;
            JMenu writMenu = new JMenu("W" + (i + 1));

            JMenuItem itemInfo = buildWritMenuItem(index, WritMenuAction.ITEM_INFO,
                    "Item: " + req.quantity() + " " + req.item(),
                    () -> submitCommand("/item exact " + req.item()));
            writMenu.add(itemInfo);

            JMenuItem listItem = buildWritMenuItem(index, WritMenuAction.LIST_STORE,
                    "List Store",
                    () -> submitCommand("list"));
            writMenu.add(listItem);

            if (req.quantity() == 2) {
                JMenuItem buyOneItem = buildWritMenuItem(index, WritMenuAction.BUY_ONE_ITEM,
                        "Buy 1 Item",
                        () -> handleStoreBuy(index, 1));
                writMenu.add(buyOneItem);
                JMenuItem buyTwoItems = buildWritMenuItem(index, WritMenuAction.BUY_TWO_ITEMS,
                        "Buy 2 Items",
                        () -> handleStoreBuy(index, 2));
                writMenu.add(buyTwoItems);
            } else {
                JMenuItem buyItem = buildWritMenuItem(index, WritMenuAction.BUY_ITEM,
                        "Buy Item",
                        () -> handleStoreBuy(index, req.quantity()));
                writMenu.add(buyItem);
            }
            writMenu.addSeparator();

            JMenuItem npcInfo = buildWritMenuItem(index, WritMenuAction.NPC_INFO,
                    "Deliver to: " + req.npc(),
                    () -> submitCommand("/writ " + (index + 1) + " npc"));
            writMenu.add(npcInfo);

            String locationText = req.locationName()
                    + (req.locationSuffix().isBlank() ? "" : " " + req.locationSuffix());
            JMenuItem locInfo = buildWritMenuItem(index, WritMenuAction.LOCATION_INFO,
                    "Location: " + locationText,
                    () -> submitCommand("/writ " + (index + 1) + " loc"));
            writMenu.add(locInfo);

            if (!hasRoute && canWriteRoutes) {
                JMenuItem addRouteItem = buildWritMenuItem(index, WritMenuAction.ADD_ROUTE,
                        "Add Current Room",
                        () -> handleAddRoute(index));
                writMenu.add(addRouteItem);
            }

            JMenuItem deliverItem = buildWritMenuItem(index, WritMenuAction.DELIVER,
                    hasRoute ? "Route and Deliver" : "Deliver",
                    () -> routeMappings.findRoutePlan(req.npc(), req.locationDisplay()).ifPresentOrElse(plan -> {
                        List<String> commands = new ArrayList<>(plan.commands());
                        commands.add("/writ " + (index + 1) + " deliver");
                        commandProcessor.speedwalkToThenCommands(
                                plan.target().roomId(),
                                commands
                        );
                    }, () -> submitCommand("/writ " + (index + 1) + " deliver")));
            writMenu.add(deliverItem);
            writMenus.add(writMenu);
            menuBar.add(writMenu);
        }
        reattachResultsMenus();
        updateTheme(mapPanel.isInverted());
    }

    private JMenuItem buildWritMenuItem(int index, WritMenuAction action, String label, Runnable onSelect) {
        boolean visited = isWritMenuVisited(index, action);
        JMenuItem item = new JMenuItem(formatWritMenuLabel(visited, label));
        item.addActionListener(event -> {
            markWritMenuVisited(index, action);
            item.setText(formatWritMenuLabel(true, label));
            onSelect.run();
        });
        return item;
    }

    private String formatWritMenuLabel(boolean visited, String label) {
        return (visited ? "\u2713 " : "  ") + label;
    }

    private boolean isWritMenuVisited(int index, WritMenuAction action) {
        EnumSet<WritMenuAction> visited = writMenuVisits.get(index);
        return visited != null && visited.contains(action);
    }

    private void markWritMenuVisited(int index, WritMenuAction action) {
        writMenuVisits.computeIfAbsent(index, ignored -> EnumSet.noneOf(WritMenuAction.class)).add(action);
    }

    private void handleMudLine(String line) {
        String normalized = normalizeWritOutput(line);
        bufferWritLines(normalized);
        bufferStoreInventoryLines(normalized);
        outputPane.appendMudText(line);
    }

    private void handleStoreBuy(int index, int quantity) {
        if (!storeInventoryTracker.hasInventory()) {
            outputPane.appendErrorText("Store inventory not cached yet. Use List Store first.");
            return;
        }
        WritTracker.WritRequirement requirement = writRequirements.get(index);
        String itemName = requirement.item();
        if (quantity == 1) {
            List<String> singulars = GrammarUtils.singularizePhrase(itemName);
            if (!singulars.isEmpty()) {
                itemName = singulars.get(0);
            }
        }

        if (storeInventoryTracker.isNameListed()) {
            submitCommand("buy " + quantity + " " + itemName);
            return;
        }
        String finalItemName = itemName;
        storeInventoryTracker.findMatch(requirement.item()).ifPresentOrElse(item ->
                        buyItemById(item.id(), quantity),
                () -> outputPane.appendErrorText("Store inventory does not list \"" + finalItemName + "\"."));
    }

    private void buyItemById(String itemId, int quantity) {
        for (int i = 0; i < quantity; i++) {
            submitCommand("buy " + itemId);
        }
    }

    private void handleRoute(int index) {
        WritTracker.WritRequirement requirement = writRequirements.get(index);
        routeMappings.findRoutePlan(requirement.npc(), requirement.locationDisplay()).ifPresentOrElse(plan -> {
            commandProcessor.speedwalkTo(plan.target().roomId());
        }, () -> outputPane.appendErrorText("No route mapping for \"" + requirement.npc()
                + "\" at \"" + requirement.locationDisplay() + "\"."));
    }

    private void handleAddRoute(int index) {
        WritTracker.WritRequirement requirement = writRequirements.get(index);
        addCurrentRoomRoute(requirement, updated -> {
            routeMappings = updated;
            rebuildWritMenus();
        }, outputPane::appendErrorText);
    }

    private JSplitPane buildSplitLayout() {
        JPanel roomInfoContent = new JPanel(new BorderLayout());
        roomInfoContent.add(roomNotePanel, BorderLayout.CENTER);
        roomInfoContent.add(roomButtonBarPanel, BorderLayout.SOUTH);
        roomInfoContent.setMinimumSize(new Dimension(20, 0));
        roomNotePanel.setMinimumSize(new Dimension(20, 0));
        roomButtonBarPanel.setMinimumSize(new Dimension(20, 0));

        mapPanel.setMinimumSize(new Dimension(20, 0));

        JSplitPane mapNotesSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, mapPanel, roomInfoContent);
        mapNotesSplit.setContinuousLayout(true);
        mapNotesSplit.setResizeWeight(0.7);
        mapNotesSplit.setDividerSize(6);
        mapNotesSplit.setBorder(null);
        mapNotesSplit.setMinimumSize(new Dimension(20, 0));
        double mapNotesInitialRatio = resolveMapNotesSplitRatio();
        mapNotesSplit.addHierarchyListener(event -> {
            if ((event.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) == 0) {
                return;
            }
            if (!mapNotesSplit.isShowing()) {
                return;
            }
            SwingUtilities.invokeLater(() -> {
                suppressSplitPersist = true;
                mapNotesSplit.setDividerLocation(mapNotesInitialRatio);
                suppressSplitPersist = false;
            });
        });
        mapNotesSplit.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, event -> {
            if (!allowSplitPersist || suppressSplitPersist || !mapNotesSplit.isShowing()) {
                return;
            }
            int height = mapNotesSplit.getHeight();
            if (height <= 0) {
                return;
            }
            double ratio = mapNotesSplit.getDividerLocation() / (double) height;
            persistMapNotesSplitRatio(ratio);
        });

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, mapNotesSplit, buildMudPanel());
        splitPane.setContinuousLayout(true);
        splitPane.setResizeWeight(0.25);
        splitPane.setDividerSize(6);
        splitPane.setBorder(null);
        double initialRatio = 1.0 - resolveMudMapSplitRatio();
        splitPane.addHierarchyListener(event -> {
            if ((event.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) == 0) {
                return;
            }
            if (!splitPane.isShowing()) {
                return;
            }
            SwingUtilities.invokeLater(() -> {
                suppressSplitPersist = true;
                splitPane.setDividerLocation(initialRatio);
                suppressSplitPersist = false;
                allowSplitPersist = true;
            });
        });
        splitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, event -> {
            if (!allowSplitPersist || suppressSplitPersist || !splitPane.isShowing()) {
                return;
            }
            int width = splitPane.getWidth();
            if (width <= 0) {
                return;
            }
            double ratio = splitPane.getDividerLocation() / (double) width;
            persistMudMapSplitRatio(1.0 - ratio);
        });
        return splitPane;
    }

    private void submitCommand(String text) {
        outputPane.scrollToBottom();
        outputScroll.setBorder(AutoScrollScrollPane.BLACK_BORDER);
        if (text == null) {
            historyIndex = -1;
            return;
        }
        commandProcessor.handleInput(text);
    }

    @Override
    public void addToHistory(String command) {
        if (command == null || command.isBlank()) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (!inputHistory.isEmpty() && inputHistory.get(0).equals(command)) {
                historyIndex = -1;
                return;
            }
            inputHistory.add(0, command);
            historyIndex = -1;
        });
    }

    private JComponent buildMudPanel() {
        chitchatScroll = new AutoScrollScrollPane(chitchatPane);

        timerPanel.setMinimumSize(new Dimension(20, 0));
        JSplitPane chitchatTimerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, chitchatScroll, timerPanel);
        chitchatTimerSplit.setContinuousLayout(true);
        chitchatTimerSplit.setResizeWeight(0.7);
        chitchatTimerSplit.setDividerSize(6);
        chitchatTimerSplit.setBorder(null);
        double chitchatTimerInitialRatio = resolveChitchatTimerSplitRatio();
        chitchatTimerSplit.addHierarchyListener(event -> {
            if ((event.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) == 0) {
                return;
            }
            if (!chitchatTimerSplit.isShowing()) {
                return;
            }
            SwingUtilities.invokeLater(() -> {
                suppressSplitPersist = true;
                chitchatTimerSplit.setDividerLocation(chitchatTimerInitialRatio);
                suppressSplitPersist = false;
            });
        });
        chitchatTimerSplit.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, event -> {
            if (!allowSplitPersist || suppressSplitPersist || !chitchatTimerSplit.isShowing()) {
                return;
            }
            int width = chitchatTimerSplit.getWidth();
            if (width <= 0) {
                return;
            }
            double ratio = chitchatTimerSplit.getDividerLocation() / (double) width;
            persistChitchatTimerSplitRatio(ratio);
        });

        outputScroll = new AutoScrollScrollPane(outputPane);

        JPanel inputPanel = new JPanel(new BorderLayout(6, 6));
        JButton sendButton = new JButton("Send");
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        Runnable sendAction = () -> {
            String text = inputField.getText();
            inputField.setText("");
            submitCommand(text);
        };
        inputField.addActionListener(e -> sendAction.run());
        sendButton.addActionListener(e -> sendAction.run());
        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                if (inputHistory.isEmpty()) {
                    return;
                }
                if (event.getKeyCode() == KeyEvent.VK_UP) {
                    if (historyIndex + 1 >= inputHistory.size()) {
                        return;
                    }
                    historyIndex += 1;
                    inputField.setText(inputHistory.get(historyIndex));
                    event.consume();
                } else if (event.getKeyCode() == KeyEvent.VK_DOWN) {
                    if (historyIndex <= -1) {
                        return;
                    }
                    historyIndex -= 1;
                    if (historyIndex == -1) {
                        inputField.setText("");
                    } else {
                        inputField.setText(inputHistory.get(historyIndex));
                    }
                    event.consume();
                }
            }
        });

        JSplitPane outputSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, chitchatTimerSplit, outputScroll);
        outputSplit.setContinuousLayout(true);
        outputSplit.setResizeWeight(0.2);
        outputSplit.setDividerSize(6);
        outputSplit.setBorder(null);
        double outputInitialRatio = resolveOutputSplitRatio();
        outputSplit.addHierarchyListener(event -> {
            if ((event.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) == 0) {
                return;
            }
            if (!outputSplit.isShowing()) {
                return;
            }
            SwingUtilities.invokeLater(() -> {
                suppressSplitPersist = true;
                outputSplit.setDividerLocation(outputInitialRatio);
                suppressSplitPersist = false;
            });
        });
        outputSplit.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, event -> {
            if (!allowSplitPersist || suppressSplitPersist || !outputSplit.isShowing()) {
                return;
            }
            int height = outputSplit.getHeight();
            if (height <= 0) {
                return;
            }
            double ratio = outputSplit.getDividerLocation() / (double) height;
            persistOutputSplitRatio(ratio);
        });

        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.add(outputSplit, BorderLayout.CENTER);
        
        JPanel bottomMudPanel = new JPanel(new BorderLayout(6, 0));
        bottomMudPanel.add(inputPanel, BorderLayout.NORTH);
        
        panel.add(bottomMudPanel, BorderLayout.SOUTH);
        return panel;
    }

    private void installInputFocusForwarding() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(event -> {
            if (!isVisible() || !isFocused() || forwardingKey) {
                return false;
            }

            if (event.getID() == KeyEvent.KEY_PRESSED) {
                suppressNextKeyTyped = false;
                if (event.getKeyLocation() == KeyEvent.KEY_LOCATION_NUMPAD) {
                    String command = KEYPAD_DIRECTIONS.get(event.getKeyCode());
                    if (command != null) {
                        submitCommand(command);
                        event.consume();
                        suppressNextKeyTyped = true;
                        return true;
                    }
                }
            } else if (event.getID() == KeyEvent.KEY_TYPED) {
                if (suppressNextKeyTyped) {
                    event.consume();
                    suppressNextKeyTyped = false;
                    return true;
                }
            }

            if (inputField.isFocusOwner()) {
                return false;
            }
            boolean shouldForward = false;
            if (event.getID() == KeyEvent.KEY_TYPED) {
                char keyChar = event.getKeyChar();
                if (keyChar != KeyEvent.CHAR_UNDEFINED && !Character.isISOControl(keyChar)) {
                    shouldForward = true;
                }
            } else if (event.getID() == KeyEvent.KEY_PRESSED) {
                int keyCode = event.getKeyCode();
                if (keyCode == KeyEvent.VK_UP || keyCode == KeyEvent.VK_DOWN || keyCode == KeyEvent.VK_ENTER) {
                    shouldForward = true;
                }
            }

            if (!shouldForward) {
                return false;
            }
            forwardingKey = true;
            try {
                inputField.requestFocusInWindow();
                if (event.getID() == KeyEvent.KEY_PRESSED && event.getKeyCode() == KeyEvent.VK_ENTER) {
                    inputField.postActionEvent();
                } else {
                    KeyEvent forwarded = new KeyEvent(
                            inputField,
                            event.getID(),
                            event.getWhen(),
                            event.getModifiersEx(),
                            event.getKeyCode(),
                            event.getKeyChar(),
                            event.getKeyLocation()
                    );
                    inputField.dispatchEvent(forwarded);
                }
                event.consume();
                return true;
            } finally {
                forwardingKey = false;
            }
        });
    }

    @Override
    public void appendSystem(String text) {
        outputPane.appendSystemText(text);
    }

    @Override
    public void appendCommandEcho(String text) {
        outputPane.appendCommandEcho(text);
    }

    @Override
    public void updateMap(String roomId) {
        SwingUtilities.invokeLater(() -> mapPanel.updateMap(roomId));
    }

    @Override
    public void updateCurrentRoom(String roomId, String roomName) {
        SwingUtilities.invokeLater(() -> {
            mapPanel.updateCurrentRoom(roomId);
            roomButtonBarPanel.updateRoom(roomId, roomName);
            roomNotePanel.updateRoom(roomId, roomName);
        });
    }

    @Override
    public void updateStats(StatsHudRenderer.StatsHudData data) {
        statsPanel.updateStats(data);
    }

    @Override
    public void updateContextualResults(com.danavalerie.matrixmudrelay.core.ContextualResultList results) {
        updateResultsMenu(results);
    }

    @Override
    public void updateSpeedwalkPath(List<RoomMapService.RoomLocation> path) {
        SwingUtilities.invokeLater(() -> mapPanel.setSpeedwalkPath(path));
    }

    private void updateResultsMenu(com.danavalerie.matrixmudrelay.core.ContextualResultList results) {
        SwingUtilities.invokeLater(() -> {
            boolean resetResultsVisits = results != null && !Objects.equals(currentResults, results);
            if (results != null) {
                currentResults = results;
            }
            if (resetResultsVisits) {
                resultsMenuVisits.clear();
            }
            for (JMenu menu : resultsMenus) {
                menuBar.remove(menu);
            }
            resultsMenus.clear();
            if (currentResults == null) {
                menuBar.revalidate();
                menuBar.repaint();
                return;
            }
            List<com.danavalerie.matrixmudrelay.core.ContextualResultList.ContextualResult> resultsList =
                    currentResults.results();
            int totalResults = resultsList.size();
            int totalMenus = Math.max(1, (int) Math.ceil(totalResults / (double) RESULTS_MENU_PAGE_SIZE));
            String title = currentResults.title();
            String footerText = currentResults.footer();

            for (int menuIndex = 0; menuIndex < totalMenus; menuIndex++) {
                JMenu menu = new JMenu("R" + (menuIndex + 1));
                if (menuIndex == 0 && title != null && !title.isBlank()) {
                    JMenuItem header = new JMenuItem(title);
                    header.setEnabled(false);
                    menu.add(header);
                    menu.addSeparator();
                }

                int start = menuIndex * RESULTS_MENU_PAGE_SIZE;
                int end = Math.min(start + RESULTS_MENU_PAGE_SIZE, totalResults);
                if (totalResults == 0) {
                    String empty = currentResults.emptyMessage() == null
                            ? "No results."
                            : currentResults.emptyMessage();
                    JMenuItem emptyItem = new JMenuItem(empty);
                    emptyItem.setEnabled(false);
                    menu.add(emptyItem);
                } else {
                    for (int index = start; index < end; index++) {
                        com.danavalerie.matrixmudrelay.core.ContextualResultList.ContextualResult result =
                                resultsList.get(index);
                        boolean visited = resultsMenuVisits.contains(index);
                        JMenuItem item = new JMenuItem(formatResultsMenuLabel(visited, result.label()));
                        int resultIndex = index;
                        if (result.mapCommand() != null && !result.mapCommand().isBlank()) {
                            item.addActionListener(event -> {
                                resultsMenuVisits.add(resultIndex);
                                item.setText(formatResultsMenuLabel(true, result.label()));
                                submitCommand(result.mapCommand());
                                showSpeedWalkPrompt(result.command());
                            });
                        } else {
                            item.addActionListener(event -> {
                                resultsMenuVisits.add(resultIndex);
                                item.setText(formatResultsMenuLabel(true, result.label()));
                                submitCommand(result.command());
                            });
                        }
                        menu.add(item);
                    }
                }

                boolean isLastMenu = menuIndex == totalMenus - 1;
                if (isLastMenu && footerText != null && !footerText.isBlank()) {
                    menu.addSeparator();
                    JMenuItem footer = new JMenuItem(footerText);
                    footer.setEnabled(false);
                    menu.add(footer);
                }

                resultsMenus.add(menu);
                menuBar.add(menu);
            }
            updateTheme(mapPanel.isInverted());
        });
    }

    private void reattachResultsMenus() {
        for (JMenu menu : resultsMenus) {
            menuBar.add(menu);
        }
    }

    private String formatResultsMenuLabel(boolean visited, String label) {
        return (visited ? "\u2713 " : "  ") + label;
    }

    private void showSpeedWalkPrompt(String speedWalkCommand) {
        if (speedWalkCommand == null || speedWalkCommand.isBlank()) {
            return;
        }
        Object[] options = {"Cancel", "Speed Walk"};
        int choice = javax.swing.JOptionPane.showOptionDialog(
                this,
                "Speed walk to this location?",
                "Speed Walk",
                javax.swing.JOptionPane.DEFAULT_OPTION,
                javax.swing.JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[1]
        );
        if (choice == 1) {
            submitCommand(speedWalkCommand);
        }
    }

    private void shutdown() {
        commandProcessor.shutdown();
        mapPanel.shutdown();
        try {
            mud.disconnect("shutdown", null);
        } catch (Exception ignored) {
        }
    }

    private void bufferWritLines(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        writLineBuffer.append(text);
        int start = 0;
        boolean updated = false;
        while (true) {
            int newline = writLineBuffer.indexOf("\n", start);
            if (newline == -1) {
                break;
            }
            String line = writLineBuffer.substring(start, newline);
            updated |= writTracker.ingest(line);
            start = newline + 1;
        }
        if (start > 0) {
            writLineBuffer.delete(0, start);
        }
        if (updated) {
            var requirements = writTracker.getRequirements();
            SwingUtilities.invokeLater(() -> updateWritMenus(requirements));
        }
    }

    private void bufferStoreInventoryLines(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        storeLineBuffer.append(text);
        int start = 0;
        while (true) {
            int newline = storeLineBuffer.indexOf("\n", start);
            if (newline == -1) {
                break;
            }
            String line = storeLineBuffer.substring(start, newline);
            storeInventoryTracker.ingest(line);
            start = newline + 1;
        }
        if (start > 0) {
            storeLineBuffer.delete(0, start);
        }
    }

    private String normalizeWritOutput(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String normalized = text.replace("\r", "");
        String combined = writPendingTail + normalized;
        AnsiColorParser.ParseResult result =
                writParser.parseStreaming(combined, writCurrentColor, writCurrentBold);
        writPendingTail = result.tail();
        writCurrentColor = result.color();
        writCurrentBold = result.bold();
        return decodeEntitiesToPlain(result.segments());
    }

    private String decodeEntitiesToPlain(List<AnsiColorParser.Segment> segments) {
        if (segments.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        StringBuilder literal = new StringBuilder();
        for (AnsiColorParser.Segment segment : segments) {
            String text = segment.text();
            if (text.isEmpty()) {
                continue;
            }
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                if (writPendingEntity.length() == 0) {
                    if (ch == '&') {
                        flushWritLiteral(out, literal);
                        startWritEntity();
                    } else {
                        literal.append(ch);
                    }
                } else {
                    if (ch == '&') {
                        flushWritPendingEntity(out);
                        startWritEntity();
                    } else {
                        writPendingEntity.append(ch);
                        if (ch == ';') {
                            out.append(decodeWritEntity(writPendingEntity.toString()));
                            writPendingEntity.setLength(0);
                        }
                    }
                }
            }
            flushWritLiteral(out, literal);
        }
        return out.toString();
    }

    private void startWritEntity() {
        writPendingEntity.setLength(0);
        writPendingEntity.append('&');
    }

    private void flushWritPendingEntity(StringBuilder out) {
        if (writPendingEntity.length() == 0) {
            return;
        }
        out.append(writPendingEntity);
        writPendingEntity.setLength(0);
    }

    private static void flushWritLiteral(StringBuilder out, StringBuilder literal) {
        if (literal.length() == 0) {
            return;
        }
        out.append(literal);
        literal.setLength(0);
    }

    private static String decodeWritEntity(String entity) {
        return switch (entity) {
            case "&lt;" -> "<";
            case "&gt;" -> ">";
            case "&amp;" -> "&";
            default -> entity;
        };
    }

    private void updateTheme(boolean inverted) {
        Color bg = inverted ? MapPanel.BACKGROUND_DARK : MapPanel.BACKGROUND_LIGHT;
        Color fg = inverted ? MapPanel.FOREGROUND_LIGHT : MapPanel.FOREGROUND_DARK;

        outputPane.updateTheme(inverted);
        chitchatPane.updateTheme(inverted);
        statsPanel.updateTheme(inverted);

        updateComponentTree(getContentPane(), bg, fg);
        roomButtonBarPanel.updateTheme(bg, fg);
        roomNotePanel.updateTheme(bg, fg);
        timerPanel.updateTheme(bg, fg);
        inputField.setCaretColor(fg);

        menuBar.setBackground(bg);
        menuBar.setForeground(fg);
        for (int i = 0; i < menuBar.getMenuCount(); i++) {
            JMenu menu = menuBar.getMenu(i);
            if (menu != null) {
                updateMenuTheme(menu, bg, fg);
            }
        }
        menuBar.revalidate();
        menuBar.repaint();

        repaint();
    }

    private void updateComponentTree(Component c, Color bg, Color fg) {
        if (c instanceof MapPanel || c instanceof MudOutputPane || c instanceof ChitchatPane || c instanceof StatsPanel) {
            return;
        }
        if (c instanceof JPanel || c instanceof JSplitPane || c instanceof JScrollPane) {
            c.setBackground(bg);
        }
        if (c instanceof JLabel || c instanceof JTextField || c instanceof JButton) {
            c.setBackground(bg);
            c.setForeground(fg);
        }

        if (c instanceof Container container) {
            for (Component child : container.getComponents()) {
                updateComponentTree(child, bg, fg);
            }
        }
    }

    private void updateMenuTheme(JMenuItem item, Color bg, Color fg) {
        item.setBackground(bg);
        item.setForeground(fg);
        if (item instanceof JMenu menu) {
            for (int i = 0; i < menu.getItemCount(); i++) {
                JMenuItem subItem = menu.getItem(i);
                if (subItem != null) {
                    updateMenuTheme(subItem, bg, fg);
                }
            }
        }
    }

    private void updateConnectionMenuItem(boolean connected) {
        if (connectionItem == null) {
            return;
        }
        connectionItem.setText(connected ? "Disconnect" : "Connect");
    }

    @Override
    public void updateConnectionState(boolean connected) {
        SwingUtilities.invokeLater(() -> updateConnectionMenuItem(connected));
    }

    public static void launch(BotConfig cfg, Path configPath, DeliveryRouteMappings routes) {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception e) {
            System.err.println("Unable to set cross-platform look and feel: " + e.getMessage());
        }
        SwingUtilities.invokeLater(() -> {
            DesktopClientFrame frame = new DesktopClientFrame(cfg, configPath, routes);
            frame.setVisible(true);
        });
    }
}

