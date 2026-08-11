package dev.jxriera.gavel.model;

import dev.jxriera.gavel.util.Items;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.List;

public final class IconSpec {
    private final Material material;
    private final String name;
    private final List<String> lore;
    private final boolean glow;
    private final int amount;

    public IconSpec(Material material, String name, List<String> lore, boolean glow, int amount) {
        this.material = material;
        this.name = name;
        this.lore = lore == null ? Collections.<String>emptyList() : lore;
        this.glow = glow;
        this.amount = amount <= 0 ? 1 : amount;
    }

    public static IconSpec from(ConfigurationSection section, Material fallback) {
        if (section == null) {
            return new IconSpec(fallback, "", Collections.<String>emptyList(), false, 1);
        }
        return new IconSpec(
                Items.material(section.getString("material"), fallback),
                section.getString("name", ""),
                Items.lines(section.getList("lore")),
                section.getBoolean("glow", false),
                section.getInt("amount", 1));
    }

    public Material getMaterial() {
        return material;
    }

    public String getName() {
        return name;
    }

    public List<String> getLore() {
        return lore;
    }

    public boolean isGlow() {
        return glow;
    }

    public int getAmount() {
        return amount;
    }
}
