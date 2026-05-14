package notify;

import io.javalin.http.Context;

import java.util.List;
import java.util.Locale;

public class NotifyController {

    private final NotifyDAO notifyDAO;

    public NotifyController(NotifyDAO notifyDAO) {
        this.notifyDAO = notifyDAO;
    }

    /**
     * GET /notify/diag — JSON counts and postcode samples (debugging empty {@code /notify}).
     */
    public void getNotifyDiagnostics(Context ctx) {
        NotifyDiagnostics diag = notifyDAO.fetchDiagnostics();
        ctx.status(200);
        ctx.json(diag);
    }

    /**
     * GET /notify — builds the notification report.
     * Query {@code format}: {@code text} (default) or {@code html}.
     */
    public void getNotifyReport(Context ctx) {
        String format = ctx.queryParam("format");
        if (format != null && !format.isBlank() && !isKnownFormat(format)) {
            ctx.status(400);
            ctx.contentType("text/plain; charset=utf-8");
            ctx.result("Unknown format; use text (default) or html.");
            return;
        }
        boolean asHtml = format != null && "html".equalsIgnoreCase(format.trim());

        List<NotificationMatch> rows = notifyDAO.fetchPurchaseNotifications();
        boolean truncated = notifyDAO.wasLastFetchTruncated();

        String body = asHtml
            ? NotificationReportFormatter.formatHtml(rows, truncated)
            : NotificationReportFormatter.formatPlainText(rows, truncated);

        ctx.status(200);
        if (asHtml) {
            ctx.contentType("text/html; charset=utf-8");
        } else {
            ctx.contentType("text/plain; charset=utf-8");
        }
        ctx.result(body);
    }

    /** Supported values for {@code format} (for docs / tests). */
    public static boolean isKnownFormat(String format) {
        if (format == null || format.isBlank()) {
            return true;
        }
        String f = format.trim().toLowerCase(Locale.ROOT);
        return "text".equals(f) || "html".equals(f);
    }
}
