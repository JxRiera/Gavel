package dev.jxriera.gavel.model;

public enum PunishmentType {
    BAN("ban-temp", "ban-perm", true),
    IPBAN("ipban-temp", "ipban-perm", true),
    MUTE("mute-temp", "mute-perm", true),
    WARN("warn-temp", "warn-perm", true),
    KICK("kick", "kick", false);

    private final String temporaryTemplate;
    private final String permanentTemplate;
    private final boolean supportsDuration;

    PunishmentType(String temporaryTemplate, String permanentTemplate, boolean supportsDuration) {
        this.temporaryTemplate = temporaryTemplate;
        this.permanentTemplate = permanentTemplate;
        this.supportsDuration = supportsDuration;
    }

    public String templateKey(boolean permanent) {
        return permanent || !supportsDuration ? permanentTemplate : temporaryTemplate;
    }

    public boolean supportsDuration() {
        return supportsDuration;
    }

    public String wordKey() {
        return "type-" + name().toLowerCase();
    }

    public static PunishmentType parse(String raw, PunishmentType fallback) {
        if (raw == null) {
            return fallback;
        }
        String value = raw.trim().toUpperCase().replace("-", "").replace("_", "");
        if (value.equals("IPBAN") || value.equals("BANIP")) {
            return IPBAN;
        }
        for (PunishmentType type : values()) {
            if (type.name().equals(value)) {
                return type;
            }
        }
        return fallback;
    }
}
