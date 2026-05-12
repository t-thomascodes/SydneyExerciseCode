package app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

final class Env {

    private static final Map<String, String> DOT_ENV = loadDotEnvFile();

    private Env() {
    }

    static String get(String name) {
        String value = System.getenv(name);
        if (value != null && !value.isBlank()) {
            return value;
        }
        return DOT_ENV.get(name);
    }

    private static Map<String, String> loadDotEnvFile() {
        Path envFile = Path.of(".env");
        if (!Files.isRegularFile(envFile)) {
            return Map.of();
        }

        try {
            return parseDotEnv(Files.readString(envFile));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read .env file", exception);
        }
    }

    private static Map<String, String> parseDotEnv(String contents) {
        Map<String, String> values = new HashMap<>();

        for (String line : contents.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }

            int separator = trimmed.indexOf('=');
            if (separator < 0) {
                continue;
            }

            String key = trimmed.substring(0, separator).trim();
            String value = stripQuotes(trimmed.substring(separator + 1).trim());
            values.put(key, value);
        }

        return values;
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2
            && ((value.startsWith("\"") && value.endsWith("\""))
            || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
