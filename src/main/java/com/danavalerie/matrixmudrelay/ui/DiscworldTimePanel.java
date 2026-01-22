package com.danavalerie.matrixmudrelay.ui;

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

    // --- In-game clock mapping (per existing code) ---
    // 18 real seconds = 1 in-game minute
    private static final long REAL_SECONDS_PER_IG_MINUTE = 18L;
    private static final long IG_MINUTES_PER_DAY = 24L * 60L;
    private static final long REAL_SECONDS_PER_IG_DAY = REAL_SECONDS_PER_IG_MINUTE * IG_MINUTES_PER_DAY; // 25920
    private static final int DAYS_PER_YEAR = 400;

    // --- Sunrise/sunset model (single-harmonic 400-day sinusoid) ---
    // Minutes after midnight (0..1439). These coefficients were derived from the observed table.
    // Sunrise here means "sun fully up" (end message). Sunset means "sun fully gone" (end message).
    private static final double SUNRISE_BASE = 328.4;
    private static final double SUNRISE_COS = 163.6;
    private static final double SUNRISE_SIN = 11.2;

    private static final double SUNSET_BASE = 1274.4;
    private static final double SUNSET_COS = -162.3;
    private static final double SUNSET_SIN = -24.1;

    public enum SunEventType { SUNRISE, SUNSET }

    public static final class NextSunEvent {
        public final SunEventType type;
        public final long secondsUntil;      // real seconds until event
        public final long igMinutesUntil;    // in-game minutes until event
        public final int eventMinuteOfDay;   // in-game minute-of-day (0..1439) for the event
        public final int eventDayOfYear;     // 0..399 day index when event occurs

        private NextSunEvent(SunEventType type, long secondsUntil, long igMinutesUntil,
                             int eventMinuteOfDay, int eventDayOfYear) {
            this.type = type;
            this.secondsUntil = secondsUntil;
            this.igMinutesUntil = igMinutesUntil;
            this.eventMinuteOfDay = eventMinuteOfDay;
            this.eventDayOfYear = eventDayOfYear;
        }
    }

    private final JLabel seasonLabel;
    private final JLabel timeLabel;
    private final Timer refreshTimer;
    private String lastTooltip;
    private boolean isDarkMode;

    public DiscworldTimePanel() {
        setLayout(new BorderLayout());
        setOpaque(false);

        seasonLabel = new JLabel("", SwingConstants.LEFT);
        seasonLabel.setBorder(new EmptyBorder(0, 10, 0, 5));
        timeLabel = new JLabel("", SwingConstants.RIGHT);
        timeLabel.setBorder(new EmptyBorder(0, 5, 0, 10));

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

        int sunriseToday = sunriseMinuteOfDay((int) dayInYear);
        int sunsetToday = sunsetMinuteOfDay((int) dayInYear);
        long igMinuteOfDay = hours * 60 + minutes;
        boolean isDay = igMinuteOfDay >= sunriseToday && igMinuteOfDay < sunsetToday;
        String sunMoon = isDay ? "\u2600" : "\uD83C\uDF19";
        String symbolColor = isDay ? (isDarkMode ? "yellow" : "orange") : (isDarkMode ? "white" : "black");

        seasonLabel.setText(season);
        timeLabel.setText("<html>" + timeStr + " <font color='" + symbolColor + "'>" + sunMoon + "</font></html>");

        String fullDate = String.format("%s %d%s %s, %d %s",
                dayName, dayInMonth, getOrdinal(dayInMonth), monthName, years, cycleName);

        // Optional: include next sunrise/sunset info in tooltip without changing any public API.
        NextSunEvent next = getNextSunEvent(unixSeconds);
        String nextStr = formatNextSunEvent(next);

        String tooltip = String.format(
                "<html><font face='monospaced'><b>%7s</b> %s<br><b>%7s</b> %s<br><b>%7s</b> %s<br><b>%7s</b> %s</font></html>",
                "Date:", fullDate, "Time:", timeStr, "Season:", season, "Next:", nextStr
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
        revalidate();
    }

    public void updateTheme(Color bg, Color fg) {
        seasonLabel.setForeground(fg);
        timeLabel.setForeground(fg);
        this.isDarkMode = bg.equals(MapPanel.BACKGROUND_DARK);
        updateTime();
    }

    // --------------------------------------------------------------------------------------------
    // NEW FUNCTIONALITY (does not change or remove any existing public method)
    // --------------------------------------------------------------------------------------------

    /** Returns time until the next sunrise or sunset (whichever occurs first). */
    public NextSunEvent getNextSunEvent() {
        return getNextSunEvent(Instant.now().getEpochSecond());
    }

    /** Returns real seconds until the next sunrise (sun fully up). */
    public long getSecondsUntilNextSunrise() {
        return getSecondsUntilNextSpecificEvent(Instant.now().getEpochSecond(), SunEventType.SUNRISE);
    }

    /** Returns real seconds until the next sunset (sun fully gone). */
    public long getSecondsUntilNextSunset() {
        return getSecondsUntilNextSpecificEvent(Instant.now().getEpochSecond(), SunEventType.SUNSET);
    }

    /** Convenience: returns a short human-readable string for the next sun event. */
    public String getNextSunEventSummary() {
        return formatNextSunEvent(getNextSunEvent());
    }

    // --------------------------------------------------------------------------------------------
    // Internals for sunrise/sunset calculations
    // --------------------------------------------------------------------------------------------

    private NextSunEvent getNextSunEvent(long unixSeconds) {
        int dayOfYear = (int) ((unixSeconds % (REAL_SECONDS_PER_IG_DAY * DAYS_PER_YEAR)) / REAL_SECONDS_PER_IG_DAY);
        long secondsIntoDay = unixSeconds % REAL_SECONDS_PER_IG_DAY;

        // Exact in-game minute position within the day (integer minutes) + leftover real seconds.
        long igMinuteOfDay = secondsIntoDay / REAL_SECONDS_PER_IG_MINUTE; // 0..1439
        long leftoverRealSeconds = secondsIntoDay % REAL_SECONDS_PER_IG_MINUTE; // 0..17

        int sunriseToday = sunriseMinuteOfDay(dayOfYear);
        int sunsetToday = sunsetMinuteOfDay(dayOfYear);

        // Determine next event.
        SunEventType type;
        int eventDay = dayOfYear;
        int eventMinute;

        if (igMinuteOfDay < sunriseToday) {
            type = SunEventType.SUNRISE;
            eventMinute = sunriseToday;
        } else if (igMinuteOfDay < sunsetToday) {
            type = SunEventType.SUNSET;
            eventMinute = sunsetToday;
        } else {
            type = SunEventType.SUNRISE;
            eventDay = (dayOfYear + 1) % DAYS_PER_YEAR;
            eventMinute = sunriseMinuteOfDay(eventDay);
        }

        long igMinutesUntil;
        if (eventDay == dayOfYear) {
            igMinutesUntil = (long) eventMinute - igMinuteOfDay;
        } else {
            igMinutesUntil = (IG_MINUTES_PER_DAY - igMinuteOfDay) + eventMinute;
        }

        // Convert to real seconds until the event, accounting for partial in-game minute.
        long secondsUntil = igMinutesUntil * REAL_SECONDS_PER_IG_MINUTE - leftoverRealSeconds;
        if (secondsUntil < 0) secondsUntil = 0;

        return new NextSunEvent(type, secondsUntil, igMinutesUntil, eventMinute, eventDay);
    }

    private long getSecondsUntilNextSpecificEvent(long unixSeconds, SunEventType want) {
        int dayOfYear = (int) ((unixSeconds % (REAL_SECONDS_PER_IG_DAY * DAYS_PER_YEAR)) / REAL_SECONDS_PER_IG_DAY);
        long secondsIntoDay = unixSeconds % REAL_SECONDS_PER_IG_DAY;
        long igMinuteOfDay = secondsIntoDay / REAL_SECONDS_PER_IG_MINUTE;
        long leftoverRealSeconds = secondsIntoDay % REAL_SECONDS_PER_IG_MINUTE;

        int todayMinute = (want == SunEventType.SUNRISE)
                ? sunriseMinuteOfDay(dayOfYear)
                : sunsetMinuteOfDay(dayOfYear);

        int eventDay = dayOfYear;
        int eventMinute = todayMinute;

        if (igMinuteOfDay >= todayMinute) {
            // Next occurrence is tomorrow.
            eventDay = (dayOfYear + 1) % DAYS_PER_YEAR;
            eventMinute = (want == SunEventType.SUNRISE)
                    ? sunriseMinuteOfDay(eventDay)
                    : sunsetMinuteOfDay(eventDay);
        }

        long igMinutesUntil;
        if (eventDay == dayOfYear) {
            igMinutesUntil = (long) eventMinute - igMinuteOfDay;
        } else {
            igMinutesUntil = (IG_MINUTES_PER_DAY - igMinuteOfDay) + eventMinute;
        }

        long secondsUntil = igMinutesUntil * REAL_SECONDS_PER_IG_MINUTE - leftoverRealSeconds;
        return Math.max(0L, secondsUntil);
    }

    private int sunriseMinuteOfDay(int dayOfYear) {
        return clampMinute((int) Math.round(harmonic(dayOfYear, SUNRISE_BASE, SUNRISE_COS, SUNRISE_SIN)));
    }

    private int sunsetMinuteOfDay(int dayOfYear) {
        return clampMinute((int) Math.round(harmonic(dayOfYear, SUNSET_BASE, SUNSET_COS, SUNSET_SIN)));
    }

    private double harmonic(int dayOfYear, double base, double cosAmp, double sinAmp) {
        double theta = (2.0 * Math.PI * (double) dayOfYear) / (double) DAYS_PER_YEAR;
        return base + cosAmp * Math.cos(theta) + sinAmp * Math.sin(theta);
    }

    private int clampMinute(int minute) {
        if (minute < 0) return 0;
        if (minute > 1439) return 1439;
        return minute;
    }

    private String formatNextSunEvent(NextSunEvent next) {
        String type = (next.type == SunEventType.SUNRISE) ? "Sunrise" : "Sunset";
        long totalRealMinutes = next.secondsUntil / 60;
        String eventTime = formatMinuteOfDay(next.eventMinuteOfDay);

        long h = totalRealMinutes / 60;
        long m = totalRealMinutes % 60;
        String duration;
        if (h > 0) {
            duration = h + " hour" + (h == 1 ? "" : "s") + " " + m + " minute" + (m == 1 ? "" : "s");
        } else {
            duration = m + " minute" + (m == 1 ? "" : "s");
        }

        return String.format(
                "%s at %s (in %s RW)",
                type, eventTime, duration
        );
    }

    private String formatMinuteOfDay(int minuteOfDay) {
        int h24 = minuteOfDay / 60;
        int m = minuteOfDay % 60;

        int h12 = h24 % 12;
        if (h12 == 0) h12 = 12;
        String ampm = (h24 >= 12) ? "pm" : "am";

        return String.format("%d:%02d%s", h12, m, ampm);
    }
}
