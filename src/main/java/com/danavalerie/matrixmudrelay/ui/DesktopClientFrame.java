package com.danavalerie.matrixmudrelay.ui;

import com.danavalerie.matrixmudrelay.config.BotConfig;
import com.danavalerie.matrixmudrelay.config.ConfigLoader;
import com.danavalerie.matrixmudrelay.core.MudCommandProcessor;
import com.danavalerie.matrixmudrelay.core.StatsHudRenderer;
import com.danavalerie.matrixmudrelay.core.WritTracker;
import com.danavalerie.matrixmudrelay.mud.MudClient;
import com.danavalerie.matrixmudrelay.util.AnsiColorParser;
import com.danavalerie.matrixmudrelay.util.TranscriptLogger;

import javax.swing.JButton;
import javax.swing.JComboBox;
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
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class DesktopClientFrame extends JFrame implements MudCommandProcessor.ClientOutput {
    private final MudOutputPane outputPane = new MudOutputPane();
    private final MapPanel mapPanel = new MapPanel();
    private final StatsPanel statsPanel = new StatsPanel();
    private final WritInfoPanel writInfoPanel;
    private final ContextualResultsPanel contextualResultsPanel;
    private final JTextField inputField = new JTextField();
    private final MudCommandProcessor commandProcessor;
    private final MudClient mud;
    private final TranscriptLogger transcript;
    private final WritTracker writTracker;
    private final BotConfig cfg;
    private final Path configPath;
    private final StringBuilder writLineBuffer = new StringBuilder();
    private final AnsiColorParser writParser = new AnsiColorParser();
    private final StringBuilder writPendingEntity = new StringBuilder();
    private String writPendingTail = "";
    private Color writCurrentColor = AnsiColorParser.defaultColor();
    private boolean writCurrentBold;
    private boolean forwardingKey;

    public DesktopClientFrame(BotConfig cfg, Path configPath, TranscriptLogger transcript) {
        super("MUD Desktop Client");
        this.transcript = transcript;
        this.cfg = cfg;
        this.configPath = configPath;

        writTracker = new WritTracker();

        mud = new MudClient(
                cfg.mud,
                line -> {
                    this.transcript.logMudToClient(line);
                    bufferWritLines(normalizeWritOutput(line));
                    outputPane.appendMudText(line);
                },
                reason -> outputPane.appendSystemText("* MUD disconnected: " + reason),
                transcript
        );

        commandProcessor = new MudCommandProcessor(cfg, mud, transcript, writTracker, this);
        mud.setGmcpListener(commandProcessor);
        writInfoPanel = new WritInfoPanel(commandProcessor::handleInput);
        contextualResultsPanel = new ContextualResultsPanel(commandProcessor::handleInput);
        applyConfiguredFont();

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(1200, 800));
        setLayout(new BorderLayout());
        setJMenuBar(buildMenuBar());
        add(buildSplitLayout(), BorderLayout.CENTER);
        pack();
        setLocationRelativeTo(null);
        installInputFocusForwarding();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                inputField.requestFocusInWindow();
            }

            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });
    }

    private JMenuBar buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu viewMenu = new JMenu("View");
        JMenuItem fontItem = new JMenuItem("Output Font...");
        fontItem.addActionListener(event -> showFontDialog());
        viewMenu.add(fontItem);
        menuBar.add(viewMenu);
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

    private void applyOutputFont(Font font) {
        applyOutputFont(font, false);
    }

    private void applyOutputFont(Font font, boolean persist) {
        outputPane.setFont(font);
        inputField.setFont(font);
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
        Font base = outputPane.getFont();
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

    private JSplitPane buildSplitLayout() {
        statsPanel.setPreferredSize(new Dimension(0, 200));
        JSplitPane writSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, writInfoPanel, contextualResultsPanel);
        writSplit.setResizeWeight(0.6);
        writSplit.setDividerSize(6);
        writSplit.setBorder(null);

        JSplitPane statsSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, writSplit, statsPanel);
        statsSplit.setResizeWeight(0.8);
        statsSplit.setDividerSize(6);
        statsSplit.setBorder(null);

        JSplitPane mudSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, statsSplit, buildMudPanel());
        mudSplit.setResizeWeight(0.0);
        mudSplit.setDividerSize(6);
        mudSplit.setBorder(null);
        mudSplit.setDividerLocation(400);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, mudSplit, mapPanel);
        splitPane.setResizeWeight(0.7);
        splitPane.setDividerSize(6);
        splitPane.setBorder(null);
        return splitPane;
    }

    private JSplitPane buildMudPanel() {
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
        };
        inputField.addActionListener(e -> sendAction.run());
        sendButton.addActionListener(e -> sendAction.run());

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, outputScroll, inputPanel);
        splitPane.setResizeWeight(1.0);
        splitPane.setDividerSize(6);
        splitPane.setBorder(null);
        return splitPane;
    }

    private void installInputFocusForwarding() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(event -> {
            if (!isVisible() || inputField.isFocusOwner() || forwardingKey) {
                return false;
            }
            if (event.getID() != KeyEvent.KEY_TYPED) {
                return false;
            }
            char keyChar = event.getKeyChar();
            if (keyChar == KeyEvent.CHAR_UNDEFINED || Character.isISOControl(keyChar)) {
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
    public void updateMap(String roomId) {
        mapPanel.updateMap(roomId);
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
            writInfoPanel.updateWrit(writTracker.getRequirements());
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

    public static void launch(BotConfig cfg, Path configPath, TranscriptLogger transcript) {
        SwingUtilities.invokeLater(() -> {
            DesktopClientFrame frame = new DesktopClientFrame(cfg, configPath, transcript);
            frame.setVisible(true);
        });
    }
}
