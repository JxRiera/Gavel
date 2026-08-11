package dev.jxriera.gavel.util;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

public final class Sounds {
    private Sounds() {
    }

    public static void play(Player player, String name) {
        if (player == null || name == null || name.trim().isEmpty()) {
            return;
        }
        try {
            Sound sound = Sound.valueOf(name.trim().toUpperCase());
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (Throwable ignored) {
        }
    }
}
