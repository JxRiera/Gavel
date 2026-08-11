package dev.jxriera.gavel.util;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.regex.Pattern;

public final class Targets {
    public static final class Resolved {
        public final UUID uuid;
        public final String name;
        public final boolean known;

        Resolved(UUID uuid, String name, boolean known) {
            this.uuid = uuid;
            this.name = name;
            this.known = known;
        }
    }

    public interface Callback {
        void done(Resolved resolved);
    }

    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private Targets() {
    }

    public static boolean isValidName(String input) {
        return input != null && VALID_NAME.matcher(input).matches();
    }

    @SuppressWarnings("deprecation")
    public static void resolve(final Plugin plugin, final String input, final Callback callback) {
        Player online = Bukkit.getPlayerExact(input);
        if (online != null) {
            callback.done(new Resolved(online.getUniqueId(), online.getName(), true));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                UUID uuid = null;
                String name = input;
                boolean known = false;
                try {
                    OfflinePlayer offline = Bukkit.getOfflinePlayer(input);
                    if (offline != null) {
                        uuid = offline.getUniqueId();
                        if (offline.getName() != null) {
                            name = offline.getName();
                        }
                        known = offline.hasPlayedBefore() || offline.isOnline();
                    }
                } catch (Throwable ignored) {
                }
                final UUID resolvedUuid = uuid;
                final String resolvedName = name;
                final boolean resolvedKnown = known;
                Bukkit.getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        callback.done(new Resolved(resolvedUuid, resolvedName, resolvedKnown));
                    }
                });
            }
        });
    }

    public static String storageKey(UUID uuid, String name) {
        return uuid != null ? uuid.toString() : ("name:" + (name == null ? "" : name.toLowerCase()));
    }
}
