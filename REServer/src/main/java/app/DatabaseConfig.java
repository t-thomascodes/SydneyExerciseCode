package app;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DatabaseConfig {

    private DatabaseConfig() {
    }

    public static Connection connect() throws SQLException {
        String url = requiredEnv("SUPABASE_DB_URL");
        String user = requiredEnv("SUPABASE_DB_USER");
        String password = requiredEnv("SUPABASE_DB_PASSWORD");
        return DriverManager.getConnection(url, user, password);
    }

    private static String requiredEnv(String name) {
        String value = Env.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "Missing required setting: " + name + ". Set it in the environment or in REServer/.env"
            );
        }
        return value;
    }
}
