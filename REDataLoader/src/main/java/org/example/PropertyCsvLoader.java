package org.example;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public final class PropertyCsvLoader {

    private static final int BATCH_SIZE = 1_000;

    private static final CSVFormat CSV_FORMAT = CSVFormat.Builder.create(CSVFormat.RFC4180)
        .setHeader()
        .setSkipHeaderRecord(true)
        .setAllowDuplicateHeaderNames(false)
        .build();

    private static final String INSERT_SQL =
        "insert into property ("
            + "property_id, download_date, council_name, purchase_price, address, post_code, "
            + "property_type, strata_lot_number, property_name, area, area_type, contract_date, "
            + "settlement_date, zoning, nature_of_property, primary_purpose, legal_description"
            + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
            + "on conflict (property_id) do nothing";

    private PropertyCsvLoader() {
    }

    public static int load(Path csvFilePath, Connection connection) throws IOException, SQLException {
        int inserted = 0;

        try (CSVParser parser = CSVParser.parse(csvFilePath, StandardCharsets.UTF_8, CSV_FORMAT);
             PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {

            connection.setAutoCommit(false);

            for (CSVRecord record : parser) {
                if (!bindRecord(statement, record)) {
                    continue;
                }

                statement.addBatch();
                inserted++;

                if (inserted % BATCH_SIZE == 0) {
                    statement.executeBatch();
                    connection.commit();
                    System.out.println("Loaded " + inserted + " records");
                }
            }

            statement.executeBatch();
            connection.commit();
        } finally {
            connection.setAutoCommit(true);
        }

        return inserted;
    }

    private static boolean bindRecord(PreparedStatement statement, CSVRecord record) throws SQLException {
        Long propertyId = parseLong(record.get("property_id"));
        Long purchasePrice = parseLong(record.get("purchase_price"));
        if (propertyId == null || purchasePrice == null) {
            return false;
        }

        statement.setLong(1, propertyId);
        statement.setDate(2, parseDate(record.get("download_date")));
        statement.setString(3, emptyToNull(record.get("council_name")));
        statement.setLong(4, purchasePrice);
        statement.setString(5, emptyToNull(record.get("address")));
        statement.setString(6, emptyToNull(record.get("post_code")));
        statement.setString(7, emptyToNull(record.get("property_type")));
        statement.setString(8, emptyToNull(record.get("strata_lot_number")));
        statement.setString(9, emptyToNull(record.get("property_name")));
        statement.setString(10, emptyToNull(record.get("area")));
        statement.setString(11, emptyToNull(record.get("area_type")));
        statement.setDate(12, parseDate(record.get("contract_date")));
        statement.setDate(13, parseDate(record.get("settlement_date")));
        statement.setString(14, emptyToNull(record.get("zoning")));
        statement.setString(15, emptyToNull(record.get("nature_of_property")));
        statement.setString(16, emptyToNull(record.get("primary_purpose")));
        statement.setString(17, emptyToNull(record.get("legal_description")));
        return true;
    }

    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Long.parseLong(value.trim());
    }

    private static Date parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Date.valueOf(LocalDate.parse(value));
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Invalid date value: " + value, exception);
        }
    }

    private static String emptyToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
