package dev.jxriera.gavel.storage;

public enum SqlDialect {
    MYSQL,
    POSTGRESQL,
    SQLITE;

    public static SqlDialect fromLiteBansDriver(String driver) {
        if (driver == null) {
            return null;
        }
        String value = driver.trim().toLowerCase();
        if (value.contains("mysql") || value.contains("mariadb")) {
            return MYSQL;
        }
        if (value.contains("postgres")) {
            return POSTGRESQL;
        }
        if (value.contains("sqlite")) {
            return SQLITE;
        }
        return null;
    }
}
