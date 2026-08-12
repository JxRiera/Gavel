package dev.jxriera.gavel.punish;

import dev.jxriera.gavel.Gavel;
import dev.jxriera.gavel.config.ConfigManager;
import dev.jxriera.gavel.config.Messages;
import dev.jxriera.gavel.escalation.EscalationEngine;
import dev.jxriera.gavel.litebans.Confirmations;
import dev.jxriera.gavel.model.Category;
import dev.jxriera.gavel.model.OffenseRecord;
import dev.jxriera.gavel.model.Tier;
import dev.jxriera.gavel.util.Durations;
import dev.jxriera.gavel.util.Sounds;
import dev.jxriera.gavel.util.Targets;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class PunishmentService {

    private final Gavel plugin;
    private final DuplicateGuard duplicates = new DuplicateGuard();

    public PunishmentService(Gavel plugin) {
        this.plugin = plugin;
    }

    public void apply(final Player staff, final UUID targetId, final String targetName,
                      final Category category, final boolean silent) {
        final ConfigManager config = plugin.config();
        final Messages messages = config.messages();
        final String key = Targets.storageKey(targetId, targetName);

        if (!duplicates.tryBegin(key, config.getDuplicateWindowMillis())) {
            messages.send(staff, "duplicate-punishment", Messages.map("target", targetName));
            Sounds.play(staff, config.getSoundDeny());
            return;
        }

        try {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
                @Override
                public void run() {
                    resolveAndDispatch(staff, targetId, targetName, category, silent, key);
                }
            });
        } catch (Throwable ex) {
            duplicates.finish(key, false);
            plugin.getLogger().log(Level.SEVERE, "Could not schedule the punishment lookup", ex);
        }
    }

    private void resolveAndDispatch(final Player staff, final UUID targetId, final String targetName,
                                    final Category category, final boolean silent, final String key) {
        final ConfigManager config = plugin.config();
        List<OffenseRecord> history;
        try {
            history = plugin.database().find(key, true);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not read the history of " + targetName, ex);
            duplicates.finish(key, false);
            notifyDatabaseError(staff);
            return;
        }
        final EscalationEngine.Result result =
                EscalationEngine.resolve(category, history, config.getOverflow());
        try {
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    boolean applied = dispatchResolved(staff, targetId, targetName, category,
                            result, silent);
                    duplicates.finish(key, applied);
                }
            });
        } catch (Throwable ex) {
            duplicates.finish(key, false);
            plugin.getLogger().log(Level.SEVERE, "Could not schedule the punishment dispatch", ex);
        }
    }

    private void notifyDatabaseError(final Player staff) {
        try {
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    plugin.config().messages().send(staff, "db-error");
                    Sounds.play(staff, plugin.config().getSoundDeny());
                }
            });
        } catch (Throwable ignored) {
            plugin.getLogger().warning("Could not notify " + staff.getName() + " of the database error.");
        }
    }

    private boolean dispatchResolved(Player staff, UUID targetId, String targetName,
                                     Category category, EscalationEngine.Result result,
                                     boolean silent) {
        ConfigManager config = plugin.config();
        Messages messages = config.messages();
        Tier tier = result.getTier();

        if (!staff.isOnline()) {
            plugin.getLogger().warning(staff.getName() + " went offline before the punishment for "
                    + targetName + " could be applied, nothing was done.");
            return false;
        }

        String templateKey = tier.getType().templateKey(tier.isPermanent());
        String template = config.getCommandTemplate(templateKey);
        if (template == null || template.trim().isEmpty()) {
            plugin.getLogger().severe("Missing template execution.commands." + templateKey
                    + " in config.yml, nothing was applied.");
            messages.send(staff, "template-missing", Messages.map("template", templateKey));
            Sounds.play(staff, config.getSoundDeny());
            return false;
        }

        if (config.getExecuteAs() == ConfigManager.ExecuteAs.PLAYER && config.isVerifyPermissions()) {
            String node = config.getLiteBansPermission(tier.getType());
            if (node != null && !staff.hasPermission(node)) {
                messages.send(staff, "missing-litebans-permission", Messages.map("permission", node));
                Sounds.play(staff, config.getSoundDeny());
                return false;
            }
        }

        boolean effectiveSilent = silent && staff.hasPermission("gavel.silent");
        if (silent && !effectiveSilent) {
            messages.send(staff, "silent-no-permission");
        }
        String flags = effectiveSilent ? config.getSilentFlag().trim() + " " : "";

        Map<String, String> placeholders = new HashMap<String, String>();
        placeholders.put("target", targetName);
        placeholders.put("duration", tier.getDuration());
        placeholders.put("reason", tier.getReason());
        placeholders.put("flags", flags);

        String command = buildCommand(template, placeholders);

        if (config.isDebug()) {
            plugin.getLogger().info(staff.getName() + " -> /" + command);
        }

        final boolean confirm = config.isConfirmWithApi()
                && targetId != null
                && plugin.liteBans().isAvailable();
        if (confirm) {
            plugin.liteBans().awaitConfirmation(targetId, tier.getType(),
                    confirmation(staff, targetId, targetName, category, tier, result, effectiveSilent));
        }

        if (!dispatch(staff, command)) {
            if (confirm) {
                plugin.liteBans().cancelConfirmation(targetId, tier.getType());
            }
            Sounds.play(staff, config.getSoundDeny());
            return false;
        }

        if (!confirm) {
            onConfirmed(staff, targetId, targetName, category, tier, result, effectiveSilent);
        }
        return true;
    }

    private Confirmations.Callback confirmation(final Player staff, final UUID targetId,
                                                final String targetName, final Category category,
                                                final Tier tier, final EscalationEngine.Result result,
                                                final boolean effectiveSilent) {
        return new Confirmations.Callback() {
            @Override
            public void done(boolean confirmed) {
                if (confirmed) {
                    onConfirmed(staff, targetId, targetName, category, tier, result, effectiveSilent);
                    return;
                }
                plugin.getLogger().warning("LiteBans never registered the " + tier.getType()
                        + " for " + targetName + " requested by " + staff.getName()
                        + "; the offence was not recorded.");
                plugin.config().messages().send(staff, "not-applied",
                        Messages.map("target", targetName));
                Sounds.play(staff, plugin.config().getSoundDeny());
            }
        };
    }

    private void onConfirmed(Player staff, UUID targetId, String targetName, Category category,
                             Tier tier, EscalationEngine.Result result, boolean effectiveSilent) {
        Messages messages = plugin.config().messages();

        runPostCommands(staff, targetName, category, tier, result.getOffenseNumber());
        record(staff, targetId, targetName, category, tier, effectiveSilent);

        String durationText = tier.getType().supportsDuration()
                ? Durations.display(tier.getDuration(), messages.word("permanent"))
                : "";
        messages.send(staff, effectiveSilent ? "applied-silent" : "applied", Messages.map(
                "target", targetName,
                "type", messages.word(tier.getType().wordKey()),
                "duration", durationText,
                "reason", tier.getReason(),
                "category", category.getId(),
                "offense", String.valueOf(result.getOffenseNumber())));
        Sounds.play(staff, plugin.config().getSoundApply());
    }

    private boolean dispatch(Player staff, String command) {
        boolean asConsole = plugin.config().getExecuteAs() == ConfigManager.ExecuteAs.CONSOLE;
        CommandSender sender = asConsole ? Bukkit.getConsoleSender() : staff;
        UUID guardKey = asConsole ? null : staff.getUniqueId();

        plugin.guard().enter(guardKey);
        try {
            return Bukkit.dispatchCommand(sender, command);
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to run '/" + command + "'", ex);
            return false;
        } finally {
            plugin.guard().exit(guardKey);
        }
    }

    private void runPostCommands(Player staff, String targetName, Category category, Tier tier,
                                 int offenseNumber) {
        if (plugin.config().getPostCommands().isEmpty()) {
            return;
        }
        Map<String, String> placeholders = new HashMap<String, String>();
        placeholders.put("target", targetName);
        placeholders.put("staff", staff.getName());
        placeholders.put("category", category.getId());
        placeholders.put("type", tier.getType().name());
        placeholders.put("duration", tier.getDuration());
        placeholders.put("reason", tier.getReason());
        placeholders.put("offense", String.valueOf(offenseNumber));

        plugin.guard().enter(null);
        try {
            for (String raw : plugin.config().getPostCommands()) {
                if (raw == null || raw.trim().isEmpty()) {
                    continue;
                }
                String command = buildCommand(raw, placeholders);
                try {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                } catch (Throwable ex) {
                    plugin.getLogger().log(Level.WARNING, "Post-command failed: /" + command, ex);
                }
            }
        } finally {
            plugin.guard().exit(null);
        }
    }

    private void record(Player staff, UUID targetId, String targetName, Category category, Tier tier,
                        boolean silent) {
        final OffenseRecord record = new OffenseRecord(
                0L,
                Targets.storageKey(targetId, targetName),
                targetName,
                category.getId(),
                tier.getNumber(),
                tier.getType().name(),
                tier.getDuration(),
                tier.getReason(),
                staff.getUniqueId().toString(),
                staff.getName(),
                plugin.config().getServerName(),
                silent,
                true,
                System.currentTimeMillis());

        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                try {
                    plugin.database().insert(record);
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.SEVERE, "Could not store the offence", ex);
                }
            }
        });
    }

    static String buildCommand(String template, Map<String, String> placeholders) {
        if (template == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (String token : template.trim().split("\\s+")) {
            String filled = fill(token, placeholders);
            if (filled.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(filled);
        }
        return out.toString();
    }

    private static String fill(String template, Map<String, String> placeholders) {
        String out = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String value = entry.getValue() == null ? "" : entry.getValue();
            out = out.replace("%" + entry.getKey() + "%", value);
        }
        return out;
    }
}
