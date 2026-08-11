package dev.jxriera.gavel.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Durations {
    private static final Pattern TOKEN = Pattern.compile("(\\d+)\\s*(mo|[smhdwy])", Pattern.CASE_INSENSITIVE);

    private static final long SECOND = 1000L;
    private static final long MINUTE = 60L * SECOND;
    private static final long HOUR = 60L * MINUTE;
    private static final long DAY = 24L * HOUR;
    private static final long WEEK = 7L * DAY;
    private static final long MONTH = 30L * DAY;
    private static final long YEAR = 365L * DAY;

    private Durations() {
    }

    public static boolean isPermanent(String raw) {
        if (raw == null) {
            return true;
        }
        String value = raw.trim().toLowerCase();
        return value.isEmpty() || value.equals("perm") || value.equals("permanent")
                || value.equals("0") || value.equals("-1") || value.equals("forever");
    }

    public static long toMillis(String raw) {
        if (isPermanent(raw)) {
            return -1L;
        }
        Matcher matcher = TOKEN.matcher(raw.trim().toLowerCase());
        long total = 0L;
        boolean found = false;
        while (matcher.find()) {
            found = true;
            long amount;
            try {
                amount = Long.parseLong(matcher.group(1));
            } catch (NumberFormatException ex) {
                return -1L;
            }
            String unit = matcher.group(2);
            if (unit.equals("s")) {
                total += amount * SECOND;
            } else if (unit.equals("m")) {
                total += amount * MINUTE;
            } else if (unit.equals("h")) {
                total += amount * HOUR;
            } else if (unit.equals("d")) {
                total += amount * DAY;
            } else if (unit.equals("w")) {
                total += amount * WEEK;
            } else if (unit.equals("mo")) {
                total += amount * MONTH;
            } else if (unit.equals("y")) {
                total += amount * YEAR;
            }
        }
        return found ? total : -1L;
    }

    public static boolean isValid(String raw) {
        if (isPermanent(raw)) {
            return true;
        }
        String cleaned = raw.trim().toLowerCase();
        Matcher matcher = TOKEN.matcher(cleaned);
        StringBuilder consumed = new StringBuilder();
        while (matcher.find()) {
            consumed.append(matcher.group(0).replace(" ", ""));
        }
        return consumed.length() > 0 && consumed.toString().equals(cleaned.replace(" ", ""));
    }

    public static String display(String raw, String permanentWord) {
        return isPermanent(raw) ? permanentWord : raw.trim();
    }
}
