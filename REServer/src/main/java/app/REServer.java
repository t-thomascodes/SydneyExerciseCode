package app;

import io.javalin.Javalin;
import listing.ListingController;
import listing.ListingDAO;
import notify.NotifyController;
import notify.NotifyDAO;
import property.PropertyController;
import property.PropertyDAO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

public class REServer {
    private static final Logger LOG = LoggerFactory.getLogger(REServer.class);

    public static void main(String[] args) {
        logStartupEnvironment();

        var properties = new PropertyDAO();
        var listings = new ListingDAO();
        var notifyDao = new NotifyDAO();
        PropertyController propertyHandler = new PropertyController(properties);
        ListingController listingHandler = new ListingController(listings, properties);
        NotifyController notifyHandler = new NotifyController(notifyDao);

        Javalin.create()
            .exception(Exception.class, (e, ctx) -> {
                LOG.error("{} {} failed", ctx.method(), ctx.path(), e);
                ctx.status(500);
                ctx.contentType("text/plain");
                ctx.result("Internal Server Error — see the terminal where REServer is running.");
            })
            .get("/", ctx -> ctx.result("Real Estate server is running"))
            // Property records are immutable hence no PUT and DELETE
            .get("/property", ctx -> propertyHandler.getAllProperties(ctx))
            .post("/property", ctx -> propertyHandler.createProperty(ctx))
            .get("/property/postcode/{postcode}", ctx ->
                propertyHandler.findPropertyByPostCode(ctx, ctx.pathParam("postcode")))
            // Register before /property/{propertyID} so "listings" path segment is not captured as an id
            .get("/property/{propertyID}/listings", ctx ->
                listingHandler.getListingsForProperty(ctx, ctx.pathParam("propertyID")))
            .get("/property/{propertyID}", ctx ->
                propertyHandler.getPropertyByID(ctx, ctx.pathParam("propertyID")))
            // Listings: same property can be listed multiple times; prices are a dated series per listing_id
            .post("/listing", ctx -> listingHandler.createListing(ctx))
            .post("/listing/{listingID}/price", ctx ->
                listingHandler.addListingPrice(ctx, ctx.pathParam("listingID")))
            .get("/listing/{listingID}", ctx ->
                listingHandler.getListing(ctx, ctx.pathParam("listingID")))
            // Purchaser ↔ for-sale property matches by preferred postcode (see sql/purchaser_interest.sql)
            .get("/notify/diag", ctx -> notifyHandler.getNotifyDiagnostics(ctx))
            .get("/notify", ctx -> notifyHandler.getNotifyReport(ctx))
            .start(7070);
    }

    private static void logStartupEnvironment() {
        Path cwd = Path.of("").toAbsolutePath();
        Path dotEnv = cwd.resolve(".env");
        LOG.info("JVM working directory (user.dir): {}", System.getProperty("user.dir"));
        LOG.info("Resolved ./.env path: {} — exists: {}", dotEnv, Files.isRegularFile(dotEnv));
        LOG.info(
            "Database settings present (env or .env): SUPABASE_DB_URL={}, SUPABASE_DB_USER={}, SUPABASE_DB_PASSWORD={}",
            envPresent("SUPABASE_DB_URL"),
            envPresent("SUPABASE_DB_USER"),
            envPresent("SUPABASE_DB_PASSWORD")
        );
    }

    private static boolean envPresent(String name) {
        String v = Env.get(name);
        return v != null && !v.isBlank();
    }
}

