package notify;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds human-readable report bodies from {@link NotificationMatch} rows.
 * Grouping is deterministic when input rows are ordered by purchaser id, then popularity.
 */
public final class NotificationReportFormatter {

    private NotificationReportFormatter() {
    }

    public static String formatPlainText(List<NotificationMatch> rows, boolean truncated) {
        Objects.requireNonNull(rows, "rows");
        StringBuilder sb = new StringBuilder(Math.max(256, rows.size() * 48));
        appendPlainText(sb, rows, truncated);
        return sb.toString();
    }

    public static String formatHtml(List<NotificationMatch> rows, boolean truncated) {
        Objects.requireNonNull(rows, "rows");
        StringBuilder sb = new StringBuilder(Math.max(512, rows.size() * 64));
        appendHtml(sb, rows, truncated);
        return sb.toString();
    }

    /** Package-private for tests that want to append without extra allocation hints. */
    static void appendPlainText(StringBuilder sb, List<NotificationMatch> rows, boolean truncated) {
        if (rows.isEmpty()) {
            sb.append("No matching for-sale properties for any purchaser interests.\n");
            if (truncated) {
                sb.append("\n[Report truncated: row cap exceeded]\n");
            }
            return;
        }
        List<List<NotificationMatch>> groups = groupByPurchaserInOrder(rows);
        for (List<NotificationMatch> group : groups) {
            NotificationMatch head = group.get(0);
            sb.append("Purchaser ")
                .append(head.purchaserId())
                .append(" — ")
                .append(head.firstName())
                .append(' ')
                .append(head.lastName())
                .append(" <")
                .append(head.email())
                .append(">\n");
            appendPostcodeActivityPlain(sb, group);
            for (NotificationMatch m : sortByAccessDesc(group)) {
                sb.append("  property_id=")
                    .append(m.propertyId())
                    .append("  post_code=")
                    .append(m.postCode())
                    .append("  sale_price=")
                    .append(m.salePrice())
                    .append("  views=")
                    .append(m.accessCount())
                    .append("  postcode_searches=")
                    .append(m.postcodeSearchCount())
                    .append('\n');
            }
            sb.append('\n');
        }
        if (truncated) {
            sb.append("[Report truncated: row cap exceeded]\n");
        }
    }

    static void appendHtml(StringBuilder sb, List<NotificationMatch> rows, boolean truncated) {
        sb.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>Purchaser notifications</title>")
            .append("<style>body{font-family:system-ui,sans-serif;margin:1.5rem}table{border-collapse:collapse}")
            .append("th,td{border:1px solid #ccc;padding:0.4rem 0.6rem;text-align:left}")
            .append("caption{font-weight:bold;text-align:left;margin-bottom:0.5rem}")
            .append(".meta{color:#444;font-size:0.95rem;margin:0.25rem 0 0.75rem}</style></head><body>");
        sb.append("<h1>For-sale matches by purchaser</h1>");
        sb.append("<p class=\"meta\">Sorted by property views (hottest first). ");
        sb.append("Postcode search counts show area interest.</p>");
        if (rows.isEmpty()) {
            sb.append("<p>No matching for-sale properties for any purchaser interests.</p>");
        } else {
            List<List<NotificationMatch>> groups = groupByPurchaserInOrder(rows);
            for (List<NotificationMatch> group : groups) {
                NotificationMatch head = group.get(0);
                sb.append("<section style=\"margin-bottom:2rem\">");
                sb.append("<h2 style=\"font-size:1.1rem\">")
                    .append(escapeHtml("Purchaser " + head.purchaserId() + " — "
                        + head.firstName() + " " + head.lastName() + " <" + head.email() + ">"))
                    .append("</h2>");
                appendPostcodeActivityHtml(sb, group);
                sb.append("<table><caption>Properties in preferred postcodes</caption>");
                sb.append("<thead><tr><th>Property ID</th><th>Postcode</th><th>Sale price</th>")
                    .append("<th>Views</th><th>Postcode searches</th></tr></thead><tbody>");
                for (NotificationMatch m : sortByAccessDesc(group)) {
                    sb.append("<tr><td>").append(m.propertyId()).append("</td><td>")
                        .append(escapeHtml(m.postCode())).append("</td><td>").append(m.salePrice())
                        .append("</td><td>").append(m.accessCount()).append("</td><td>")
                        .append(m.postcodeSearchCount()).append("</td></tr>");
                }
                sb.append("</tbody></table></section>");
            }
        }
        if (truncated) {
            sb.append("<p><strong>Report truncated:</strong> row cap exceeded.</p>");
        }
        sb.append("</body></html>");
    }

    static void appendPostcodeActivityPlain(StringBuilder sb, List<NotificationMatch> group) {
        List<PostcodeActivity> activity = postcodeActivityForGroup(group);
        if (activity.isEmpty()) {
            return;
        }
        sb.append("  Trending postcodes: ");
        for (int i = 0; i < activity.size(); i++) {
            PostcodeActivity a = activity.get(i);
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(a.postCode()).append(" (").append(a.searchCount()).append(" searches)");
        }
        sb.append('\n');
    }

    static void appendPostcodeActivityHtml(StringBuilder sb, List<NotificationMatch> group) {
        List<PostcodeActivity> activity = postcodeActivityForGroup(group);
        if (activity.isEmpty()) {
            return;
        }
        sb.append("<p class=\"meta\"><strong>Trending postcodes:</strong> ");
        for (int i = 0; i < activity.size(); i++) {
            PostcodeActivity a = activity.get(i);
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(escapeHtml(a.postCode())).append(" (").append(a.searchCount()).append(" searches)");
        }
        sb.append("</p>");
    }

    static List<PostcodeActivity> postcodeActivityForGroup(List<NotificationMatch> group) {
        Map<String, Long> byPostcode = new LinkedHashMap<>();
        for (NotificationMatch m : group) {
            String pc = m.postCode() == null ? "" : m.postCode();
            if (pc.isBlank()) {
                continue;
            }
            byPostcode.merge(pc, m.postcodeSearchCount(), Math::max);
        }
        List<PostcodeActivity> activity = new ArrayList<>();
        byPostcode.forEach((pc, count) -> activity.add(new PostcodeActivity(pc, count)));
        activity.sort(Comparator.comparingLong(PostcodeActivity::searchCount).reversed()
            .thenComparing(PostcodeActivity::postCode));
        return activity;
    }

    static List<NotificationMatch> sortByAccessDesc(List<NotificationMatch> group) {
        return group.stream()
            .sorted(Comparator.comparingLong(NotificationMatch::accessCount).reversed()
                .thenComparingLong(NotificationMatch::propertyId))
            .toList();
    }

    /**
     * Groups consecutive rows with the same purchaser id. Input must already be sorted by
     * purchaser id (as produced by {@link NotifyDAO}).
     */
    static List<List<NotificationMatch>> groupByPurchaserInOrder(List<NotificationMatch> rows) {
        List<List<NotificationMatch>> out = new ArrayList<>();
        List<NotificationMatch> current = null;
        long lastId = Long.MIN_VALUE;
        for (NotificationMatch m : rows) {
            if (current == null || m.purchaserId() != lastId) {
                current = new ArrayList<>();
                out.add(current);
                lastId = m.purchaserId();
            }
            current.add(m);
        }
        return out;
    }

    static String escapeHtml(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(raw.length() + 8);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private record PostcodeActivity(String postCode, long searchCount) {
    }
}
