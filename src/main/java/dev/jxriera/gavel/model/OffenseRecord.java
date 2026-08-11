package dev.jxriera.gavel.model;

public final class OffenseRecord {
    private final long id;
    private final String uuid;
    private final String name;
    private final String category;
    private final int tier;
    private final String type;
    private final String duration;
    private final String reason;
    private final String staffUuid;
    private final String staffName;
    private final String server;
    private final boolean silent;
    private final boolean active;
    private final long created;

    public OffenseRecord(long id, String uuid, String name, String category, int tier, String type,
                         String duration, String reason, String staffUuid, String staffName,
                         String server, boolean silent, boolean active, long created) {
        this.id = id;
        this.uuid = uuid;
        this.name = name;
        this.category = category;
        this.tier = tier;
        this.type = type;
        this.duration = duration;
        this.reason = reason;
        this.staffUuid = staffUuid;
        this.staffName = staffName;
        this.server = server;
        this.silent = silent;
        this.active = active;
        this.created = created;
    }

    public long getId() {
        return id;
    }

    public String getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public int getTier() {
        return tier;
    }

    public String getType() {
        return type;
    }

    public String getDuration() {
        return duration;
    }

    public String getReason() {
        return reason;
    }

    public String getStaffUuid() {
        return staffUuid;
    }

    public String getStaffName() {
        return staffName;
    }

    public String getServer() {
        return server;
    }

    public boolean isSilent() {
        return silent;
    }

    public boolean isActive() {
        return active;
    }

    public long getCreated() {
        return created;
    }
}
