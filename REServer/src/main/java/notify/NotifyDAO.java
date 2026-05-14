package notify;

import app.DatabaseConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Loads purchaser ↔ for-sale property matches in one round trip using set-based SQL
 * (CTEs + DISTINCT ON), suitable for large property tables when indexes exist on
 * {@code purchaser_interest(post_code)} and {@code property(post_code, for_sale)}.
 */
public class NotifyDAO {

    /**
     * Hard cap on returned rows so a single HTTP response cannot exhaust heap if data grows unexpectedly.
     */
    public static final int NOTIFY_MAX_ROWS = 50_000;

    private boolean lastFetchTruncated;

    public boolean wasLastFetchTruncated() {
        return lastFetchTruncated;
    }

    /**
     * For each purchaser, returns for-sale properties in postcodes they follow.
     * <p>
     * Price rule: latest {@code listing_price} row for the chosen listing (highest {@code listing_id}
     * per property that has any price history), otherwise {@code property.purchase_price}.
     * </p>
     * <p>
     * Uses schema table names {@code listing} and {@code listing_price} (matches course ERD / Supabase).
     * </p>
     */
    public List<NotificationMatch> fetchPurchaseNotifications() {
        lastFetchTruncated = false;
        String sql =
            "with latest_price_per_listing as ("
                + "  select distinct on (listing_id) listing_id, price as latest_price"
                + "  from listing_price"
                + "  order by listing_id, price_date desc, price_id desc"
                + "),"
                + "chosen_listing_per_property as ("
                + "  select distinct on (l.property_id) l.property_id, lp.latest_price"
                + "  from listing l"
                + "  inner join latest_price_per_listing lp on lp.listing_id = l.listing_id"
                + "  order by l.property_id, l.listing_id desc"
                + ")"
                + "select"
                + "  p.purchaser_id,"
                + "  p.email,"
                + "  p.first_name,"
                + "  p.last_name,"
                + "  pr.property_id,"
                + "  coalesce(clp.latest_price, pr.purchase_price) as sale_price"
                + " from purchaser p"
                + " inner join purchaser_interest pi on pi.purchaser_id = p.purchaser_id"
                + " inner join property pr"
                + "  on trim(pr.post_code) = trim(pi.post_code)"
                + " and pr.for_sale = true"
                + " left join chosen_listing_per_property clp on clp.property_id = pr.property_id"
                + " order by p.purchaser_id, pr.property_id"
                + " limit ?";

        try (Connection connection = DatabaseConfig.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, NOTIFY_MAX_ROWS + 1);
            List<NotificationMatch> out = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    if (out.size() == NOTIFY_MAX_ROWS) {
                        lastFetchTruncated = true;
                        break;
                    }
                    out.add(mapRow(rs));
                }
            }
            return out;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load purchase notifications", exception);
        }
    }

    private static NotificationMatch mapRow(ResultSet rs) throws SQLException {
        return new NotificationMatch(
            rs.getLong("purchaser_id"),
            rs.getString("email"),
            rs.getString("first_name"),
            rs.getString("last_name"),
            rs.getLong("property_id"),
            rs.getLong("sale_price")
        );
    }

    /**
     * Lightweight counts and postcode samples for debugging empty {@link #fetchPurchaseNotifications()} results.
     */
    public NotifyDiagnostics fetchDiagnostics() {
        try (Connection connection = DatabaseConfig.connect()) {
            long purchaserCount = queryLong(connection, "select count(*) from purchaser");
            long interestCount = queryLong(connection, "select count(*) from purchaser_interest");
            long forSaleCount = queryLong(
                connection, "select count(*) from property where for_sale = true"
            );
            long pairMatchCount = queryLong(
                connection,
                "select count(*) from purchaser_interest pi"
                    + " inner join property pr on trim(pr.post_code) = trim(pi.post_code)"
                    + " and pr.for_sale = true"
            );
            List<String> sampleInterest = distinctPostcodes(
                connection,
                "select distinct trim(post_code) as pc from purchaser_interest"
                    + " where trim(post_code) <> '' order by pc nulls last limit 20"
            );
            List<String> sampleForSale = distinctPostcodes(
                connection,
                "select distinct trim(post_code) as pc from property"
                    + " where for_sale = true and trim(post_code) <> '' order by pc nulls last limit 20"
            );
            List<String> hints = buildHints(
                purchaserCount,
                interestCount,
                forSaleCount,
                pairMatchCount
            );
            return new NotifyDiagnostics(
                purchaserCount,
                interestCount,
                forSaleCount,
                pairMatchCount,
                sampleInterest,
                sampleForSale,
                hints
            );
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load /notify diagnostics", exception);
        }
    }

    private static long queryLong(Connection connection, String sql) throws SQLException {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (!rs.next()) {
                return 0L;
            }
            return rs.getLong(1);
        }
    }

    private static List<String> distinctPostcodes(Connection connection, String sql) throws SQLException {
        Set<String> ordered = new LinkedHashSet<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String pc = rs.getString("pc");
                if (pc != null && !pc.isBlank()) {
                    ordered.add(pc);
                }
            }
        }
        return List.copyOf(ordered);
    }

    private static List<String> buildHints(
        long purchaserCount,
        long interestCount,
        long forSaleCount,
        long pairMatchCount
    ) {
        List<String> hints = new ArrayList<>();
        if (purchaserCount == 0) {
            hints.add("Table purchaser has no rows.");
        }
        if (interestCount == 0) {
            hints.add("Table purchaser_interest has no rows — nothing to match to postcodes.");
        }
        if (forSaleCount == 0) {
            hints.add("No property rows have for_sale = true — /notify only includes for-sale properties.");
        }
        if (interestCount > 0 && forSaleCount > 0 && pairMatchCount == 0) {
            hints.add(
                "At least one interest and one for-sale property exist, but trimmed postcodes never match."
                    + " Compare sampleInterestPostcodesTrimmed vs sampleForSalePropertyPostcodesTrimmed"
                    + " (format, spacing, or different regions)."
            );
        }
        if (pairMatchCount > 0) {
            hints.add(
                "Postcode + for_sale join finds "
                    + pairMatchCount
                    + " (interest, property) pair(s); /notify should surface these with purchaser details."
            );
        }
        if (hints.isEmpty()) {
            hints.add("No issues detected from counts alone.");
        }
        return List.copyOf(hints);
    }
}
