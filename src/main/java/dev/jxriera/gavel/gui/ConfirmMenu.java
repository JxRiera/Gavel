package dev.jxriera.gavel.gui;

import dev.jxriera.gavel.Gavel;
import dev.jxriera.gavel.config.Messages;
import dev.jxriera.gavel.escalation.EscalationEngine;
import dev.jxriera.gavel.model.Category;
import dev.jxriera.gavel.model.IconSpec;
import dev.jxriera.gavel.model.Tier;
import dev.jxriera.gavel.util.Durations;
import dev.jxriera.gavel.util.Items;
import dev.jxriera.gavel.util.Sounds;
import dev.jxriera.gavel.util.Text;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ConfirmMenu extends Menu {
    private static final int INFO_SLOT = 13;
    private static final int DEFAULT_ACCEPT_SLOT = 11;
    private static final int DEFAULT_DENY_SLOT = 15;

    private final UUID targetId;
    private final String targetName;
    private final Category category;
    private final EscalationEngine.Result result;
    private final boolean silent;

    public ConfirmMenu(Gavel plugin, Player viewer, UUID targetId, String targetName,
                       Category category, EscalationEngine.Result result, boolean silent) {
        super(plugin, viewer);
        this.targetId = targetId;
        this.targetName = targetName;
        this.category = category;
        this.result = result;
        this.silent = silent;
    }

    @Override
    protected String title() {
        return Text.color(Text.apply(plugin.config().messages().raw("confirm.title"),
                Messages.map("target", targetName)));
    }

    @Override
    protected int size() {
        return 27;
    }

    @Override
    protected void build() {
        Messages messages = plugin.config().messages();
        Tier tier = result.getTier();

        Map<String, String> placeholders = Messages.map(
                "target", targetName,
                "category", category.getId(),
                "offense", String.valueOf(result.getOffenseNumber()),
                "type", messages.word(tier.getType().wordKey()),
                "duration", tier.getType().supportsDuration()
                        ? Durations.display(tier.getDuration(), messages.word("permanent"))
                        : messages.word("none"),
                "reason", tier.getReason(),
                "silent", messages.bool(silent));

        if (plugin.config().isFillerEnabled()) {
            ItemStack filler = Items.build(plugin.config().getFillerMaterial(),
                    plugin.config().getFillerName(), null);
            for (int slot = 0; slot < size(); slot++) {
                set(slot, filler);
            }
        }

        set(INFO_SLOT, render(messages.section("confirm.info"), Material.PAPER, placeholders));

        ConfigurationSection acceptSection = messages.section("confirm.accept");
        int acceptSlot = acceptSection == null
                ? DEFAULT_ACCEPT_SLOT
                : acceptSection.getInt("slot", DEFAULT_ACCEPT_SLOT);
        set(acceptSlot, render(acceptSection, Material.LIME_WOOL, placeholders), new ClickAction() {
            @Override
            public void run(InventoryClickEvent event) {
                viewer.closeInventory();
                plugin.punishments().apply(viewer, targetId, targetName, category,
                        result.getTier(), result.getOffenseNumber(), silent);
            }
        });

        ConfigurationSection denySection = messages.section("confirm.deny");
        int denySlot = denySection == null
                ? DEFAULT_DENY_SLOT
                : denySection.getInt("slot", DEFAULT_DENY_SLOT);
        set(denySlot, render(denySection, Material.RED_WOOL, placeholders), new ClickAction() {
            @Override
            public void run(InventoryClickEvent event) {
                Sounds.play(viewer, plugin.config().getSoundDeny());
                plugin.config().messages().send(viewer, "cancelled");
                PunishMenu.open(plugin, viewer, targetId, targetName);
            }
        });
    }

    private ItemStack render(ConfigurationSection section, Material fallback,
                             Map<String, String> placeholders) {
        IconSpec spec = IconSpec.from(section, fallback);
        List<String> lore = new ArrayList<String>();
        for (String line : spec.getLore()) {
            lore.add(Text.apply(line, placeholders));
        }
        return Items.build(spec.getMaterial(), Text.apply(spec.getName(), placeholders), lore,
                spec.isGlow(), spec.getAmount());
    }
}
