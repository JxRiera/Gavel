package dev.jxriera.gavel.config;

import dev.jxriera.gavel.Gavel;
import dev.jxriera.gavel.escalation.Overflow;
import dev.jxriera.gavel.model.Category;
import dev.jxriera.gavel.model.IconSpec;
import dev.jxriera.gavel.model.PunishmentType;
import dev.jxriera.gavel.model.Tier;
import dev.jxriera.gavel.storage.Database;
import dev.jxriera.gavel.storage.LiteBansSqlConfig;
import dev.jxriera.gavel.storage.SqlDialect;
import dev.jxriera.gavel.util.Durations;
import dev.jxriera.gavel.util.Items;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

public final class ConfigManager {
    private static final Set<String> MIGRATED =
            new HashSet<String>(Arrays.asList("config.yml", "messages.yml"));

    public enum ExecuteAs {
        PLAYER,
        CONSOLE
    }


    private final Gavel plugin;
    private final Messages messages = new Messages();
    private final List<String> migrationAdditions = new ArrayList<String>();

    private String serverName = "main";
    private boolean debug;

    private boolean interceptEnabled = true;
    private Set<String> interceptCommands = new HashSet<String>();
    private boolean allowBypassPermission = true;
    private boolean denySelf = true;
    private boolean passthroughWithoutTarget = true;
    private boolean requireKnownPlayer = true;

    private ExecuteAs executeAs = ExecuteAs.PLAYER;
    private Map<String, String> commandTemplates = new LinkedHashMap<String, String>();
    private String silentFlag = "-s";
    private String broadcastFlag = "";
    private boolean liteBansIpBansByDefault;
    private boolean liteBansSilentByDefault;
    private List<String> postCommands = new ArrayList<String>();
    private Map<String, String> undoCommands = new LinkedHashMap<String, String>();
    private boolean webhookEnabled;
    private String webhookUrl = "";
    private boolean webhookIncludeSilent = true;
    private int webhookTimeoutMillis = 5000;
    private Map<String, String> webhookPayloads = new LinkedHashMap<String, String>();
    private long duplicateWindowMillis = 5000L;
    private boolean confirmWithApi = true;
    private long confirmTimeoutMillis = 3000L;
    private boolean verifyPermissions = true;
    private Map<String, String> liteBansPermissions = new LinkedHashMap<String, String>();

    private String externalRemovals = "ALL";
    private Set<String> ignoredExecutors = new HashSet<String>();

    private boolean revertEnabled = true;
    private boolean revertAll;
    private Map<String, Set<String>> revertCommands = new LinkedHashMap<String, Set<String>>();
    private Map<String, String> revertPermissions = new LinkedHashMap<String, String>();

    private boolean confirmEnabled = true;
    private boolean confirmOnlyPermanent;

    private long globalExpireMillis = -1L;
    private Overflow overflow = Overflow.LAST;

    private String menuTitle = "";
    private int menuRows = 5;
    private boolean fillerEnabled = true;
    private Material fillerMaterial = Material.STONE;
    private String fillerName = " ";
    private String tierFormat = "";
    private String tierFormatNext = "";
    private int historySlot = -1;
    private IconSpec historyIcon;
    private int closeSlot = -1;
    private IconSpec closeIcon;

    private Map<String, Category> categories = new LinkedHashMap<String, Category>();
    private Database.Settings databaseSettings = new Database.Settings();

    private String soundOpen = "";
    private String soundApply = "";
    private String soundDeny = "";

    public ConfigManager(Gavel plugin) {
        this.plugin = plugin;
    }

    public Messages messages() {
        return messages;
    }

    public List<String> getMigrationAdditions() {
        return migrationAdditions;
    }

    public void load() {
        migrationAdditions.clear();
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        messages.bind(loadYaml("messages.yml"));
        FileConfiguration categoriesFile = loadYaml("categories.yml");

        serverName = config.getString("server-name", "main");
        debug = config.getBoolean("debug", false);

        interceptEnabled = config.getBoolean("intercept.enabled", true);
        interceptCommands = new HashSet<String>();
        for (String raw : config.getStringList("intercept.commands")) {
            if (raw != null && !raw.trim().isEmpty()) {
                interceptCommands.add(normalizeLabel(raw));
            }
        }
        allowBypassPermission = config.getBoolean("intercept.allow-bypass-permission", true);
        denySelf = config.getBoolean("intercept.deny-self", true);
        passthroughWithoutTarget = config.getBoolean("intercept.passthrough-without-target", true);
        requireKnownPlayer = config.getBoolean("intercept.require-known-player", true);

        executeAs = ExecuteAs.valueOf(readChoice("execution.execute-as",
                config.getString("execution.execute-as", "PLAYER"), "PLAYER", "PLAYER", "CONSOLE"));
        commandTemplates = readStringMap(config.getConfigurationSection("execution.commands"), false);
        silentFlag = config.getString("execution.silent-flag", "-s");
        broadcastFlag = config.getString("execution.broadcast-flag", "");
        liteBansIpBansByDefault = config.getBoolean("execution.litebans-defaults.ip-bans", false);
        liteBansSilentByDefault = config.getBoolean("execution.litebans-defaults.silent", false);
        postCommands = config.getStringList("execution.post-commands");
        undoCommands = readStringMap(config.getConfigurationSection("execution.undo-commands"), true);
        webhookEnabled = config.getBoolean("webhook.enabled", false);
        webhookUrl = config.getString("webhook.url", "");
        webhookIncludeSilent = config.getBoolean("webhook.include-silent", true);
        webhookTimeoutMillis = Math.max(500, config.getInt("webhook.timeout-ms", 5000));
        webhookPayloads = readStringMap(config.getConfigurationSection("webhook.payloads"), false);
        duplicateWindowMillis = Math.max(0, config.getInt("execution.duplicate-window-seconds", 5)) * 1000L;
        confirmWithApi = config.getBoolean("execution.confirm-with-api", true);
        confirmTimeoutMillis = Math.max(250L, config.getLong("execution.confirm-timeout-ms", 3000L));
        verifyPermissions = config.getBoolean("execution.verify-permissions", true);
        liteBansPermissions = readStringMap(config.getConfigurationSection("execution.permissions"), true);

        externalRemovals = readChoice("tracking.external-removals",
                config.getString("tracking.external-removals", "ALL"), "ALL", "ALL", "PLAYERS", "GAVEL");
        ignoredExecutors = new HashSet<String>();
        for (String raw : config.getStringList("tracking.ignored-executors")) {
            if (raw != null && !raw.trim().isEmpty()) {
                ignoredExecutors.add(raw.trim().toLowerCase(Locale.ROOT));
            }
        }

        revertEnabled = config.getBoolean("revert.enabled", true);
        revertAll = "ALL".equals(readChoice("revert.scope",
                config.getString("revert.scope", "LATEST"), "LATEST", "LATEST", "ALL"));
        revertCommands = new LinkedHashMap<String, Set<String>>();
        ConfigurationSection revertSection = config.getConfigurationSection("revert.commands");
        if (revertSection != null) {
            for (String key : revertSection.getKeys(false)) {
                Set<String> types = new HashSet<String>();
                for (String raw : revertSection.getStringList(key)) {
                    PunishmentType type = PunishmentType.parse(raw, null);
                    if (type == null) {
                        plugin.getLogger().warning("revert.commands." + key + ": unknown type '" + raw + "'.");
                        continue;
                    }
                    types.add(type.name());
                }
                if (!types.isEmpty()) {
                    revertCommands.put(normalizeLabel(key), types);
                }
            }
        }
        revertPermissions = readStringMap(config.getConfigurationSection("revert.permissions"), false);

        confirmEnabled = config.getBoolean("confirm.enabled", true);
        confirmOnlyPermanent = config.getBoolean("confirm.only-permanent", false);

        globalExpireMillis = readExpire(config.getString("escalation.expire-after", "perm"));
        overflow = Overflow.parse(readChoice("escalation.on-overflow",
                config.getString("escalation.on-overflow", "LAST"), "LAST", "LAST", "CYCLE"), Overflow.LAST);

        soundOpen = config.getString("sounds.open", "");
        soundApply = config.getString("sounds.apply", "");
        soundDeny = config.getString("sounds.deny", "");

        loadMenu(categoriesFile);
        loadCategories(categoriesFile);
        databaseSettings = resolveDatabase(config);
        warnOnRevertOverlap();
    }

    private FileConfiguration loadYaml(String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.isFile()) {
            plugin.saveResource(name, false);
        } else if (MIGRATED.contains(name)) {
            migrate(file, name);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private void migrate(File file, String name) {
        try {
            String defaults = readResource(name);
            if (defaults == null) {
                return;
            }
            String current = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            YamlMerger.Result result = YamlMerger.merge(defaults, current);
            if (result.getFailure() != null) {
                plugin.getLogger().warning(name + " was left untouched: " + result.getFailure());
                return;
            }
            if (!result.isChanged()) {
                return;
            }
            File backup = new File(file.getParentFile(), name + ".backup");
            Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.write(file.toPath(), result.getText().getBytes(StandardCharsets.UTF_8));
            migrationAdditions.addAll(result.getAdded());
            plugin.getLogger().info("Added " + result.getAdded().size() + " new option(s) to " + name
                    + ", your values and comments were kept and the previous file is at "
                    + backup.getName() + ": " + result.getAdded());
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, name + " could not be updated automatically", ex);
        }
    }

    private String readResource(String name) throws IOException {
        InputStream stream = plugin.getResource(name);
        if (stream == null) {
            return null;
        }
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = stream.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            try {
                stream.close();
            } catch (IOException ignored) {
                return null;
            }
        }
    }

    private Map<String, String> readStringMap(ConfigurationSection section, boolean upperCaseKeys) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        if (section == null) {
            return out;
        }
        for (String key : section.getKeys(false)) {
            String normalized = upperCaseKeys
                    ? key.toUpperCase(Locale.ROOT)
                    : key.toLowerCase(Locale.ROOT);
            out.put(normalized, section.getString(key, ""));
        }
        return out;
    }

    private void warnOnRevertOverlap() {
        for (String label : revertCommands.keySet()) {
            if (interceptCommands.contains(label)) {
                plugin.getLogger().warning("'" + label + "' is listed in both intercept.commands and"
                        + " revert.commands. The overlay wins and the rollback never runs; remove it"
                        + " from one of the two lists.");
            }
        }
    }

    private String readChoice(String path, String raw, String fallback, String... allowed) {
        String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        for (String candidate : allowed) {
            if (candidate.equals(value)) {
                return candidate;
            }
        }
        if (!value.isEmpty()) {
            plugin.getLogger().warning(path + ": '" + raw + "' is not a valid value ("
                    + String.join(", ", allowed) + "), using " + fallback + ".");
        }
        return fallback;
    }

    private long readExpire(String raw) {
        if (Durations.isPermanent(raw)) {
            return -1L;
        }
        long millis = Durations.toMillis(raw);
        if (millis <= 0L) {
            plugin.getLogger().warning("Invalid expire-after value '" + raw + "', treating it as 'perm'.");
            return -1L;
        }
        return millis;
    }

    private void loadMenu(FileConfiguration file) {
        menuTitle = file.getString("menu.title", "Punish %target%");
        menuRows = Math.max(1, Math.min(6, file.getInt("menu.rows", 5)));
        fillerEnabled = file.getBoolean("menu.filler.enabled", true);
        fillerMaterial = Items.material(file.getString("menu.filler.material", "GRAY_STAINED_GLASS_PANE"),
                Material.STONE);
        fillerName = file.getString("menu.filler.name", " ");
        tierFormat = file.getString("menu.tier-format", "");
        tierFormatNext = file.getString("menu.tier-format-next", "");

        historySlot = readButtonSlot(file, "menu.history-button");
        historyIcon = IconSpec.from(file.getConfigurationSection("menu.history-button"), Material.BOOK);
        closeSlot = readButtonSlot(file, "menu.close-button");
        closeIcon = IconSpec.from(file.getConfigurationSection("menu.close-button"), Material.BARRIER);
    }

    private int readButtonSlot(FileConfiguration file, String path) {
        int slot = file.getInt(path + ".slot", -1);
        int maxSlot = menuRows * 9 - 1;
        if (slot > maxSlot) {
            plugin.getLogger().warning(path + ".slot is " + slot + " but the menu only has "
                    + (maxSlot + 1) + " slots (0-" + maxSlot + "), hiding the button.");
            return -1;
        }
        return slot;
    }

    private void loadCategories(FileConfiguration file) {
        Map<String, Category> loaded = new LinkedHashMap<String, Category>();
        ConfigurationSection root = file.getConfigurationSection("categories");
        if (root == null) {
            plugin.getLogger().warning("categories.yml has no 'categories' section.");
            categories = loaded;
            return;
        }
        int maxSlot = menuRows * 9 - 1;
        Map<Integer, String> takenSlots = new LinkedHashMap<Integer, String>();
        if (historySlot >= 0) {
            takenSlots.put(historySlot, "menu.history-button");
        }
        if (closeSlot >= 0) {
            takenSlots.put(closeSlot, "menu.close-button");
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            int slot = section.getInt("slot", -1);
            if (slot < 0 || slot > maxSlot) {
                plugin.getLogger().warning("Category '" + id + "': slot " + slot
                        + " is outside the menu (0-" + maxSlot + "), skipping it.");
                continue;
            }
            String owner = takenSlots.get(slot);
            if (owner != null) {
                plugin.getLogger().warning("Category '" + id + "': slot " + slot
                        + " is already used by " + owner + ", skipping it.");
                continue;
            }
            List<Tier> tiers = readTiers(id, section);
            if (tiers.isEmpty()) {
                plugin.getLogger().warning("Category '" + id + "' has no valid tiers, skipping it.");
                continue;
            }
            long expire = section.contains("expire-after")
                    ? readExpire(section.getString("expire-after", "perm"))
                    : globalExpireMillis;
            String permission = section.getString("permission", null);
            if (permission != null && permission.trim().isEmpty()) {
                permission = null;
            }
            String key = id.toLowerCase(Locale.ROOT);
            takenSlots.put(slot, "category '" + key + "'");
            loaded.put(key, new Category(key, slot, permission, expire,
                    IconSpec.from(section.getConfigurationSection("icon"), Material.PAPER), tiers));
        }
        categories = loaded;
    }

    private List<Tier> readTiers(String categoryId, ConfigurationSection section) {
        List<Tier> tiers = new ArrayList<Tier>();
        int number = 1;
        for (Map<?, ?> entry : section.getMapList("tiers")) {
            String rawType = stringValue(entry.get("type"));
            String duration = stringValue(entry.get("duration"));
            String reason = stringValue(entry.get("reason"));

            PunishmentType type = PunishmentType.parse(rawType, null);
            if (type == null) {
                plugin.getLogger().warning("Category '" + categoryId + "' tier " + number
                        + ": unknown type '" + rawType + "' (BAN, IPBAN, MUTE, WARN, KICK), skipping it.");
                number++;
                continue;
            }
            if (duration == null || duration.trim().isEmpty()) {
                duration = "perm";
            }
            if (!Durations.isValid(duration)) {
                plugin.getLogger().warning("Category '" + categoryId + "' tier " + number
                        + ": invalid duration '" + duration + "' (30m, 15d, 2w, perm), skipping it.");
                number++;
                continue;
            }
            if (reason == null || reason.trim().isEmpty()) {
                reason = categoryId + " #" + number;
            }
            tiers.add(new Tier(number, type, duration.trim(), reason.trim()));
            number++;
        }
        return tiers;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Database.Settings resolveDatabase(FileConfiguration config) {
        Database.Settings settings = new Database.Settings();
        settings.tablePrefix = config.getString("database.table-prefix", "gavel_");
        settings.sqliteFile = new File(plugin.getDataFolder(), config.getString("database.file", "gavel.db"));

        String mode = readChoice("database.mode", config.getString("database.mode", "AUTO"),
                "AUTO", "AUTO", "LITEBANS", "MYSQL", "POSTGRESQL", "SQLITE");

        if (mode.equals("SQLITE")) {
            settings.dialect = SqlDialect.SQLITE;
            settings.source = "configured";
            return settings;
        }
        if (mode.equals("MYSQL") || mode.equals("POSTGRESQL")) {
            settings.dialect = mode.equals("MYSQL") ? SqlDialect.MYSQL : SqlDialect.POSTGRESQL;
            settings.host = config.getString("database.host", "localhost");
            settings.port = config.getInt("database.port", settings.dialect == SqlDialect.MYSQL ? 3306 : 5432);
            settings.database = config.getString("database.database", "litebans");
            settings.username = config.getString("database.username", "root");
            settings.password = config.getString("database.password", "");
            settings.ssl = config.getBoolean("database.ssl", false);
            settings.extraProperties = config.getString("database.properties", "");
            settings.source = "configured";
            return settings;
        }

        LiteBansSqlConfig liteBans = LiteBansSqlConfig.read(plugin.getDataFolder().getParentFile());
        SqlDialect dialect = liteBans == null ? null : liteBans.getDialect();
        if (dialect == null || dialect == SqlDialect.SQLITE) {
            String driver = liteBans == null ? "unknown" : liteBans.getRawDriver();
            if (mode.equals("LITEBANS")) {
                plugin.getLogger().warning("database.mode is LITEBANS but LiteBans runs on '" + driver
                        + "', which cannot take a second connection. Falling back to local SQLite.");
            } else {
                plugin.getLogger().info("LiteBans runs on '" + driver
                        + "' (embedded engine), storing Gavel's history in a local SQLite file.");
            }
            settings.dialect = SqlDialect.SQLITE;
            settings.source = "fallback from " + driver;
            return settings;
        }
        settings.dialect = dialect;
        settings.host = liteBans.getHost();
        settings.port = liteBans.getPort();
        settings.database = liteBans.getDatabase();
        settings.username = liteBans.getUsername();
        settings.password = liteBans.getPassword();
        settings.ssl = liteBans.isSsl();
        settings.extraProperties = config.getString("database.properties", "");
        settings.source = "litebans config";
        return settings;
    }

    public static String normalizeLabel(String raw) {
        String label = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (label.startsWith("/")) {
            label = label.substring(1);
        }
        int namespace = label.indexOf(':');
        if (namespace >= 0) {
            label = label.substring(namespace + 1);
        }
        return label;
    }

    public String getServerName() {
        return serverName;
    }

    public boolean isDebug() {
        return debug;
    }

    public boolean isInterceptEnabled() {
        return interceptEnabled;
    }

    public boolean isIntercepted(String label) {
        return interceptCommands.contains(normalizeLabel(label));
    }

    public boolean isAllowBypassPermission() {
        return allowBypassPermission;
    }

    public boolean isDenySelf() {
        return denySelf;
    }

    public boolean isPassthroughWithoutTarget() {
        return passthroughWithoutTarget;
    }

    public boolean isRequireKnownPlayer() {
        return requireKnownPlayer;
    }

    public ExecuteAs getExecuteAs() {
        return executeAs;
    }

    public String getCommandTemplate(String key) {
        return commandTemplates.get(key == null ? "" : key.toLowerCase(Locale.ROOT));
    }

    public String getSilentFlag() {
        return silentFlag;
    }

    public String getBroadcastFlag() {
        return broadcastFlag;
    }

    public boolean isLiteBansIpBansByDefault() {
        return liteBansIpBansByDefault;
    }

    public boolean isLiteBansSilentByDefault() {
        return liteBansSilentByDefault;
    }

    public List<String> getPostCommands() {
        return postCommands;
    }

    public long getDuplicateWindowMillis() {
        return duplicateWindowMillis;
    }

    public boolean isConfirmWithApi() {
        return confirmWithApi;
    }

    public long getConfirmTimeoutMillis() {
        return confirmTimeoutMillis;
    }

    public String getUndoCommand(PunishmentType type) {
        return type == null ? null : undoCommands.get(type.name());
    }

    public boolean isWebhookEnabled() {
        return webhookEnabled;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public boolean isWebhookIncludeSilent() {
        return webhookIncludeSilent;
    }

    public int getWebhookTimeoutMillis() {
        return webhookTimeoutMillis;
    }

    public String getWebhookPayload(String key) {
        return key == null ? null : webhookPayloads.get(key.toLowerCase(Locale.ROOT));
    }

    public boolean isVerifyPermissions() {
        return verifyPermissions;
    }

    public String getLiteBansPermission(PunishmentType type) {
        if (type == null) {
            return null;
        }
        String node = liteBansPermissions.get(type.name());
        return node == null || node.trim().isEmpty() ? null : node.trim();
    }

    public boolean acceptsRemoval(String executorUuid, String executorName) {
        return acceptsRemoval(externalRemovals, executorUuid, executorName, ignoredExecutors);
    }

    public static boolean acceptsRemoval(String mode, String executorUuid, String executorName,
                                         Set<String> ignoredExecutors) {
        if (executorName != null && ignoredExecutors != null
                && ignoredExecutors.contains(executorName.trim().toLowerCase(Locale.ROOT))) {
            return false;
        }
        if ("GAVEL".equals(mode)) {
            return false;
        }
        if ("PLAYERS".equals(mode)) {
            return isPlayerUuid(executorUuid);
        }
        return true;
    }

    private static boolean isPlayerUuid(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return false;
        }
        try {
            return java.util.UUID.fromString(raw.trim()).getMostSignificantBits() != 0L;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public boolean isRevertEnabled() {
        return revertEnabled;
    }

    public boolean isRevertAll() {
        return revertAll;
    }

    public Set<String> getRevertTypes(String label) {
        return revertCommands.get(normalizeLabel(label));
    }

    public String getRevertPermission(String label) {
        String node = revertPermissions.get(normalizeLabel(label));
        return node == null || node.trim().isEmpty() ? null : node.trim();
    }

    public boolean isConfirmEnabled() {
        return confirmEnabled;
    }

    public boolean isConfirmOnlyPermanent() {
        return confirmOnlyPermanent;
    }

    public Overflow getOverflow() {
        return overflow;
    }

    public String getMenuTitle() {
        return menuTitle;
    }

    public int getMenuRows() {
        return menuRows;
    }

    public boolean isFillerEnabled() {
        return fillerEnabled;
    }

    public Material getFillerMaterial() {
        return fillerMaterial;
    }

    public String getFillerName() {
        return fillerName;
    }

    public String getTierFormat() {
        return tierFormat;
    }

    public String getTierFormatNext() {
        return tierFormatNext;
    }

    public int getHistorySlot() {
        return historySlot;
    }

    public IconSpec getHistoryIcon() {
        return historyIcon;
    }

    public int getCloseSlot() {
        return closeSlot;
    }

    public IconSpec getCloseIcon() {
        return closeIcon;
    }

    public Map<String, Category> getCategories() {
        return categories;
    }

    public Category getCategory(String id) {
        return id == null ? null : categories.get(id.toLowerCase(Locale.ROOT));
    }

    public List<Category> visibleTo(Player player) {
        List<Category> out = new ArrayList<Category>();
        for (Category category : categories.values()) {
            if (category.getPermission() == null || player.hasPermission(category.getPermission())) {
                out.add(category);
            }
        }
        return out;
    }

    public Database.Settings getDatabaseSettings() {
        return databaseSettings;
    }

    public String getSoundOpen() {
        return soundOpen;
    }

    public String getSoundApply() {
        return soundApply;
    }

    public String getSoundDeny() {
        return soundDeny;
    }
}
