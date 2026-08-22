package dev.jxriera.gavel.config;

import dev.jxriera.gavel.Gavel;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

public final class UpdateNotice implements Listener {

    private static final String FILE_NAME = "update-state.yml";
    private static final long JOIN_DELAY_TICKS = 40L;

    private final Gavel plugin;
    private final File file;

    private String previousVersion;
    private String currentVersion;
    private List<String> addedKeys = Collections.emptyList();
    private boolean pending;

    public UpdateNotice(Gavel plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), FILE_NAME);
    }

    public boolean isPending() {
        return pending;
    }

    public String getPreviousVersion() {
        return previousVersion;
    }

    public List<String> getAddedKeys() {
        return addedKeys;
    }

    public void check(String version, List<String> migrationAdditions) {
        this.currentVersion = version;
        YamlConfiguration state = read();
        String known = state.getString("last-version", null);
        boolean acknowledged = state.getBoolean("acknowledged", true);

        if (known == null) {
            this.pending = false;
            this.previousVersion = null;
            this.addedKeys = Collections.emptyList();
            write(version, true, Collections.<String>emptyList());
            return;
        }

        if (!known.equals(version)) {
            this.previousVersion = known;
            this.addedKeys = new ArrayList<String>(migrationAdditions);
            this.pending = true;
            write(version, false, this.addedKeys);
            announce();
            return;
        }

        this.previousVersion = state.getString("previous-version", known);
        this.addedKeys = state.getStringList("added-keys");
        this.pending = !acknowledged;
    }

    public void acknowledge() {
        this.pending = false;
        write(currentVersion, true, Collections.<String>emptyList());
    }

    public void notify(final Player player) {
        if (!pending || !player.hasPermission("gavel.admin")) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (pending && player.isOnline()) {
                    send(player);
                }
            }
        }, JOIN_DELAY_TICKS);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        notify(event.getPlayer());
    }

    private void send(Player player) {
        Messages messages = plugin.config().messages();
        messages.send(player, "update.notice", Messages.map(
                "previous", previousVersion == null ? "?" : previousVersion,
                "current", currentVersion,
                "added", String.valueOf(addedKeys.size())));
    }

    private void announce() {
        plugin.getLogger().warning("--------------------------------------------------");
        plugin.getLogger().warning("Gavel was updated: " + previousVersion + " -> " + currentVersion);
        if (addedKeys.isEmpty()) {
            plugin.getLogger().warning("No new configuration options were added.");
        } else {
            plugin.getLogger().warning(addedKeys.size() + " new option(s) were added to your"
                    + " configuration, review them: " + addedKeys);
        }
        plugin.getLogger().warning("Run /gavel ack to stop this reminder.");
        plugin.getLogger().warning("--------------------------------------------------");
    }

    private YamlConfiguration read() {
        if (!file.isFile()) {
            return new YamlConfiguration();
        }
        try {
            return YamlConfiguration.loadConfiguration(file);
        } catch (Throwable ex) {
            return new YamlConfiguration();
        }
    }

    private void write(String version, boolean acknowledged, List<String> keys) {
        try {
            YamlConfiguration state = new YamlConfiguration();
            state.set("last-version", version);
            state.set("previous-version", previousVersion);
            state.set("acknowledged", acknowledged);
            state.set("added-keys", keys);
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                return;
            }
            state.save(file);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Could not store the update state", ex);
        }
    }
}
