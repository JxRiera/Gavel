package dev.jxriera.gavel.model;

import java.util.Collections;
import java.util.List;

public final class Category {
    private final String id;
    private final int slot;
    private final String permission;
    private final long expireAfterMillis;
    private final IconSpec icon;
    private final List<Tier> tiers;

    public Category(String id, int slot, String permission, long expireAfterMillis,
                    IconSpec icon, List<Tier> tiers) {
        this.id = id;
        this.slot = slot;
        this.permission = permission;
        this.expireAfterMillis = expireAfterMillis;
        this.icon = icon;
        this.tiers = tiers == null ? Collections.<Tier>emptyList() : tiers;
    }

    public String getId() {
        return id;
    }

    public int getSlot() {
        return slot;
    }

    public String getPermission() {
        return permission;
    }

    public long getExpireAfterMillis() {
        return expireAfterMillis;
    }

    public IconSpec getIcon() {
        return icon;
    }

    public List<Tier> getTiers() {
        return tiers;
    }
}
