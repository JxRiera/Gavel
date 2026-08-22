package dev.jxriera.gavel.stats;

import dev.jxriera.gavel.Gavel;
import dev.jxriera.gavel.escalation.EscalationEngine;
import dev.jxriera.gavel.model.Category;
import dev.jxriera.gavel.model.OffenseRecord;
import dev.jxriera.gavel.util.Targets;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class OffenseCache implements Listener {

    private final Gavel plugin;
    private final Map<UUID, Map<String, Integer>> counts = new ConcurrentHashMap<UUID, Map<String, Integer>>();
    private final Set<UUID> loading = ConcurrentHashMap.newKeySet();

    public OffenseCache(Gavel plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        refresh(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        counts.remove(event.getPlayer().getUniqueId());
    }

    public boolean isCached(UUID uuid) {
        return uuid != null && counts.containsKey(uuid);
    }

    public int count(UUID uuid, String categoryId) {
        Map<String, Integer> cached = uuid == null ? null : counts.get(uuid);
        if (cached == null || categoryId == null) {
            return 0;
        }
        Integer value = cached.get(categoryId.toLowerCase());
        return value == null ? 0 : value;
    }

    public int total(UUID uuid) {
        Map<String, Integer> cached = uuid == null ? null : counts.get(uuid);
        if (cached == null) {
            return 0;
        }
        int total = 0;
        for (Integer value : cached.values()) {
            total += value;
        }
        return total;
    }

    public EscalationEngine.Result next(UUID uuid, Category category) {
        return EscalationEngine.resolveFromCount(category, count(uuid, category.getId()),
                plugin.config().getOverflow());
    }

    public void refresh(final UUID uuid, final String name) {
        if (uuid == null || !loading.add(uuid)) {
            return;
        }
        final String key = Targets.storageKey(uuid, name);
        try {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
                @Override
                public void run() {
                    try {
                        counts.put(uuid, tally(plugin.database().find(key, true)));
                    } catch (Exception ex) {
                        plugin.getLogger().log(Level.WARNING,
                                "Could not refresh the cached offence counts of " + name, ex);
                    } finally {
                        loading.remove(uuid);
                    }
                }
            });
        } catch (Throwable ex) {
            loading.remove(uuid);
        }
    }

    public void refresh(UUID uuid) {
        if (uuid == null) {
            return;
        }
        Player online = Bukkit.getPlayer(uuid);
        refresh(uuid, online == null ? uuid.toString() : online.getName());
    }

    private Map<String, Integer> tally(List<OffenseRecord> history) {
        Map<String, Integer> tally = new HashMap<String, Integer>();
        long now = System.currentTimeMillis();
        for (Category category : plugin.config().getCategories().values()) {
            tally.put(category.getId(), EscalationEngine.count(category, history, now));
        }
        return Collections.unmodifiableMap(tally);
    }
}
