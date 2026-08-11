package dev.jxriera.gavel.gui;

import dev.jxriera.gavel.Gavel;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public abstract class Menu implements InventoryHolder {
    public interface ClickAction {
        void run(InventoryClickEvent event);
    }

    protected final Gavel plugin;
    protected final Player viewer;
    private final Map<Integer, ClickAction> actions = new HashMap<Integer, ClickAction>();
    private Inventory inventory;

    protected Menu(Gavel plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
    }

    protected abstract String title();

    protected abstract int size();

    protected abstract void build();

    @Override
    public Inventory getInventory() {
        if (inventory == null) {
            String rendered = title();
            try {
                inventory = Bukkit.createInventory(this, size(), rendered);
            } catch (Throwable ex) {
                inventory = Bukkit.createInventory(this, size(), truncate(rendered, 32));
            }
        }
        return inventory;
    }

    protected void set(int slot, ItemStack item, ClickAction action) {
        if (slot < 0 || slot >= size()) {
            return;
        }
        getInventory().setItem(slot, item);
        if (action == null) {
            actions.remove(slot);
        } else {
            actions.put(slot, action);
        }
    }

    protected void set(int slot, ItemStack item) {
        set(slot, item, null);
    }

    protected void clear() {
        getInventory().clear();
        actions.clear();
    }

    public void open() {
        clear();
        build();
        viewer.openInventory(getInventory());
    }

    public void refresh() {
        clear();
        build();
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (viewer.isOnline()) {
                    viewer.updateInventory();
                }
            }
        });
    }

    static String truncate(String input, int max) {
        if (input == null || input.length() <= max) {
            return input == null ? "" : input;
        }
        String cut = input.substring(0, max);
        while (!cut.isEmpty() && cut.charAt(cut.length() - 1) == ChatColor.COLOR_CHAR) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut;
    }

    void handleClick(InventoryClickEvent event) {
        ClickAction action = actions.get(event.getRawSlot());
        if (action != null) {
            action.run(event);
        }
    }
}
