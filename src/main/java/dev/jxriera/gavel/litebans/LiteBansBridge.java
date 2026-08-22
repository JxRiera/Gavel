package dev.jxriera.gavel.litebans;

import dev.jxriera.gavel.Gavel;
import dev.jxriera.gavel.model.PunishmentType;
import dev.jxriera.gavel.util.Targets;
import litebans.api.Database;
import litebans.api.Entry;
import litebans.api.Events;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class LiteBansBridge {

    private static final long EXPIRY_CHECK_TICKS = 10L;
    private static final long LOOKUP_SLACK_MILLIS = 10_000L;

    private final Gavel plugin;
    private final Confirmations confirmations = new Confirmations();
    private Events.Listener listener;
    private BukkitTask expiryTask;
    private boolean available;

    public LiteBansBridge(Gavel plugin) {
        this.plugin = plugin;
    }

    public boolean isAvailable() {
        return available;
    }

    public void enable() {
        try {
            Events events = Events.get();
            if (events == null) {
                plugin.getLogger().warning("The LiteBans events API is not initialised; punishments"
                        + " will be recorded without confirmation.");
                return;
            }
            listener = new Events.Listener() {
                @Override
                public void entryAdded(Entry entry) {
                    onEntryAdded(entry);
                }

                @Override
                public void entryRemoved(Entry entry) {
                    onEntryRemoved(entry);
                }
            };
            events.register(listener);
            available = true;
        } catch (Throwable ex) {
            available = false;
            plugin.getLogger().log(Level.WARNING, "Could not hook the LiteBans API; punishments will"
                    + " be recorded without confirmation and rollbacks fall back to intercepting"
                    + " unban commands.", ex);
            return;
        }

        expiryTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                for (Confirmations.Pending expired : confirmations.expire(System.currentTimeMillis())) {
                    verify(expired);
                }
            }
        }, EXPIRY_CHECK_TICKS, EXPIRY_CHECK_TICKS);

        plugin.getLogger().info("Hooked the LiteBans API: punishments are confirmed against LiteBans"
                + " and rollbacks follow every unban, whatever issued it.");
    }

    public void disable() {
        if (expiryTask != null) {
            expiryTask.cancel();
            expiryTask = null;
        }
        if (listener != null && available) {
            try {
                Events.get().unregister(listener);
            } catch (Throwable ignored) {
                plugin.getLogger().warning("Could not unregister the LiteBans listener.");
            }
        }
        listener = null;
        available = false;
    }

    public void awaitConfirmation(UUID targetId, PunishmentType type, Confirmations.Callback callback) {
        long now = System.currentTimeMillis();
        confirmations.await(targetId, type, now, now + plugin.config().getConfirmTimeoutMillis(), callback);
    }

    public void cancelConfirmation(UUID targetId, PunishmentType type) {
        confirmations.cancel(targetId, type);
    }

    private void onEntryAdded(Entry entry) {
        try {
            debugEntry("entryAdded", entry);
            final Confirmations.Pending matched =
                    confirmations.confirm(entry.getUuid(), Confirmations.familyOf(entry.getType()));
            if (matched == null) {
                return;
            }
            complete(matched, true, flag(entry, true), flag(entry, false));
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to handle a LiteBans entryAdded event", ex);
        }
    }

    private void onEntryRemoved(Entry entry) {
        try {
            debugEntry("entryRemoved", entry);
            if (!plugin.config().isRevertEnabled()) {
                return;
            }
            if (!plugin.config().acceptsRemoval(entry.getRemovedByUUID(), entry.getRemovedByName())) {
                if (plugin.config().isDebug()) {
                    plugin.getLogger().info("Ignoring a removal by " + entry.getRemovedByName()
                            + ": tracking.external-removals does not accept it.");
                }
                return;
            }
            Set<String> types = Confirmations.typesOf(Confirmations.familyOf(entry.getType()));
            UUID targetId = parseUuid(entry.getUuid());
            if (types.isEmpty() || targetId == null) {
                return;
            }
            plugin.rollback().rollback(Targets.storageKey(targetId, targetId.toString()),
                    targetId.toString(), types, parseUuid(entry.getRemovedByUUID()), false);
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to handle a LiteBans entryRemoved event", ex);
        }
    }

    private void verify(final Confirmations.Pending pending) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                Entry found = null;
                try {
                    found = lookup(pending);
                } catch (Throwable ex) {
                    plugin.getLogger().log(Level.WARNING, "Could not ask LiteBans whether the "
                            + pending.getType() + " for " + pending.getTarget() + " exists", ex);
                }
                if (found != null && plugin.config().isDebug()) {
                    plugin.getLogger().info("No entryAdded event arrived for the " + pending.getType()
                            + " on " + pending.getTarget() + ", but LiteBans has the record.");
                }
                complete(pending, found != null, flag(found, true), flag(found, false));
            }
        });
    }

    private Entry lookup(Confirmations.Pending pending) {
        Database database = Database.get();
        if (database == null) {
            return null;
        }
        UUID target = pending.getTarget();
        String scope = Database.ANY_SERVER_SCOPE;
        Entry entry;
        switch (pending.getType()) {
            case BAN:
            case IPBAN:
                entry = database.getBan(target, null, scope);
                break;
            case MUTE:
                entry = database.getMute(target, null, scope);
                break;
            case WARN:
                entry = database.getWarning(target, null, scope);
                break;
            case KICK:
                entry = database.getKick(target, null, scope);
                break;
            default:
                return null;
        }
        if (entry == null || entry.getDateStart() < pending.getSince() - LOOKUP_SLACK_MILLIS) {
            return null;
        }
        return entry;
    }

    private static Boolean flag(Entry entry, boolean ipBan) {
        if (entry == null) {
            return null;
        }
        try {
            return ipBan ? entry.isIpban() : entry.isSilent();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void complete(final Confirmations.Pending pending, final boolean confirmed,
                          final Boolean ipBan, final Boolean silent) {
        try {
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    pending.getCallback().done(confirmed, ipBan, silent);
                }
            });
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.SEVERE, "The " + pending.getType() + " for "
                    + pending.getTarget() + " could not be finalised because the server is"
                    + " shutting down", ex);
        }
    }

    private void debugEntry(String event, Entry entry) {
        if (!plugin.config().isDebug()) {
            return;
        }
        String ip;
        boolean ipban;
        try {
            ip = entry.getIp();
            ipban = entry.isIpban();
        } catch (Throwable ignored) {
            ip = "?";
            ipban = false;
        }
        plugin.getLogger().info(event + ": type=" + entry.getType() + " uuid=" + entry.getUuid()
                + " ip=" + ip + " ipban=" + ipban + " reason='" + entry.getReason()
                + "' executor=" + entry.getExecutorName() + " pending=" + confirmations.size());
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

}
