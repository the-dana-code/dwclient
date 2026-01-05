package com.danavalerie.matrixmudrelay.ui;

import com.danavalerie.matrixmudrelay.core.StatsHudRenderer;

import javax.swing.BorderFactory;
import javax.swing.JProgressBar;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.text.NumberFormat;
import java.util.Locale;

public final class StatsPanel extends JPanel {
    private static final Color BACKGROUND = new Color(10, 10, 15);
    private static final Color TEXT_COLOR = new Color(220, 220, 220);
    private static final Color BAR_BG = new Color(26, 26, 26);
    private static final Color HP_COLOR = new Color(0, 150, 0);
    private static final Color GP_COLOR = new Color(0, 60, 160);
    private static final Color BURDEN_COLOR = new Color(0, 130, 0);
    private static final Color XP_COLOR = new Color(200, 110, 0);
    private static final int XP_CAP = 1_000_000;
    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getIntegerInstance(Locale.US);

    private final JLabel nameLabel = new JLabel("Character: --");
    private final JProgressBar hpBar = buildBar(HP_COLOR);
    private final JProgressBar gpBar = buildBar(GP_COLOR);
    private final JProgressBar burdenBar = buildBar(BURDEN_COLOR);
    private final JProgressBar xpBar = buildBar(XP_COLOR);

    public StatsPanel() {
        setLayout(new BorderLayout(0, 8));
        setBackground(BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        nameLabel.setForeground(TEXT_COLOR);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 14f));
        add(nameLabel, BorderLayout.NORTH);

        JPanel bars = new JPanel(new GridLayout(4, 1, 0, 8));
        bars.setBackground(BACKGROUND);
        bars.add(hpBar);
        bars.add(gpBar);
        bars.add(burdenBar);
        bars.add(xpBar);
        add(bars, BorderLayout.CENTER);

        setUnavailable();
    }

    public void updateStats(StatsHudRenderer.StatsHudData data) {
        SwingUtilities.invokeLater(() -> {
            if (data == null) {
                setUnavailable();
                return;
            }
            nameLabel.setText("Character: " + data.name());
            updateBar(hpBar, data.hp(), data.maxHp(),
                    "HP " + format(data.hp()) + " / " + format(data.maxHp()));
            updateBar(gpBar, data.gp(), data.maxGp(),
                    "GP " + format(data.gp()) + " / " + format(data.maxGp()));

            int burdenValue = clamp(data.burden(), 0, 100);
            updateBar(burdenBar, burdenValue, 100, "Burden " + burdenValue + "%");

            long xpValue = Math.max(0, data.xp());
            int xpBarValue = (int) Math.min(Integer.MAX_VALUE, xpValue);
            updateBar(xpBar, Math.min(xpBarValue, XP_CAP), XP_CAP, "XP " + format(xpValue));
        });
    }

    private void setUnavailable() {
        nameLabel.setText("Character: --");
        updateBar(hpBar, 0, 1, "HP --");
        updateBar(gpBar, 0, 1, "GP --");
        updateBar(burdenBar, 0, 100, "Burden --");
        updateBar(xpBar, 0, XP_CAP, "XP --");
    }

    private static JProgressBar buildBar(Color color) {
        JProgressBar bar = new JProgressBar();
        bar.setStringPainted(true);
        bar.setForeground(color);
        bar.setBackground(BAR_BG);
        bar.setBorderPainted(true);
        return bar;
    }

    private static void updateBar(JProgressBar bar, int value, int max, String label) {
        int safeMax = Math.max(1, max);
        int safeValue = Math.max(0, Math.min(value, safeMax));
        bar.setMaximum(safeMax);
        bar.setValue(safeValue);
        bar.setString(label);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String format(long value) {
        return NUMBER_FORMAT.format(value);
    }
}
