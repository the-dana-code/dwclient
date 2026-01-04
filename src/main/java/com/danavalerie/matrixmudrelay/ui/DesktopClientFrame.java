package com.danavalerie.matrixmudrelay.ui;

import com.danavalerie.matrixmudrelay.config.BotConfig;
import com.danavalerie.matrixmudrelay.core.MudCommandProcessor;
import com.danavalerie.matrixmudrelay.core.WritTracker;
import com.danavalerie.matrixmudrelay.mud.MudClient;
import com.danavalerie.matrixmudrelay.util.TranscriptLogger;

import javax.swing.JButton;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public final class DesktopClientFrame extends JFrame implements MudCommandProcessor.ClientOutput {
    private final MudOutputPane outputPane = new MudOutputPane();
    private final MapPanel mapPanel = new MapPanel();
    private final JTextField inputField = new JTextField();
    private final MudCommandProcessor commandProcessor;
    private final MudClient mud;
    private final TranscriptLogger transcript;
    private boolean forwardingKey;

    public DesktopClientFrame(BotConfig cfg, TranscriptLogger transcript) {
        super("MUD Desktop Client");
        this.transcript = transcript;

        mud = new MudClient(
                cfg.mud,
                line -> {
                    this.transcript.logMudToClient(line);
                    outputPane.appendMudText(line);
                },
                reason -> outputPane.appendSystemText("* MUD disconnected: " + reason),
                transcript
        );

        commandProcessor = new MudCommandProcessor(cfg, mud, transcript, new WritTracker(), this);
        mud.setGmcpListener(commandProcessor);

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setPreferredSize(new Dimension(1200, 800));
        setLayout(new BorderLayout());
        add(buildDesktop(), BorderLayout.CENTER);
        pack();
        setLocationRelativeTo(null);
        installInputFocusForwarding();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });
    }

    private JDesktopPane buildDesktop() {
        JDesktopPane desktop = new JDesktopPane();

        JInternalFrame mudFrame = new JInternalFrame("MUD Console", false, false, false, true);
        mudFrame.setContentPane(buildMudPanel());
        mudFrame.setBounds(10, 10, 760, 740);
        mudFrame.setVisible(true);

        JInternalFrame mapFrame = new JInternalFrame("Map", false, false, false, true);
        mapFrame.setContentPane(mapPanel);
        mapFrame.setBounds(780, 10, 400, 740);
        mapFrame.setVisible(true);

        desktop.add(mudFrame);
        desktop.add(mapFrame);

        return desktop;
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

    public static void launch(BotConfig cfg, TranscriptLogger transcript) {
        SwingUtilities.invokeLater(() -> {
            DesktopClientFrame frame = new DesktopClientFrame(cfg, transcript);
            frame.setVisible(true);
        });
    }
}
