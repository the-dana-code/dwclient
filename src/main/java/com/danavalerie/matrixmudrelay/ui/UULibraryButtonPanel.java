package com.danavalerie.matrixmudrelay.ui;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

public class UULibraryButtonPanel extends JPanel {
    private final Consumer<String> commandSubmitter;

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
        btn.addActionListener(e -> commandSubmitter.accept(label));
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
