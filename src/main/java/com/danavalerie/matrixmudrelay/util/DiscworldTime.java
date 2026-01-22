package com.danavalerie.matrixmudrelay.util;

import java.time.Instant;

public final class DiscworldTime {
    private static final String[] MONTH_NAMES = {
        "Offle", "February", "March", "April", "May", "June", "Grune", "August", "Spune",
        "Sektober", "Ember", "December", "Ick",
        "Off", "Feb", "Mar", "Apr", "May", "Jun", "Gru", "Aug", "Spu", "Sek", "Emb", "Dec", "Ick"
    };

    private static final String[] DAY_NAMES = {
        "Octeday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday",
        "Oct", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"
    };

    private static final int[] DAYS_PER_MONTH = {
        32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 16
    };

    private static final String[] CYCLES = {"Prime", "Secundus", "I", "II"};

    private DiscworldTime() {
    }

    public static void main(String[] args) {
        long unixSeconds = Instant.now().getEpochSecond();
        DiscTime discTime = fromUnixSeconds(unixSeconds);

        String time = String.format("%d:%02d%s", discTime.hoursTwelve, discTime.minutes, discTime.ampm);
        String dateLong = String.format("%s %s %s", discTime.dayName, ordinal(discTime.dayInMonth), discTime.monthName);

        System.out.println("Discworld time: " + time);
        System.out.println("Discworld date: " + dateLong);
        System.out.println("Discworld year: " + discTime.year + " " + discTime.cycleName + " cycle");
    }

    public static DiscTime fromUnixSeconds(long unixSeconds) {
        long seconds = mod(unixSeconds, 18);
        long minutes = mod(unixSeconds, 18 * 60) / 18;
        long hours = mod(unixSeconds, 18 * 60 * 24) / (18 * 60);
        long hoursTwelve = mod(hours, 12);
        if (hoursTwelve == 0) {
            hoursTwelve = 12;
        }
        String ampm = hours > 11 ? "pm" : "am";

        long dayInYear = mod(unixSeconds, 18L * 60 * 24 * 400) / (18L * 60 * 24);
        long dayInWeek = mod(unixSeconds + (18L * 60 * 24 * 5), 18L * 60 * 24 * 8) / (18L * 60 * 24);
        long years = (unixSeconds / (18L * 60 * 24 * 400 * 2)) + 1966;
        long cycle = mod(unixSeconds / (18L * 60 * 24 * 400), 2);

        int month = 0;
        long totalDays = dayInYear;
        while (totalDays >= DAYS_PER_MONTH[month]) {
            totalDays -= DAYS_PER_MONTH[month];
            month += 1;
        }
        long dayInMonth = totalDays + 1;

        String specialDay = null;
        if (month == 0 && dayInMonth == 1) {
            specialDay = (cycle == 0) ? "Hogswatch" : "Crueltide";
        }
        if (month == 6 && dayInMonth == 16) {
            specialDay = "Small Gods day";
        }
        if (month == 7 && dayInMonth == 23) {
            specialDay = "Soul Cake Tuesday";
        }
        if (month == 9 && dayInMonth == 1) {
            specialDay = "Sektober Fools' Day";
        }

        String dayName = specialDay != null ? specialDay : DAY_NAMES[(int) dayInWeek];
        String shortDayName = DAY_NAMES[(int) dayInWeek + 8];
        String monthName = MONTH_NAMES[month];
        String shortMonthName = MONTH_NAMES[month + 13];
        String cycleName = CYCLES[(int) cycle];
        String shortCycleName = CYCLES[(int) cycle + 2];

        return new DiscTime(
            (int) seconds,
            (int) minutes,
            (int) hours,
            (int) hoursTwelve,
            ampm,
            (int) dayInYear,
            (int) dayInWeek,
            (int) years,
            (int) cycle,
            month,
            (int) dayInMonth,
            monthName,
            shortMonthName,
            dayName,
            shortDayName,
            cycleName,
            shortCycleName
        );
    }

    private static long mod(long value, long divisor) {
        long result = value % divisor;
        return result < 0 ? result + divisor : result;
    }

    private static String ordinal(long day) {
        long digit = day % 10;
        if (digit > 0 && digit <= 3 && day != 11 && day != 12 && day != 13) {
            return day + new String[] {"st", "nd", "rd"}[(int) digit - 1];
        }
        return day + "th";
    }

    public static final class DiscTime {
        public final int seconds;
        public final int minutes;
        public final int hours;
        public final int hoursTwelve;
        public final String ampm;
        public final int dayInYear;
        public final int dayInWeek;
        public final int year;
        public final int cycle;
        public final int month;
        public final int dayInMonth;
        public final String monthName;
        public final String shortMonthName;
        public final String dayName;
        public final String shortDayName;
        public final String cycleName;
        public final String shortCycleName;

        private DiscTime(
            int seconds,
            int minutes,
            int hours,
            int hoursTwelve,
            String ampm,
            int dayInYear,
            int dayInWeek,
            int year,
            int cycle,
            int month,
            int dayInMonth,
            String monthName,
            String shortMonthName,
            String dayName,
            String shortDayName,
            String cycleName,
            String shortCycleName
        ) {
            this.seconds = seconds;
            this.minutes = minutes;
            this.hours = hours;
            this.hoursTwelve = hoursTwelve;
            this.ampm = ampm;
            this.dayInYear = dayInYear;
            this.dayInWeek = dayInWeek;
            this.year = year;
            this.cycle = cycle;
            this.month = month;
            this.dayInMonth = dayInMonth;
            this.monthName = monthName;
            this.shortMonthName = shortMonthName;
            this.dayName = dayName;
            this.shortDayName = shortDayName;
            this.cycleName = cycleName;
            this.shortCycleName = shortCycleName;
        }
    }
}
