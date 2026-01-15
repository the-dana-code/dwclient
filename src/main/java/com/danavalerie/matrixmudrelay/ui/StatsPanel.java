package com.danavalerie.matrixmudrelay.ui;

import com.danavalerie.matrixmudrelay.core.StatsHudRenderer;
import com.danavalerie.matrixmudrelay.util.ThreadUtils;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JProgressBar;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class StatsPanel extends JPanel implements FontChangeListener {
    private static final Color BACKGROUND = new Color(10, 10, 15);
    private static final Color TEXT_COLOR = new Color(220, 220, 220);
    private static final Color BAR_BG = new Color(26, 26, 26);
    private static final Color HP_COLOR = new Color(0, 150, 0);
    private static final Color GP_COLOR = new Color(0, 60, 160);
    private static final Color BURDEN_COLOR = new Color(0, 130, 0);
    private static final Color XP_COLOR = new Color(200, 110, 0);
    private static final int XP_CAP = 1_000_000;
    private static final int BAR_MIN_WIDTH = 80;
    private static final int BAR_PREFERRED_WIDTH = 160;
    private static final int BAR_MAX_WIDTH = 220;
    private static final int LABEL_WIDTH = 60;
    private static final int NAME_MIN_WIDTH = 140;
    private static final int NAME_MAX_WIDTH = 260;
    private static final int GP_RATE_SAMPLE_SIZE = 10;
    private static final int GP_TIMER_DEFAULT_INTERVAL_MS = 1000;
    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getIntegerInstance(Locale.US);

    private final JLabel nameLabel = new JLabel("Character: --");
    private final JLabel hpLabel = new JLabel("HP");
    private final JLabel gpLabel = new JLabel("GP");
    private final JLabel burdenLabel = new JLabel("Burden");
    private final JLabel xpLabel = new JLabel("XP");
    private final JProgressBar hpBar = buildBar(HP_COLOR);
    private final JProgressBar gpBar = buildBar(GP_COLOR);
    private final JProgressBar burdenBar = buildBar(BURDEN_COLOR);
    private final JProgressBar xpBar = buildBar(XP_COLOR);
    private final int[] gpRateSamples = new int[GP_RATE_SAMPLE_SIZE];
    private final Timer gpTimer;
    private int gpRateIndex = 0;
    private int gpRateCount = 0;
    private int gpRateMode = 0;
    private Integer lastReportedGp = null;
    private long lastGpUpdateTimeMs = 0L;
    private int gpMillisPerPoint = 0;
    private int currentGp = 0;
    private int currentMaxGp = 1;

    public StatsPanel() {
        setLayout(new BorderLayout());
        Arrays.fill(gpRateSamples, -1);

        int namePreferredWidth = Math.max(NAME_MIN_WIDTH, nameLabel.getPreferredSize().width);
        int nameMaxWidth = Math.max(NAME_MAX_WIDTH, namePreferredWidth);
        Dimension nameMin = new Dimension(NAME_MIN_WIDTH, nameLabel.getPreferredSize().height);
        Dimension namePref = new Dimension(namePreferredWidth, nameLabel.getPreferredSize().height);
        Dimension nameMax = new Dimension(nameMaxWidth, nameLabel.getPreferredSize().height);
        nameLabel.setMinimumSize(nameMin);
        nameLabel.setPreferredSize(namePref);
        nameLabel.setMaximumSize(nameMax);
        JPanel statusBar = new JPanel();
        statusBar.setLayout(new BoxLayout(statusBar, BoxLayout.X_AXIS));
        statusBar.add(nameLabel);
        statusBar.add(Box.createHorizontalStrut(12));
        statusBar.add(buildStatGroup(hpLabel, hpBar));
        statusBar.add(Box.createHorizontalStrut(10));
        statusBar.add(buildStatGroup(gpLabel, gpBar));
        statusBar.add(Box.createHorizontalStrut(10));
        statusBar.add(buildStatGroup(burdenLabel, burdenBar));
        statusBar.add(Box.createHorizontalStrut(10));
        statusBar.add(buildStatGroup(xpLabel, xpBar));
        statusBar.add(Box.createHorizontalGlue());
        add(statusBar, BorderLayout.CENTER);

        updateTheme(true);
        setUnavailable();
        gpTimer = new Timer(GP_TIMER_DEFAULT_INTERVAL_MS, this::onGpTick);
        gpTimer.start();
    }

    public void updateTheme(boolean inverted) {
        Color bg = inverted ? MapPanel.BACKGROUND_DARK : MapPanel.BACKGROUND_LIGHT;
        Color fg = inverted ? MapPanel.FOREGROUND_LIGHT : MapPanel.FOREGROUND_DARK;
        Color barBg = inverted ? BAR_BG : new Color(220, 220, 215);

        setBackground(bg);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, barBg),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));

        updateComponentTree(this, bg, fg, barBg);
    }

    private void updateComponentTree(Component c, Color bg, Color fg, Color barBg) {
        if (c instanceof JPanel) {
            c.setBackground(bg);
            for (Component child : ((Container) c).getComponents()) {
                updateComponentTree(child, bg, fg, barBg);
            }
        } else if (c instanceof JLabel) {
            c.setForeground(fg);
        } else if (c instanceof JProgressBar) {
            c.setBackground(barBg);
        }
    }

    @Override
    public void onFontChange(Font font) {
        nameLabel.setFont(font.deriveFont(Font.BOLD));
        hpLabel.setFont(font);
        gpLabel.setFont(font);
        burdenLabel.setFont(font);
        xpLabel.setFont(font);
        hpBar.setFont(font);
        gpBar.setFont(font);
        burdenBar.setFont(font);
        xpBar.setFont(font);
    }

    public void updateStats(StatsHudRenderer.StatsHudData data) {
        SwingUtilities.invokeLater(() -> {
            if (data == null) {
                setUnavailable();
                return;
            }
            updateNameLabel("Character: " + data.name());
            updateBar(hpBar, data.hp(), data.maxHp(),
                    format(data.hp()) + " / " + format(data.maxHp()));
            updateGpFromVitals(data.gp(), data.maxGp());

            int burdenValue = clamp(data.burden(), 0, 100);
            updateBar(burdenBar, burdenValue, 100, burdenValue + "%");

            long xpValue = Math.max(0, data.xp());
            int xpBarValue = (int) Math.min(Integer.MAX_VALUE, xpValue);
            updateBar(xpBar, Math.min(xpBarValue, XP_CAP), XP_CAP, format(xpValue));
        });
    }

    private void setUnavailable() {
        updateNameLabel("Character: --");
        updateBar(hpBar, 0, 1, "--");
        updateBar(gpBar, 0, 1, "--");
        updateBar(burdenBar, 0, 100, "--");
        updateBar(xpBar, 0, XP_CAP, "--");
        lastReportedGp = null;
        lastGpUpdateTimeMs = 0L;
        gpMillisPerPoint = 0;
        currentGp = 0;
        currentMaxGp = 1;
        gpRateIndex = 0;
        gpRateCount = 0;
        gpRateMode = 0;
        Arrays.fill(gpRateSamples, -1);
    }

    private static JProgressBar buildBar(Color color) {
        JProgressBar bar = new JProgressBar();
        bar.setStringPainted(true);
        bar.setForeground(color);
        bar.setBackground(BAR_BG);
        bar.setBorderPainted(true);
        return bar;
    }

    private static JPanel buildStatGroup(JLabel label, JProgressBar bar) {
        label.setForeground(TEXT_COLOR);
        Dimension labelSize = new Dimension(LABEL_WIDTH, label.getPreferredSize().height);
        label.setPreferredSize(labelSize);
        label.setMinimumSize(labelSize);
        JPanel panel = new JPanel(new BorderLayout(6, 0));
        panel.setBackground(BACKGROUND);
        panel.add(label, BorderLayout.WEST);

        Dimension preferred = new Dimension(BAR_PREFERRED_WIDTH, bar.getPreferredSize().height);
        Dimension minimum = new Dimension(BAR_MIN_WIDTH, bar.getPreferredSize().height);
        Dimension maximum = new Dimension(BAR_MAX_WIDTH, bar.getPreferredSize().height);
        bar.setPreferredSize(preferred);
        bar.setMinimumSize(minimum);
        bar.setMaximumSize(maximum);
        panel.add(bar, BorderLayout.CENTER);

        Dimension panelMax = new Dimension(LABEL_WIDTH + BAR_MAX_WIDTH + 6, bar.getPreferredSize().height);
        panel.setMaximumSize(panelMax);
        panel.setMinimumSize(new Dimension(LABEL_WIDTH + BAR_MIN_WIDTH + 6, bar.getPreferredSize().height));
        return panel;
    }

    private static void updateBar(JProgressBar bar, int value, int max, String label) {
        ThreadUtils.checkEdt();
        int safeMax = Math.max(1, max);
        int safeValue = Math.max(0, Math.min(value, safeMax));
        bar.setMaximum(safeMax);
        bar.setValue(safeValue);
        bar.setString(label);
    }

    private void updateNameLabel(String text) {
        ThreadUtils.checkEdt();
        nameLabel.setText(text);
        int textWidth = nameLabel.getFontMetrics(nameLabel.getFont()).stringWidth(text) + 10;
        int targetWidth = Math.max(NAME_MIN_WIDTH, Math.min(NAME_MAX_WIDTH, textWidth));
        Dimension target = new Dimension(targetWidth, nameLabel.getPreferredSize().height);
        nameLabel.setMinimumSize(target);
        nameLabel.setPreferredSize(target);
        nameLabel.setMaximumSize(new Dimension(Math.max(NAME_MAX_WIDTH, targetWidth), target.height));
        nameLabel.revalidate();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String format(long value) {
        return NUMBER_FORMAT.format(value);
    }

    private void updateGpFromVitals(int gp, int maxGp) {
        long now = System.currentTimeMillis();
        if (gp < maxGp && lastReportedGp != null && lastGpUpdateTimeMs > 0L) {
            long elapsedMs = now - lastGpUpdateTimeMs;
            if (elapsedMs > 0L) {
                double seconds = elapsedMs / 1000.0;
                if (seconds >= 6.0) {
                    double perTwoSeconds = (gp - lastReportedGp) / seconds * 2.0;
                    if (perTwoSeconds > 0) {
                        int roundedRate = (int) Math.round(perTwoSeconds);
                        if (roundedRate > 0) {
                            recordGpRate(roundedRate);
                        }
                    }
                }
            }
        }
        lastReportedGp = gp;
        lastGpUpdateTimeMs = now;
        currentGp = gp;
        currentMaxGp = Math.max(1, maxGp);
        updateBar(gpBar, gp, currentMaxGp, format(gp) + " / " + format(currentMaxGp));
    }

    private void recordGpRate(int rate) {
        gpRateSamples[gpRateIndex] = rate;
        gpRateIndex = (gpRateIndex + 1) % GP_RATE_SAMPLE_SIZE;
        gpRateCount = Math.min(gpRateCount + 1, GP_RATE_SAMPLE_SIZE);
        gpRateMode = calculateMode();
        updateGpTimerInterval();
    }

    private int calculateMode() {
        if (gpRateCount == 0) {
            return 0;
        }
        Map<Integer, Integer> counts = new HashMap<>();
        for (int i = 0; i < gpRateCount; i++) {
            int value = gpRateSamples[i];
            if (value < 0) {
                continue;
            }
            counts.put(value, counts.getOrDefault(value, 0) + 1);
        }
        int mode = 0;
        int bestCount = -1;
        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            int value = entry.getKey();
            int count = entry.getValue();
            if (count > bestCount) {
                bestCount = count;
                mode = value;
            }
        }
        return mode;
    }

    private void onGpTick(ActionEvent event) {
        ThreadUtils.checkEdt();
        if (gpRateMode <= 0 || gpMillisPerPoint <= 0 || currentGp >= currentMaxGp) {
            return;
        }
        currentGp = Math.min(currentGp + 1, currentMaxGp);
        updateBar(gpBar, currentGp, currentMaxGp, format(currentGp) + " / " + format(currentMaxGp));
    }

    private void updateGpTimerInterval() {
        if (gpRateMode <= 0) {
            gpMillisPerPoint = 0;
            return;
        }
        gpMillisPerPoint = Math.max(1, (int) Math.round(2000.0 / gpRateMode));
        gpTimer.setDelay(gpMillisPerPoint);
        gpTimer.setInitialDelay(gpMillisPerPoint);
    }
}
