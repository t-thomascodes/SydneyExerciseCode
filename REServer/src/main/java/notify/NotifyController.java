package notify;

import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;

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
    @OpenApi(
        summary = "Diagnostics for purchaser notification query",
        operationId = "getNotifyDiagnostics",
        path = "/notify/diag",
        methods = HttpMethod.GET,
        tags = {"Notify"},
        responses = {
            @OpenApiResponse(status = "200", content = {@OpenApiContent(from = NotifyDiagnostics.class)})
        }
    )
    public void getNotifyDiagnostics(Context ctx) {
        NotifyDiagnostics diag = notifyDAO.fetchDiagnostics();
        ctx.status(200);
        ctx.json(diag);
    }

    /**
     * GET /notify — builds the notification report.
     * Query {@code format}: {@code text} (default) or {@code html}.
     */
    @OpenApi(
        operationId = "getNotifyReport",
        path = "/notify",
        methods = HttpMethod.GET,
        tags = {"Notify"},
        queryParams = {
            @OpenApiParam(
                name = "format",
                type = String.class,
                description = "text (default) or html",
                required = false
            ),
            @OpenApiParam(
                name = "minAccess",
                type = Long.class,
                description = "Only properties with at least this many views",
                required = false
            ),
            @OpenApiParam(
                name = "minPostcodeSearches",
                type = Long.class,
                description = "Only properties in postcodes searched at least this many times",
                required = false
            ),
            @OpenApiParam(
                name = "hotPostcodesOnly",
                type = Boolean.class,
                description = "Shorthand for minPostcodeSearches=1",
                required = false
            )
        },
        responses = {
            @OpenApiResponse(status = "200", description = "text/plain or text/html body"),
            @OpenApiResponse(status = "400", description = "Unknown format")
        }
    )
    public void getNotifyReport(Context ctx) {
        String format = ctx.queryParam("format");
        if (format != null && !format.isBlank() && !isKnownFormat(format)) {
            ctx.status(400);
            ctx.contentType("text/plain; charset=utf-8");
            ctx.result("Unknown format; use text (default) or html.");
            return;
        }
        boolean asHtml = format != null && "html".equalsIgnoreCase(format.trim());

        NotifyFilters filters;
        try {
            filters = NotifyFilters.fromQueryParams(
                ctx.queryParam("minAccess"),
                ctx.queryParam("minPostcodeSearches"),
                ctx.queryParam("hotPostcodesOnly")
            );
        } catch (IllegalArgumentException exception) {
            ctx.status(400);
            ctx.contentType("text/plain; charset=utf-8");
            ctx.result(exception.getMessage());
            return;
        }

        List<NotificationMatch> rows = notifyDAO.fetchPurchaseNotifications(filters);
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
