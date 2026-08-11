package dev.jxriera.gavel.model;

import dev.jxriera.gavel.util.Durations;

public final class Tier {
    private final int number;
    private final PunishmentType type;
    private final String duration;
    private final String reason;

    public Tier(int number, PunishmentType type, String duration, String reason) {
        this.number = number;
        this.type = type;
        this.duration = duration;
        this.reason = reason;
    }

    public int getNumber() {
        return number;
    }

    public PunishmentType getType() {
        return type;
    }

    public String getDuration() {
        return duration;
    }

    public String getReason() {
        return reason;
    }

    public boolean isPermanent() {
        return !type.supportsDuration() || Durations.isPermanent(duration);
    }
}
