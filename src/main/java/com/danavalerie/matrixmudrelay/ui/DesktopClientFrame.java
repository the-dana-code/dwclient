package com.danavalerie.matrixmudrelay.ui;

import com.danavalerie.matrixmudrelay.config.BotConfig;
import com.danavalerie.matrixmudrelay.core.MudCommandProcessor;
import com.danavalerie.matrixmudrelay.core.StatsHudRenderer;
import com.danavalerie.matrixmudrelay.core.WritTracker;
import com.danavalerie.matrixmudrelay.mud.MudClient;
import com.danavalerie.matrixmudrelay.util.AnsiColorParser;
import com.danavalerie.matrixmudrelay.util.TranscriptLogger;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

public final class DesktopClientFrame extends JFrame implements MudCommandProcessor.ClientOutput {
    private final MudOutputPane outputPane = new MudOutputPane();
    private final MapPanel mapPanel = new MapPanel();
    private final StatsPanel statsPanel = new StatsPanel();
    private final WritInfoPanel writInfoPanel = new WritInfoPanel();
    private final JTextField inputField = new JTextField();
    private final MudCommandProcessor commandProcessor;
    private final MudClient mud;
    private final TranscriptLogger transcript;
    private final WritTracker writTracker;
    private final StringBuilder writLineBuffer = new StringBuilder();
    private final AnsiColorParser writParser = new AnsiColorParser();
    private final StringBuilder writPendingEntity = new StringBuilder();
    private String writPendingTail = "";
    private Color writCurrentColor = AnsiColorParser.defaultColor();
    private boolean writCurrentBold;
    private boolean forwardingKey;

    public DesktopClientFrame(BotConfig cfg, TranscriptLogger transcript) {
        super("MUD Desktop Client");
        this.transcript = transcript;

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

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setPreferredSize(new Dimension(1200, 800));
        setLayout(new BorderLayout());
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

    private JSplitPane buildSplitLayout() {
        statsPanel.setPreferredSize(new Dimension(0, 200));
        JSplitPane statsSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, writInfoPanel, statsPanel);
        statsSplit.setResizeWeight(1.0);
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
        boolean ingested = false;
        while (true) {
            int newline = writLineBuffer.indexOf("\n", start);
            if (newline == -1) {
                break;
            }
            String line = writLineBuffer.substring(start, newline);
            writTracker.ingest(line);
            ingested = true;
            start = newline + 1;
        }
        if (start > 0) {
            writLineBuffer.delete(0, start);
        }
        if (ingested) {
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

    public static void launch(BotConfig cfg, TranscriptLogger transcript) {
        SwingUtilities.invokeLater(() -> {
            DesktopClientFrame frame = new DesktopClientFrame(cfg, transcript);
            frame.setVisible(true);
        });
    }
}
