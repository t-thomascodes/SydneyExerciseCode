package app;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * One-shot migration: read all REServer tables from Supabase/Postgres and write Firestore documents.
 * <p>
 * Requires both Supabase ({@code SUPABASE_DB_*}) and Firestore ({@code GOOGLE_APPLICATION_CREDENTIALS}) in
 * {@code .env}. Run from the REServer directory:
 * </p>
 * <pre>
 * mvn -q exec:java -Dexec.mainClass=app.FirestoreSupabaseMigrator
 * </pre>
 * Optional env: {@code MIGRATE_ROW_LIMIT} (per-table cap for dry runs), {@code MIGRATE_TABLES}
 * (comma-separated: property, listing, listing_price, purchaser, purchaser_interest, post_code_search_stat, counters).
 */
public final class FirestoreSupabaseMigrator {

    private static final int PAGE_SIZE = 500;
    private static final long LOG_EVERY = 50_000L;

    private FirestoreSupabaseMigrator() {
    }

    public static void main(String[] args) throws SQLException {
        if (!SupabaseConfig.isConfigured()) {
            System.err.println("Supabase is not configured. Set SUPABASE_DB_URL, SUPABASE_DB_USER, SUPABASE_DB_PASSWORD in .env");
            System.exit(1);
        }
        if (!FirestoreConfig.isConfigured()) {
            System.err.println("Firestore is not configured. Set GOOGLE_APPLICATION_CREDENTIALS (and optionally FIRESTORE_PROJECT_ID) in .env");
            System.exit(1);
        }

        Set<String> tables = parseTables(System.getenv("MIGRATE_TABLES"));
        Long rowLimit = parseLongEnv(System.getenv("MIGRATE_ROW_LIMIT"));

        Firestore db = FirestoreConfig.db();
        System.out.println("Starting Supabase → Firestore migration. Tables: " + tables);
        if (rowLimit != null) {
            System.out.println("MIGRATE_ROW_LIMIT=" + rowLimit + " (dry run — omit for full import)");
        }

        try (Connection connection = SupabaseConfig.connect()) {
            connection.setReadOnly(true);
            System.out.println("Connected to Supabase (read-only).");
            printSupabaseRowCounts(connection);

            if (tables.contains("property")) {
                migrateProperties(connection, db, rowLimit);
            }
            if (tables.contains("listing")) {
                migrateListings(connection, db, rowLimit);
            }
            if (tables.contains("listing_price")) {
                migrateListingPrices(connection, db, rowLimit);
            }
            if (tables.contains("purchaser")) {
                migratePurchasers(connection, db, rowLimit);
            }
            if (tables.contains("purchaser_interest")) {
                migratePurchaserInterests(connection, db, rowLimit);
            }
            if (tables.contains("post_code_search_stat")) {
                migratePostCodeSearchStats(connection, db, rowLimit);
            }
            if (tables.contains("counters")) {
                migrateCounters(connection, db);
            }
        }

        System.out.println("Migration finished.");
    }

    private static void printSupabaseRowCounts(Connection connection) throws SQLException {
        System.out.println("Supabase row counts:");
        printCount(connection, "property");
        printCount(connection, "listing");
        printCount(connection, "listing_price");
        printCount(connection, "purchaser");
        printCount(connection, "purchaser_interest");
        printCount(connection, "post_code_search_stat");
    }

    private static void printCount(Connection connection, String table) throws SQLException {
        if (!tableExists(connection, table)) {
            System.out.println("  " + table + ": (table missing)");
            return;
        }
        long count = queryLong(connection, "select count(*) from " + table);
        System.out.println("  " + table + ": " + count);
        if (count == 0) {
            System.out.println("    ^ empty — load data into Supabase first (e.g. REDataLoader + CSV)");
        }
    }

    private static Set<String> parseTables(String raw) {
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("all")) {
            return Set.of(
                "property",
                "listing",
                "listing_price",
                "purchaser",
                "purchaser_interest",
                "post_code_search_stat",
                "counters"
            );
        }
        Set<String> tables = new HashSet<>();
        for (String part : raw.split(",")) {
            String name = part.trim().toLowerCase(Locale.ROOT);
            if (!name.isEmpty()) {
                tables.add(name);
            }
        }
        return tables;
    }

    private static Long parseLongEnv(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Long.parseLong(value.trim());
    }

    private static void migrateProperties(Connection connection, Firestore db, Long rowLimit) throws SQLException {
        System.out.println("Migrating property → properties …");
        String accessColumn = columnExists(connection, "property", "access_count")
            ? "coalesce(access_count, 0) as access_count"
            : "0 as access_count";
        String sql =
            "select property_id, post_code, purchase_price, for_sale, "
                + accessColumn
                + " from property where property_id > ? order by property_id limit ?";

        FirestoreBatchWriter writer = new FirestoreBatchWriter(db, "properties");
        long lastId = 0L;
        long rows = 0L;
        long nextLog = LOG_EVERY;

        while (true) {
            int pageLimit = pageLimit(rowLimit, rows);
            if (pageLimit == 0) {
                break;
            }

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, lastId);
                statement.setInt(2, pageLimit);
                try (ResultSet rs = statement.executeQuery()) {
                    int pageRows = 0;
                    while (rs.next()) {
                        long propertyId = rs.getLong("property_id");
                        lastId = propertyId;
                        pageRows++;

                        Map<String, Object> doc = new HashMap<>();
                        doc.put("propertyId", propertyId);
                        doc.put("postCode", rs.getString("post_code"));
                        doc.put("purchasePrice", rs.getLong("purchase_price"));
                        doc.put("forSale", rs.getBoolean("for_sale"));
                        doc.put("accessCount", rs.getLong("access_count"));

                        DocumentReference ref = db.collection(FirestoreCollections.PROPERTIES)
                            .document(String.valueOf(propertyId));
                        writer.set(ref, doc);
                        rows++;

                        if (rows >= nextLog) {
                            writer.logProgressIfNeeded(nextLog);
                            nextLog += LOG_EVERY;
                        }
                    }
                    if (pageRows < pageLimit) {
                        break;
                    }
                }
            }
        }

        writer.flush();
        System.out.println("  properties: " + writer.committedCount() + " documents.");
    }

    private static void migrateListings(Connection connection, Firestore db, Long rowLimit) throws SQLException {
        System.out.println("Migrating listing → listings …");
        String sql =
            "select listing_id, property_id, datelisted, forsale "
                + "from listing where listing_id > ? order by listing_id limit ?";

        FirestoreBatchWriter writer = new FirestoreBatchWriter(db, "listings");
        long lastId = 0L;
        long rows = 0L;

        while (true) {
            int pageLimit = pageLimit(rowLimit, rows);
            if (pageLimit == 0) {
                break;
            }

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, lastId);
                statement.setInt(2, pageLimit);
                try (ResultSet rs = statement.executeQuery()) {
                    int pageRows = 0;
                    while (rs.next()) {
                        long listingId = rs.getLong("listing_id");
                        lastId = listingId;
                        pageRows++;

                        Map<String, Object> doc = new HashMap<>();
                        doc.put("listingId", listingId);
                        doc.put("propertyId", rs.getLong("property_id"));
                        Date listed = rs.getDate("datelisted");
                        doc.put("dateListed", listed == null ? null : listed.toLocalDate().toString());
                        doc.put("forSale", rs.getBoolean("forsale"));

                        writer.set(
                            db.collection(FirestoreCollections.LISTINGS).document(String.valueOf(listingId)),
                            doc
                        );
                        rows++;
                    }
                    if (pageRows < pageLimit) {
                        break;
                    }
                }
            }
        }

        writer.flush();
        System.out.println("  listings: " + writer.committedCount() + " documents.");
    }

    private static void migrateListingPrices(Connection connection, Firestore db, Long rowLimit) throws SQLException {
        System.out.println("Migrating listing_price → listings/{id}/prices …");
        String sql =
            "select price_id, listing_id, price_date, price "
                + "from listing_price where price_id > ? order by price_id limit ?";

        FirestoreBatchWriter writer = new FirestoreBatchWriter(db, "listing prices");
        long lastId = 0L;
        long rows = 0L;

        while (true) {
            int pageLimit = pageLimit(rowLimit, rows);
            if (pageLimit == 0) {
                break;
            }

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, lastId);
                statement.setInt(2, pageLimit);
                try (ResultSet rs = statement.executeQuery()) {
                    int pageRows = 0;
                    while (rs.next()) {
                        long priceId = rs.getLong("price_id");
                        lastId = priceId;
                        pageRows++;

                        long listingId = rs.getLong("listing_id");
                        Map<String, Object> doc = new HashMap<>();
                        doc.put("priceId", priceId);
                        Date priceDate = rs.getDate("price_date");
                        doc.put("priceDate", priceDate == null ? null : priceDate.toLocalDate().toString());
                        doc.put("price", rs.getLong("price"));

                        DocumentReference ref = db.collection(FirestoreCollections.LISTINGS)
                            .document(String.valueOf(listingId))
                            .collection(FirestoreCollections.LISTING_PRICES)
                            .document(String.valueOf(priceId));
                        writer.set(ref, doc);
                        rows++;
                    }
                    if (pageRows < pageLimit) {
                        break;
                    }
                }
            }
        }

        writer.flush();
        System.out.println("  listing prices: " + writer.committedCount() + " documents.");
    }

    private static void migratePurchasers(Connection connection, Firestore db, Long rowLimit) throws SQLException {
        System.out.println("Migrating purchaser (+ person) → purchasers …");
        String sqlWithPerson =
            "select p.purchaser_id, "
                + "coalesce(per.email, '') as email, "
                + "coalesce(per.first_name, '') as first_name, "
                + "coalesce(per.last_name, '') as last_name "
                + "from purchaser p "
                + "left join person per on per.person_id = p.person_id "
                + "where p.purchaser_id > ? order by p.purchaser_id limit ?";

        String sqlPurchaserOnly =
            "select purchaser_id, "
                + "coalesce(email, '') as email, "
                + "coalesce(first_name, '') as first_name, "
                + "coalesce(last_name, '') as last_name "
                + "from purchaser where purchaser_id > ? order by purchaser_id limit ?";

        String sql = sqlWithPerson;
        if (!tableExists(connection, "person")) {
            sql = sqlPurchaserOnly;
            System.out.println("  (person table not found — using purchaser columns only)");
        } else if (!columnExists(connection, "purchaser", "person_id")) {
            sql = sqlPurchaserOnly;
            System.out.println("  (purchaser.person_id not found — using purchaser columns only)");
        }

        FirestoreBatchWriter writer = new FirestoreBatchWriter(db, "purchasers");
        long lastId = 0L;
        long rows = 0L;

        while (true) {
            int pageLimit = pageLimit(rowLimit, rows);
            if (pageLimit == 0) {
                break;
            }

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, lastId);
                statement.setInt(2, pageLimit);
                try (ResultSet rs = statement.executeQuery()) {
                    int pageRows = 0;
                    while (rs.next()) {
                        long purchaserId = rs.getLong("purchaser_id");
                        lastId = purchaserId;
                        pageRows++;

                        Map<String, Object> doc = new HashMap<>();
                        doc.put("purchaserId", purchaserId);
                        doc.put("email", rs.getString("email"));
                        doc.put("firstName", rs.getString("first_name"));
                        doc.put("lastName", rs.getString("last_name"));

                        writer.set(
                            db.collection(FirestoreCollections.PURCHASERS).document(String.valueOf(purchaserId)),
                            doc
                        );
                        rows++;
                    }
                    if (pageRows < pageLimit) {
                        break;
                    }
                }
            }
        }

        writer.flush();
        System.out.println("  purchasers: " + writer.committedCount() + " documents.");
    }

    private static void migratePurchaserInterests(Connection connection, Firestore db, Long rowLimit)
        throws SQLException {
        System.out.println("Migrating purchaser_interest → purchaser_interests …");
        String sql = "select purchaser_id, post_code from purchaser_interest order by purchaser_id, post_code";

        FirestoreBatchWriter writer = new FirestoreBatchWriter(db, "purchaser_interests");
        long rows = 0L;

        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                if (rowLimit != null && rows >= rowLimit) {
                    break;
                }
                long purchaserId = rs.getLong("purchaser_id");
                String postCode = rs.getString("post_code");
                String docId = interestDocId(purchaserId, postCode);

                Map<String, Object> doc = new HashMap<>();
                doc.put("purchaserId", purchaserId);
                doc.put("postCode", postCode);

                writer.set(
                    db.collection(FirestoreCollections.PURCHASER_INTERESTS).document(docId),
                    doc
                );
                rows++;
            }
        }

        writer.flush();
        System.out.println("  purchaser_interests: " + writer.committedCount() + " documents.");
    }

    private static void migratePostCodeSearchStats(Connection connection, Firestore db, Long rowLimit)
        throws SQLException {
        if (!tableExists(connection, "post_code_search_stat")) {
            System.out.println("Skipping post_code_search_stat (table not found).");
            return;
        }

        System.out.println("Migrating post_code_search_stat → post_code_search_stats …");
        FirestoreBatchWriter writer = new FirestoreBatchWriter(db, "post_code_search_stats");
        long rows = 0L;

        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                 "select post_code, search_count from post_code_search_stat order by post_code"
             )) {
            while (rs.next()) {
                if (rowLimit != null && rows >= rowLimit) {
                    break;
                }
                String postCode = rs.getString("post_code");
                Map<String, Object> doc = new HashMap<>();
                doc.put("postCode", postCode);
                doc.put("searchCount", rs.getLong("search_count"));
                writer.set(
                    db.collection(FirestoreCollections.POST_CODE_SEARCH_STATS).document(postCode),
                    doc
                );
                rows++;
            }
        }

        writer.flush();
        System.out.println("  post_code_search_stats: " + writer.committedCount() + " documents.");
    }

    private static void migrateCounters(Connection connection, Firestore db) throws SQLException {
        System.out.println("Setting Firestore counters from Supabase max ids …");
        long maxListingId = queryLong(connection, "select coalesce(max(listing_id), 0) from listing");
        long maxPriceId = queryLong(connection, "select coalesce(max(price_id), 0) from listing_price");

        FirestoreOps.await(
            db.collection(FirestoreCollections.COUNTERS).document(FirestoreCollections.COUNTER_LISTINGS)
                .set(Map.of("next", maxListingId))
        );
        FirestoreOps.await(
            db.collection(FirestoreCollections.COUNTERS).document(FirestoreCollections.COUNTER_PRICE_IDS)
                .set(Map.of("next", maxPriceId))
        );
        System.out.println("  counters/listings.next = " + maxListingId);
        System.out.println("  counters/price_ids.next = " + maxPriceId);
    }

    private static long queryLong(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            if (!rs.next()) {
                return 0L;
            }
            return rs.getLong(1);
        }
    }

    private static boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (ResultSet rs = connection.getMetaData().getTables(null, "public", tableName, new String[] {"TABLE"})) {
            return rs.next();
        }
    }

    private static boolean columnExists(Connection connection, String tableName, String columnName)
        throws SQLException {
        try (ResultSet rs = connection.getMetaData().getColumns(null, "public", tableName, columnName)) {
            return rs.next();
        }
    }

    private static String interestDocId(long purchaserId, String postCode) {
        return purchaserId + "_" + (postCode == null ? "" : postCode.replaceAll("\\s+", ""));
    }

    private static int pageLimit(Long rowLimit, long rowsSoFar) {
        if (rowLimit == null) {
            return PAGE_SIZE;
        }
        long remaining = rowLimit - rowsSoFar;
        if (remaining <= 0) {
            return 0;
        }
        return (int) Math.min(PAGE_SIZE, remaining);
    }
}
