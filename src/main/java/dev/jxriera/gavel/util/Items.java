package dev.jxriera.gavel.util;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class Items {
    private Items() {
    }

    public static Material material(String name, Material fallback) {
        if (name == null || name.trim().isEmpty()) {
            return fallback;
        }
        Material found = Material.matchMaterial(name.trim().toUpperCase());
        if (found == null) {
            found = Material.matchMaterial("minecraft:" + name.trim().toLowerCase());
        }
        return found == null ? fallback : found;
    }

    public static ItemStack build(Material material, String name, List<String> lore, boolean glow, int amount) {
        ItemStack item = new ItemStack(material == null ? Material.STONE : material, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null) {
                meta.setDisplayName(Text.color(name));
            }
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(Text.color(lore));
            }
            if (glow) {
                Enchantment enchantment = glowEnchantment();
                if (enchantment != null) {
                    meta.addEnchant(enchantment, 1, true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                }
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack build(Material material, String name, List<String> lore) {
        return build(material, name, lore, false, 1);
    }

    private static Enchantment glowEnchantment() {
        try {
            return Enchantment.getByKey(NamespacedKey.minecraft("unbreaking"));
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static List<String> lines(List<?> raw) {
        List<String> out = new ArrayList<String>();
        if (raw == null) {
            return out;
        }
        for (Object entry : raw) {
            out.add(entry == null ? "" : String.valueOf(entry));
        }
        return out;
    }
}
