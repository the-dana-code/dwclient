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

import com.danavalerie.matrixmudrelay.config.ClientConfig;
import com.danavalerie.matrixmudrelay.config.ConfigLoader;
import com.danavalerie.matrixmudrelay.config.DeliveryRouteMappings;
import com.danavalerie.matrixmudrelay.config.UiConfig;
import com.danavalerie.matrixmudrelay.core.MenuPersistenceService;
import com.danavalerie.matrixmudrelay.core.MudCommandProcessor;
import com.danavalerie.matrixmudrelay.core.WritMenuAction;
import com.danavalerie.matrixmudrelay.core.RoomMapService;
import com.danavalerie.matrixmudrelay.core.StoreInventoryTracker;
import com.danavalerie.matrixmudrelay.core.data.RoomData;
import com.danavalerie.matrixmudrelay.core.data.ShopItem;
import com.danavalerie.matrixmudrelay.core.StatsHudRenderer;
import com.danavalerie.matrixmudrelay.core.TimerService;
import com.danavalerie.matrixmudrelay.core.RoomNoteService;
import com.danavalerie.matrixmudrelay.core.WritTracker;
import com.danavalerie.matrixmudrelay.ui.SpeedwalkMenuItem.SpeedwalkEstimate;
import java.util.Optional;
import java.util.stream.Collectors;
import com.danavalerie.matrixmudrelay.core.TeleportRegistry;
import java.util.regex.Pattern;
import com.danavalerie.matrixmudrelay.mud.MudClient;
import com.danavalerie.matrixmudrelay.util.AnsiColorParser;
import com.danavalerie.matrixmudrelay.util.GrammarUtils;
import com.danavalerie.matrixmudrelay.util.PasswordPreferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JCheckBox;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JSpinner;
import javax.swing.JSlider;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
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
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
    private static final int WRIT_ITEM_SEARCH_LIMIT = 999;
    private static final int MAX_REGEN_RATE = 4;
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
    private Color currentBg;
    private Color currentFg;
    private final MudCommandProcessor commandProcessor;
    private final MudClient mud;
    private DeliveryRouteMappings routeMappings;
    private final WritTracker writTracker;
    private final StoreInventoryTracker storeInventoryTracker;
    private String currentRoomId;
    private String currentRoomName;
    private final TimerService timerService;
    private final ClientConfig cfg;
    private final UiConfig uiCfg;
    private final Path configPath;
    private final Path routesPath;
    private final RoomMapService routeMapService;
    private final UiFontManager fontManager;
    private final JMenuBar menuBar = new JMenuBar();
    private KeepOpenMenuItem connectionItem;
    private AutoScrollScrollPane outputScroll;
    private AutoScrollScrollPane chitchatScroll;
    private com.danavalerie.matrixmudrelay.core.ContextualResultList currentResults;
    private final Set<Integer> resultsMenuVisits = new HashSet<>();
    private final List<WritTracker.WritRequirement> writRequirements = new ArrayList<>();
    private final Map<Integer, EnumSet<WritMenuAction>> writMenuVisits = new HashMap<>();
    private final Map<Integer, MenuPersistenceService.WritItemMenuState> writItemMenuStates = new HashMap<>();
    private int selectedWritIndex = 0;
    private int selectedResultsPageIndex = 0;
    private JMenu teleportsMenu;
    private JMenu bookmarksMenu;
    private JMenu writTopMenu;
    private JMenu resultsTopMenu;
    private JMenu regenMenu;
    private JMenu hpRegenMenu;
    private JMenu gpRegenMenu;
    private SpeedwalkMenuItem repeatLastSpeedwalkItem;
    private KeepOpenMenuItem writRouteMenuItem;
    private String currentCharacterName = null;
    private final StringBuilder writLineBuffer = new StringBuilder();
    private String writCharacterName = null;
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
    private final UULibraryButtonPanel uuLibraryButtonPanel;
    private final DiscworldTimePanel discworldTimePanel = new DiscworldTimePanel();
    private final MenuPersistenceService menuPersistenceService;

    private JPanel teleportInfoPanel;
    private JLabel teleportCommandLabel;
    private JLabel teleportTargetLabel;
    private JButton restartSpeedwalkButton;
    private JButton cancelTeleportButton;


    public DesktopClientFrame(ClientConfig cfg, UiConfig uiCfg, Path configPath, DeliveryRouteMappings routeMappings, RoomMapService routeMapService) {
        super("Lesa's Discworld MUD Client");
        this.cfg = cfg;
        this.uiCfg = uiCfg;
        this.routeMapService = routeMapService;
        com.danavalerie.matrixmudrelay.core.TeleportRegistry.initialize(cfg.characters);
        this.configPath = configPath;
        this.routesPath = configPath.resolveSibling("delivery-routes.json");
        this.routeMappings = routeMappings;
        this.roomButtonService = new RoomNoteService(configPath.resolveSibling("room-notes.json"));
        this.roomButtonService.populateMissingNames(this.routeMapService);
        this.roomButtonBarPanel = new RoomButtonBarPanel(roomButtonService, this::submitCommand);
        this.uuLibraryButtonPanel = new UULibraryButtonPanel(this::submitCommand);
        this.roomNotePanel = new RoomNotePanel(roomButtonService);
        
        com.danavalerie.matrixmudrelay.core.UULibraryService.getInstance().addListener(() -> {
            SwingUtilities.invokeLater(() -> {
                boolean active = com.danavalerie.matrixmudrelay.core.UULibraryService.getInstance().isActive();
                uuLibraryButtonPanel.setVisible(active);
                if (active) {
                    uuLibraryButtonPanel.rebuildLayout();
                }
            });
        });
        this.mapPanel = new MapPanel(
                routeMapService,
                resolveMapZoomPercent(),
                this::persistMapZoomConfig,
                resolveMapInvert(),
                this::persistMapInvertConfig
        );

        writTracker = new WritTracker();
        storeInventoryTracker = new StoreInventoryTracker();
        timerService = new TimerService(cfg, uiCfg, configPath);
        menuPersistenceService = new MenuPersistenceService(configPath.resolveSibling("menus.json"));

        MenuPersistenceService.SavedMenus saved = menuPersistenceService.load();
        if (saved != null) {
            this.writCharacterName = saved.writCharacterName();
            if (saved.writRequirements() != null) {
                this.writRequirements.addAll(saved.writRequirements());
                this.writTracker.setRequirements(saved.writRequirements());
            }
            if (saved.writMenuVisits() != null) {
                this.writMenuVisits.putAll(saved.writMenuVisits());
            }
            if (saved.writItemMenuStates() != null) {
                this.writItemMenuStates.putAll(saved.writItemMenuStates());
            }
        }
        selectedWritIndex = resolveSelectedWritIndex();
        restoreResultsMenuState();

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
        outputPane.setTriggers(cfg.triggers);
        commandProcessor = new MudCommandProcessor(cfg, uiCfg, configPath, mud, routeMapService, writTracker, storeInventoryTracker, timerService, () -> routeMappings, this);
        outputPane.setLineListener(line -> commandProcessor.onFullLineReceived(line));
        SpeedwalkMenuItem.setEstimateProvider(this::estimateSpeedwalkForMenu);
        mapPanel.setSpeedwalkHandler(
                location -> commandProcessor.speedwalkTo(location.roomId())
        );
        mud.setGmcpListener(commandProcessor);
        mud.setConnectListener(commandProcessor);
        fontManager = new UiFontManager(this, outputPane.getFont());
        fontManager.registerListener(statsPanel);
        fontManager.registerListener(roomButtonBarPanel);
        fontManager.registerListener(uuLibraryButtonPanel);
        fontManager.registerListener(discworldTimePanel);
        fontManager.registerListener(font -> updateTheme(mapPanel.isInverted()));

        statsPanel.setCharacterSelector(name -> {
            String current = statsPanel.getCurrentCharacterName();
            if (current == null) {
                submitCommand(name);
            } else {
                submitCommand("su " + name);
            }
        });
        statsPanel.setCharacterGpSamplesLoader(name -> {
            UiConfig.CharacterUiData c = uiCfg.characters.get(name);
            return c != null ? c.gpRateSamples : null;
        });
        statsPanel.setCharacterHpSamplesLoader(name -> {
            UiConfig.CharacterUiData c = uiCfg.characters.get(name);
            return c != null ? c.hpRateSamples : null;
        });
        statsPanel.setOnGpSamplesChanged((name, samples) -> {
            UiConfig.CharacterUiData c = uiCfg.characters.computeIfAbsent(name, k -> new UiConfig.CharacterUiData());
            c.gpRateSamples = new ArrayList<>(samples);
            saveUiConfig();
        });
        statsPanel.setOnHpSamplesChanged((name, samples) -> {
            UiConfig.CharacterUiData c = uiCfg.characters.computeIfAbsent(name, k -> new UiConfig.CharacterUiData());
            c.hpRateSamples = new ArrayList<>(samples);
            saveUiConfig();
        });
        applyCharacterRegenOverrides();
        if (cfg.characters != null) {
            statsPanel.setConfigCharacters(new ArrayList<>(cfg.characters.keySet()));
        }

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(1200, 800));
        setLayout(new BorderLayout());
        setJMenuBar(buildMenuBar());
        rebuildWritMenus();
        updateResultsMenu(currentResults);
        add(buildSplitLayout(), BorderLayout.CENTER);
        add(statsPanel, BorderLayout.SOUTH);
        applyConfiguredFont();
        pack();
        applyWindowConfiguration();
        if (!isWindowMaximized() && !hasStoredWindowPosition()) {
            setLocationRelativeTo(null);
        }
        installInputFocusForwarding();
        updateTheme(mapPanel.isInverted());
        installWindowStatePersistence();

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

    private void applyWindowConfiguration() {
        Integer width = uiCfg.windowWidth;
        Integer height = uiCfg.windowHeight;
        if (width != null && height != null && width > 0 && height > 0) {
            setSize(new Dimension(width, height));
        }
        Integer posX = uiCfg.windowX;
        Integer posY = uiCfg.windowY;
        if (posX != null && posY != null) {
            setLocation(posX, posY);
        }
        if (Boolean.TRUE.equals(uiCfg.windowMaximized)) {
            setExtendedState(getExtendedState() | JFrame.MAXIMIZED_BOTH);
        }
    }

    private boolean hasStoredWindowPosition() {
        return uiCfg.windowX != null && uiCfg.windowY != null;
    }

    private void installWindowStatePersistence() {
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (!isShowing() || isWindowMaximized()) {
                    return;
                }
                persistWindowSize(getSize());
            }

            @Override
            public void componentMoved(ComponentEvent e) {
                if (!isShowing() || isWindowMaximized()) {
                    return;
                }
                persistWindowPosition(getLocation());
            }
        });

        addWindowStateListener(new WindowAdapter() {
            @Override
            public void windowStateChanged(WindowEvent e) {
                boolean maximized = (e.getNewState() & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH;
                persistWindowMaximized(maximized);
                if (!maximized) {
                    persistWindowSize(getSize());
                    persistWindowPosition(getLocation());
                }
            }
        });
    }

    private boolean isWindowMaximized() {
        return (getExtendedState() & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH;
    }

    private void persistWindowSize(Dimension size) {
        if (size == null || size.width <= 0 || size.height <= 0) {
            return;
        }
        if (Objects.equals(uiCfg.windowWidth, size.width) && Objects.equals(uiCfg.windowHeight, size.height)) {
            return;
        }
        uiCfg.windowWidth = size.width;
        uiCfg.windowHeight = size.height;
        saveUiConfig();
    }

    private void persistWindowPosition(Point location) {
        if (location == null) {
            return;
        }
        if (Objects.equals(uiCfg.windowX, location.x) && Objects.equals(uiCfg.windowY, location.y)) {
            return;
        }
        uiCfg.windowX = location.x;
        uiCfg.windowY = location.y;
        saveUiConfig();
    }

    private void persistWindowMaximized(boolean maximized) {
        if (Objects.equals(uiCfg.windowMaximized, maximized)) {
            return;
        }
        uiCfg.windowMaximized = maximized;
        saveUiConfig();
    }

    private JMenuBar buildMenuBar() {
        JMenu mainMenu = new JMenu("Menu");
        if (currentBg != null && currentFg != null) {
            updateMenuTheme(mainMenu, currentBg, currentFg);
        }
        connectionItem = new KeepOpenMenuItem("", false);
        if (currentBg != null && currentFg != null) {
            updateMenuTheme(connectionItem, currentBg, currentFg);
        }
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
        KeepOpenMenuItem sendPasswordItem = new KeepOpenMenuItem("Send Password", false);
        if (currentBg != null && currentFg != null) {
            updateMenuTheme(sendPasswordItem, currentBg, currentFg);
        }
        sendPasswordItem.addActionListener(event -> submitCommand("/pw"));
        mainMenu.add(sendPasswordItem);

        KeepOpenMenuItem editPasswordItem = new KeepOpenMenuItem("Edit Password...", false);
        if (currentBg != null && currentFg != null) {
            updateMenuTheme(editPasswordItem, currentBg, currentFg);
        }
        editPasswordItem.addActionListener(event -> showEditPasswordDialog(null));
        mainMenu.add(editPasswordItem);

        mainMenu.addSeparator();
        KeepOpenMenuItem fontItem = new KeepOpenMenuItem("Output Font...", false);
        if (currentBg != null && currentFg != null) {
            updateMenuTheme(fontItem, currentBg, currentFg);
        }
        fontItem.addActionListener(event -> showFontDialog());
        mainMenu.add(fontItem);

        KeepOpenMenuItem triggersItem = new KeepOpenMenuItem("Triggers...", false);
        if (currentBg != null && currentFg != null) {
            updateMenuTheme(triggersItem, currentBg, currentFg);
        }
        triggersItem.addActionListener(event -> showTriggerDialog());
        mainMenu.add(triggersItem);

        KeepOpenMenuItem editRoomItem = new KeepOpenMenuItem("Edit Current Room...", false);
        if (currentBg != null && currentFg != null) {
            updateMenuTheme(editRoomItem, currentBg, currentFg);
        }
        editRoomItem.addActionListener(event -> showEditCurrentRoomDialog());
        mainMenu.add(editRoomItem);

        mainMenu.addSeparator();

        KeepOpenCheckBoxMenuItem invertItem = new KeepOpenCheckBoxMenuItem("Invert Map", resolveMapInvert());
        if (currentBg != null && currentFg != null) {
            updateMenuTheme(invertItem, currentBg, currentFg);
        }
        invertItem.addActionListener(event -> mapPanel.setInverted(invertItem.isChecked()));
        mainMenu.add(invertItem);

        mainMenu.addSeparator();

        JMenu zoomSubMenu = new JMenu("Zoom");
        if (currentBg != null && currentFg != null) {
            updateMenuTheme(zoomSubMenu, currentBg, currentFg);
        }
        KeepOpenRadioMenuItem.RadioMenuGroup zoomGroup = new KeepOpenRadioMenuItem.RadioMenuGroup();
        int currentZoom = resolveMapZoomPercent();
        for (int z = 20; z <= 200; z += 20) {
            final int zoomVal = z;
            KeepOpenRadioMenuItem zoomItem = new KeepOpenRadioMenuItem(z + "%", z == currentZoom, zoomGroup, null);
            if (currentBg != null && currentFg != null) {
                updateMenuTheme(zoomItem, currentBg, currentFg);
            }
            zoomItem.addActionListener(event -> mapPanel.setZoomPercent(zoomVal));
            zoomSubMenu.add(zoomItem);
        }
        mainMenu.add(zoomSubMenu);

        mainMenu.addSeparator();

        regenMenu = new JMenu("Regeneration Rates");
        if (currentBg != null && currentFg != null) {
            updateMenuTheme(regenMenu, currentBg, currentFg);
        }
        regenMenu.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent e) {
                rebuildRegenMenus();
            }

            @Override
            public void menuDeselected(MenuEvent e) {
            }

            @Override
            public void menuCanceled(MenuEvent e) {
            }
        });
        hpRegenMenu = new JMenu("HP");
        gpRegenMenu = new JMenu("GP");
        if (currentBg != null && currentFg != null) {
            updateMenuTheme(hpRegenMenu, currentBg, currentFg);
            updateMenuTheme(gpRegenMenu, currentBg, currentFg);
        }
        regenMenu.add(hpRegenMenu);
        regenMenu.add(gpRegenMenu);
        rebuildRegenMenus();
        mainMenu.add(regenMenu);

        mainMenu.addSeparator();
        KeepOpenMenuItem exitItem = new KeepOpenMenuItem("Exit", false);
        if (currentBg != null && currentFg != null) {
            updateMenuTheme(exitItem, currentBg, currentFg);
        }
        exitItem.addActionListener(event -> {
            shutdown();
            System.exit(0);
        });
        mainMenu.add(exitItem);

        menuBar.add(mainMenu);
        
        JMenu quickLinksMenu = new JMenu("Navigate");
        if (currentBg != null && currentFg != null) {
            updateMenuTheme(quickLinksMenu, currentBg, currentFg);
        }

        repeatLastSpeedwalkItem = new SpeedwalkMenuItem("", false, commandProcessor::getLastSpeedwalkTargetRoomId);
        if (currentBg != null && currentFg != null) {
            updateMenuTheme(repeatLastSpeedwalkItem, currentBg, currentFg);
        }
        updateRepeatLastSpeedwalkItem();
        repeatLastSpeedwalkItem.addActionListener(e -> submitCommand("/restart"));
        quickLinksMenu.add(repeatLastSpeedwalkItem);

        quickLinksMenu.addSeparator();

        bookmarksMenu = new JMenu("Bookmarks");
        if (currentBg != null && currentFg != null) {
            updateMenuTheme(bookmarksMenu, currentBg, currentFg);
        }
        quickLinksMenu.add(bookmarksMenu);
        refreshBookmarksMenu();

        quickLinksMenu.addSeparator();

        teleportsMenu = new JMenu("Teleports");
        if (currentBg != null && currentFg != null) {
            updateMenuTheme(teleportsMenu, currentBg, currentFg);
        }
        quickLinksMenu.add(teleportsMenu);
        refreshTeleportsMenu();

        quickLinksMenu.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent e) {
                updateRepeatLastSpeedwalkItem();
            }

            @Override
            public void menuDeselected(MenuEvent e) {
            }

            @Override
            public void menuCanceled(MenuEvent e) {
            }
        });
        
        menuBar.add(quickLinksMenu);

        writTopMenu = new JMenu("Writ");
        if (currentBg != null && currentFg != null) {
            updateMenuTheme(writTopMenu, currentBg, currentFg);
        }
        menuBar.add(writTopMenu);

        resultsTopMenu = new JMenu("Results");
        if (currentBg != null && currentFg != null) {
            updateMenuTheme(resultsTopMenu, currentBg, currentFg);
        }
        menuBar.add(resultsTopMenu);

        return menuBar;
    }

    @SuppressWarnings("unchecked")
    private void addBookmarksToMenu(Container menu, List<ClientConfig.Bookmark> bookmarks) {
        if (bookmarks == null) {
            return;
        }

        // Use a TreeMap to keep categories sorted
        Map<String, Object> hierarchy = new java.util.TreeMap<>();

        for (ClientConfig.Bookmark b : bookmarks) {
            String name = b.name;
            if (name == null || name.isBlank()) {
                name = b.roomId;
                try {
                    RoomMapService.RoomLocation roomLoc = routeMapService.lookupRoomLocation(b.roomId);
                    if (roomLoc != null) {
                        name = roomLoc.roomShort();
                    }
                } catch (Exception ignored) {}
            }

            String[] parts = name.split("/");
            Map<String, Object> currentLevel = hierarchy;
            for (int i = 0; i < parts.length - 1; i++) {
                String part = parts[i];
                if (part.isBlank()) continue;
                Object existing = currentLevel.get(part);
                if (!(existing instanceof Map)) {
                    existing = new java.util.TreeMap<String, Object>();
                    currentLevel.put(part, existing);
                }
                currentLevel = (Map<String, Object>) existing;
            }
            String leafName = parts[parts.length - 1];
            if (!(currentLevel.get(leafName) instanceof Map)) {
                currentLevel.put(leafName, b);
            }
        }

        buildMenuFromHierarchy(menu, hierarchy);
    }

    @SuppressWarnings("unchecked")
    private void buildMenuFromHierarchy(Container menu, Map<String, Object> level) {
        for (Map.Entry<String, Object> entry : level.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Map) {
                JMenu subMenu = new JMenu(name);
                if (currentBg != null && currentFg != null) {
                    updateMenuTheme(subMenu, currentBg, currentFg);
                }
                buildMenuFromHierarchy(subMenu, (Map<String, Object>) value);
                menu.add(subMenu);
            } else if (value instanceof ClientConfig.Bookmark link) {
                JMenu bmSubMenu = new JMenu(name);
                if (currentBg != null && currentFg != null) {
                    updateMenuTheme(bmSubMenu, currentBg, currentFg);
                }

                SpeedwalkMenuItem speedwalkNow = new SpeedwalkMenuItem("Speedwalk Now", true, link.roomId);
                if (currentBg != null && currentFg != null) {
                    updateMenuTheme(speedwalkNow, currentBg, currentFg);
                }
                speedwalkNow.addActionListener(event -> {
                    commandProcessor.speedwalkTo(link.roomId);
                    submitCommand(null); // Just reset history index if we're not recording navigation
                });
                bmSubMenu.add(speedwalkNow);

                bmSubMenu.addSeparator();

                KeepOpenMenuItem editBm = new KeepOpenMenuItem("Edit...", false);
                if (currentBg != null && currentFg != null) {
                    updateMenuTheme(editBm, currentBg, currentFg);
                }
                editBm.addActionListener(e -> showEditBookmarkDialog(link));
                bmSubMenu.add(editBm);

                KeepOpenMenuItem deleteBm = new KeepOpenMenuItem("Delete...", false);
                if (currentBg != null && currentFg != null) {
                    updateMenuTheme(deleteBm, currentBg, currentFg);
                }
                deleteBm.addActionListener(e -> {
                    Object[] options = {"Yes", "No"};
                    int choice = JOptionPane.showOptionDialog(
                            this,
                            "Are you sure you want to delete bookmark to " + bmSubMenu.getText() + "?",
                            "Confirm Delete",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.WARNING_MESSAGE,
                            null,
                            options,
                            options[1]
                    );
                    if (choice == 0) { // Index of "Yes"
                        cfg.bookmarks.remove(link);
                        saveConfig();
                        refreshBookmarksMenu();
                    }
                });
                bmSubMenu.add(deleteBm);

                menu.add(bmSubMenu);
            }
        }
    }


    private void showTriggerDialog() {
        TriggerConfigDialog dialog = new TriggerConfigDialog(this, cfg.triggers);
        if (currentBg != null && currentFg != null) {
            updateComponentTree(dialog.getContentPane(), currentBg, currentFg);
        }
        dialog.setVisible(true);
        if (dialog.isSaved()) {
            cfg.triggers = dialog.getTriggers();
            outputPane.setTriggers(cfg.triggers);
            saveConfig();
        }
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
        try {
            String roomId = mud.getCurrentRoomSnapshot().roomId();
            if (roomId == null || roomId.isBlank()) {
                onError.accept("Error: No room info available yet.");
                return;
            }
            RoomMapService.RoomLocation location = routeMapService.lookupRoomLocation(roomId);
            DeliveryRouteMappings updated = appendRouteMapping(requirement, location);
            ConfigLoader.saveRoutes(routesPath, updated);
            routeMappings = updated;
            onSuccess.accept(updated);
            outputPane.appendSystemText("Saved delivery route for \"" + requirement.npc()
                    + "\" at \"" + requirement.locationDisplay() + "\".");
        } catch (RoomMapService.MapLookupException e) {
            onError.accept("Error: " + e.getMessage());
        } catch (IllegalStateException e) {
            onError.accept("Error: " + e.getMessage());
        } catch (Exception e) {
            log.warn("route mapping save failed", e);
            onError.accept("Error: Unable to save delivery route.");
        }
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
        saveConfig();
        updateTheme(invertMap);
    }

    private Integer sanitizeRegenRateOverride(Integer rate) {
        if (rate == null || rate < 0) {
            return null;
        }
        return Math.min(rate, MAX_REGEN_RATE);
    }

    private Integer resolveCharacterRegenRateOverride(String characterName, boolean isHp) {
        if (characterName == null || characterName.isBlank()) {
            return null;
        }
        ClientConfig.CharacterConfig charCfg = cfg.characters.get(characterName);
        if (charCfg == null) {
            return null;
        }
        return sanitizeRegenRateOverride(isHp ? charCfg.hpRegenRateOverride : charCfg.gpRegenRateOverride);
    }

    private void applyCharacterRegenOverrides() {
        String charName = currentCharacterName;
        statsPanel.setHpRateOverride(resolveCharacterRegenRateOverride(charName, true));
        statsPanel.setGpRateOverride(resolveCharacterRegenRateOverride(charName, false));
    }

    private void setHpRegenRateOverride(Integer rate) {
        Integer sanitized = sanitizeRegenRateOverride(rate);
        if (currentCharacterName == null || currentCharacterName.isBlank()) {
            JOptionPane.showMessageDialog(this, "No character logged in.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        ClientConfig.CharacterConfig charCfg = cfg.characters.computeIfAbsent(currentCharacterName, k -> new ClientConfig.CharacterConfig());
        charCfg.hpRegenRateOverride = sanitized;
        statsPanel.setHpRateOverride(charCfg.hpRegenRateOverride);
        saveConfig();
        rebuildRegenMenus();
    }

    private void setGpRegenRateOverride(Integer rate) {
        Integer sanitized = sanitizeRegenRateOverride(rate);
        if (currentCharacterName == null || currentCharacterName.isBlank()) {
            JOptionPane.showMessageDialog(this, "No character logged in.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        ClientConfig.CharacterConfig charCfg = cfg.characters.computeIfAbsent(currentCharacterName, k -> new ClientConfig.CharacterConfig());
        charCfg.gpRegenRateOverride = sanitized;
        statsPanel.setGpRateOverride(charCfg.gpRegenRateOverride);
        saveConfig();
        rebuildRegenMenus();
    }

    private void rebuildRegenMenus() {
        if (hpRegenMenu == null || gpRegenMenu == null) {
            return;
        }
        hpRegenMenu.removeAll();
        gpRegenMenu.removeAll();
        String charName = currentCharacterName;
        boolean enabled = charName != null && !charName.isBlank();
        Integer hpRate = resolveCharacterRegenRateOverride(charName, true);
        Integer gpRate = resolveCharacterRegenRateOverride(charName, false);
        addRegenRateMenuItems(hpRegenMenu, hpRate, this::setHpRegenRateOverride, enabled);
        addRegenRateMenuItems(gpRegenMenu, gpRate, this::setGpRegenRateOverride, enabled);
        hpRegenMenu.setEnabled(enabled);
        gpRegenMenu.setEnabled(enabled);
        if (currentBg != null && currentFg != null) {
            updateMenuTheme(hpRegenMenu, currentBg, currentFg);
            updateMenuTheme(gpRegenMenu, currentBg, currentFg);
        }
    }

    private void addRegenRateMenuItems(JMenu menu, Integer selectedRate, Consumer<Integer> onSelect, boolean enabled) {
        KeepOpenRadioMenuItem.RadioMenuGroup group = new KeepOpenRadioMenuItem.RadioMenuGroup();
        KeepOpenRadioMenuItem autoItem = new KeepOpenRadioMenuItem("Auto", selectedRate == null, group, null);
        if (currentBg != null && currentFg != null) {
            updateMenuTheme(autoItem, currentBg, currentFg);
        }
        autoItem.setEnabled(enabled);
        autoItem.addActionListener(event -> onSelect.accept(null));
        menu.add(autoItem);

        for (int rate = 0; rate <= MAX_REGEN_RATE; rate++) {
            boolean isSelected = selectedRate != null && selectedRate == rate;
            KeepOpenRadioMenuItem rateItem = new KeepOpenRadioMenuItem(String.valueOf(rate), isSelected, group, null);
            if (currentBg != null && currentFg != null) {
                updateMenuTheme(rateItem, currentBg, currentFg);
            }
            rateItem.setEnabled(enabled);
            int selectedValue = rate;
            rateItem.addActionListener(event -> onSelect.accept(selectedValue));
            menu.add(rateItem);
        }
    }


    private double resolveMudMapSplitRatio() {
        Double ratio = uiCfg.mudMapSplitRatio;
        if (ratio == null || ratio <= 0 || ratio >= 1) {
            return 0.75;
        }
        return ratio;
    }

    private void persistMudMapSplitRatio(double ratio) {
        if (Double.isNaN(ratio) || ratio <= 0 || ratio >= 1) {
            return;
        }
        uiCfg.mudMapSplitRatio = ratio;
        saveUiConfig();
    }

    private double resolveMapNotesSplitRatio() {
        Double ratio = uiCfg.mapNotesSplitRatio;
        if (ratio == null || ratio <= 0 || ratio >= 1) {
            return 0.7;
        }
        return ratio;
    }

    private void persistMapNotesSplitRatio(double ratio) {
        if (Double.isNaN(ratio) || ratio <= 0 || ratio >= 1) {
            return;
        }
        uiCfg.mapNotesSplitRatio = ratio;
        saveUiConfig();
    }

    private double resolveChitchatTimerSplitRatio() {
        Double ratio = uiCfg.chitchatTimerSplitRatio;
        if (ratio == null || ratio <= 0 || ratio >= 1) {
            return 0.7;
        }
        return ratio;
    }

    private void persistChitchatTimerSplitRatio(double ratio) {
        if (Double.isNaN(ratio) || ratio <= 0 || ratio >= 1) {
            return;
        }
        uiCfg.chitchatTimerSplitRatio = ratio;
        saveUiConfig();
    }

    private double resolveOutputSplitRatio() {
        Double ratio = uiCfg.outputSplitRatio;
        if (ratio == null || ratio <= 0 || ratio >= 1) {
            return 0.2;
        }
        return ratio;
    }

    private void persistOutputSplitRatio(double ratio) {
        if (Double.isNaN(ratio) || ratio <= 0 || ratio >= 1) {
            return;
        }
        uiCfg.outputSplitRatio = ratio;
        saveUiConfig();
    }

    private int resolveSelectedWritIndex() {
        String charName = writCharacterName;
        if (charName == null || charName.isBlank()) {
            return selectedWritIndex;
        }
        UiConfig.CharacterUiData charCfg = uiCfg.characters.get(charName);
        if (charCfg == null || charCfg.selectedWritIndex == null || charCfg.selectedWritIndex < 0) {
            return selectedWritIndex;
        }
        return charCfg.selectedWritIndex;
    }

    private void persistSelectedWritIndex(int index) {
        String charName = writCharacterName;
        if (charName == null || charName.isBlank()) {
            return;
        }
        UiConfig.CharacterUiData charCfg = uiCfg.characters.computeIfAbsent(charName, k -> new UiConfig.CharacterUiData());
        if (charCfg.selectedWritIndex != null && charCfg.selectedWritIndex == index) {
            return;
        }
        charCfg.selectedWritIndex = index;
        saveUiConfig();
    }

    private void setSelectedWritIndex(int index) {
        if (index < 0) {
            index = 0;
        }
        if (selectedWritIndex == index) {
            return;
        }
        selectedWritIndex = index;
        persistSelectedWritIndex(index);
    }

    private void setSelectedResultsPageIndex(int index) {
        if (index < 0) {
            index = 0;
        }
        if (selectedResultsPageIndex == index) {
            return;
        }
        selectedResultsPageIndex = index;
        persistResultsMenuState();
    }

    private int getWritItemSelectedPageIndex(int index) {
        MenuPersistenceService.WritItemMenuState state = writItemMenuStates.get(index);
        Integer selected = state != null ? state.selectedPageIndex() : null;
        return (selected == null || selected < 0) ? 0 : selected;
    }

    private void setWritItemSelectedPageIndex(int index, int pageIndex) {
        if (pageIndex < 0) {
            pageIndex = 0;
        }
        MenuPersistenceService.WritItemMenuState state = writItemMenuStates.get(index);
        Integer selected = state != null ? state.selectedPageIndex() : null;
        if (Objects.equals(selected, pageIndex)) {
            return;
        }
        List<MenuPersistenceService.WritItemMenuEntry> entries = state != null ? state.entries() : null;
        boolean truncated = state != null && state.truncated();
        writItemMenuStates.put(index, new MenuPersistenceService.WritItemMenuState(entries, truncated, pageIndex));
        saveMenus();
    }

    private int resolveWritItemSelectedPageIndex(int index, int totalPages) {
        int pageIndex = getWritItemSelectedPageIndex(index);
        if (totalPages > 0 && pageIndex >= totalPages) {
            pageIndex = 0;
            setWritItemSelectedPageIndex(index, pageIndex);
        }
        return pageIndex;
    }

    private void saveConfig() {
        ConfigLoader.save(configPath, cfg);
    }

    private void saveUiConfig() {
        ConfigLoader.saveUi(configPath.resolveSibling("ui.json"), uiCfg);
    }

    private void saveMenus() {
        menuPersistenceService.save(writRequirements, writCharacterName, writMenuVisits, writItemMenuStates);
    }

    private void restoreResultsMenuState() {
        UiConfig.ResultsMenuState saved = uiCfg.resultsMenu;
        if (saved == null) {
            return;
        }
        currentResults = saved.results;
        resultsMenuVisits.clear();
        if (saved.visitedResults != null) {
            resultsMenuVisits.addAll(saved.visitedResults);
        }
        if (saved.selectedPageIndex != null && saved.selectedPageIndex >= 0) {
            selectedResultsPageIndex = saved.selectedPageIndex;
        }
    }

    private void persistResultsMenuState() {
        UiConfig.ResultsMenuState state = uiCfg.resultsMenu;
        if (state == null) {
            state = new UiConfig.ResultsMenuState();
            uiCfg.resultsMenu = state;
        }
        state.results = currentResults;
        if (resultsMenuVisits.isEmpty()) {
            state.visitedResults = null;
        } else {
            List<Integer> visits = new ArrayList<>(resultsMenuVisits);
            visits.sort(Integer::compareTo);
            state.visitedResults = visits;
        }
        state.selectedPageIndex = selectedResultsPageIndex;
        saveUiConfig();
    }


    @Override
    public void updateRepeatLastSpeedwalkItem() {
        if (SwingUtilities.isEventDispatchThread()) {
            updateRepeatLastSpeedwalkItemUI();
        } else {
            SwingUtilities.invokeLater(this::updateRepeatLastSpeedwalkItemUI);
        }
    }

    @Override
    public void appendTeleportBanner(String banner) {
        outputPane.appendErrorText(banner);
    }

    @Override
    public void setTeleportQueued(String command, String targetName) {
        SwingUtilities.invokeLater(() -> {
            teleportCommandLabel.setText("Command: " + command);
            teleportTargetLabel.setText("Target: " + targetName);
            teleportInfoPanel.setVisible(true);
            updateComponentTree(teleportInfoPanel, currentBg, currentFg);
            revalidate();
            repaint();
        });
    }

    @Override
    public void clearTeleportQueued() {
        SwingUtilities.invokeLater(() -> {
            teleportInfoPanel.setVisible(false);
            revalidate();
            repaint();
        });
    }

    private void updateRepeatLastSpeedwalkItemUI() {
        if (repeatLastSpeedwalkItem == null) return;
        boolean hasLast = commandProcessor.hasLastSpeedwalk();
        repeatLastSpeedwalkItem.setEnabled(hasLast);
        if (hasLast) {
            String name = commandProcessor.getLastSpeedwalkTargetName();
            repeatLastSpeedwalkItem.setText("Repeat: " + (name != null ? name : "Unknown"));
        } else {
            repeatLastSpeedwalkItem.setText("Repeat: N/A");
        }
    }

    void updateWritMenus(List<WritTracker.WritRequirement> requirements) {
        writRequirements.clear();
        if (requirements != null) {
            writRequirements.addAll(optimizeWritOrder(requirements));
        }
        writMenuVisits.clear();
        writItemMenuStates.clear();
        setSelectedWritIndex(0);
        rebuildWritMenus();
        saveMenus();
    }

    private List<WritTracker.WritRequirement> optimizeWritOrder(List<WritTracker.WritRequirement> requirements) {
        if (requirements == null || requirements.size() <= 1) {
            return requirements != null ? requirements : List.of();
        }

        String startRoomId = "a5846b0acd26afec87c2dc20daa7ccadecc94cff";
        List<List<Integer>> permutations = new ArrayList<>();
        generatePermutations(requirements.size(), new ArrayList<>(), new boolean[requirements.size()], permutations);

        double minTotalCost = Double.MAX_VALUE;
        List<WritTracker.WritRequirement> bestPath = null;

        for (List<Integer> p : permutations) {
            double currentTotalCost = 0;
            String currentRoom = startRoomId;
            List<WritTracker.WritRequirement> currentWritPath = new ArrayList<>();
            boolean validPermutation = true;

            for (int index : p) {
                WritTracker.WritRequirement req = requirements.get(index);
                try {
                    List<RoomMapService.ItemSearchResult> itemMatches = searchExactItemMatches(req.item());
                    if (itemMatches.isEmpty()) {
                        validPermutation = false;
                        break;
                    }
                    String itemName = itemMatches.get(0).itemName();
                    List<RoomMapService.RoomSearchResult> shops = routeMapService.searchRoomsByItemName(itemName, 100).stream()
                            .filter(r -> "Shop".equals(r.sourceInfo()))
                            .collect(Collectors.toList());

                    if (shops.isEmpty()) {
                        validPermutation = false;
                        break;
                    }

                    RoomMapService.RoomSearchResult bestShop = null;
                    double minShopCost = Double.MAX_VALUE;

                    for (RoomMapService.RoomSearchResult shop : shops) {
                        try {
                            RoomMapService.RouteResult routeToShop = routeMapService.findRoute(currentRoom, shop.roomId(), getUseTeleportsForPrompt(), currentCharacterName, true);
                            double costToShop = calculateRouteCost(routeToShop);
                            if (costToShop < minShopCost) {
                                minShopCost = costToShop;
                                bestShop = shop;
                            }
                        } catch (RoomMapService.MapLookupException ignored) {}
                    }

                    if (bestShop == null) {
                        validPermutation = false;
                        break;
                    }

                    currentTotalCost += minShopCost;
                    currentRoom = bestShop.roomId();

                    Optional<DeliveryRouteMappings.RoutePlan> deliveryPlan = routeMappings != null
                            ? routeMappings.findRoutePlan(req.npc(), req.locationDisplay())
                            : Optional.empty();

                    if (deliveryPlan.isPresent()) {
                        String deliveryRoomId = deliveryPlan.get().target().roomId();
                        RoomMapService.RouteResult routeToDelivery = routeMapService.findRoute(currentRoom, deliveryRoomId, getUseTeleportsForPrompt(), currentCharacterName, true);
                        currentTotalCost += calculateRouteCost(routeToDelivery);
                        currentRoom = deliveryRoomId;
                    } else {
                        // If no delivery route is found, we can't accurately calculate the total path.
                        // However, the prompt says "navigate to the delivery location", implying it exists.
                        // If we can't find it, we might have to treat it as 0 distance or skip.
                        // Given the instructions, let's assume if we can't find a delivery location, we still proceed but cost is unknown.
                        // But usually, these exist in delivery-routes.json.
                    }
                    currentWritPath.add(req.withShop(bestShop.roomId(), bestShop.roomShort()));

                } catch (Exception e) {
                    validPermutation = false;
                    break;
                }
            }

            if (validPermutation && currentTotalCost < minTotalCost) {
                minTotalCost = currentTotalCost;
                bestPath = currentWritPath;
            }
        }

        return bestPath != null ? bestPath : requirements;
    }

    private void generatePermutations(int n, List<Integer> current, boolean[] used, List<List<Integer>> result) {
        if (current.size() == n) {
            result.add(new ArrayList<>(current));
            return;
        }
        for (int i = 0; i < n; i++) {
            if (!used[i]) {
                used[i] = true;
                current.add(i);
                generatePermutations(n, current, used, result);
                current.remove(current.size() - 1);
                used[i] = false;
            }
        }
    }

    private double calculateRouteCost(RoomMapService.RouteResult route) {
        if (route == null || route.steps().isEmpty()) return 0;
        double cost = 0;
        TeleportRegistry.CharacterTeleports tpCfg = TeleportRegistry.forCharacter(currentCharacterName);
        int tpPenalty = tpCfg.speedwalkingPenalty();
        Set<String> tpCommands = tpCfg.teleports().stream().map(TeleportRegistry.TeleportLocation::command).collect(Collectors.toSet());

        for (RoomMapService.RouteStep step : route.steps()) {
            boolean isTp = tpCommands.contains(step.exit()) || step.exit().startsWith("tp ");
            if (isTp) {
                cost += tpPenalty;
            } else {
                cost += 1;
            }
        }
        return cost;
    }

    @Override
    public void onCharacterChanged(String characterName) {
        if (!Objects.equals(this.currentCharacterName, characterName)) {
            this.currentCharacterName = characterName;
            SwingUtilities.invokeLater(() -> {
                if (cfg.characters != null) {
                    statsPanel.setConfigCharacters(new ArrayList<>(cfg.characters.keySet()));
                }
                refreshTeleportsMenu();
                refreshBookmarksMenu();
                applyCharacterRegenOverrides();
                rebuildRegenMenus();
            });
        }
    }

    private void refreshTeleportsMenu() {
        if (teleportsMenu == null) {
            return;
        }
        teleportsMenu.removeAll();

        String charName = currentCharacterName;
        ClientConfig.CharacterConfig charCfg = (charName != null) ? cfg.characters.get(charName) : null;

        JMenu optionsMenu = new JMenu("Options");

        boolean useTp = (charCfg != null && charCfg.useTeleports != null) ? charCfg.useTeleports : false;
        KeepOpenCheckBoxMenuItem useTpItem = new KeepOpenCheckBoxMenuItem("Use Teleports for Speedwalking", useTp);
        useTpItem.addActionListener(e -> {
            if (charCfg != null) {
                charCfg.useTeleports = useTpItem.isChecked();
            }
            saveConfig();
        });
        optionsMenu.add(useTpItem);

        KeepOpenCheckBoxMenuItem reliableTpItem = new KeepOpenCheckBoxMenuItem("Reliable Teleports", false);
        if (charCfg != null && charCfg.teleports != null) {
            reliableTpItem.setChecked(charCfg.teleports.reliable);
        } else {
            reliableTpItem.setEnabled(false);
        }
        reliableTpItem.addActionListener(e -> {
            if (charCfg != null && charCfg.teleports != null) {
                charCfg.teleports.reliable = reliableTpItem.isChecked();
                com.danavalerie.matrixmudrelay.core.TeleportRegistry.initialize(cfg.characters);
                saveConfig();
            }
        });
        optionsMenu.add(reliableTpItem);

        KeepOpenCheckBoxMenuItem outdoorTpItem = new KeepOpenCheckBoxMenuItem("Outdoor Use Only", false);
        if (charCfg != null && charCfg.teleports != null) {
            outdoorTpItem.setChecked(charCfg.teleports.outdoorOnly);
        } else {
            outdoorTpItem.setEnabled(false);
        }
        outdoorTpItem.addActionListener(e -> {
            if (charCfg != null && charCfg.teleports != null) {
                charCfg.teleports.outdoorOnly = outdoorTpItem.isChecked();
                com.danavalerie.matrixmudrelay.core.TeleportRegistry.initialize(cfg.characters);
                saveConfig();
            }
        });
        optionsMenu.add(outdoorTpItem);

        SpeedwalkMenuItem penaltyItem = new SpeedwalkMenuItem("Speedwalking Teleport Penalty...", false);
        if (charCfg != null && charCfg.teleports != null) {
            penaltyItem.addActionListener(e -> showSpeedwalkingPenaltyDialog(charCfg.teleports));
        } else {
            penaltyItem.setEnabled(false);
        }
        optionsMenu.add(penaltyItem);

        teleportsMenu.add(optionsMenu);

        KeepOpenMenuItem addTpItem = new KeepOpenMenuItem("Add Teleport...", false);
        addTpItem.addActionListener(e -> showAddTeleportDialog());
        teleportsMenu.add(addTpItem);

        teleportsMenu.addSeparator();

        if (charCfg != null && charCfg.teleports != null && charCfg.teleports.locations != null) {
            List<ClientConfig.TeleportLocation> locations = charCfg.teleports.locations;
            if (locations.size() > 15) {
                for (int i = 0; i < locations.size(); i += 15) {
                    int pageNum = (i / 15) + 1;
                    int end = Math.min(i + 15, locations.size());
                    JMenu pageMenu = new JMenu("Page " + pageNum);
                    if (currentBg != null && currentFg != null) {
                        updateMenuTheme(pageMenu, currentBg, currentFg);
                    }
                    for (int j = i; j < end; j++) {
                        pageMenu.add(createTeleportSubMenu(locations.get(j), charCfg));
                    }
                    teleportsMenu.add(pageMenu);
                }
            } else {
                for (ClientConfig.TeleportLocation loc : locations) {
                    teleportsMenu.add(createTeleportSubMenu(loc, charCfg));
                }
            }
        }

        if (currentBg != null && currentFg != null) {
            updateMenuTheme(teleportsMenu, currentBg, currentFg);
        }
    }

    private JMenu createTeleportSubMenu(ClientConfig.TeleportLocation loc, ClientConfig.CharacterConfig charCfg) {
        String displayName = loc.name;
        if (displayName == null || displayName.isBlank()) {
            displayName = loc.roomId;
            try {
                RoomMapService.RoomLocation roomLoc = routeMapService.lookupRoomLocation(loc.roomId);
                if (roomLoc != null) {
                    displayName = roomLoc.roomShort();
                }
            } catch (Exception ignored) {}
        }

        JMenu tpSubMenu = new JMenu(displayName);
        if (currentBg != null && currentFg != null) {
            updateMenuTheme(tpSubMenu, currentBg, currentFg);
        }

        KeepOpenMenuItem tpNow = new KeepOpenMenuItem("Teleport Now", false);
        if (currentBg != null && currentFg != null) {
            updateMenuTheme(tpNow, currentBg, currentFg);
        }
        tpNow.addActionListener(e -> submitCommand(loc.command, true));
        tpSubMenu.add(tpNow);

        tpSubMenu.addSeparator();

        KeepOpenMenuItem editTp = new KeepOpenMenuItem("Edit...", false);
        editTp.addActionListener(e -> showEditTeleportDialog(loc));
        tpSubMenu.add(editTp);

        KeepOpenMenuItem deleteTp = new KeepOpenMenuItem("Delete...", false);
        deleteTp.addActionListener(e -> {
            Object[] options = {"Yes", "No"};
            int choice = JOptionPane.showOptionDialog(
                    this,
                    "Are you sure you want to delete teleport to " + tpSubMenu.getText() + "?",
                    "Confirm Delete",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                    null,
                    options,
                    options[1]
            );
            if (choice == 0) { // Index of "Yes"
                charCfg.teleports.locations.remove(loc);
                com.danavalerie.matrixmudrelay.core.TeleportRegistry.initialize(cfg.characters);
                saveConfig();
                refreshTeleportsMenu();
            }
        });
        tpSubMenu.add(deleteTp);

        return tpSubMenu;
    }

    private void refreshBookmarksMenu() {
        if (bookmarksMenu == null) {
            return;
        }
        bookmarksMenu.removeAll();

        KeepOpenMenuItem addBmItem = new KeepOpenMenuItem("Add Bookmark...", false);
        if (currentBg != null && currentFg != null) {
            updateMenuTheme(addBmItem, currentBg, currentFg);
        }
        addBmItem.addActionListener(e -> showAddBookmarkDialog());
        bookmarksMenu.add(addBmItem);

        bookmarksMenu.addSeparator();

        addBookmarksToMenu(bookmarksMenu, cfg.bookmarks);

        if (currentBg != null && currentFg != null) {
            updateMenuTheme(bookmarksMenu, currentBg, currentFg);
        }
    }

    private void showEditBookmarkDialog(ClientConfig.Bookmark bookmark) {
        String roomName = bookmark.roomId;
        try {
            RoomMapService.RoomLocation roomLoc = routeMapService.lookupRoomLocation(bookmark.roomId);
            if (roomLoc != null) {
                roomName = roomLoc.roomShort();
            }
        } catch (Exception ignored) {}

        JPanel panel = new JPanel(new GridLayout(0, 1, 5, 5));

        JPanel roomPanel = new JPanel(new BorderLayout(5, 0));
        roomPanel.add(new JLabel("Room: " + roomName), BorderLayout.CENTER);
        JButton showOnMapBtn = new JButton("Show on Map");
        showOnMapBtn.addActionListener(e -> updateMap(bookmark.roomId));
        roomPanel.add(showOnMapBtn, BorderLayout.EAST);
        panel.add(roomPanel);

        panel.add(new JLabel("Name (optional):"));
        JTextField nameField = new JTextField(bookmark.name);
        nameField.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && nameField.isShowing()) {
                SwingUtilities.invokeLater(nameField::requestFocusInWindow);
            }
        });
        panel.add(nameField);

        int result = JOptionPane.showConfirmDialog(this, panel, "Edit Bookmark", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            String name = nameField.getText().trim();
            bookmark.name = name.isEmpty() ? null : name;
            saveConfig();
            refreshBookmarksMenu();
        }
    }

    private void showAddBookmarkDialog() {
        String roomId = currentRoomId;
        String roomName = currentRoomName;

        if (roomId == null || roomId.isBlank()) {
            JOptionPane.showMessageDialog(this, "Current room unknown. Cannot add bookmark.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JPanel panel = new JPanel(new GridLayout(0, 1, 5, 5));
        panel.add(new JLabel("Current Room: " + (roomName != null ? roomName : roomId)));
        panel.add(new JLabel("Name (optional):"));
        JTextField nameField = new JTextField();
        nameField.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && nameField.isShowing()) {
                SwingUtilities.invokeLater(nameField::requestFocusInWindow);
            }
        });
        panel.add(nameField);

        int result = JOptionPane.showConfirmDialog(this, panel, "Add Bookmark", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            String name = nameField.getText().trim();
            cfg.bookmarks.add(new ClientConfig.Bookmark(name.isEmpty() ? null : name, roomId));
            saveConfig();
            refreshBookmarksMenu();
        }
    }

    private void showEditCurrentRoomDialog() {
        String roomId = currentRoomId;
        String roomName = currentRoomName;

        if (roomId == null || roomId.isBlank()) {
            JOptionPane.showMessageDialog(this, "Current room unknown. Cannot edit room.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        RoomData room = routeMapService.getRoomData(roomId);
        if (room == null) {
            JOptionPane.showMessageDialog(this, "Current room not found in rooms.json.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (roomName == null || roomName.isBlank()) {
            try {
                RoomMapService.RoomLocation roomLoc = routeMapService.lookupRoomLocation(roomId);
                if (roomLoc != null) {
                    roomName = roomLoc.roomShort();
                }
            } catch (Exception ignored) {}
        }
        String storedRoomName = room.getRoomShort();
        String displayName = (roomName == null || roomName.isBlank()) ? roomId : roomName;
        if (storedRoomName != null && !storedRoomName.isBlank()) {
            displayName = storedRoomName;
        }

        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JPanel topPanel = new JPanel(new GridLayout(0, 1, 5, 5));

        JPanel roomPanel = new JPanel(new BorderLayout(5, 0));
        roomPanel.add(new JLabel("Room: " + displayName + " (" + roomId + ")"), BorderLayout.CENTER);
        JButton copyIdButton = new JButton("Copy ID");
        copyIdButton.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(roomId), null);
        });
        roomPanel.add(copyIdButton, BorderLayout.EAST);
        topPanel.add(roomPanel);

        topPanel.add(new JLabel("Room Name:"));
        JTextField nameField = new JTextField(storedRoomName != null ? storedRoomName : (roomName != null ? roomName : ""));
        nameField.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && nameField.isShowing()) {
                SwingUtilities.invokeLater(nameField::requestFocusInWindow);
            }
        });
        topPanel.add(nameField);

        topPanel.add(new JLabel("Map ID:"));
        JTextField mapIdField = new JTextField(String.valueOf(room.getMapId()));
        applyIntegerFilter(mapIdField);
        topPanel.add(mapIdField);

        topPanel.add(new JLabel("X Position:"));
        JTextField xposField = new JTextField(String.valueOf(room.getXpos()));
        applyIntegerFilter(xposField);
        topPanel.add(xposField);

        topPanel.add(new JLabel("Y Position:"));
        JTextField yposField = new JTextField(String.valueOf(room.getYpos()));
        applyIntegerFilter(yposField);
        topPanel.add(yposField);

        topPanel.add(new JLabel("Room Type:"));
        String[] roomTypes = {"inside", "outside", "special"};
        JComboBox<String> roomTypeBox = new JComboBox<>(roomTypes);
        String currentType = room.getRoomType();
        boolean matched = false;
        if (currentType != null && !currentType.isBlank()) {
            for (String type : roomTypes) {
                if (type.equalsIgnoreCase(currentType)) {
                    roomTypeBox.setSelectedItem(type);
                    matched = true;
                    break;
                }
            }
        }
        if (!matched) {
            roomTypeBox.setSelectedItem("outside");
        }
        topPanel.add(roomTypeBox);

        JCheckBox noTeleportCheck = new JCheckBox("No Teleport (notp)");
        noTeleportCheck.setSelected(room.hasFlag(RoomData.FLAG_NO_TELEPORT));
        topPanel.add(noTeleportCheck);

        panel.add(topPanel, BorderLayout.NORTH);

        DefaultTableModel exitsModel = new DefaultTableModel(new Object[]{"Exit", "Room ID"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return true;
            }
        };
        if (room.getExits() != null) {
            room.getExits().forEach((exit, target) -> exitsModel.addRow(new Object[]{exit, target}));
        }
        JTable exitsTable = new JTable(exitsModel);
        exitsTable.setFillsViewportHeight(true);
        exitsTable.setPreferredScrollableViewportSize(new Dimension(420, 160));
        exitsTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);

        JPanel exitsPanel = new JPanel(new BorderLayout(5, 5));
        exitsPanel.add(new JLabel("Exits:"), BorderLayout.NORTH);
        exitsPanel.add(new JScrollPane(exitsTable), BorderLayout.CENTER);

        JPanel exitButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addExitButton = new JButton("Add");
        addExitButton.addActionListener(e -> {
            exitsModel.addRow(new Object[]{"", ""});
            int row = exitsModel.getRowCount() - 1;
            exitsTable.requestFocusInWindow();
            exitsTable.changeSelection(row, 0, false, false);
            exitsTable.editCellAt(row, 0);
        });
        JButton removeExitButton = new JButton("Remove");
        removeExitButton.addActionListener(e -> {
            int row = exitsTable.getSelectedRow();
            if (row >= 0) {
                exitsModel.removeRow(row);
            } else {
                JOptionPane.showMessageDialog(this, "Select an exit to remove.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        exitButtons.add(addExitButton);
        exitButtons.add(removeExitButton);
        exitsPanel.add(exitButtons, BorderLayout.SOUTH);

        panel.add(exitsPanel, BorderLayout.CENTER);

        if (currentBg != null && currentFg != null) {
            updateComponentTree(panel, currentBg, currentFg);
        }

        int result = JOptionPane.showConfirmDialog(this, panel, "Edit Current Room", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            if (exitsTable.isEditing()) {
                exitsTable.getCellEditor().stopCellEditing();
            }
            Integer mapIdValue = parseIntegerField(mapIdField, "Map ID");
            if (mapIdValue == null) {
                return;
            }
            Integer xposValue = parseIntegerField(xposField, "X Position");
            if (xposValue == null) {
                return;
            }
            Integer yposValue = parseIntegerField(yposField, "Y Position");
            if (yposValue == null) {
                return;
            }
            String updatedName = nameField.getText().trim();
            if (updatedName.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Room name cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            Map<String, String> exits = new java.util.TreeMap<>();
            for (int i = 0; i < exitsModel.getRowCount(); i++) {
                Object exitObj = exitsModel.getValueAt(i, 0);
                Object targetObj = exitsModel.getValueAt(i, 1);
                String exit = exitObj == null ? "" : exitObj.toString().trim();
                String target = targetObj == null ? "" : targetObj.toString().trim();
                if (exit.isEmpty() && target.isEmpty()) {
                    continue;
                }
                if (exit.isEmpty() || target.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Exit name and room ID are required for each exit.", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                exits.put(exit, target);
            }
            String selectedType = (String) roomTypeBox.getSelectedItem();
            boolean noTeleport = noTeleportCheck.isSelected();
            if (!routeMapService.updateRoomDetails(roomId, selectedType, noTeleport, updatedName, exits, mapIdValue, xposValue, yposValue)) {
                JOptionPane.showMessageDialog(this, "Failed to update room details.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private static void applyIntegerFilter(JTextField field) {
        if (field.getDocument() instanceof AbstractDocument doc) {
            doc.setDocumentFilter(new IntegerDocumentFilter());
        }
    }

    private Integer parseIntegerField(JTextField field, String label) {
        String text = field.getText().trim();
        if (text.isEmpty() || "-".equals(text)) {
            JOptionPane.showMessageDialog(this, label + " must be an integer.", "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, label + " must be an integer.", "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    private static final class IntegerDocumentFilter extends DocumentFilter {
        private boolean isValid(String text) {
            return text.isEmpty() || "-".equals(text) || text.matches("-?\\d+");
        }

        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
            if (string == null) {
                return;
            }
            String current = fb.getDocument().getText(0, fb.getDocument().getLength());
            StringBuilder updated = new StringBuilder(current);
            updated.insert(offset, string);
            if (isValid(updated.toString())) {
                super.insertString(fb, offset, string, attr);
            }
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
            String current = fb.getDocument().getText(0, fb.getDocument().getLength());
            StringBuilder updated = new StringBuilder(current);
            updated.replace(offset, offset + length, text == null ? "" : text);
            if (isValid(updated.toString())) {
                super.replace(fb, offset, length, text, attrs);
            }
        }

        @Override
        public void remove(FilterBypass fb, int offset, int length) throws BadLocationException {
            String current = fb.getDocument().getText(0, fb.getDocument().getLength());
            StringBuilder updated = new StringBuilder(current);
            updated.delete(offset, offset + length);
            if (isValid(updated.toString())) {
                super.remove(fb, offset, length);
            }
        }
    }

    private void showEditTeleportDialog(ClientConfig.TeleportLocation loc) {
        String roomName = loc.roomId;
        try {
            RoomMapService.RoomLocation roomLoc = routeMapService.lookupRoomLocation(loc.roomId);
            if (roomLoc != null) {
                roomName = roomLoc.roomShort();
            }
        } catch (Exception ignored) {}

        JPanel panel = new JPanel(new GridLayout(0, 1, 5, 5));

        JPanel roomPanel = new JPanel(new BorderLayout(5, 0));
        roomPanel.add(new JLabel("Room: " + roomName), BorderLayout.CENTER);
        JButton showOnMapBtn = new JButton("Show on Map");
        showOnMapBtn.addActionListener(e -> updateMap(loc.roomId));
        roomPanel.add(showOnMapBtn, BorderLayout.EAST);
        panel.add(roomPanel);

        panel.add(new JLabel("Name (optional):"));
        JTextField nameField = new JTextField(loc.name);
        nameField.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && nameField.isShowing()) {
                SwingUtilities.invokeLater(nameField::requestFocusInWindow);
            }
        });
        panel.add(nameField);
        panel.add(new JLabel("Teleport Command:"));
        JTextField commandField = new JTextField(loc.command);
        panel.add(commandField);

        int result = JOptionPane.showConfirmDialog(this, panel, "Edit Teleport", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            String name = nameField.getText().trim();
            String command = commandField.getText().trim();
            if (command.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Command cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            loc.name = name.isEmpty() ? null : name;
            loc.command = command;
            com.danavalerie.matrixmudrelay.core.TeleportRegistry.initialize(cfg.characters);
            saveConfig();
            refreshTeleportsMenu();
        }
    }

    private void showAddTeleportDialog() {
        String roomId = currentRoomId;
        String roomName = currentRoomName;

        if (roomId == null || roomId.isBlank()) {
            JOptionPane.showMessageDialog(this, "Current room unknown. Cannot add teleport.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JPanel panel = new JPanel(new GridLayout(0, 1, 5, 5));
        panel.add(new JLabel("Current Room: " + (roomName != null ? roomName : roomId)));
        panel.add(new JLabel("Name (optional):"));
        JTextField nameField = new JTextField();
        nameField.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && nameField.isShowing()) {
                SwingUtilities.invokeLater(nameField::requestFocusInWindow);
            }
        });
        panel.add(nameField);
        panel.add(new JLabel("Teleport Command:"));
        JTextField commandField = new JTextField();
        panel.add(commandField);

        int result = JOptionPane.showConfirmDialog(this, panel, "Add Teleport", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            String name = nameField.getText().trim();
            String command = commandField.getText().trim();
            if (command.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Command cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (currentCharacterName == null) {
                JOptionPane.showMessageDialog(this, "No character logged in.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            ClientConfig.CharacterConfig charCfg = cfg.characters.computeIfAbsent(currentCharacterName, k -> new ClientConfig.CharacterConfig());
            if (charCfg.teleports == null) {
                charCfg.teleports = new ClientConfig.CharacterTeleports();
            }
            charCfg.teleports.locations.add(new ClientConfig.TeleportLocation(name.isEmpty() ? null : name, command, roomId));
            com.danavalerie.matrixmudrelay.core.TeleportRegistry.initialize(cfg.characters);
            saveConfig();
            refreshTeleportsMenu();
        }
    }

    private void showSpeedwalkingPenaltyDialog(ClientConfig.CharacterTeleports teleportsCfg) {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.add(new JLabel("Penalty:"), BorderLayout.NORTH);

        JSlider slider = new JSlider(0, 100, teleportsCfg.speedwalkingPenalty);
        slider.setMajorTickSpacing(20);
        slider.setMinorTickSpacing(5);
        slider.setPaintTicks(true);
        slider.setPaintLabels(true);

        JLabel valueLabel = new JLabel(String.valueOf(teleportsCfg.speedwalkingPenalty), SwingConstants.CENTER);
        slider.addChangeListener(e -> valueLabel.setText(String.valueOf(slider.getValue())));

        panel.add(slider, BorderLayout.CENTER);
        panel.add(valueLabel, BorderLayout.SOUTH);

        if (currentBg != null && currentFg != null) {
            updateComponentTree(panel, currentBg, currentFg);
        }

        int result = JOptionPane.showConfirmDialog(this, panel, "Speedwalking Penalty", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            teleportsCfg.speedwalkingPenalty = slider.getValue();
            com.danavalerie.matrixmudrelay.core.TeleportRegistry.initialize(cfg.characters);
            saveConfig();
        }
    }

    @Override
    public void showEditPasswordDialog(Runnable onPasswordStored) {
        String currentPassword = PasswordPreferences.getPassword();
        JDialog dialog = new JDialog(this, "Edit Password", true);
        dialog.setLayout(new BorderLayout());

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 0, 5, 0);
        panel.add(new JLabel("Enter MUD Password:"), gbc);

        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        JTextField passwordField = new JTextField(currentPassword != null ? currentPassword : "", 20);
        panel.add(passwordField, gbc);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("Cancel");
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);

        dialog.add(panel, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        Runnable onOk = () -> {
            String password = passwordField.getText();
            PasswordPreferences.setPassword(password);
            dialog.dispose();
            if (onPasswordStored != null) {
                onPasswordStored.run();
            }
        };

        Runnable onCancel = dialog::dispose;

        okButton.addActionListener(e -> onOk.run());
        cancelButton.addActionListener(e -> onCancel.run());

        passwordField.addActionListener(e -> onOk.run());

        passwordField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    onCancel.run();
                }
            }
        });

        dialog.pack();
        dialog.setLocationRelativeTo(this);
        
        passwordField.requestFocusInWindow();
        
        dialog.setVisible(true);
    }

    private void rebuildWritMenus() {
        if (writTopMenu == null) return;

        writTopMenu.removeAll();
        writRouteMenuItem = null;

        if (writRequirements.isEmpty()) {
            writTopMenu.setText("Writ");
            KeepOpenMenuItem noWritItem = new KeepOpenMenuItem("No active writ", writTopMenu, true);
            noWritItem.setEnabled(false);
            writTopMenu.add(noWritItem);
        } else {
            if (writCharacterName != null && !writCharacterName.isBlank()) {
                writTopMenu.setText("Writ:" + writCharacterName);
            } else {
                writTopMenu.setText("Writ");
            }

            if (selectedWritIndex >= writRequirements.size()) {
                setSelectedWritIndex(0);
            }

            KeepOpenRadioMenuItem.RadioMenuGroup menuGroup = new KeepOpenRadioMenuItem.RadioMenuGroup();
            for (int i = 0; i < writRequirements.size(); i++) {
                int index = i;
                String label = "Writ " + (i + 1);
                boolean selected = (i == selectedWritIndex);
                KeepOpenRadioMenuItem radioItem = new KeepOpenRadioMenuItem(label, selected, menuGroup, writTopMenu);
                radioItem.addActionListener(e -> {
                    setSelectedWritIndex(index);
                    rebuildWritMenus();
                });
                writTopMenu.add(radioItem);
            }

            writTopMenu.addSeparator();

            WritTracker.WritRequirement req = writRequirements.get(selectedWritIndex);
            DeliveryRouteMappings.RoutePlan routePlan = routeMappings != null
                    ? routeMappings.findRoutePlan(req.npc(), req.locationDisplay()).orElse(null)
                    : null;
            boolean hasRoute = routePlan != null;
            String routeTargetRoomId = routePlan != null ? routePlan.target().roomId() : null;
            boolean canWriteRoutes = Files.isWritable(routesPath);
            int originalIndex = req.originalIndex();

            JMenu itemMenu = buildWritItemMenu(originalIndex, req);
            writTopMenu.add(itemMenu);

            writTopMenu.addSeparator();

            if (req.shopRoomName() != null && !req.shopRoomName().isEmpty()) {
                String shopLabel = "Shop: " + req.shopRoomName();
                String shopRoomId = req.shopRoomId();
                KeepOpenMenuItem shopItem = buildWritMenuItem(originalIndex, WritMenuAction.SHOP,
                        shopLabel,
                        shopRoomId,
                        () -> handleShop(selectedWritIndex));
                if (shopItem instanceof SpeedwalkMenuItem swi) {
                    swi.setEstimateProvider(this::estimateSpeedwalkForMenu);
                }
                writTopMenu.add(shopItem);
            } else {
                KeepOpenMenuItem shopItem = new KeepOpenMenuItem("Shop: [no path found]", writTopMenu, true);
                shopItem.setEnabled(false);
                writTopMenu.add(shopItem);
            }

            KeepOpenMenuItem listItem = buildWritMenuItem(originalIndex, WritMenuAction.LIST_STORE,
                    "List Store",
                    null,
                    () -> submitCommand("list"));
            writTopMenu.add(listItem);

            if (req.quantity() == 2) {
                KeepOpenMenuItem buyOneItem = buildWritMenuItem(originalIndex, WritMenuAction.BUY_ONE_ITEM,
                        "Buy 1 Item",
                        null,
                        () -> handleStoreBuy(selectedWritIndex, 1));
                writTopMenu.add(buyOneItem);
                KeepOpenMenuItem buyTwoItems = buildWritMenuItem(originalIndex, WritMenuAction.BUY_TWO_ITEMS,
                        "Buy 2 Items",
                        null,
                        () -> handleStoreBuy(selectedWritIndex, 2));
                writTopMenu.add(buyTwoItems);
            } else {
                KeepOpenMenuItem buyItem = buildWritMenuItem(originalIndex, WritMenuAction.BUY_ITEM,
                        "Buy Item",
                        null,
                        () -> handleStoreBuy(selectedWritIndex, req.quantity()));
                writTopMenu.add(buyItem);
            }

            if (hasRoute) {
                KeepOpenMenuItem routeItem = buildWritMenuItem(originalIndex, WritMenuAction.ROUTE,
                        "Route",
                        routeTargetRoomId,
                        () -> handleRoute(selectedWritIndex));
                routeItem.setKeepMenuOpen(true);
                writRouteMenuItem = routeItem;
                writTopMenu.add(routeItem);
            } else if (canWriteRoutes) {
                JMenu addRouteMenu = buildWritSubMenu(originalIndex, WritMenuAction.ADD_ROUTE,
                        "Add Current Room", "Confirm",
                        () -> handleAddRoute(selectedWritIndex),
                        false);
                writTopMenu.add(addRouteMenu);
            }

            KeepOpenMenuItem deliverItem = buildWritMenuItem(originalIndex, WritMenuAction.DELIVER,
                    "Deliver",
                    null,
                    () -> submitCommand("/writ " + (req.originalIndex() + 1) + " deliver"));
            writTopMenu.add(deliverItem);

            writTopMenu.addSeparator();

            JMenu npcMenu = buildWritSubMenu(originalIndex, WritMenuAction.NPC_INFO,
                    "Deliver to: " + req.npc(), "Search",
                    () -> submitCommand("/writ " + (req.originalIndex() + 1) + " npc"), true);
            writTopMenu.add(npcMenu);

            String locationText = req.locationDisplay();
            JMenu locMenu = buildWritSubMenu(originalIndex, WritMenuAction.LOCATION_INFO,
                    "Location: " + locationText, "Search",
                    () -> submitCommand("/writ " + (req.originalIndex() + 1) + " loc"), true);
            writTopMenu.add(locMenu);
        }
        updateTheme(mapPanel.isInverted());
        menuBar.revalidate();
        menuBar.repaint();
        saveMenus();
    }

    private KeepOpenMenuItem buildWritMenuItem(int index, WritMenuAction action, String label, String speedwalkTargetRoomId, Runnable onSelect) {
        boolean visited = isWritMenuVisited(index, action);
        KeepOpenMenuItem item = (action == WritMenuAction.ROUTE || action == WritMenuAction.SHOP)
                ? new SpeedwalkMenuItem(formatWritMenuLabel(visited, label), writTopMenu, true, speedwalkTargetRoomId)
                : new KeepOpenMenuItem(formatWritMenuLabel(visited, label), writTopMenu, true);
        item.addActionListener(event -> {
            markWritMenuVisited(index, action);
            item.setText(formatWritMenuLabel(true, label));
            onSelect.run();
        });
        return item;
    }

    private JMenu buildWritItemMenu(int index, WritTracker.WritRequirement requirement) {
        String label = "Item: " + requirement.quantity() + " " + requirement.item();
        boolean visited = hasVisitedWritItemEntries(index);
        JMenu menu = new JMenu(formatWritMenuLabel(visited, label));
        menu.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent e) {
                rebuildWritItemMenu(menu, index, requirement, label);
            }

            @Override
            public void menuDeselected(MenuEvent e) {
            }

            @Override
            public void menuCanceled(MenuEvent e) {
            }
        });
        return menu;
    }

    private void rebuildWritItemMenu(JMenu menu, int index, WritTracker.WritRequirement requirement, String label) {
        menu.removeAll();
        updateWritItemMenuLabel(menu, index, label);

        try {
            List<RoomMapService.ItemSearchResult> matches = searchExactItemMatches(requirement.item());
            if (matches.isEmpty()) {
                addDisabledWritItemMenuEntry(menu, "Item not found");
                updateWritItemMenuState(index, List.of(), false);
                updateWritItemMenuLabel(menu, index, label);
                updateWritItemMenuTheme(menu);
                return;
            }
            if (matches.size() > 1) {
                addDisabledWritItemMenuEntry(menu, "ERROR: Multiple Matches");
                updateWritItemMenuState(index, List.of(), false);
                updateWritItemMenuLabel(menu, index, label);
                updateWritItemMenuTheme(menu);
                return;
            }

            String itemName = matches.get(0).itemName();
            List<RoomMapService.RoomSearchResult> results = routeMapService.searchRoomsByItemName(itemName, WRIT_ITEM_SEARCH_LIMIT + 1);
            boolean truncated = results.size() > WRIT_ITEM_SEARCH_LIMIT;
            if (truncated) {
                results = results.subList(0, WRIT_ITEM_SEARCH_LIMIT);
            }

            if (results.isEmpty()) {
                addDisabledWritItemMenuEntry(menu, "No rooms or NPCs found");
                updateWritItemMenuState(index, List.of(), false);
                updateWritItemMenuLabel(menu, index, label);
                updateWritItemMenuTheme(menu);
                return;
            }

            List<MenuPersistenceService.WritItemMenuEntry> entries = new ArrayList<>(results.size());
            for (RoomMapService.RoomSearchResult result : results) {
                boolean visited = isWritItemResultVisited(index, result);
                entries.add(new MenuPersistenceService.WritItemMenuEntry(result, visited));
            }
            updateWritItemMenuState(index, entries, truncated);

            int totalResults = results.size();
            int totalPages = (int) Math.ceil(totalResults / (double) RESULTS_MENU_PAGE_SIZE);
            int selectedPageIndex = resolveWritItemSelectedPageIndex(index, totalPages);

            KeepOpenRadioMenuItem.RadioMenuGroup menuGroup = new KeepOpenRadioMenuItem.RadioMenuGroup();
            for (int i = 0; i < totalPages; i++) {
                int pageIndex = i;
                String pageLabel = "Results " + (i + 1);
                boolean selected = (i == selectedPageIndex);
                KeepOpenRadioMenuItem radioItem = new KeepOpenRadioMenuItem(pageLabel, selected, menuGroup, menu);
                radioItem.addActionListener(e -> {
                    setWritItemSelectedPageIndex(index, pageIndex);
                    rebuildWritItemMenu(menu, index, requirement, label);
                });
                menu.add(radioItem);
            }

            menu.addSeparator();

            int start = selectedPageIndex * RESULTS_MENU_PAGE_SIZE;
            int end = Math.min(start + RESULTS_MENU_PAGE_SIZE, totalResults);
            RoomMapService.RoomSearchResult prev = start > 0 ? results.get(start - 1) : null;
            for (int resultIndex = start; resultIndex < end; resultIndex++) {
                RoomMapService.RoomSearchResult result = results.get(resultIndex);
                if (prev != null && "Shop".equals(prev.sourceInfo())
                        && result.sourceInfo() != null && result.sourceInfo().startsWith("NPC:")) {
                    menu.addSeparator();
                }
                boolean visited = isWritItemResultVisited(index, result);
                addWritItemLocationMenuEntry(menu, index, result, label, visited);
                prev = result;
            }

            if (truncated && selectedPageIndex == totalPages - 1) {
                menu.addSeparator();
                addDisabledWritItemMenuEntry(menu, "Showing first " + WRIT_ITEM_SEARCH_LIMIT + " matches.");
            }
            updateWritItemMenuLabel(menu, index, label);
        } catch (RoomMapService.MapLookupException e) {
            addDisabledWritItemMenuEntry(menu, "Error: " + e.getMessage());
            log.warn("writ item menu lookup failed err={}", e.toString());
        } catch (Exception e) {
            addDisabledWritItemMenuEntry(menu, "Error: Unable to search items.");
            log.warn("writ item menu search failed err={}", e.toString());
        }
        updateWritItemMenuTheme(menu);
    }

    private List<RoomMapService.ItemSearchResult> searchExactItemMatches(String query) throws Exception {
        List<String> terms = buildItemSearchTerms(query);
        for (String term : terms) {
            List<RoomMapService.ItemSearchResult> results = routeMapService.searchItemsByExactName(term, WRIT_ITEM_SEARCH_LIMIT + 1);
            if (!results.isEmpty()) {
                return results;
            }
        }
        return List.of();
    }

    private List<String> buildItemSearchTerms(String query) {
        String trimmed = query == null ? "" : query.trim();
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        if (!trimmed.isBlank()) {
            terms.add(trimmed);
            for (String singular : GrammarUtils.singularizePhrase(trimmed)) {
                terms.add(singular);
            }
        }
        return List.copyOf(terms);
    }

    private void addWritItemLocationMenuEntry(JMenu menu, int index, RoomMapService.RoomSearchResult result, String menuLabel,
                                              boolean visited) {
        String label = (result.sourceInfo() != null ? "[" + result.sourceInfo() + "] " : "")
                + routeMapService.getMapDisplayName(result.mapId())
                + ": "
                + result.roomShort();
        String roomId = result.roomId();
        SpeedwalkMenuItem item = new SpeedwalkMenuItem(formatWritItemMenuEntryLabel(visited, label), menu, true, roomId);
        item.addActionListener(event -> {
            markWritItemResultVisited(index, result);
            item.setText(formatWritItemMenuEntryLabel(true, label));
            updateWritItemMenuLabel(menu, index, menuLabel);
            submitCommand("/map " + roomId);
            submitCommand("/route " + roomId);
        });
        menu.add(item);
        if (currentBg != null && currentFg != null) {
            updateMenuTheme(item, currentBg, currentFg);
        }
    }

    private void addDisabledWritItemMenuEntry(JMenu menu, String text) {
        KeepOpenMenuItem item = new KeepOpenMenuItem(text, menu, true);
        item.setEnabled(false);
        menu.add(item);
        if (currentBg != null && currentFg != null) {
            updateMenuTheme(item, currentBg, currentFg);
        }
    }

    private void updateWritItemMenuTheme(JMenu menu) {
        if (currentBg != null && currentFg != null) {
            updateMenuTheme(menu, currentBg, currentFg);
        }
    }

    private JMenu buildWritSubMenu(int index, WritMenuAction action, String menuLabel, String itemLabel, Runnable onSelect, boolean keepMenuOpen) {
        boolean visited = isWritMenuVisited(index, action);
        JMenu menu = new JMenu(formatWritMenuLabel(visited, menuLabel));
        KeepOpenMenuItem item = new KeepOpenMenuItem(formatWritMenuLabel(visited, itemLabel), writTopMenu, keepMenuOpen);
        item.addActionListener(event -> {
            markWritMenuVisited(index, action);
            menu.setText(formatWritMenuLabel(true, menuLabel));
            item.setText(formatWritMenuLabel(true, itemLabel));
            onSelect.run();
        });
        menu.add(item);
        return menu;
    }

    private String formatWritMenuLabel(boolean visited, String label) {
        return (visited ? "\u2713 " : "  ") + label;
    }

    private String formatWritItemMenuEntryLabel(boolean visited, String label) {
        return (visited ? "\u2713 " : "  ") + label;
    }

    private void updateWritItemMenuLabel(JMenu menu, int index, String label) {
        menu.setText(formatWritMenuLabel(hasVisitedWritItemEntries(index), label));
    }

    private boolean hasVisitedWritItemEntries(int index) {
        MenuPersistenceService.WritItemMenuState state = writItemMenuStates.get(index);
        if (state == null || state.entries() == null) {
            return false;
        }
        for (MenuPersistenceService.WritItemMenuEntry entry : state.entries()) {
            if (entry != null && entry.visited()) {
                return true;
            }
        }
        return false;
    }

    private boolean isWritItemResultVisited(int index, RoomMapService.RoomSearchResult result) {
        if (result == null) {
            return false;
        }
        MenuPersistenceService.WritItemMenuState state = writItemMenuStates.get(index);
        if (state == null || state.entries() == null) {
            return false;
        }
        for (MenuPersistenceService.WritItemMenuEntry entry : state.entries()) {
            if (entry != null && result.equals(entry.result())) {
                return entry.visited();
            }
        }
        return false;
    }

    private void updateWritItemMenuState(int index, List<MenuPersistenceService.WritItemMenuEntry> entries, boolean truncated) {
        List<MenuPersistenceService.WritItemMenuEntry> storedEntries = (entries == null) ? null : List.copyOf(entries);
        MenuPersistenceService.WritItemMenuState existing = writItemMenuStates.get(index);
        Integer selectedPageIndex = existing != null ? existing.selectedPageIndex() : null;
        MenuPersistenceService.WritItemMenuState nextState = new MenuPersistenceService.WritItemMenuState(storedEntries, truncated, selectedPageIndex);
        if (!Objects.equals(writItemMenuStates.get(index), nextState)) {
            writItemMenuStates.put(index, nextState);
            saveMenus();
        }
    }

    private void markWritItemResultVisited(int index, RoomMapService.RoomSearchResult result) {
        if (result == null) {
            return;
        }
        MenuPersistenceService.WritItemMenuState state = writItemMenuStates.get(index);
        List<MenuPersistenceService.WritItemMenuEntry> existing = (state != null && state.entries() != null)
                ? state.entries()
                : List.of();
        List<MenuPersistenceService.WritItemMenuEntry> updated = new ArrayList<>(existing.size() + 1);
        boolean found = false;
        for (MenuPersistenceService.WritItemMenuEntry entry : existing) {
            if (!found && result.equals(entry.result())) {
                updated.add(new MenuPersistenceService.WritItemMenuEntry(entry.result(), true));
                found = true;
            } else {
                updated.add(entry);
            }
        }
        if (!found) {
            updated.add(new MenuPersistenceService.WritItemMenuEntry(result, true));
        }
        boolean truncated = state != null && state.truncated();
        Integer selectedPageIndex = state != null ? state.selectedPageIndex() : null;
        writItemMenuStates.put(index, new MenuPersistenceService.WritItemMenuState(updated, truncated, selectedPageIndex));
        markWritMenuVisited(index, WritMenuAction.ITEM_INFO);
    }

    private boolean isWritMenuVisited(int index, WritMenuAction action) {
        EnumSet<WritMenuAction> visited = writMenuVisits.get(index);
        return visited != null && visited.contains(action);
    }

    private void markWritMenuVisited(int index, WritMenuAction action) {
        writMenuVisits.computeIfAbsent(index, ignored -> EnumSet.noneOf(WritMenuAction.class)).add(action);
        saveMenus();
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
        List<String> singulars = GrammarUtils.singularizePhrase(itemName);
        if (!singulars.isEmpty()) {
            itemName = singulars.get(0);
        }
        final String lookupName = itemName;

        String searchName = routeMapService.findShopItem(currentRoomId, lookupName)
                .map(ShopItem::getShopName)
                .orElseGet(() -> {
                    for (ShopItem si : routeMapService.findShopItemsGlobally(lookupName)) {
                        if (storeInventoryTracker.findMatch(si.getShopName()).isPresent()) {
                            return si.getShopName();
                        }
                    }
                    return lookupName;
                });

        if (storeInventoryTracker.isNameListed()) {
            for (int i = 0; i < quantity; i++) {
                submitCommand("buy " + searchName);
            }
            return;
        }
        storeInventoryTracker.findMatch(searchName).ifPresentOrElse(item ->
                        buyItemById(item.id(), quantity),
                () -> outputPane.appendErrorText("Store inventory does not list \"" + lookupName + "\"."));
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

    private void handleShop(int index) {
        WritTracker.WritRequirement requirement = writRequirements.get(index);
        if (requirement.shopRoomId() != null && !requirement.shopRoomId().isEmpty()) {
            commandProcessor.speedwalkTo(requirement.shopRoomId());
        } else {
            outputPane.appendErrorText("No shop found for this writ requirement.");
        }
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

        JPanel mapContainer = new JPanel(new BorderLayout());
        mapContainer.add(mapPanel, BorderLayout.CENTER);

        JPanel southContainer = new JPanel(new BorderLayout());
        southContainer.setOpaque(false);
        southContainer.add(discworldTimePanel, BorderLayout.NORTH);

        JPanel uuLibraryContainer = new JPanel(new BorderLayout());
        uuLibraryContainer.setOpaque(false);
        uuLibraryContainer.add(uuLibraryButtonPanel, BorderLayout.CENTER);

        southContainer.add(uuLibraryContainer, BorderLayout.SOUTH);

        mapContainer.add(southContainer, BorderLayout.SOUTH);

        JSplitPane mapNotesSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, mapContainer, roomInfoContent);
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
        submitCommand(text, false);
    }

    private void submitCommand(String text, boolean fromSystem) {
        outputPane.scrollToBottom();
        outputScroll.setBorder(AutoScrollScrollPane.BLACK_BORDER);
        if (text == null) {
            historyIndex = -1;
            return;
        }
        commandProcessor.handleInput(text, fromSystem);
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

        teleportInfoPanel = new JPanel();
        teleportInfoPanel.setLayout(new javax.swing.BoxLayout(teleportInfoPanel, javax.swing.BoxLayout.Y_AXIS));
        teleportInfoPanel.setVisible(false);
        teleportCommandLabel = new JLabel();
        teleportTargetLabel = new JLabel();
        restartSpeedwalkButton = new JButton("Restart Speedwalk");
        restartSpeedwalkButton.setBackground(Color.RED);
        restartSpeedwalkButton.setForeground(Color.WHITE);
        cancelTeleportButton = new JButton("Cancel");

        teleportCommandLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        teleportTargetLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel teleportButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        teleportButtons.setOpaque(false);
        teleportButtons.setAlignmentX(Component.LEFT_ALIGNMENT);
        teleportButtons.add(restartSpeedwalkButton);
        teleportButtons.add(new JLabel("  "));
        teleportButtons.add(cancelTeleportButton);

        teleportInfoPanel.add(teleportCommandLabel);
        teleportInfoPanel.add(teleportTargetLabel);
        teleportInfoPanel.add(teleportButtons);

        restartSpeedwalkButton.addActionListener(e -> {
            submitCommand("/restart");
        });
        cancelTeleportButton.addActionListener(e -> clearTeleportQueued());

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
        bottomMudPanel.add(teleportInfoPanel, BorderLayout.NORTH);
        bottomMudPanel.add(inputPanel, BorderLayout.CENTER);
        
        panel.add(bottomMudPanel, BorderLayout.SOUTH);
        return panel;
    }

    private void installInputFocusForwarding() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(event -> {
            if (!isVisible() || !isFocused() || forwardingKey) {
                return false;
            }

            if (event.getID() == KeyEvent.KEY_PRESSED) {
                if (event.isControlDown()) {
                    if (event.getKeyCode() == KeyEvent.VK_R) {
                        submitCommand("/restart");
                        event.consume();
                        return true;
                    } else if (event.getKeyCode() == KeyEvent.VK_P) {
                        submitCommand("/pw");
                        event.consume();
                        return true;
                    }
                }
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
        mapPanel.updateMap(roomId);
    }

    @Override
    public void updateCurrentRoom(String roomId, String roomName) {
        this.currentRoomId = roomId;
        this.currentRoomName = roomName;
        SpeedwalkMenuItem.setCurrentRoomId(roomId);
        mapPanel.updateCurrentRoom(roomId);
        roomButtonBarPanel.updateRoom(roomId, roomName);
        roomNotePanel.updateRoom(roomId, roomName);
    }

    @Override
    public void updateStats(StatsHudRenderer.StatsHudData data) {
        statsPanel.updateStats(data);
    }

    @Override
    public void updateRoomEnvironment(com.danavalerie.matrixmudrelay.mud.CurrentRoomInfo.RoomEnvironment environment) {
        discworldTimePanel.updateRoomEnvironment(environment);
    }

    @Override
    public void updateContextualResults(com.danavalerie.matrixmudrelay.core.ContextualResultList results) {
        updateResultsMenu(results);
    }

    @Override
    public void updateSpeedwalkPath(List<RoomMapService.RoomLocation> path) {
        mapPanel.setSpeedwalkPath(path);
    }

    private void updateResultsMenu(com.danavalerie.matrixmudrelay.core.ContextualResultList results) {
        if (resultsTopMenu == null) return;

        boolean resultsChanged = results != null && !Objects.equals(currentResults, results);
        if (results != null) {
            currentResults = results;
        }
        if (resultsChanged) {
            resultsMenuVisits.clear();
            selectedResultsPageIndex = 0;
        }
        rebuildResultsMenu();
        if (resultsChanged) {
            persistResultsMenuState();
        }
    }

    private void rebuildResultsMenu() {
        if (resultsTopMenu == null) return;

        resultsTopMenu.removeAll();

        if (currentResults == null || currentResults.results().isEmpty()) {
            resultsTopMenu.setText("Results");
            String emptyMsg = (currentResults != null && !currentResults.emptyMessage().isBlank())
                    ? currentResults.emptyMessage()
                    : "No active results";
            KeepOpenMenuItem noResultsItem = new KeepOpenMenuItem(emptyMsg, resultsTopMenu, true);
            noResultsItem.setEnabled(false);
            resultsTopMenu.add(noResultsItem);
        } else {
            String shortTitle = currentResults.shortTitle();
            if (shortTitle != null && !shortTitle.isBlank()) {
                resultsTopMenu.setText("Results:" + shortTitle);
            } else {
                resultsTopMenu.setText("Results");
            }

            List<com.danavalerie.matrixmudrelay.core.ContextualResultList.ContextualResult> resultsList =
                    currentResults.results();
            int totalResults = resultsList.size();
            int totalPages = (int) Math.ceil(totalResults / (double) RESULTS_MENU_PAGE_SIZE);

            if (selectedResultsPageIndex >= totalPages) {
                setSelectedResultsPageIndex(0);
            }

            KeepOpenRadioMenuItem.RadioMenuGroup menuGroup = new KeepOpenRadioMenuItem.RadioMenuGroup();
            for (int i = 0; i < totalPages; i++) {
                int pageIndex = i;
                String label = "Results " + (i + 1);
                boolean selected = (i == selectedResultsPageIndex);
                KeepOpenRadioMenuItem radioItem = new KeepOpenRadioMenuItem(label, selected, menuGroup, resultsTopMenu);
                radioItem.addActionListener(e -> {
                    setSelectedResultsPageIndex(pageIndex);
                    rebuildResultsMenu();
                });
                resultsTopMenu.add(radioItem);
            }

            resultsTopMenu.addSeparator();

            String title = currentResults.title();
            if (selectedResultsPageIndex == 0 && title != null && !title.isBlank()) {
                KeepOpenMenuItem header = new KeepOpenMenuItem(title, resultsTopMenu, true);
                header.setEnabled(false);
                resultsTopMenu.add(header);
                resultsTopMenu.addSeparator();
            }

            int start = selectedResultsPageIndex * RESULTS_MENU_PAGE_SIZE;
            int end = Math.min(start + RESULTS_MENU_PAGE_SIZE, totalResults);

            for (int index = start; index < end; index++) {
                com.danavalerie.matrixmudrelay.core.ContextualResultList.ContextualResult result =
                        resultsList.get(index);
                if (result.isSeparator()) {
                    resultsTopMenu.addSeparator();
                    continue;
                }
                boolean visited = resultsMenuVisits.contains(index);
                boolean isSpeedwalkResult = result.mapCommand() != null && !result.mapCommand().isBlank();
                String speedwalkTargetRoomId = isSpeedwalkResult ? parseSpeedwalkTargetRoomId(result.command()) : null;
                KeepOpenMenuItem item = isSpeedwalkResult
                        ? new SpeedwalkMenuItem(formatResultsMenuLabel(visited, result.label()), resultsTopMenu, true, speedwalkTargetRoomId)
                        : new KeepOpenMenuItem(formatResultsMenuLabel(visited, result.label()), resultsTopMenu, true);
                int resultIndex = index;
                if (isSpeedwalkResult) {
                    item.setKeepMenuOpen(true);
                    item.addActionListener(event -> {
                        markResultVisited(resultIndex);
                        item.setText(formatResultsMenuLabel(true, result.label()));
                        submitCommand(result.mapCommand());
                        showSpeedWalkPrompt(result.command());
                    });
                } else {
                    item.addActionListener(event -> {
                        markResultVisited(resultIndex);
                        item.setText(formatResultsMenuLabel(true, result.label()));
                        submitCommand(result.command());
                    });
                }
                resultsTopMenu.add(item);
            }

            String footerText = currentResults.footer();
            boolean isLastPage = selectedResultsPageIndex == totalPages - 1;
            if (isLastPage && footerText != null && !footerText.isBlank()) {
                resultsTopMenu.addSeparator();
                KeepOpenMenuItem footer = new KeepOpenMenuItem(footerText, resultsTopMenu, true);
                footer.setEnabled(false);
                resultsTopMenu.add(footer);
            }
        }
        updateTheme(mapPanel.isInverted());
        menuBar.revalidate();
        menuBar.repaint();
    }

    private void markResultVisited(int index) {
        if (resultsMenuVisits.add(index)) {
            persistResultsMenuState();
        }
    }

    private String formatResultsMenuLabel(boolean visited, String label) {
        return (visited ? "\u2713 " : "  ") + label;
    }

    private SpeedwalkEstimate estimateSpeedwalk(String speedWalkCommand) {
        String targetRoomId = parseSpeedwalkTargetRoomId(speedWalkCommand);
        if (targetRoomId == null) {
            return null;
        }
        return estimateSpeedwalkToRoom(currentRoomId, targetRoomId, true);
    }

    private SpeedwalkEstimate estimateSpeedwalkForMenu(String startRoomId, String targetRoomId) {
        return estimateSpeedwalkToRoom(startRoomId, targetRoomId, false);
    }

    private SpeedwalkEstimate estimateSpeedwalkToRoom(String startRoomId, String targetRoomId, boolean logFailures) {
        if (targetRoomId == null || targetRoomId.isBlank()) {
            return null;
        }
        if (startRoomId == null || startRoomId.isBlank()) {
            return null;
        }
        boolean useTeleports = getUseTeleportsForPrompt();
        try {
            RoomMapService.RouteResult route = routeMapService.findRoute(
                    startRoomId,
                    targetRoomId,
                    useTeleports,
                    currentCharacterName,
                    true
            );
            return buildSpeedwalkEstimate(route);
        } catch (Exception e) {
            if (logFailures) {
                log.warn("speedwalk step estimate failed err={}", e.toString());
            }
            return null;
        }
    }

    private boolean getUseTeleportsForPrompt() {
        String charName = currentCharacterName;
        if (charName != null && cfg.characters.containsKey(charName)) {
            Boolean val = cfg.characters.get(charName).useTeleports;
            if (val != null) {
                return val;
            }
        }
        return false;
    }

    private String parseSpeedwalkTargetRoomId(String speedWalkCommand) {
        if (speedWalkCommand == null) {
            return null;
        }
        String trimmed = speedWalkCommand.trim();
        if (!trimmed.regionMatches(true, 0, "/route", 0, 6)) {
            return null;
        }
        String args = trimmed.substring(6).trim();
        if (args.isBlank()) {
            return null;
        }
        String token = args.split("\\s+")[0];
        boolean isNumeric = true;
        for (int i = 0; i < token.length(); i++) {
            if (!Character.isDigit(token.charAt(i))) {
                isNumeric = false;
                break;
            }
        }
        if (isNumeric) {
            return null;
        }
        return token;
    }

    private SpeedwalkEstimate buildSpeedwalkEstimate(RoomMapService.RouteResult route) {
        if (route == null) {
            return new SpeedwalkEstimate(0, false);
        }
        int walkCount = 0;
        boolean hasTeleport = false;
        for (RoomMapService.RouteStep step : route.steps()) {
            String exit = step.exit();
            if (exit != null && !exit.isBlank()) {
                if (exit.regionMatches(true, 0, "tp ", 0, 3)) {
                    hasTeleport = true;
                } else {
                    walkCount++;
                }
            }
        }
        return new SpeedwalkEstimate(walkCount, hasTeleport);
    }

    private void showSpeedWalkPrompt(String speedWalkCommand) {
        if (speedWalkCommand == null || speedWalkCommand.isBlank()) {
            return;
        }
        SpeedwalkEstimate estimate = estimateSpeedwalk(speedWalkCommand);
        String message = "Speed walk to this location?";
        if (estimate != null) {
            String prefix = estimate.hasTeleport() ? "Teleport+" : "";
            message = "Speed walk to this location?\n(" + prefix + estimate.steps() + " Steps)";
        }
        Object[] options = {"Cancel", "Speed Walk"};
        int choice = javax.swing.JOptionPane.showOptionDialog(
                this,
                message,
                "Speed Walk",
                javax.swing.JOptionPane.DEFAULT_OPTION,
                javax.swing.JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[1]
        );
        mapPanel.centerOnCurrentRoom();
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
            String charName = mud.getCurrentRoomSnapshot().characterName();
            SwingUtilities.invokeLater(() -> {
                writCharacterName = charName;
                updateWritMenus(requirements);
            });
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
        this.currentBg = bg;
        this.currentFg = fg;

        outputPane.updateTheme(inverted);
        chitchatPane.updateTheme(inverted);
        statsPanel.updateTheme(inverted);

        updateComponentTree(getContentPane(), bg, fg);
        roomButtonBarPanel.updateTheme(bg, fg);
        uuLibraryButtonPanel.updateTheme(bg, fg);
        discworldTimePanel.updateTheme(bg, fg);
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
        if (c == restartSpeedwalkButton) {
            restartSpeedwalkButton.setBackground(Color.RED);
            restartSpeedwalkButton.setForeground(Color.WHITE);
            return;
        }
        if (c instanceof MapPanel || c instanceof MudOutputPane || c instanceof ChitchatPane || c instanceof StatsPanel
                || c instanceof RoomButtonBarPanel || c instanceof UULibraryButtonPanel || c instanceof DiscworldTimePanel || c instanceof RoomNotePanel || c instanceof TimerPanel) {
            return;
        }
        if (c instanceof JPanel || c instanceof JSplitPane || c instanceof JScrollPane || c instanceof JViewport) {
            c.setBackground(bg);
        }
        if (c instanceof JLabel || c instanceof JTextField || c instanceof JTextArea || c instanceof JButton || c instanceof JTable || c instanceof JComboBox || c instanceof JSpinner || c instanceof JCheckBox) {
            c.setBackground(bg);
            c.setForeground(fg);
            if (c instanceof JTextArea ta) {
                ta.setCaretColor(fg);
            }
            if (c instanceof JTextField tf) {
                tf.setCaretColor(fg);
            }
        }

        if (c instanceof Container container) {
            for (Component child : container.getComponents()) {
                updateComponentTree(child, bg, fg);
            }
        }
    }

    private void updateMenuTheme(JMenuItem item, Color bg, Color fg) {
        if (item == null) return;
        item.setBackground(bg);
        item.setForeground(fg);
        item.setOpaque(true);
        if (item instanceof JMenu menu) {
            JPopupMenu popup = menu.getPopupMenu();
            if (popup != null) {
                popup.setBackground(bg);
                popup.setForeground(fg);
            }
            for (int i = 0; i < menu.getItemCount(); i++) {
                JMenuItem subItem = menu.getItem(i);
                updateMenuTheme(subItem, bg, fg);
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

    @Override
    public void setUULibraryButtonsEnabled(boolean enabled) {
        SwingUtilities.invokeLater(() -> uuLibraryButtonPanel.setButtonsEnabled(enabled));
    }

    @Override
    public void setUULibraryDistortion(boolean distortion) {
        SwingUtilities.invokeLater(() -> uuLibraryButtonPanel.setDistortion(distortion));
    }

    @Override
    public void playUULibraryReadySound() {
        com.danavalerie.matrixmudrelay.util.SoundUtils.playUULibraryReadySound();
    }

    @Override
    public void playUULibraryAlertSound() {
        com.danavalerie.matrixmudrelay.util.SoundUtils.playUULibraryAlertSound();
    }

    public static void launch(ClientConfig cfg, UiConfig uiCfg, Path configPath, DeliveryRouteMappings routes, RoomMapService routeMapService) {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception e) {
            System.err.println("Unable to set cross-platform look and feel: " + e.getMessage());
        }
        SwingUtilities.invokeLater(() -> {
            DesktopClientFrame frame = new DesktopClientFrame(cfg, uiCfg, configPath, routes, routeMapService);
            frame.setVisible(true);
        });
    }
}
