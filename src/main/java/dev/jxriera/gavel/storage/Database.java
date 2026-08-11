package dev.jxriera.gavel.storage;

import dev.jxriera.gavel.model.OffenseRecord;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public final class Database {
    public static final class Settings {
        public SqlDialect dialect = SqlDialect.SQLITE;
        public String host = "localhost";
        public int port = 3306;
        public String database = "litebans";
        public String username = "";
        public String password = "";
        public boolean ssl = false;
        public String extraProperties = "";
        public File sqliteFile;
        public String tablePrefix = "gavel_";
        public String source = "manual";
    }

    private final Settings settings;
    private final Object lock = new Object();
    private Connection connection;

    public Database(Settings settings) {
        this.settings = settings;
    }

    public String table() {
        return settings.tablePrefix + "offenses";
    }

    public String describe() {
        if (settings.dialect == SqlDialect.SQLITE) {
            String name = settings.sqliteFile == null ? "?" : settings.sqliteFile.getName();
            return "SQLITE " + name + " (" + settings.source + ")";
        }
        return settings.dialect + " " + settings.host + ":" + settings.port + "/" + settings.database
                + " (" + settings.source + ")";
    }

    public void connect() throws SQLException {
        synchronized (lock) {
            connection = open();
            createSchema(connection);
        }
    }

    private Connection connection() throws SQLException {
        if (connection == null || connection.isClosed() || !isValid(connection)) {
            connection = open();
        }
        return connection;
    }

    private boolean isValid(Connection candidate) {
        try {
            return candidate.isValid(3);
        } catch (Throwable ex) {
            return false;
        }
    }

    private Connection open() throws SQLException {
        Properties properties = new Properties();
        switch (settings.dialect) {
            case SQLITE: {
                File file = settings.sqliteFile;
                File parent = file.getParentFile();
                if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                    throw new SQLException("Could not create directory for " + file.getAbsolutePath());
                }
                Connection created = new org.sqlite.JDBC()
                        .connect("jdbc:sqlite:" + file.getAbsolutePath(), properties);
                if (created == null) {
                    throw new SQLException("SQLite driver rejected the connection URL");
                }
                return created;
            }
            case MYSQL: {
                properties.setProperty("user", settings.username == null ? "" : settings.username);
                properties.setProperty("password", settings.password == null ? "" : settings.password);
                StringBuilder url = new StringBuilder("jdbc:mysql://")
                        .append(settings.host).append(':').append(settings.port)
                        .append('/').append(settings.database)
                        .append("?useSSL=").append(settings.ssl)
                        .append("&allowPublicKeyRetrieval=true");
                if (settings.extraProperties != null && !settings.extraProperties.trim().isEmpty()) {
                    url.append('&').append(settings.extraProperties.trim());
                }
                Connection created = new com.mysql.cj.jdbc.NonRegisteringDriver()
                        .connect(url.toString(), properties);
                if (created == null) {
                    throw new SQLException("MySQL driver rejected the connection URL");
                }
                return created;
            }
            case POSTGRESQL: {
                properties.setProperty("user", settings.username == null ? "" : settings.username);
                properties.setProperty("password", settings.password == null ? "" : settings.password);
                if (settings.ssl) {
                    properties.setProperty("ssl", "true");
                }
                String url = "jdbc:postgresql://" + settings.host + ":" + settings.port
                        + "/" + settings.database;
                Connection created = new org.postgresql.Driver().connect(url, properties);
                if (created == null) {
                    throw new SQLException("PostgreSQL driver rejected the connection URL");
                }
                return created;
            }
            default:
                throw new SQLException("Unsupported dialect: " + settings.dialect);
        }
    }

    public void close() {
        synchronized (lock) {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ignored) {
                }
                connection = null;
            }
        }
    }

    private void createSchema(Connection target) throws SQLException {
        for (String sql : ddl()) {
            Statement statement = target.createStatement();
            try {
                statement.execute(sql);
            } finally {
                closeQuietly(statement);
            }
        }
    }

    private List<String> ddl() {
        String table = table();
        String index = "idx_" + table + "_lookup";
        switch (settings.dialect) {
            case MYSQL:
                return Arrays.asList(
                        "CREATE TABLE IF NOT EXISTS " + table + " ("
                                + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,"
                                + "uuid VARCHAR(36) NOT NULL,"
                                + "name VARCHAR(32) NULL,"
                                + "category VARCHAR(64) NOT NULL,"
                                + "tier INT NOT NULL,"
                                + "type VARCHAR(16) NOT NULL,"
                                + "duration VARCHAR(32) NOT NULL,"
                                + "reason VARCHAR(255) NOT NULL,"
                                + "staff_uuid VARCHAR(36) NULL,"
                                + "staff_name VARCHAR(32) NULL,"
                                + "server VARCHAR(64) NULL,"
                                + "silent TINYINT NOT NULL DEFAULT 0,"
                                + "active TINYINT NOT NULL DEFAULT 1,"
                                + "created BIGINT NOT NULL,"
                                + "INDEX " + index + " (uuid, category, active)"
                                + ") DEFAULT CHARSET=utf8mb4");
            case POSTGRESQL:
                return Arrays.asList(
                        "CREATE TABLE IF NOT EXISTS " + table + " ("
                                + "id BIGSERIAL PRIMARY KEY,"
                                + "uuid VARCHAR(36) NOT NULL,"
                                + "name VARCHAR(32) NULL,"
                                + "category VARCHAR(64) NOT NULL,"
                                + "tier INT NOT NULL,"
                                + "type VARCHAR(16) NOT NULL,"
                                + "duration VARCHAR(32) NOT NULL,"
                                + "reason VARCHAR(255) NOT NULL,"
                                + "staff_uuid VARCHAR(36) NULL,"
                                + "staff_name VARCHAR(32) NULL,"
                                + "server VARCHAR(64) NULL,"
                                + "silent SMALLINT NOT NULL DEFAULT 0,"
                                + "active SMALLINT NOT NULL DEFAULT 1,"
                                + "created BIGINT NOT NULL"
                                + ")",
                        "CREATE INDEX IF NOT EXISTS " + index + " ON " + table + " (uuid, category, active)");
            default:
                return Arrays.asList(
                        "CREATE TABLE IF NOT EXISTS " + table + " ("
                                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                                + "uuid VARCHAR(36) NOT NULL,"
                                + "name VARCHAR(32),"
                                + "category VARCHAR(64) NOT NULL,"
                                + "tier INTEGER NOT NULL,"
                                + "type VARCHAR(16) NOT NULL,"
                                + "duration VARCHAR(32) NOT NULL,"
                                + "reason VARCHAR(255) NOT NULL,"
                                + "staff_uuid VARCHAR(36),"
                                + "staff_name VARCHAR(32),"
                                + "server VARCHAR(64),"
                                + "silent INTEGER NOT NULL DEFAULT 0,"
                                + "active INTEGER NOT NULL DEFAULT 1,"
                                + "created INTEGER NOT NULL"
                                + ")",
                        "CREATE INDEX IF NOT EXISTS " + index + " ON " + table + " (uuid, category, active)");
        }
    }

    public void insert(OffenseRecord record) throws SQLException {
        String sql = "INSERT INTO " + table()
                + " (uuid, name, category, tier, type, duration, reason, staff_uuid, staff_name,"
                + " server, silent, active, created) VALUES (?,?,?,?,?,?,?,?,?,?,?,1,?)";
        synchronized (lock) {
            PreparedStatement statement = connection().prepareStatement(sql);
            try {
                statement.setString(1, record.getUuid());
                statement.setString(2, record.getName());
                statement.setString(3, record.getCategory());
                statement.setInt(4, record.getTier());
                statement.setString(5, record.getType());
                statement.setString(6, record.getDuration());
                statement.setString(7, truncate(record.getReason(), 255));
                statement.setString(8, record.getStaffUuid());
                statement.setString(9, record.getStaffName());
                statement.setString(10, record.getServer());
                statement.setInt(11, record.isSilent() ? 1 : 0);
                statement.setLong(12, record.getCreated());
                statement.executeUpdate();
            } finally {
                closeQuietly(statement);
            }
        }
    }

    public List<OffenseRecord> find(String uuid, boolean onlyActive) throws SQLException {
        String sql = "SELECT id, uuid, name, category, tier, type, duration, reason, staff_uuid,"
                + " staff_name, server, silent, created FROM " + table()
                + " WHERE uuid = ?" + (onlyActive ? " AND active = 1" : "")
                + " ORDER BY created DESC";
        List<OffenseRecord> out = new ArrayList<OffenseRecord>();
        synchronized (lock) {
            PreparedStatement statement = connection().prepareStatement(sql);
            ResultSet results = null;
            try {
                statement.setString(1, uuid);
                results = statement.executeQuery();
                while (results.next()) {
                    out.add(new OffenseRecord(
                            results.getLong("id"),
                            results.getString("uuid"),
                            results.getString("name"),
                            results.getString("category"),
                            results.getInt("tier"),
                            results.getString("type"),
                            results.getString("duration"),
                            results.getString("reason"),
                            results.getString("staff_uuid"),
                            results.getString("staff_name"),
                            results.getString("server"),
                            results.getInt("silent") == 1,
                            results.getLong("created")));
                }
            } finally {
                closeQuietly(results);
                closeQuietly(statement);
            }
        }
        return out;
    }

    public int deactivate(String uuid, String category) throws SQLException {
        String sql = "UPDATE " + table() + " SET active = 0 WHERE uuid = ? AND active = 1"
                + (category == null ? "" : " AND category = ?");
        synchronized (lock) {
            PreparedStatement statement = connection().prepareStatement(sql);
            try {
                statement.setString(1, uuid);
                if (category != null) {
                    statement.setString(2, category);
                }
                return statement.executeUpdate();
            } finally {
                closeQuietly(statement);
            }
        }
    }

    public int deactivateIds(List<Long> ids) throws SQLException {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        String sql = "UPDATE " + table() + " SET active = 0 WHERE id = ? AND active = 1";
        synchronized (lock) {
            PreparedStatement statement = connection().prepareStatement(sql);
            try {
                for (Long id : ids) {
                    statement.setLong(1, id);
                    statement.addBatch();
                }
                int affected = 0;
                for (int result : statement.executeBatch()) {
                    if (result > 0) {
                        affected += result;
                    }
                }
                return affected;
            } finally {
                closeQuietly(statement);
            }
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }
}
