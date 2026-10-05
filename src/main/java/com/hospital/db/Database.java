package com.hospital.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Central place that reads connection settings and hands out JDBC connections.
 * Lookup order: -Ddb.config=/path/file, ./config/db.properties, classpath /db.properties.
 * The ojdbc8 driver self-registers (JDBC 4), so no Class.forName is needed.
 */
public final class Database {
    private static Properties props;

    private Database() { }

    public static Connection getConnection() throws SQLException {
        Properties p = load();
        String url = p.getProperty("db.url");
        String user = p.getProperty("db.user");
        String pass = System.getenv("DB_PASSWORD") != null
                ? System.getenv("DB_PASSWORD") : p.getProperty("db.password");
        if (url == null || user == null || pass == null) {
            throw new SQLException("db.url, db.user and db.password must be configured.");
        }
        DriverManager.setLoginTimeout(10);
        return DriverManager.getConnection(url, user, pass);
    }

    private static synchronized Properties load() throws SQLException {
        if (props != null) return props;
        Properties p = new Properties();
        Path file = Path.of(System.getProperty("db.config", "config/db.properties"));
        try {
            if (Files.isRegularFile(file)) {
                try (InputStream in = Files.newInputStream(file)) { p.load(in); }
            } else {
                try (InputStream in = Database.class.getResourceAsStream("/db.properties")) {
                    if (in == null) throw new SQLException("Configuration file not found: " + file.toAbsolutePath());
                    p.load(in);
                }
            }
        } catch (IOException e) {
            throw new SQLException("Cannot read database configuration: " + e.getMessage(), e);
        }
        props = p;
        return p;
    }
}
