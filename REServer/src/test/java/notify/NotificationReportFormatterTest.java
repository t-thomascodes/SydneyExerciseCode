package notify;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link NotificationReportFormatter}: turning {@link NotificationMatch} rows into
 * plain text or HTML for {@code GET /notify}.
 */
class NotificationReportFormatterTest {

    private static NotificationMatch match(
        long purchaserId,
        String email,
        String first,
        String last,
        long propertyId,
        String postCode,
        long salePrice,
        long accessCount,
        long postcodeSearchCount
    ) {
        return new NotificationMatch(
            purchaserId,
            email,
            first,
            last,
            propertyId,
            postCode,
            salePrice,
            accessCount,
            postcodeSearchCount
        );
    }

    @Test
    void escapeHtml_escapesSpecialCharacters() {
        assertEquals("", NotificationReportFormatter.escapeHtml(""));
        assertEquals("a&amp;b", NotificationReportFormatter.escapeHtml("a&b"));
        assertEquals("&lt;script&gt;", NotificationReportFormatter.escapeHtml("<script>"));
        assertEquals("&quot;x&quot;", NotificationReportFormatter.escapeHtml("\"x\""));
    }

    @Test
    void groupByPurchaserInOrder_splitsByPurchaserId() {
        List<NotificationMatch> rows = List.of(
            match(1, "a@example.com", "A", "One", 10, "2000", 100_000L, 1, 5),
            match(1, "a@example.com", "A", "One", 11, "2000", 200_000L, 2, 5),
            match(2, "b@course.edu", "B", "Two", 20, "2010", 300_000L, 0, 1)
        );
        List<List<NotificationMatch>> groups = NotificationReportFormatter.groupByPurchaserInOrder(rows);
        assertEquals(2, groups.size());
        assertEquals(2, groups.get(0).size());
        assertEquals(1, groups.get(1).size());
        assertEquals(10, groups.get(0).get(0).propertyId());
        assertEquals(20, groups.get(1).get(0).propertyId());
    }

    @Test
    void formatPlainText_includesMetricsAndTrendingPostcodes() {
        List<NotificationMatch> rows = List.of(
            match(42, "buyer42@university.edu", "Jane", "Doe", 1001, "2000", 850_000L, 12, 40),
            match(42, "buyer42@university.edu", "Jane", "Doe", 1002, "2000", 920_000L, 3, 40)
        );
        String text = NotificationReportFormatter.formatPlainText(rows, false);
        assertTrue(text.contains("Purchaser 42"));
        assertTrue(text.contains("buyer42@university.edu"));
        assertTrue(text.contains("Trending postcodes"));
        assertTrue(text.contains("2000 (40 searches)"));
        assertTrue(text.contains("property_id=1001"));
        assertTrue(text.contains("views=12"));
        assertTrue(text.contains("property_id=1002"));
        assertTrue(text.contains("views=3"));
        assertFalse(text.contains("[Report truncated"));
    }

    @Test
    void sortByAccessDesc_ordersPropertiesByViews() {
        List<NotificationMatch> rows = List.of(
            match(1, "a@x.com", "A", "B", 1, "2000", 1, 2, 0),
            match(1, "a@x.com", "A", "B", 2, "2000", 1, 9, 0)
        );
        List<NotificationMatch> sorted = NotificationReportFormatter.sortByAccessDesc(rows);
        assertEquals(2, sorted.get(0).propertyId());
        assertEquals(1, sorted.get(1).propertyId());
    }

    @Test
    void formatPlainText_emptyExplainsNoMatches() {
        String text = NotificationReportFormatter.formatPlainText(List.of(), false);
        assertTrue(text.contains("No matching"));
    }

    @Test
    void formatPlainText_truncationNotice() {
        String text = NotificationReportFormatter.formatPlainText(List.of(), true);
        assertTrue(text.contains("truncated"));
    }

    @Test
    void formatHtml_containsMetricsColumnsAndEscapesHeader() {
        List<NotificationMatch> rows = List.of(
            match(7, "evil@x.com", "<Evil>", "Name", 1, "2000", 1, 5, 10)
        );
        String html = NotificationReportFormatter.formatHtml(rows, false);
        assertTrue(html.contains("<!DOCTYPE html>"));
        assertTrue(html.contains("<th>Views</th>"));
        assertTrue(html.contains("<th>Postcode searches</th>"));
        assertTrue(html.contains("<td>5</td>"));
        assertTrue(html.contains("<td>10</td>"));
        assertTrue(html.contains("&lt;Evil&gt;"));
        assertFalse(html.contains("<Evil>"));
    }
}
