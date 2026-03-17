package com.student.util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Singleton Database Connection Manager.
 * Loads credentials from .env file.
 */
public class DatabaseConnection {

    private static DatabaseConnection instance;
    private Connection connection;

    private static final String ENV_FILE = ".env";

    private DatabaseConnection() {
        // private constructor for singleton
    }

    public static DatabaseConnection getInstance() {
        if (instance == null) {
            instance = new DatabaseConnection();
        }
        return instance;
    }

    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connect();
        }
        return connection;
    }

    private void connect() throws SQLException {
        Map<String, String> env = loadEnv();

        // Prefer a full DATABASE_URL when provided (e.g. from Neon)
        if (env.containsKey("DATABASE_URL") && env.get("DATABASE_URL") != null && !env.get("DATABASE_URL").isEmpty()) {
            String databaseUrl = env.get("DATABASE_URL");
            try {
                URI uri = new URI(databaseUrl);

                String user = null;
                String password = null;
                String userInfo = uri.getUserInfo();
                if (userInfo != null) {
                    String[] ui = userInfo.split(":", 2);
                    user = URLDecoder.decode(ui[0], StandardCharsets.UTF_8.name());
                    if (ui.length > 1) password = URLDecoder.decode(ui[1], StandardCharsets.UTF_8.name());
                }

                String host = uri.getHost();
                int port = uri.getPort() == -1 ? 5432 : uri.getPort();
                String path = uri.getPath();
                if (path != null && path.startsWith("/")) path = path.substring(1);
                String query = uri.getQuery();

                String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s", host, port, path);
                if (query != null && !query.isEmpty()) jdbcUrl += "?" + query;

                try {
                    Class.forName("org.postgresql.Driver");
                } catch (ClassNotFoundException e) {
                    throw new SQLException("PostgreSQL JDBC Driver not found. Add postgresql jar to classpath.", e);
                }

                if (user != null && password != null) {
                    connection = DriverManager.getConnection(jdbcUrl, user, password);
                } else {
                    connection = DriverManager.getConnection(jdbcUrl);
                }

                System.out.println(ConsoleColors.GREEN + "[DB] Connected to PostgreSQL (via DATABASE_URL): " + path + ConsoleColors.RESET);
                return;
            } catch (URISyntaxException | java.io.UnsupportedEncodingException e) {
                throw new SQLException("Invalid DATABASE_URL format", e);
            }
        }

        String host     = env.getOrDefault("DB_HOST", "localhost");
        String port     = env.getOrDefault("DB_PORT", "5432");
        String dbName   = env.getOrDefault("DB_NAME", "student_system");
        String user     = env.getOrDefault("DB_USER", "postgres");
        String password = env.getOrDefault("DB_PASSWORD", "");

        String url = String.format("jdbc:postgresql://%s:%s/%s", host, port, dbName);

        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("PostgreSQL JDBC Driver not found. Add postgresql jar to classpath.", e);
        }

        connection = DriverManager.getConnection(url, user, password);
        System.out.println(ConsoleColors.GREEN + "[DB] Connected to PostgreSQL: " + dbName + ConsoleColors.RESET);
    }

    private Map<String, String> loadEnv() {
        Map<String, String> envMap = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(ENV_FILE))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    String val = parts[1].trim();
                    // strip surrounding quotes if present
                    if (val.length() >= 2 && ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'")))) {
                        val = val.substring(1, val.length() - 1);
                    }
                    envMap.put(parts[0].trim(), val);
                }
            }
        } catch (IOException e) {
            System.err.println(ConsoleColors.YELLOW + "[WARN] .env file not found. Using system environment variables." + ConsoleColors.RESET);
            // Fallback to system env vars
            String[] keys = {"DB_HOST", "DB_PORT", "DB_NAME", "DB_USER", "DB_PASSWORD", "DATABASE_URL"};
            for (String key : keys) {
                String val = System.getenv(key);
                if (val != null) envMap.put(key, val);
            }
        }
        return envMap;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println(ConsoleColors.CYAN + "[DB] Connection closed." + ConsoleColors.RESET);
            }
        } catch (SQLException e) {
            System.err.println("Error closing connection: " + e.getMessage());
        }
    }
}