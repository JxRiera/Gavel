package dev.jxriera.gavel;

import dev.jxriera.gavel.command.GavelCommand;
import dev.jxriera.gavel.config.ConfigManager;
import dev.jxriera.gavel.gui.Menu;
import dev.jxriera.gavel.gui.MenuListener;
import dev.jxriera.gavel.listener.CommandInterceptor;
import dev.jxriera.gavel.punish.DispatchGuard;
import dev.jxriera.gavel.punish.PunishmentService;
import dev.jxriera.gavel.storage.Database;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.logging.Level;

public final class Gavel extends JavaPlugin {
    private ConfigManager config;
    private Database database;
    private PunishmentService punishments;
    private DispatchGuard guard;

    @Override
    public void onEnable() {
        this.guard = new DispatchGuard();
        this.config = new ConfigManager(this);

        try {
            config.load();
        } catch (Throwable ex) {
            getLogger().log(Level.SEVERE, "Configuration could not be read, disabling", ex);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        if (config.getCategories().isEmpty()) {
            getLogger().warning("No valid categories in categories.yml, the overlay will be empty.");
        }

        this.database = new Database(config.getDatabaseSettings());
        try {
            database.connect();
            getLogger().info("Storage ready: " + database.describe());
        } catch (SQLException ex) {
            getLogger().log(Level.SEVERE, "Could not connect to the database. Escalation cannot be "
                    + "calculated without the history, so Gavel is disabling itself.", ex);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        this.punishments = new PunishmentService(this);

        Bukkit.getPluginManager().registerEvents(new MenuListener(), this);
        Bukkit.getPluginManager().registerEvents(new CommandInterceptor(this), this);

        PluginCommand command = getCommand("gavel");
        if (command != null) {
            GavelCommand executor = new GavelCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        getLogger().info("Enabled with " + config.getCategories().size() + " categories, running commands as "
                + config.getExecuteAs() + ".");
    }

    @Override
    public void onDisable() {
        closeOpenMenus();
        if (database != null) {
            database.close();
        }
    }

    private void closeOpenMenus() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory() != null
                    && player.getOpenInventory().getTopInventory().getHolder() instanceof Menu) {
                player.closeInventory();
            }
        }
    }

    public void reconnectDatabase() throws SQLException {
        Database previous = this.database;
        Database replacement = new Database(config.getDatabaseSettings());
        replacement.connect();
        this.database = replacement;
        if (previous != null) {
            previous.close();
        }
        getLogger().info("Storage reconnected: " + replacement.describe());
    }

    public ConfigManager config() {
        return config;
    }

    public Database database() {
        return database;
    }

    public PunishmentService punishments() {
        return punishments;
    }

    public DispatchGuard guard() {
        return guard;
    }
}
