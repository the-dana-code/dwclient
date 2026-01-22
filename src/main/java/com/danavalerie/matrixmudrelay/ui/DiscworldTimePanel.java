package com.danavalerie.matrixmudrelay.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
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
    private final Timer refreshTimer;

    public DiscworldTimePanel() {
        setLayout(new BorderLayout());
        setOpaque(false);

        seasonLabel = new JLabel("", SwingConstants.LEFT);
        seasonLabel.setBorder(new EmptyBorder(0, 10, 0, 5));
        timeLabel = new JLabel("", SwingConstants.RIGHT);
        timeLabel.setBorder(new EmptyBorder(0, 5, 0, 10));

        add(seasonLabel, BorderLayout.CENTER);
        add(timeLabel, BorderLayout.EAST);

        refreshTimer = new Timer(1000, e -> updateTime());
        refreshTimer.start();
        updateTime();
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

        long minutes = (unixSeconds % (18 * 60)) / 18;
        long hours = (unixSeconds % (18 * 60 * 24)) / (18 * 60);
        long hoursTwelve = hours % 12;
        if (hoursTwelve == 0) hoursTwelve = 12;
        String ampm = hours > 11 ? "pm" : "am";
        String timeStr = String.format("%d:%02d%s", hoursTwelve, minutes, ampm);

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
        
        String fullDate = String.format("%s %d%s %s, %d %s", dayName, dayInMonth, getOrdinal(dayInMonth), monthName, years, cycleName);
        String tooltip = String.format("<html><b>Date:</b> %s<br><b>Time:</b> %s<br><b>Season:</b> %s</html>", fullDate, timeStr, season);
        seasonLabel.setToolTipText(tooltip);
        timeLabel.setToolTipText(tooltip);
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
        revalidate();
    }

    public void updateTheme(Color bg, Color fg) {
        seasonLabel.setForeground(fg);
        timeLabel.setForeground(fg);
    }
}
