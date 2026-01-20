package com.danavalerie.matrixmudrelay.ui;

import javax.swing.*;
import java.awt.*;
import java.util.Map;
import java.util.function.Consumer;

public class UULibraryButtonPanel extends JPanel {
    private final Consumer<String> commandSubmitter;

    private static final Map<String, Point> BUTTON_COORDINATES = Map.ofEntries(
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
            Map.entry("G", new Point(12, 6)),
            Map.entry("X", new Point(1, 5))
    );

    public UULibraryButtonPanel(Consumer<String> commandSubmitter) {
        super(new GridLayout(3, 6, 2, 2));
        this.commandSubmitter = commandSubmitter;
        this.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        
        for (int i = 1; i <= 16; i++) {
            addButton(String.valueOf(i));
        }
        addButton("G");
        addButton("X");
        
        setVisible(false);
    }

    private void addButton(String label) {
        JButton btn = new JButton(label);
        btn.setMargin(new Insets(2, 2, 2, 2));
        btn.setFocusable(false);
        btn.addActionListener(e -> {
            Point target = BUTTON_COORDINATES.get(label);
            if (target != null) {
                String cmd = com.danavalerie.matrixmudrelay.core.UULibraryService.getInstance()
                        .getNextStepCommand(target.x, target.y);
                if (cmd != null) {
                    commandSubmitter.accept(cmd);
                }
            }
        });
        add(btn);
    }

    public void updateTheme(Color bg, Color fg) {
        this.setBackground(bg);
        this.setForeground(fg);
        this.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(fg),
                BorderFactory.createEmptyBorder(2, 2, 2, 2)
        ));
        for (Component c : getComponents()) {
            if (c instanceof JButton) {
                c.setBackground(bg);
                c.setForeground(fg);
                ((JButton) c).setBorder(BorderFactory.createLineBorder(fg));
            }
        }
    }
}
