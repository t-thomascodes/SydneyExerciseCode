package app;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** JDBC connection to the legacy Supabase/Postgres database (migration tool only). */
public final class SupabaseConfig {

    private SupabaseConfig() {
    }

    public static Connection connect() throws SQLException {
        String url = requiredEnv("SUPABASE_DB_URL");
        String user = requiredEnv("SUPABASE_DB_USER");
        String password = requiredEnv("SUPABASE_DB_PASSWORD");
        return DriverManager.getConnection(url, user, password);
    }

    public static boolean isConfigured() {
        return envPresent("SUPABASE_DB_URL")
            && envPresent("SUPABASE_DB_USER")
            && envPresent("SUPABASE_DB_PASSWORD");
    }

    private static boolean envPresent(String name) {
        String value = Env.get(name);
        return value != null && !value.isBlank();
    }

    private static String requiredEnv(String name) {
        String value = Env.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "Missing required setting: " + name + ". Set it in the environment or in REServer/.env"
            );
        }
        return value.trim();
    }
}
