package dev.jxriera.gavel.gui;

import dev.jxriera.gavel.Gavel;
import dev.jxriera.gavel.config.ConfigManager;
import dev.jxriera.gavel.config.Messages;
import dev.jxriera.gavel.escalation.EscalationEngine;
import dev.jxriera.gavel.model.Category;
import dev.jxriera.gavel.model.IconSpec;
import dev.jxriera.gavel.model.OffenseRecord;
import dev.jxriera.gavel.model.Tier;
import dev.jxriera.gavel.util.Durations;
import dev.jxriera.gavel.util.Items;
import dev.jxriera.gavel.util.Sounds;
import dev.jxriera.gavel.util.Targets;
import dev.jxriera.gavel.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class PunishMenu extends Menu {
    private final UUID targetId;
    private final String targetName;
    private final List<OffenseRecord> history;

    public PunishMenu(Gavel plugin, Player viewer, UUID targetId, String targetName,
                      List<OffenseRecord> history) {
        super(plugin, viewer);
        this.targetId = targetId;
        this.targetName = targetName;
        this.history = history == null ? Collections.<OffenseRecord>emptyList() : history;
    }

    public static void open(final Gavel plugin, final Player staff, final UUID targetId,
                            final String targetName) {
        final String key = Targets.storageKey(targetId, targetName);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                List<OffenseRecord> loaded;
                try {
                    loaded = plugin.database().find(key, true);
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.SEVERE, "Could not read the history of " + targetName, ex);
                    Bukkit.getScheduler().runTask(plugin, new Runnable() {
                        @Override
                        public void run() {
                            plugin.config().messages().send(staff, "db-error");
                            Sounds.play(staff, plugin.config().getSoundDeny());
                        }
                    });
                    return;
                }
                final List<OffenseRecord> result = loaded;
                Bukkit.getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        if (!staff.isOnline()) {
                            return;
                        }
                        new PunishMenu(plugin, staff, targetId, targetName, result).open();
                        Sounds.play(staff, plugin.config().getSoundOpen());
                    }
                });
            }
        });
    }

    @Override
    protected String title() {
        return Text.color(Text.apply(plugin.config().getMenuTitle(), Messages.map("target", targetName)));
    }

    @Override
    protected int size() {
        return plugin.config().getMenuRows() * 9;
    }

    @Override
    protected void build() {
        ConfigManager config = plugin.config();

        if (config.isFillerEnabled()) {
            ItemStack filler = Items.build(config.getFillerMaterial(), config.getFillerName(), null);
            for (int slot = 0; slot < size(); slot++) {
                set(slot, filler);
            }
        }

        for (final Category category : config.visibleTo(viewer)) {
            final EscalationEngine.Result result = EscalationEngine.resolve(category, history, config.getOverflow());
            set(category.getSlot(), icon(category, result), new ClickAction() {
                @Override
                public void run(InventoryClickEvent event) {
                    handleCategory(category, result, event.isShiftClick());
                }
            });
        }

        if (config.getHistorySlot() >= 0 && viewer.hasPermission("gavel.history")) {
            IconSpec spec = config.getHistoryIcon();
            Map<String, String> placeholders = Messages.map(
                    "target", targetName,
                    "total", String.valueOf(history.size()));
            set(config.getHistorySlot(), render(spec, placeholders), new ClickAction() {
                @Override
                public void run(InventoryClickEvent event) {
                    HistoryMenu.open(plugin, viewer, targetId, targetName);
                }
            });
        }

        if (config.getCloseSlot() >= 0) {
            IconSpec spec = config.getCloseIcon();
            set(config.getCloseSlot(), render(spec, null), new ClickAction() {
                @Override
                public void run(InventoryClickEvent event) {
                    viewer.closeInventory();
                }
            });
        }
    }

    private void handleCategory(Category category, EscalationEngine.Result result, boolean silent) {
        if (category.getPermission() != null && !viewer.hasPermission(category.getPermission())) {
            plugin.config().messages().send(viewer, "category-no-permission",
                    Messages.map("category", category.getId()));
            Sounds.play(viewer, plugin.config().getSoundDeny());
            return;
        }
        Tier tier = result.getTier();
        boolean needsConfirm = plugin.config().isConfirmEnabled()
                && (!plugin.config().isConfirmOnlyPermanent() || tier.isPermanent());

        if (needsConfirm) {
            new ConfirmMenu(plugin, viewer, targetId, targetName, category, result, silent).open();
            return;
        }
        viewer.closeInventory();
        plugin.punishments().apply(viewer, targetId, targetName, category, silent);
    }

    private ItemStack icon(Category category, EscalationEngine.Result result) {
        Messages messages = plugin.config().messages();
        Tier next = result.getTier();

        Map<String, String> placeholders = new HashMap<String, String>();
        placeholders.put("target", targetName);
        placeholders.put("category", category.getId());
        placeholders.put("offenses", String.valueOf(result.getPreviousOffenses()));
        placeholders.put("next_number", String.valueOf(next.getNumber()));
        placeholders.put("next_type", messages.word(next.getType().wordKey()));
        placeholders.put("next_duration", durationOf(next));
        placeholders.put("next_reason", next.getReason());

        IconSpec spec = category.getIcon();
        List<String> lore = new ArrayList<String>();
        for (String line : spec.getLore()) {
            if (line != null && line.contains("%tiers%")) {
                lore.addAll(tierLines(category, next));
            } else {
                lore.add(Text.apply(line, placeholders));
            }
        }
        return Items.build(spec.getMaterial(), Text.apply(spec.getName(), placeholders), lore,
                spec.isGlow(), spec.getAmount());
    }

    private List<String> tierLines(Category category, Tier next) {
        Messages messages = plugin.config().messages();
        List<String> lines = new ArrayList<String>();
        for (Tier tier : category.getTiers()) {
            String format = tier.getNumber() == next.getNumber()
                    ? plugin.config().getTierFormatNext()
                    : plugin.config().getTierFormat();
            lines.add(Text.apply(format, Messages.map(
                    "number", String.valueOf(tier.getNumber()),
                    "type", messages.word(tier.getType().wordKey()),
                    "duration", durationOf(tier),
                    "reason", tier.getReason())));
        }
        return lines;
    }

    private String durationOf(Tier tier) {
        Messages messages = plugin.config().messages();
        return tier.getType().supportsDuration()
                ? Durations.display(tier.getDuration(), messages.word("permanent"))
                : messages.word("none");
    }

    private ItemStack render(IconSpec spec, Map<String, String> placeholders) {
        List<String> lore = new ArrayList<String>();
        for (String line : spec.getLore()) {
            lore.add(Text.apply(line, placeholders));
        }
        return Items.build(spec.getMaterial(), Text.apply(spec.getName(), placeholders), lore,
                spec.isGlow(), spec.getAmount());
    }
}
