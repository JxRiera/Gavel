package dev.jxriera.gavel.storage;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public final class LiteBansSqlConfig {
    private final String rawDriver;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final boolean ssl;

    private LiteBansSqlConfig(String rawDriver, String host, int port, String database,
                              String username, String password, boolean ssl) {
        this.rawDriver = rawDriver;
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.ssl = ssl;
    }

    public static LiteBansSqlConfig read(File pluginsFolder) {
        File file = new File(new File(pluginsFolder, "LiteBans"), "config.yml");
        if (!file.isFile()) {
            return null;
        }
        YamlConfiguration yaml;
        try {
            yaml = YamlConfiguration.loadConfiguration(file);
        } catch (Throwable ex) {
            return null;
        }
        String driver = yaml.getString("sql.driver", "h2");
        String address = yaml.getString("sql.address", "localhost:3306");
        String database = yaml.getString("sql.database", "litebans");
        String username = yaml.getString("sql.username", "");
        String password = yaml.getString("sql.password", "");
        boolean ssl = yaml.getBoolean("sql.ssl", false);

        String host = address == null ? "localhost" : address.trim();
        int port = 3306;
        int separator = host.lastIndexOf(':');
        if (separator > 0 && separator < host.length() - 1) {
            try {
                port = Integer.parseInt(host.substring(separator + 1).trim());
            } catch (NumberFormatException ignored) {
            }
            host = host.substring(0, separator).trim();
        }
        if (host.isEmpty()) {
            host = "localhost";
        }
        return new LiteBansSqlConfig(driver, host, port, database, username, password, ssl);
    }

    public String getRawDriver() {
        return rawDriver;
    }

    public SqlDialect getDialect() {
        return SqlDialect.fromLiteBansDriver(rawDriver);
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getDatabase() {
        return database;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public boolean isSsl() {
        return ssl;
    }
}
