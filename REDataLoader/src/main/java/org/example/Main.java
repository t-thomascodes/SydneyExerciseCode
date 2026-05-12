package org.example;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;

public class Main {

    public static void main(String[] args) {
        final Path csvFilePath = resolveCsvPath(args);

        if (!Files.isRegularFile(csvFilePath)) {
            System.err.println("CSV file not found: " + csvFilePath);
            System.exit(1);
        }

        try (Connection connection = DatabaseConfig.connect()) {
            System.out.println("Connected to Supabase");
            int recordCount = PropertyCsvLoader.load(csvFilePath, connection);
            System.out.println("Finished loading " + recordCount + " records from " + csvFilePath);
        } catch (IllegalStateException exception) {
            System.err.println(exception.getMessage());
            System.exit(1);
        } catch (IOException | SQLException exception) {
            System.err.println("Failed to load property data");
            exception.printStackTrace();
            System.exit(1);
        }
    }

    private static Path resolveCsvPath(String[] args) {
        if (args.length > 0) {
            return Paths.get(args[0]);
        }

        String csvPathFromEnv = Env.get("PROPERTY_CSV_PATH");
        if (csvPathFromEnv != null && !csvPathFromEnv.isBlank()) {
            return Paths.get(csvPathFromEnv);
        }

        return Paths.get("/Users/tt/Downloads/nsw_property_data.csv");
    }
}
