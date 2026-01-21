package com.danavalerie.matrixmudrelay.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Map;
import java.util.function.Consumer;
import com.danavalerie.matrixmudrelay.core.UULibraryService;

public class UULibraryButtonPanel extends JPanel {
    private final Consumer<String> commandSubmitter;

    private final JComboBox<String> targetCombo;
    private final JLabel stepButton;
    private Color themeBg = null;
    private Color themeFg = null;

    private static final Map<String, Point> BUTTON_COORDINATES = Map.ofEntries(
            Map.entry("Exit", new Point(1, 5)),
            Map.entry("1", new Point(2, 6)),
            Map.entry("2", new Point(3, 5)),
            Map.entry("3", new Point(4, 2)),
            Map.entry("4", new Point(5, 7)),
            Map.entry("5", new Point(6, 3)),
            Map.entry("6", new Point(6, 6)),
            Map.entry("7", new Point(7, 1)),
            Map.entry("8", new Point(10, 8)),
            Map.entry("9", new Point(11, 5)),
            Map.entry("10", new Point(15, 3)),
            Map.entry("11", new Point(16, 4)),
            Map.entry("12", new Point(17, 7)),
            Map.entry("13", new Point(19, 1)),
            Map.entry("14", new Point(22, 6)),
            Map.entry("15", new Point(22, 8)),
            Map.entry("16", new Point(23, 3)),
            Map.entry("Gap", new Point(12, 6))
    );

    private static final Insets BUTTON_MARGIN = new Insets(2, 50, 2, 50);

    public UULibraryButtonPanel(Consumer<String> commandSubmitter) {
        super(new FlowLayout(FlowLayout.CENTER, 5, 2));
        this.commandSubmitter = commandSubmitter;
        this.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

        add(new JLabel("Target:"));

        String[] targets = new String[18];
        targets[0] = "Exit";
        for (int i = 1; i <= 16; i++) {
            targets[i] = String.valueOf(i);
        }
        targets[17] = "Gap";

        targetCombo = new JComboBox<>(targets);
        targetCombo.setFocusable(false);
        add(targetCombo);

        stepButton = new JLabel("Step", SwingConstants.CENTER);
        stepButton.setFocusable(false);
        stepButton.setOpaque(true);
        stepButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (stepButton.isEnabled()) {
                    String label = (String) targetCombo.getSelectedItem();
                    Point target = BUTTON_COORDINATES.get(label);
                    if (target != null) {
                        setButtonsEnabled(false);
                        new Thread(() -> {
                            String cmd = UULibraryService.getInstance().getNextStepCommand(target.x, target.y);
                            SwingUtilities.invokeLater(() -> {
                                if (cmd != null) {
                                    commandSubmitter.accept(cmd);
                                    commandSubmitter.accept("look distortion");
                                } else {
                                    setButtonsEnabled(true);
                                }
                            });
                        }, "UU-AStar-Thread").start();
                    }
                }
            }
        });
        add(stepButton);

        setVisible(false);
    }

    public void setButtonsEnabled(boolean enabled) {
        stepButton.setEnabled(enabled);
        targetCombo.setEnabled(enabled);
        updateStepButtonColor();
    }

    private void updateStepButtonColor() {
        if (stepButton.isEnabled()) {
            stepButton.setBackground(Color.RED);
            stepButton.setForeground(Color.WHITE);
        } else {
            stepButton.setBackground(themeBg);
            stepButton.setForeground(themeFg);
        }
    }

    public void updateTheme(Color bg, Color fg) {
        this.themeBg = bg;
        this.themeFg = fg;
        this.setBackground(bg);
        this.setForeground(fg);
        this.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(fg),
                BorderFactory.createEmptyBorder(2, 2, 2, 2)
        ));
        for (Component c : getComponents()) {
            if (c instanceof JComboBox) {
                c.setBackground(bg);
                c.setForeground(fg);
                ((JComboBox<?>) c).setBorder(BorderFactory.createLineBorder(fg));
            } else if (c == stepButton) {
                stepButton.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(fg),
                        BorderFactory.createEmptyBorder(BUTTON_MARGIN.top, BUTTON_MARGIN.left, BUTTON_MARGIN.bottom, BUTTON_MARGIN.right)
                ));
            } else if (c instanceof JLabel) {
                c.setForeground(fg);
            }
        }
        updateStepButtonColor();
    }
}
