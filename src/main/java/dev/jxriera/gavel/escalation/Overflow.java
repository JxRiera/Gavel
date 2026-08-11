package dev.jxriera.gavel.escalation;

public enum Overflow {

    LAST,
    CYCLE;

    public static Overflow parse(String raw, Overflow fallback) {
        if (raw == null) {
            return fallback;
        }
        String value = raw.trim().toUpperCase();
        for (Overflow overflow : values()) {
            if (overflow.name().equals(value)) {
                return overflow;
            }
        }
        return fallback;
    }
}
