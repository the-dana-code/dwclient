package com.danavalerie.matrixmudrelay.ui;

import com.danavalerie.matrixmudrelay.config.BotConfig;
import com.danavalerie.matrixmudrelay.config.ConfigLoader;
import com.danavalerie.matrixmudrelay.config.DeliveryRouteMappings;
import com.danavalerie.matrixmudrelay.core.MudCommandProcessor;
import com.danavalerie.matrixmudrelay.core.RoomMapService;
import com.danavalerie.matrixmudrelay.core.StoreInventoryTracker;
import com.danavalerie.matrixmudrelay.core.StatsHudRenderer;
import com.danavalerie.matrixmudrelay.core.WritTracker;
import com.danavalerie.matrixmudrelay.mud.MudClient;
import com.danavalerie.matrixmudrelay.util.AnsiColorParser;
import com.danavalerie.matrixmudrelay.util.ThreadUtils;
import com.danavalerie.matrixmudrelay.util.TranscriptLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
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
import java.awt.BorderLayout;
import java.awt.Color;
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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class DesktopClientFrame extends JFrame implements MudCommandProcessor.ClientOutput {
    private static final Logger log = LoggerFactory.getLogger(DesktopClientFrame.class);
    private final MudOutputPane outputPane = new MudOutputPane();
    private final ChitchatPane chitchatPane = new ChitchatPane();
    private final MapPanel mapPanel;
    private final StatsPanel statsPanel = new StatsPanel();
    private final ContextualResultsPanel contextualResultsPanel;
    private final JTextField inputField = new JTextField();
    private final MudCommandProcessor commandProcessor;
    private final MudClient mud;
    private final TranscriptLogger transcript;
    private DeliveryRouteMappings routeMappings;
    private final WritTracker writTracker;
    private final StoreInventoryTracker storeInventoryTracker;
    private final BotConfig cfg;
    private final Path configPath;
    private final Path routesPath;
    private final RoomMapService routeMapService = new RoomMapService("database.db");
    private final UiFontManager fontManager;
    private final JMenuBar menuBar = new JMenuBar();
    private final List<WritTracker.WritRequirement> writRequirements = new ArrayList<>();
    private final List<JMenu> writMenus = new ArrayList<>();
    private final StringBuilder writLineBuffer = new StringBuilder();
    private static final QuickLink[] QUICK_LINKS = {
            new QuickLink("Mended Drum", 1, 718, 802),
            new QuickLink("Bologna shop", 1, 648, 897),
            new QuickLink("Jobs Market", 1, 699, 905),
            new QuickLink("Wisdom Buff", 38, 838, 265),
            new QuickLink("Intelligence Buff", 25, 290, 200)
    };
    private final AnsiColorParser writParser = new AnsiColorParser();
    private final StringBuilder writPendingEntity = new StringBuilder();
    private String writPendingTail = "";
    private final StringBuilder storeLineBuffer = new StringBuilder();
    private Color writCurrentColor = AnsiColorParser.defaultColor();
    private boolean writCurrentBold;
    private boolean forwardingKey;
    private final List<String> inputHistory = new ArrayList<>();
    private int historyIndex = -1;

    public DesktopClientFrame(BotConfig cfg, Path configPath, DeliveryRouteMappings routeMappings, TranscriptLogger transcript) {
        super("MUD Desktop Client");
        this.transcript = transcript;
        this.cfg = cfg;
        this.configPath = configPath;
        this.routesPath = configPath.resolveSibling("delivery-routes.json");
        this.routeMappings = routeMappings;
        this.mapPanel = new MapPanel(resolveMapZoomPercent(), this::persistMapZoomConfig);

        writTracker = new WritTracker();
        storeInventoryTracker = new StoreInventoryTracker();

        mud = new MudClient(
                cfg.mud,
                line -> {
                    this.transcript.logMudToClient(line);
                    String normalized = normalizeWritOutput(line);
                    bufferWritLines(normalized);
                    bufferStoreInventoryLines(normalized);
                    outputPane.appendMudText(line);
                },
                reason -> outputPane.appendSystemText("* MUD disconnected: " + reason),
                transcript
        );
        outputPane.setChitchatListener((text, color) -> chitchatPane.appendChitchatLine(text, color));

        commandProcessor = new MudCommandProcessor(cfg, mud, transcript, writTracker, storeInventoryTracker, this);
        mud.setGmcpListener(commandProcessor);
        contextualResultsPanel = new ContextualResultsPanel(commandProcessor::handleInput);
        fontManager = new UiFontManager(this, outputPane.getFont());
        fontManager.registerListener(contextualResultsPanel);
        fontManager.registerListener(statsPanel);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(1200, 800));
        setLayout(new BorderLayout());
        setJMenuBar(buildMenuBar());
        add(buildSplitLayout(), BorderLayout.CENTER);
        applyConfiguredFont();
        pack();
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setLocationRelativeTo(null);
        installInputFocusForwarding();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                inputField.requestFocusInWindow();
                commandProcessor.handleInput("mm connect");
            }

            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });
    }

    private JMenuBar buildMenuBar() {
        JMenu viewMenu = new JMenu("View");
        JMenuItem fontItem = new JMenuItem("Output Font...");
        fontItem.addActionListener(event -> showFontDialog());
        viewMenu.add(fontItem);
        menuBar.add(viewMenu);

        JMenu quickLinksMenu = new JMenu("Navigate");
        for (QuickLink link : QUICK_LINKS) {
            JMenuItem linkItem = new JMenuItem(link.name());
            linkItem.addActionListener(event -> commandProcessor.speedwalkTo(link.mapId(), link.x(), link.y()));
            quickLinksMenu.add(linkItem);
        }
        menuBar.add(quickLinksMenu);
        return menuBar;
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
                List.of(location.mapId(), location.xpos(), location.ypos())
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
        try {
            ConfigLoader.save(configPath, cfg);
        } catch (IOException e) {
            outputPane.appendSystemText("* Unable to save config: " + e.getMessage());
        }
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
        try {
            ConfigLoader.save(configPath, cfg);
        } catch (IOException e) {
            outputPane.appendSystemText("* Unable to save config: " + e.getMessage());
        }
    }

    private record QuickLink(String name, int mapId, int x, int y) {}

    private void updateWritMenus(List<WritTracker.WritRequirement> requirements) {
        writRequirements.clear();
        if (requirements != null) {
            writRequirements.addAll(requirements);
        }
        rebuildWritMenus();
    }

    private void rebuildWritMenus() {
        for (JMenu menu : writMenus) {
            menuBar.remove(menu);
        }
        writMenus.clear();
        if (writRequirements.isEmpty()) {
            menuBar.revalidate();
            menuBar.repaint();
            return;
        }
        for (int i = 0; i < writRequirements.size(); i++) {
            WritTracker.WritRequirement req = writRequirements.get(i);
            boolean hasRoute = routeMappings.findRoute(req.npc(), req.locationDisplay()).isPresent();
            int index = i;
            JMenu writMenu = new JMenu("Writ" + (i + 1));

            JMenuItem itemInfo = new JMenuItem("Item: " + req.quantity() + " " + req.item());
            itemInfo.addActionListener(event -> commandProcessor.handleInput("mm item exact " + req.item()));
            writMenu.add(itemInfo);

            JMenuItem npcInfo = new JMenuItem("Deliver to: " + req.npc());
            npcInfo.addActionListener(event -> commandProcessor.handleInput("mm writ " + (index + 1) + " npc"));
            writMenu.add(npcInfo);

            String locationText = req.locationName()
                    + (req.locationSuffix().isBlank() ? "" : " " + req.locationSuffix());
            JMenuItem locInfo = new JMenuItem("Location: " + locationText);
            locInfo.addActionListener(event -> commandProcessor.handleInput("mm writ " + (index + 1) + " loc"));
            writMenu.add(locInfo);
            writMenu.addSeparator();

            JMenuItem listItem = new JMenuItem("List Store");
            listItem.addActionListener(event -> commandProcessor.handleInput("list"));
            writMenu.add(listItem);

            JMenuItem buyItem = new JMenuItem("Buy Item");
            buyItem.addActionListener(event -> handleStoreBuy(index));
            writMenu.add(buyItem);

            if (!hasRoute) {
                JMenuItem addRouteItem = new JMenuItem("Add Current Room");
                addRouteItem.addActionListener(event -> handleAddRoute(index));
                writMenu.add(addRouteItem);
            }

            JMenuItem deliverItem = new JMenuItem("Deliver");
            deliverItem.addActionListener(event -> {
                routeMappings.findRoute(req.npc(), req.locationDisplay()).ifPresentOrElse(target -> {
                    commandProcessor.speedwalkToThenCommand(
                            target.mapId(),
                            target.x(),
                            target.y(),
                            "mm writ " + (index + 1) + " deliver"
                    );
                }, () -> commandProcessor.handleInput("mm writ " + (index + 1) + " deliver"));
            });
            writMenu.add(deliverItem);
            writMenus.add(writMenu);
            menuBar.add(writMenu);
        }
        menuBar.revalidate();
        menuBar.repaint();
    }

    private void handleStoreBuy(int index) {
        if (!storeInventoryTracker.hasInventory()) {
            outputPane.appendErrorText("Store inventory not cached yet. Use List Store first.");
            return;
        }
        WritTracker.WritRequirement requirement = writRequirements.get(index);
        if (storeInventoryTracker.isNameListed()) {
            commandProcessor.handleInput("buy " + requirement.quantity() + " " + requirement.item());
            return;
        }
        storeInventoryTracker.findMatch(requirement.item()).ifPresentOrElse(item ->
                        commandProcessor.handleInput("buy " + item.id()),
                () -> outputPane.appendErrorText("Store inventory does not list \"" + requirement.item() + "\"."));
    }

    private void handleRoute(int index) {
        WritTracker.WritRequirement requirement = writRequirements.get(index);
        routeMappings.findRoute(requirement.npc(), requirement.locationDisplay()).ifPresentOrElse(target -> {
            commandProcessor.speedwalkTo(target.mapId(), target.x(), target.y());
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
        statsPanel.setPreferredSize(new Dimension(0, 200));
        JSplitPane statsSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, contextualResultsPanel, statsPanel);
        statsSplit.setContinuousLayout(true);
        statsSplit.setResizeWeight(0.8);
        statsSplit.setDividerSize(6);
        statsSplit.setBorder(null);

        JSplitPane mudSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, statsSplit, buildMudPanel());
        mudSplit.setContinuousLayout(true);
        mudSplit.setResizeWeight(0.0);
        mudSplit.setDividerSize(6);
        mudSplit.setBorder(null);
        mudSplit.setDividerLocation(400);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, mudSplit, mapPanel);
        splitPane.setContinuousLayout(true);
        splitPane.setResizeWeight(0.7);
        splitPane.setDividerSize(6);
        splitPane.setBorder(null);
        return splitPane;
    }

    private JComponent buildMudPanel() {
        JScrollPane chitchatScroll = new JScrollPane(chitchatPane);
        chitchatScroll.setBorder(null);
        JScrollPane outputScroll = new JScrollPane(outputPane);
        outputScroll.setBorder(null);

        JPanel inputPanel = new JPanel(new BorderLayout(6, 6));
        JButton sendButton = new JButton("Send");
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        Runnable sendAction = () -> {
            String text = inputField.getText();
            inputField.setText("");
            commandProcessor.handleInput(text);
            if (!text.isBlank()) {
                inputHistory.add(0, text);
            }
            historyIndex = -1;
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

        JSplitPane outputSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, chitchatScroll, outputScroll);
        outputSplit.setContinuousLayout(true);
        outputSplit.setResizeWeight(0.2);
        outputSplit.setDividerSize(6);
        outputSplit.setBorder(null);

        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.add(outputSplit, BorderLayout.CENTER);
        panel.add(inputPanel, BorderLayout.SOUTH);
        return panel;
    }

    private void installInputFocusForwarding() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(event -> {
            if (!isVisible() || inputField.isFocusOwner() || forwardingKey) {
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
                if (keyCode == KeyEvent.VK_UP || keyCode == KeyEvent.VK_DOWN) {
                    shouldForward = true;
                }
            }

            if (!shouldForward) {
                return false;
            }
            forwardingKey = true;
            try {
                inputField.requestFocusInWindow();
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
    public void updateStats(StatsHudRenderer.StatsHudData data) {
        statsPanel.updateStats(data);
    }

    @Override
    public void updateContextualResults(com.danavalerie.matrixmudrelay.core.ContextualResultList results) {
        contextualResultsPanel.updateResults(results);
    }

    private void shutdown() {
        commandProcessor.shutdown();
        mapPanel.shutdown();
        try {
            mud.disconnect("shutdown", null);
        } catch (Exception ignored) {
        }
        try {
            transcript.close();
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

    public static void launch(BotConfig cfg, Path configPath, DeliveryRouteMappings routes, TranscriptLogger transcript) {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception e) {
            System.err.println("Unable to set cross-platform look and feel: " + e.getMessage());
        }
        SwingUtilities.invokeLater(() -> {
            DesktopClientFrame frame = new DesktopClientFrame(cfg, configPath, routes, transcript);
            frame.setVisible(true);
        });
    }
}
