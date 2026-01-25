package com.danavalerie.matrixmudrelay.ui;

import com.danavalerie.matrixmudrelay.util.DiscworldTimeUtils;
import com.danavalerie.matrixmudrelay.mud.CurrentRoomInfo;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;

public class DiscworldTimePanel extends JPanel implements FontChangeListener {
    private static final String[] MONTH_NAMES = {
            "Offle", "February", "March", "April", "May", "June", "Grune", "August", "Spune",
            "Sektober", "Ember", "December", "Ick"
    };

    private static final String[] DAY_NAMES = {
            "Octeday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
    };

    private static final int[] DAYS_PER_MONTH = {
            32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 16
    };

    private static final String[] CYCLES = {"Prime", "Secundus"};

    private final JLabel seasonLabel;
    private final JLabel timeLabel;
    private final JLabel locationLabel;
    private final Timer refreshTimer;
    private String lastTooltip;
    private CurrentRoomInfo.RoomEnvironment currentEnvironment;

    public DiscworldTimePanel() {
        setLayout(new BorderLayout());
        setOpaque(false);

        locationLabel = new JLabel("", SwingConstants.LEFT);
        locationLabel.setBorder(new EmptyBorder(0, 10, 0, 4));
        seasonLabel = new JLabel("", SwingConstants.LEFT);
        seasonLabel.setBorder(new EmptyBorder(0, 4, 0, 5));
        timeLabel = new JLabel("", SwingConstants.RIGHT);
        timeLabel.setBorder(new EmptyBorder(0, 5, 0, 10));

        add(locationLabel, BorderLayout.WEST);
        add(seasonLabel, BorderLayout.CENTER);
        add(timeLabel, BorderLayout.EAST);

        MouseAdapter tooltipExtender = new MouseAdapter() {
            private int originalDelay;

            @Override
            public void mouseEntered(MouseEvent e) {
                originalDelay = ToolTipManager.sharedInstance().getDismissDelay();
                ToolTipManager.sharedInstance().setDismissDelay(Integer.MAX_VALUE);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                ToolTipManager.sharedInstance().setDismissDelay(originalDelay);
            }
        };
        seasonLabel.addMouseListener(tooltipExtender);
        timeLabel.addMouseListener(tooltipExtender);
        locationLabel.addMouseListener(tooltipExtender);

        refreshTimer = new Timer(1000, e -> updateTime());
        refreshTimer.start();
        updateTime();
        updateRoomEnvironment(CurrentRoomInfo.RoomEnvironment.UNKNOWN);
    }

    private String getSeason(long cycle, long dayInYear) {
        if (cycle == 0) { // Prime
            if (dayInYear >= 52 && dayInYear <= 150) return "Spring Prime";
            if (dayInYear >= 151 && dayInYear <= 250) return "Summer Prime";
            if (dayInYear >= 251 && dayInYear <= 350) return "Autumn Prime";
            if (dayInYear >= 351) return "Spindlewinter";
            return "Backspindlewinter"; // 0..51
        } else { // Secundus
            if (dayInYear <= 50) return "Spindlewinter";
            if (dayInYear >= 51 && dayInYear <= 163) return "Secundus Spring";
            if (dayInYear >= 164 && dayInYear <= 252) return "Secundus Summer";
            if (dayInYear >= 253 && dayInYear <= 352) return "Secundus Autumn";
            return "Backspindlewinter"; // 353..399
        }
    }

    private void updateTime() {
        long unixSeconds = Instant.now().getEpochSecond();

        String timeStr = DiscworldTimeUtils.formatDiscworldTime(unixSeconds);

        long dayInYear = (unixSeconds % (18L * 60 * 24 * 400)) / (18L * 60 * 24);
        long dayInWeek = ((unixSeconds + (18L * 60 * 24 * 5)) % (18L * 60 * 24 * 8)) / (18L * 60 * 24);
        long years = (unixSeconds / (18L * 60 * 24 * 400 * 2)) + 1966;
        long cycle = (unixSeconds / (18L * 60 * 24 * 400)) % 2;

        int month = 0;
        long totalDays = dayInYear;
        while (month < DAYS_PER_MONTH.length && totalDays >= DAYS_PER_MONTH[month]) {
            totalDays -= DAYS_PER_MONTH[month];
            month += 1;
        }
        long dayInMonth = totalDays + 1;

        String specialDay = null;
        if (month == 0 && dayInMonth == 1) {
            specialDay = (cycle == 0) ? "Hogswatch" : "Crueltide";
        } else if (month == 6 && dayInMonth == 16) {
            specialDay = "Small Gods day";
        } else if (month == 7 && dayInMonth == 23) {
            specialDay = "Soul Cake Tuesday";
        } else if (month == 9 && dayInMonth == 1) {
            specialDay = "Sektober Fools' Day";
        }

        String dayName = specialDay != null ? specialDay : DAY_NAMES[(int) dayInWeek];
        String monthName = MONTH_NAMES[month];
        String cycleName = CYCLES[(int) cycle];
        String season = getSeason(cycle, dayInYear);

        seasonLabel.setText(season);
        timeLabel.setText(timeStr);

        String fullDate = String.format("%s %d%s %s, %d %s",
                dayName, dayInMonth, getOrdinal(dayInMonth), monthName, years, cycleName);

        String tooltip = String.format(
                "<html><font face='monospaced'><b>%7s</b> %s<br><b>%7s</b> %s<br><b>%7s</b> %s</font></html>",
                "Date:", fullDate, "Time:", timeStr, "Season:", season
        ).replace(" ", "&nbsp;");

        if (!tooltip.equals(lastTooltip)) {
            seasonLabel.setToolTipText(tooltip);
            timeLabel.setToolTipText(tooltip);
            lastTooltip = tooltip;
        }
    }

    private String getOrdinal(long day) {
        long digit = day % 10;
        if (digit > 0 && digit <= 3 && day != 11 && day != 12 && day != 13) {
            return new String[]{"st", "nd", "rd"}[(int) digit - 1];
        }
        return "th";
    }

    @Override
    public void onFontChange(Font font) {
        seasonLabel.setFont(font);
        timeLabel.setFont(font);
        locationLabel.setFont(font);
        revalidate();
    }

    public void updateTheme(Color bg, Color fg) {
        seasonLabel.setForeground(fg);
        timeLabel.setForeground(fg);
        locationLabel.setForeground(fg);
        updateTime();
    }

    public void updateRoomEnvironment(CurrentRoomInfo.RoomEnvironment environment) {
        if (environment == null) {
            environment = CurrentRoomInfo.RoomEnvironment.UNKNOWN;
        }
        if (environment == currentEnvironment) {
            return;
        }
        currentEnvironment = environment;
        switch (environment) {
            case OUTSIDE -> {
                locationLabel.setText("☀️");
                locationLabel.setToolTipText("Outside");
            }
            case INSIDE -> {
                locationLabel.setText("🏠");
                locationLabel.setToolTipText("Inside");
            }
            default -> {
                locationLabel.setText("❔");
                locationLabel.setToolTipText("Unknown environment");
            }
        }
        revalidate();
        repaint();
    }

}
