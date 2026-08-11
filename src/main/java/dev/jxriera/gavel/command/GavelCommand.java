package dev.jxriera.gavel.command;

import dev.jxriera.gavel.Gavel;
import dev.jxriera.gavel.config.ConfigManager;
import dev.jxriera.gavel.config.Messages;
import dev.jxriera.gavel.gui.HistoryMenu;
import dev.jxriera.gavel.gui.PunishMenu;
import dev.jxriera.gavel.model.Category;
import dev.jxriera.gavel.util.Sounds;
import dev.jxriera.gavel.util.Targets;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

public final class GavelCommand implements CommandExecutor, TabCompleter {
    private final Gavel plugin;

    public GavelCommand(Gavel plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages messages = plugin.config().messages();

        if (args.length == 0) {
            messages.send(sender, "usage");
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("reload")) {
            if (!sender.hasPermission("gavel.admin")) {
                messages.send(sender, "no-permission");
                return true;
            }
            reload(sender);
            return true;
        }

        if (sub.equals("history")) {
            if (!sender.hasPermission("gavel.history")) {
                messages.send(sender, "no-permission");
                return true;
            }
            if (!(sender instanceof Player)) {
                messages.send(sender, "players-only");
                return true;
            }
            if (args.length < 2) {
                messages.send(sender, "usage");
                return true;
            }
            openHistory((Player) sender, args[1]);
            return true;
        }

        if (sub.equals("clear")) {
            if (!sender.hasPermission("gavel.admin")) {
                messages.send(sender, "no-permission");
                return true;
            }
            if (args.length < 2) {
                messages.send(sender, "usage");
                return true;
            }
            clear(sender, args[1], args.length >= 3 ? args[2] : null);
            return true;
        }

        if (sub.equals("version")) {
            messages.send(sender, "version", Messages.map(
                    "version", plugin.getDescription().getVersion(),
                    "storage", plugin.database().describe()));
            return true;
        }

        if (!sender.hasPermission("gavel.use")) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (!(sender instanceof Player)) {
            messages.send(sender, "players-only");
            return true;
        }
        openOverlay((Player) sender, args[0]);
        return true;
    }

    private void reload(final CommandSender sender) {
        final Messages messages = plugin.config().messages();
        try {
            plugin.config().load();
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.SEVERE, "Reload failed", ex);
            messages.send(sender, "reload-failed");
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                try {
                    plugin.reconnectDatabase();
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.SEVERE, "Could not reconnect to the database", ex);
                }
            }
        });
        messages.send(sender, "reloaded",
                Messages.map("count", String.valueOf(plugin.config().getCategories().size())));
    }

    private void openOverlay(final Player staff, final String targetName) {
        final ConfigManager config = plugin.config();
        if (config.isDenySelf() && targetName.equalsIgnoreCase(staff.getName())) {
            config.messages().send(staff, "cannot-punish-self");
            return;
        }
        Targets.resolve(plugin, targetName, new Targets.Callback() {
            @Override
            public void done(Targets.Resolved resolved) {
                if (!staff.isOnline()) {
                    return;
                }
                if (config.isRequireKnownPlayer() && !resolved.known) {
                    config.messages().send(staff, "unknown-player", Messages.map("target", targetName));
                    Sounds.play(staff, config.getSoundDeny());
                    return;
                }
                PunishMenu.open(plugin, staff, resolved.uuid, resolved.name);
            }
        });
    }

    private void openHistory(final Player staff, final String targetName) {
        Targets.resolve(plugin, targetName, new Targets.Callback() {
            @Override
            public void done(Targets.Resolved resolved) {
                if (staff.isOnline()) {
                    HistoryMenu.open(plugin, staff, resolved.uuid, resolved.name);
                }
            }
        });
    }

    private void clear(final CommandSender sender, final String targetName, final String rawCategory) {
        final Messages messages = plugin.config().messages();
        final String category;
        if (rawCategory == null) {
            category = null;
        } else {
            Category found = plugin.config().getCategory(rawCategory);
            if (found == null) {
                messages.send(sender, "usage");
                return;
            }
            category = found.getId();
        }

        Targets.resolve(plugin, targetName, new Targets.Callback() {
            @Override
            public void done(final Targets.Resolved resolved) {
                final String key = Targets.storageKey(resolved.uuid, resolved.name);
                Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
                    @Override
                    public void run() {
                        int affected;
                        try {
                            affected = plugin.database().deactivate(key, category);
                        } catch (Exception ex) {
                            plugin.getLogger().log(Level.SEVERE, "Could not clear the history", ex);
                            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                                @Override
                                public void run() {
                                    messages.send(sender, "db-error");
                                }
                            });
                            return;
                        }
                        final int count = affected;
                        Bukkit.getScheduler().runTask(plugin, new Runnable() {
                            @Override
                            public void run() {
                                messages.send(sender, "cleared", Messages.map(
                                        "count", String.valueOf(count),
                                        "target", resolved.name));
                            }
                        });
                    }
                });
            }
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<String>();
        if (args.length == 1) {
            List<String> options = new ArrayList<String>();
            if (sender.hasPermission("gavel.admin")) {
                options.add("reload");
                options.add("clear");
            }
            if (sender.hasPermission("gavel.history")) {
                options.add("history");
            }
            options.add("version");
            for (Player online : Bukkit.getOnlinePlayers()) {
                options.add(online.getName());
            }
            String prefix = args[0].toLowerCase();
            for (String option : options) {
                if (option.toLowerCase().startsWith(prefix)) {
                    out.add(option);
                }
            }
            return out;
        }
        if (args.length == 2) {
            String prefix = args[1].toLowerCase();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase().startsWith(prefix)) {
                    out.add(online.getName());
                }
            }
            return out;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("clear")) {
            String prefix = args[2].toLowerCase();
            for (String id : plugin.config().getCategories().keySet()) {
                if (id.startsWith(prefix)) {
                    out.add(id);
                }
            }
            return out;
        }
        return Collections.emptyList();
    }
}
