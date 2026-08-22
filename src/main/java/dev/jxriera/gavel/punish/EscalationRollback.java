package dev.jxriera.gavel.punish;

import dev.jxriera.gavel.Gavel;
import dev.jxriera.gavel.config.Messages;
import dev.jxriera.gavel.model.OffenseRecord;
import dev.jxriera.gavel.util.Durations;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public final class EscalationRollback {

    private final Gavel plugin;

    public EscalationRollback(Gavel plugin) {
        this.plugin = plugin;
    }

    public void rollback(final String storageKey, final String fallbackName,
                         final Collection<String> types, final UUID notifyPlayerId,
                         final boolean notifyConsole) {
        final UUID targetId = parseUuid(storageKey);
        if (storageKey == null || types == null || types.isEmpty()) {
            return;
        }
        final boolean all = plugin.config().isRevertAll();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                List<Long> ids = new ArrayList<Long>();
                String displayName = fallbackName;
                int affected;
                try {
                    for (OffenseRecord record : plugin.database().find(storageKey, true)) {
                        if (record.getType() == null
                                || !types.contains(record.getType().toUpperCase())) {
                            continue;
                        }
                        if (Durations.hasElapsed(record.getDuration(), record.getCreated(), now)) {
                            continue;
                        }
                        if (ids.isEmpty() && record.getName() != null) {
                            displayName = record.getName();
                        }
                        ids.add(record.getId());
                        if (!all) {
                            break;
                        }
                    }
                    affected = plugin.database().deactivateIds(ids);
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.SEVERE,
                            "Could not roll back the escalation of " + fallbackName, ex);
                    return;
                }
                if (affected == 0) {
                    if (plugin.config().isDebug()) {
                        plugin.getLogger().info("Nothing to roll back for " + fallbackName
                                + ": no active, unexpired offence of type " + types + ".");
                    }
                    return;
                }
                plugin.cache().refresh(targetId);
                java.util.Map<String, String> hook = new java.util.HashMap<String, String>();
                hook.put("target", displayName);
                hook.put("target_uuid", targetId == null ? "" : targetId.toString());
                hook.put("count", String.valueOf(affected));
                hook.put("server", plugin.config().getServerName());
                hook.put("date", dev.jxriera.gavel.webhook.Webhook.now(
                        plugin.config().messages().dateFormat()));
                plugin.webhook().send("reverted", hook);
                announce(displayName, affected, notifyPlayerId, notifyConsole);
            }
        });
    }

    private static UUID parseUuid(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void announce(final String displayName, final int affected, final UUID notifyPlayerId,
                          final boolean notifyConsole) {
        if (plugin.config().isDebug()) {
            plugin.getLogger().info("Rolled back " + affected + " offence(s) for " + displayName + ".");
        }
        if (notifyPlayerId == null && !notifyConsole) {
            return;
        }
        try {
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    CommandSender target = null;
                    if (notifyPlayerId != null) {
                        Player online = Bukkit.getPlayer(notifyPlayerId);
                        if (online != null && online.isOnline()) {
                            target = online;
                        }
                    } else {
                        target = Bukkit.getConsoleSender();
                    }
                    if (target != null) {
                        plugin.config().messages().send(target, "reverted", Messages.map(
                                "target", displayName,
                                "count", String.valueOf(affected)));
                    }
                }
            });
        } catch (Throwable ignored) {
            plugin.getLogger().info("Rolled back " + affected + " offence(s) for " + displayName + ".");
        }
    }
}
