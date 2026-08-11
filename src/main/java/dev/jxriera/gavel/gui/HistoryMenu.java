package dev.jxriera.gavel.gui;

import dev.jxriera.gavel.Gavel;
import dev.jxriera.gavel.config.Messages;
import dev.jxriera.gavel.model.IconSpec;
import dev.jxriera.gavel.model.OffenseRecord;
import dev.jxriera.gavel.model.PunishmentType;
import dev.jxriera.gavel.util.Durations;
import dev.jxriera.gavel.util.Items;
import dev.jxriera.gavel.util.Sounds;
import dev.jxriera.gavel.util.Targets;
import dev.jxriera.gavel.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class HistoryMenu extends Menu {
    private static final int PAGE_SIZE = 45;
    private static final int SLOT_PREVIOUS = 45;
    private static final int SLOT_BACK = 49;
    private static final int SLOT_NEXT = 53;
    private static final int SLOT_EMPTY = 22;

    private final UUID targetId;
    private final String targetName;
    private final List<OffenseRecord> records;
    private int page;

    private HistoryMenu(Gavel plugin, Player viewer, UUID targetId, String targetName,
                        List<OffenseRecord> records, int page) {
        super(plugin, viewer);
        this.targetId = targetId;
        this.targetName = targetName;
        this.records = records;
        this.page = page;
    }

    public static void open(final Gavel plugin, final Player staff, final UUID targetId,
                            final String targetName) {
        final String key = Targets.storageKey(targetId, targetName);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                List<OffenseRecord> loaded;
                try {
                    loaded = plugin.database().find(key, false);
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
                        new HistoryMenu(plugin, staff, targetId, targetName, result, 0).open();
                        Sounds.play(staff, plugin.config().getSoundOpen());
                    }
                });
            }
        });
    }

    @Override
    protected String title() {
        return Text.color(Text.apply(plugin.config().messages().raw("history.title"),
                Messages.map("target", targetName)));
    }

    @Override
    protected int size() {
        return 54;
    }

    @Override
    protected void build() {
        Messages messages = plugin.config().messages();

        if (records.isEmpty()) {
            IconSpec empty = IconSpec.from(messages.section("history.empty"), Material.BARRIER);
            set(SLOT_EMPTY, Items.build(empty.getMaterial(), empty.getName(), empty.getLore()));
        } else {
            int from = page * PAGE_SIZE;
            int to = Math.min(records.size(), from + PAGE_SIZE);
            for (int index = from; index < to; index++) {
                set(index - from, entry(records.get(index), index + 1));
            }
        }

        if (page > 0) {
            IconSpec spec = IconSpec.from(messages.section("history.previous"), Material.ARROW);
            set(SLOT_PREVIOUS, Items.build(spec.getMaterial(), spec.getName(), spec.getLore()),
                    new ClickAction() {
                        @Override
                        public void run(InventoryClickEvent event) {
                            page--;
                            refresh();
                        }
                    });
        }
        if ((page + 1) * PAGE_SIZE < records.size()) {
            IconSpec spec = IconSpec.from(messages.section("history.next"), Material.ARROW);
            set(SLOT_NEXT, Items.build(spec.getMaterial(), spec.getName(), spec.getLore()),
                    new ClickAction() {
                        @Override
                        public void run(InventoryClickEvent event) {
                            page++;
                            refresh();
                        }
                    });
        }

        IconSpec back = IconSpec.from(messages.section("history.back"), Material.BARRIER);
        set(SLOT_BACK, Items.build(back.getMaterial(), back.getName(), back.getLore()),
                new ClickAction() {
                    @Override
                    public void run(InventoryClickEvent event) {
                        PunishMenu.open(plugin, viewer, targetId, targetName);
                    }
                });
    }

    private ItemStack entry(OffenseRecord record, int index) {
        Messages messages = plugin.config().messages();
        PunishmentType type = PunishmentType.parse(record.getType(), null);
        String typeWord = type == null ? String.valueOf(record.getType()) : messages.word(type.wordKey());
        String duration = type != null && !type.supportsDuration()
                ? messages.word("none")
                : Durations.display(record.getDuration(), messages.word("permanent"));

        String date;
        try {
            date = new SimpleDateFormat(messages.dateFormat()).format(new Date(record.getCreated()));
        } catch (Exception ex) {
            date = String.valueOf(record.getCreated());
        }

        Map<String, String> placeholders = Messages.map(
                "index", String.valueOf(index),
                "category", record.getCategory(),
                "tier", String.valueOf(record.getTier()),
                "type", typeWord,
                "duration", duration,
                "reason", record.getReason(),
                "staff", record.getStaffName() == null ? messages.word("unknown") : record.getStaffName(),
                "date", date,
                "server", record.getServer() == null ? messages.word("none") : record.getServer(),
                "silent", messages.bool(record.isSilent()),
                "active", messages.bool(record.isActive()));

        IconSpec spec = IconSpec.from(messages.section("history.entry"), Material.PAPER);
        List<String> lore = new ArrayList<String>();
        for (String line : spec.getLore()) {
            lore.add(Text.apply(line, placeholders));
        }
        return Items.build(spec.getMaterial(), Text.apply(spec.getName(), placeholders), lore,
                spec.isGlow(), spec.getAmount());
    }
}
