package com.danavalerie.matrixmudrelay.ui;

import com.danavalerie.matrixmudrelay.core.StatsHudRenderer;

import javax.swing.BorderFactory;
import javax.swing.JProgressBar;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class StatsPanel extends JPanel {
    private static final Color BACKGROUND = new Color(10, 10, 15);
    private static final Color TEXT_COLOR = new Color(220, 220, 220);
    private static final Color BAR_BG = new Color(26, 26, 26);
    private static final Color HP_COLOR = new Color(0, 150, 0);
    private static final Color GP_COLOR = new Color(0, 60, 160);
    private static final Color BURDEN_COLOR = new Color(0, 130, 0);
    private static final Color XP_COLOR = new Color(200, 110, 0);
    private static final int XP_CAP = 1_000_000;
    private static final int GP_RATE_SAMPLE_SIZE = 10;
    private static final int GP_TIMER_DEFAULT_INTERVAL_MS = 1000;
    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getIntegerInstance(Locale.US);

    private final JLabel nameLabel = new JLabel("Character: --");
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
        setLayout(new BorderLayout(0, 8));
        setBackground(BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        Arrays.fill(gpRateSamples, -1);

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
        gpTimer = new Timer(GP_TIMER_DEFAULT_INTERVAL_MS, this::onGpTick);
        gpTimer.start();
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
            updateGpFromVitals(data.gp(), data.maxGp());

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
        updateBar(gpBar, gp, currentMaxGp, "GP " + format(gp) + " / " + format(currentMaxGp));
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
        if (gpRateMode <= 0 || gpMillisPerPoint <= 0 || currentGp >= currentMaxGp) {
            return;
        }
        currentGp = Math.min(currentGp + 1, currentMaxGp);
        updateBar(gpBar, currentGp, currentMaxGp, "GP " + format(currentGp) + " / " + format(currentMaxGp));
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
