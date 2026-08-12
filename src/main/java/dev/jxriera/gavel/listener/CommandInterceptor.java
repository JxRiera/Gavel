package dev.jxriera.gavel.listener;

import dev.jxriera.gavel.Gavel;
import dev.jxriera.gavel.config.ConfigManager;
import dev.jxriera.gavel.config.Messages;
import dev.jxriera.gavel.gui.PunishMenu;
import dev.jxriera.gavel.util.Sounds;
import dev.jxriera.gavel.util.Targets;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.RemoteServerCommandEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.Set;
import java.util.UUID;

public final class CommandInterceptor implements Listener {
    private final Gavel plugin;

    public CommandInterceptor(Gavel plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        ConfigManager config = plugin.config();
        if (!config.isInterceptEnabled()) {
            return;
        }
        Player staff = event.getPlayer();
        if (plugin.guard().isDispatching(staff.getUniqueId())) {
            return;
        }

        String message = event.getMessage();
        if (message == null || message.trim().length() < 2) {
            return;
        }
        String[] parts = message.trim().split("\\s+");
        if (!config.isIntercepted(parts[0])) {
            return;
        }
        if (!staff.hasPermission("gavel.use")) {
            return;
        }
        if (config.isAllowBypassPermission() && staff.hasPermission("gavel.bypass")) {
            return;
        }

        String targetName = firstNonFlag(parts);
        if (targetName == null) {
            if (config.isPassthroughWithoutTarget()) {
                return;
            }
            event.setCancelled(true);
            config.messages().send(staff, "unknown-player", Messages.map("target", ""));
            return;
        }

        event.setCancelled(true);
        if (!Targets.isValidName(targetName)) {
            config.messages().send(staff, "unknown-player", Messages.map("target", targetName));
            Sounds.play(staff, config.getSoundDeny());
            return;
        }
        openOverlay(staff, targetName);
    }

    private void openOverlay(final Player staff, final String targetName) {
        final ConfigManager config = plugin.config();
        final Messages messages = config.messages();

        if (config.isDenySelf() && targetName.equalsIgnoreCase(staff.getName())) {
            messages.send(staff, "cannot-punish-self");
            Sounds.play(staff, config.getSoundDeny());
            return;
        }
        if (config.visibleTo(staff).isEmpty()) {
            messages.send(staff, "no-categories");
            Sounds.play(staff, config.getSoundDeny());
            return;
        }

        messages.send(staff, "opening", Messages.map("target", targetName));

        Targets.resolve(plugin, targetName, new Targets.Callback() {
            @Override
            public void done(Targets.Resolved resolved) {
                if (!staff.isOnline()) {
                    return;
                }
                if (config.isRequireKnownPlayer() && !resolved.known) {
                    messages.send(staff, "unknown-player", Messages.map("target", targetName));
                    Sounds.play(staff, config.getSoundDeny());
                    return;
                }
                if (config.isDenySelf() && resolved.uuid != null
                        && resolved.uuid.equals(staff.getUniqueId())) {
                    messages.send(staff, "cannot-punish-self");
                    Sounds.play(staff, config.getSoundDeny());
                    return;
                }
                PunishMenu.open(plugin, staff, resolved.uuid, resolved.name);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerRevert(PlayerCommandPreprocessEvent event) {
        Player staff = event.getPlayer();
        handleRevert(staff, staff.getUniqueId(), event.getMessage());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsoleRevert(ServerCommandEvent event) {
        handleRevert(event.getSender(), null, event.getCommand());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRemoteRevert(RemoteServerCommandEvent event) {
        handleRevert(event.getSender(), null, event.getCommand());
    }

    private void handleRevert(final CommandSender sender, UUID guardKey, String rawCommand) {
        final ConfigManager config = plugin.config();
        if (!config.isRevertEnabled() || rawCommand == null) {
            return;
        }
        if (plugin.liteBans().isAvailable()) {
            return;
        }
        if (plugin.guard().isDispatching(guardKey)) {
            return;
        }
        String[] parts = rawCommand.trim().split("\\s+");
        if (parts.length == 0) {
            return;
        }
        final Set<String> types = config.getRevertTypes(parts[0]);
        if (types == null || types.isEmpty()) {
            return;
        }
        if (sender instanceof Player && config.isVerifyPermissions()) {
            String node = config.getRevertPermission(parts[0]);
            if (node != null && !sender.hasPermission(node)) {
                return;
            }
        }
        final String targetName = firstNonFlag(parts);
        if (!Targets.isValidName(targetName)) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                Targets.resolve(plugin, targetName, new Targets.Callback() {
                    @Override
                    public void done(Targets.Resolved resolved) {
                        UUID notify = sender instanceof Player ? ((Player) sender).getUniqueId() : null;
                        plugin.rollback().rollback(Targets.storageKey(resolved.uuid, resolved.name),
                                resolved.name, types, notify, notify == null);
                    }
                });
            }
        }, 1L);
    }

    private String firstNonFlag(String[] parts) {
        for (int index = 1; index < parts.length; index++) {
            String part = parts[index];
            if (part.isEmpty() || part.charAt(0) == '-') {
                continue;
            }
            return part;
        }
        return null;
    }
}
