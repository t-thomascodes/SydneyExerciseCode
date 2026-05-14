package notify;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link NotificationReportFormatter}: turning {@link NotificationMatch} rows into
 * plain text or HTML for {@code GET /notify}.
 * <p>
 * These tests use <strong>in-memory lists only</strong> (no Javalin, no Postgres). They prove the
 * report layout, grouping, and escaping rules that the DAO is expected to feed with sorted rows
 * from {@link NotifyDAO#fetchPurchaseNotifications()}.
 * </p>
 */
class NotificationReportFormatterTest {

    /** User-controlled fields (names, emails) must not break HTML; special chars become entities. */
    @Test
    void escapeHtml_escapesSpecialCharacters() {
        assertEquals("", NotificationReportFormatter.escapeHtml(""));
        assertEquals("a&amp;b", NotificationReportFormatter.escapeHtml("a&b"));
        assertEquals("&lt;script&gt;", NotificationReportFormatter.escapeHtml("<script>"));
        assertEquals("&quot;x&quot;", NotificationReportFormatter.escapeHtml("\"x\""));
    }

    /**
     * DAO orders by purchaser then property; the formatter groups consecutive rows with the same
     * {@code purchaserId} so each purchaser gets one block in the report.
     */
    @Test
    void groupByPurchaserInOrder_splitsByPurchaserId() {
        List<NotificationMatch> rows = List.of(
            new NotificationMatch(1, "a@example.com", "A", "One", 10, 100_000L),
            new NotificationMatch(1, "a@example.com", "A", "One", 11, 200_000L),
            new NotificationMatch(2, "b@course.edu", "B", "Two", 20, 300_000L)
        );
        List<List<NotificationMatch>> groups = NotificationReportFormatter.groupByPurchaserInOrder(rows);
        assertEquals(2, groups.size());
        assertEquals(2, groups.get(0).size());
        assertEquals(1, groups.get(1).size());
        assertEquals(10, groups.get(0).get(0).propertyId());
        assertEquals(20, groups.get(1).get(0).propertyId());
    }

    /**
     * End-to-end for the text report: one purchaser, two properties — output must contain header
     * (id, name, email) and both property lines with the expected {@code sale_price} digits.
     */
    @Test
    void formatPlainText_includesPurchaserEmailAndPropertyLines() {
        List<NotificationMatch> rows = List.of(
            new NotificationMatch(
                42,
                "buyer42@university.edu",
                "Jane",
                "Doe",
                1001,
                850_000L
            ),
            new NotificationMatch(
                42,
                "buyer42@university.edu",
                "Jane",
                "Doe",
                1002,
                920_000L
            )
        );
        String text = NotificationReportFormatter.formatPlainText(rows, false);
        assertTrue(text.contains("Purchaser 42"));
        assertTrue(text.contains("buyer42@university.edu"));
        assertTrue(text.contains("property_id=1001"));
        assertTrue(text.contains("sale_price=850000"));
        assertTrue(text.contains("property_id=1002"));
        assertFalse(text.contains("[Report truncated"));
    }

    /** When the DAO returns no rows, the text body still explains that nothing matched (not a blank page). */
    @Test
    void formatPlainText_emptyExplainsNoMatches() {
        String text = NotificationReportFormatter.formatPlainText(List.of(), false);
        assertTrue(text.contains("No matching"));
    }

    /** When the row cap is hit in the DAO, the formatter appends a truncation line so users know the report was cut off. */
    @Test
    void formatPlainText_truncationNotice() {
        String text = NotificationReportFormatter.formatPlainText(List.of(), true);
        assertTrue(text.contains("truncated"));
    }

    /**
     * HTML report wraps matches in a table; the purchaser line is escaped so a malicious-looking
     * name cannot inject tags (e.g. {@code <Evil>}) into the document.
     */
    @Test
    void formatHtml_containsTableAndEscapesHeader() {
        List<NotificationMatch> rows = List.of(
            new NotificationMatch(7, "evil@x.com", "<Evil>", "Name", 1, 1L)
        );
        String html = NotificationReportFormatter.formatHtml(rows, false);
        assertTrue(html.contains("<!DOCTYPE html>"));
        assertTrue(html.contains("<table"));
        assertTrue(html.contains("<td>1</td>"));
        assertTrue(html.contains("&lt;Evil&gt;"));
        assertFalse(html.contains("<Evil>"));
    }
}
