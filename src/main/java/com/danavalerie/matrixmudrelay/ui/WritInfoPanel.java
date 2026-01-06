package com.danavalerie.matrixmudrelay.ui;

import com.danavalerie.matrixmudrelay.core.WritTracker;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.util.List;

public final class WritInfoPanel extends JPanel {
    private static final Color BACKGROUND = new Color(10, 10, 15);
    private static final Color TEXT_COLOR = new Color(220, 220, 220);

    private final JLabel titleLabel = new JLabel("Writ Info");
    private final JTextArea detailsArea = new JTextArea();

    public WritInfoPanel() {
        setLayout(new BorderLayout(0, 8));
        setBackground(BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        titleLabel.setForeground(TEXT_COLOR);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        add(titleLabel, BorderLayout.NORTH);

        detailsArea.setEditable(false);
        detailsArea.setLineWrap(true);
        detailsArea.setWrapStyleWord(true);
        detailsArea.setForeground(TEXT_COLOR);
        detailsArea.setBackground(BACKGROUND);
        detailsArea.setFont(detailsArea.getFont().deriveFont(Font.PLAIN, 12f));
        detailsArea.setText("No writ requirements tracked yet.");
        add(detailsArea, BorderLayout.CENTER);
    }

    public void updateWrit(List<WritTracker.WritRequirement> requirements) {
        SwingUtilities.invokeLater(() -> {
            if (requirements == null || requirements.isEmpty()) {
                detailsArea.setText("No writ requirements tracked yet.");
                return;
            }
            StringBuilder details = new StringBuilder();
            for (int i = 0; i < requirements.size(); i++) {
                WritTracker.WritRequirement req = requirements.get(i);
                if (i > 0) {
                    details.append("\n");
                }
                details.append(i + 1).append(". ")
                        .append(req.quantity()).append(" ").append(req.item())
                        .append("\n   Deliver to ").append(req.npc())
                        .append(" at ").append(req.location());
            }
            detailsArea.setText(details.toString());
        });
    }
}
