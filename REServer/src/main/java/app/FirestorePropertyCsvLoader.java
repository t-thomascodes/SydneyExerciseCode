package app;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteBatch;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Bulk-load NSW property CSV (same columns as {@code REDataLoader}) into Firestore {@code properties}.
 * <p>
 * Run from the REServer directory with {@code .env} pointing at your service account ({@link FirestoreConfig}):
 * {@code mvn -q exec:java -Dexec.mainClass=app.FirestorePropertyCsvLoader -Dexec.args="'/path/to/nsw_property_data.csv'"}
 * </p>
 * <ul>
 *   <li>Env {@code PROPERTY_CSV_PATH} or positional arg selects the CSV (default tries common Downloads path).</li>
 *   <li>Env {@code LOADER_BATCH_SIZE} batches writes (max 500; default 500).</li>
 *   <li>Env {@code LOADER_ROW_LIMIT} stops after N valid rows for dry runs.</li>
 * </ul>
 */
public final class FirestorePropertyCsvLoader {

    private static final int FIRESTORE_BATCH_MAX = 500;

    private static final CSVFormat CSV_FORMAT = CSVFormat.Builder.create(CSVFormat.RFC4180)
        .setHeader()
        .setSkipHeaderRecord(true)
        .setAllowDuplicateHeaderNames(false)
        .build();

    private FirestorePropertyCsvLoader() {
    }

    public static void main(String[] args) throws IOException {
        Path csvPath = resolveCsvPath(args);
        if (!Files.isRegularFile(csvPath)) {
            System.err.println("CSV file not found: " + csvPath.toAbsolutePath());
            System.exit(1);
        }

        Long rowLimit = parseLongEnv(System.getenv("LOADER_ROW_LIMIT"));

        Firestore db = FirestoreConfig.db();
        long written = loadCsv(csvPath, db, batchSizeEnv(), rowLimit);

        System.out.println("FirestorePropertyCsvLoader finished. Rows written or updated (set): " + written);
        if (rowLimit != null) {
            System.out.println("Note: LOADER_ROW_LIMIT was set — rerun without it for full import.");
        }
    }

    private static Path resolveCsvPath(String[] args) {
        for (String arg : args) {
            if (!arg.startsWith("--") && Files.isReadable(Paths.get(arg))) {
                return Paths.get(arg);
            }
        }
        String fromEnv = Env.get("PROPERTY_CSV_PATH");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return Paths.get(fromEnv.trim());
        }
        return Paths.get(System.getProperty("user.home")).resolve("Downloads/nsw_property_data.csv");
    }

    private static int batchSizeEnv() {
        Integer fromEnv = parseIntEnv(System.getenv("LOADER_BATCH_SIZE"));
        if (fromEnv == null || fromEnv < 1) {
            return FIRESTORE_BATCH_MAX;
        }
        return Math.min(FIRESTORE_BATCH_MAX, fromEnv);
    }

    private static Long parseLongEnv(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Long.parseLong(value.trim());
    }

    private static Integer parseIntEnv(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Integer.parseInt(value.trim());
    }

    private static long loadCsv(Path csvFilePath, Firestore db, int batchSize, Long rowLimit) throws IOException {
        long committedWrites = 0;
        WriteBatch batch = db.batch();
        int inBatch = 0;
        long nextLogThreshold = 50_000L;

        try (CSVParser parser = CSVParser.parse(csvFilePath, StandardCharsets.UTF_8, CSV_FORMAT)) {

            int rowNumber = 0;
            int skippedRows = 0;

            for (CSVRecord record : parser) {
                rowNumber++;

                DocumentReference ref = toPropertyRef(db, record);
                Map<String, Object> data = toPropertyFields(record);

                if (ref == null || data == null) {
                    skippedRows++;
                    continue;
                }

                if (rowLimit != null && committedWrites + inBatch >= rowLimit) {
                    break;
                }

                batch.set(ref, data);
                inBatch++;

                if (inBatch >= batchSize) {
                    FirestoreOps.await(batch.commit());
                    committedWrites += inBatch;
                    inBatch = 0;
                    batch = db.batch();
                    nextLogThreshold = logCommittedIfReached(committedWrites, rowNumber, nextLogThreshold);
                }
            }

            if (inBatch > 0) {
                FirestoreOps.await(batch.commit());
                committedWrites += inBatch;
                logCommittedIfReached(committedWrites, rowNumber, nextLogThreshold);
            }

            System.out.println("Skipped invalid rows (missing id or purchase_price): " + skippedRows);
        }

        return committedWrites;
    }

    private static long logCommittedIfReached(long committedWrites, int lastCsvRow, long nextThreshold) {
        while (committedWrites >= nextThreshold) {
            System.out.println("Committed " + committedWrites + " rows (last CSV row seen ~ " + lastCsvRow + ")");
            nextThreshold += 50_000L;
        }
        return nextThreshold;
    }

    private static DocumentReference toPropertyRef(Firestore db, CSVRecord record) {
        Long propertyId = parseLong(record.isMapped("property_id") ? record.get("property_id") : null);
        if (propertyId == null) {
            return null;
        }
        return db.collection(FirestoreCollections.PROPERTIES).document(String.valueOf(propertyId));
    }

    private static Map<String, Object> toPropertyFields(CSVRecord record) {
        Long propertyId = parseLong(emptyToBlank(record.isMapped("property_id") ? record.get("property_id") : null));
        Long purchasePrice = parseLong(emptyToBlank(record.isMapped("purchase_price") ? record.get("purchase_price") : null));
        if (propertyId == null || purchasePrice == null) {
            return null;
        }

        Map<String, Object> doc = new HashMap<>();
        doc.put("propertyId", propertyId);
        doc.put("postCode", emptyToBlank(record.isMapped("post_code") ? record.get("post_code") : ""));
        doc.put("purchasePrice", purchasePrice);
        doc.put("forSale", Boolean.FALSE);
        doc.put("accessCount", 0L);

        putIfPresent(doc, "downloadDate", emptyToBlank(record.isMapped("download_date") ? record.get("download_date") : ""));
        putIfPresent(doc, "councilName", trimOrNull(record.isMapped("council_name") ? record.get("council_name") : null));
        putIfPresent(doc, "address", trimOrNull(record.isMapped("address") ? record.get("address") : null));
        putIfPresent(doc, "propertyType", trimOrNull(record.isMapped("property_type") ? record.get("property_type") : null));
        putIfPresent(doc, "strataLotNumber", trimOrNull(record.isMapped("strata_lot_number") ? record.get("strata_lot_number") : null));
        putIfPresent(doc, "propertyName", trimOrNull(record.isMapped("property_name") ? record.get("property_name") : null));
        putIfPresent(doc, "area", trimOrNull(record.isMapped("area") ? record.get("area") : null));
        putIfPresent(doc, "areaType", trimOrNull(record.isMapped("area_type") ? record.get("area_type") : null));
        putIfPresent(doc, "contractDate", trimOrNull(record.isMapped("contract_date") ? record.get("contract_date") : null));
        putIfPresent(doc, "settlementDate", trimOrNull(record.isMapped("settlement_date") ? record.get("settlement_date") : null));
        putIfPresent(doc, "zoning", trimOrNull(record.isMapped("zoning") ? record.get("zoning") : null));
        putIfPresent(doc, "natureOfProperty", trimOrNull(record.isMapped("nature_of_property") ? record.get("nature_of_property") : null));
        putIfPresent(doc, "primaryPurpose", trimOrNull(record.isMapped("primary_purpose") ? record.get("primary_purpose") : null));
        putIfPresent(doc, "legalDescription", trimOrNull(record.isMapped("legal_description") ? record.get("legal_description") : null));

        return doc;
    }

    private static void putIfPresent(Map<String, Object> doc, String key, String value) {
        if (value != null && !value.isEmpty()) {
            doc.put(key, value);
        }
    }

    private static String trimOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        return t.isEmpty() ? null : t;
    }

    private static String emptyToBlank(String raw) {
        String t = trimOrNull(raw);
        return t == null ? "" : t;
    }

    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
