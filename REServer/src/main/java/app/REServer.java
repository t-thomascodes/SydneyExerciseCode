package app;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.post;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.openapi.plugin.OpenApiPlugin;
import io.javalin.openapi.plugin.swagger.SwaggerPlugin;
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

    @OpenApi(
        summary = "Health check",
        operationId = "root",
        path = "/",
        methods = HttpMethod.GET,
        responses = {@OpenApiResponse(status = "200", description = "Plain text")}
    )
    public static void serveRoot(Context ctx) {
        ctx.result("Real Estate server is running");
    }

    public static void main(String[] args) {
        logStartupEnvironment();

        var properties = new PropertyDAO();
        var listings = new ListingDAO();
        var notifyDao = new NotifyDAO();
        PropertyController propertyHandler = new PropertyController(properties);
        ListingController listingHandler = new ListingController(listings, properties);
        NotifyController notifyHandler = new NotifyController(notifyDao);

        Javalin app = Javalin.create(config -> {
            config.registerPlugin(new OpenApiPlugin(pluginConfig ->
                pluginConfig.withDefinitionConfiguration((version, definition) ->
                    definition.withInfo(info -> {
                        info.setTitle("REServer");
                        info.setVersion("1.0-SNAPSHOT");
                        info.setDescription("Real estate HTTP API");
                    })
                )
            ));
            config.registerPlugin(new SwaggerPlugin());

            config.router.apiBuilder(() -> {
                get("/", REServer::serveRoot);
                get("/property", propertyHandler::getAllProperties);
                post("/property", propertyHandler::createProperty);
                get("/property/postcode/{postcode}", propertyHandler::findPropertyByPostCode);
                get("/property/{propertyID}/listings", listingHandler::getListingsForProperty);
                get("/property/{propertyID}", propertyHandler::getPropertyByID);
                post("/listing", listingHandler::createListing);
                post("/listing/{listingID}/price", listingHandler::addListingPrice);
                get("/listing/{listingID}", listingHandler::getListing);
                get("/notify/diag", notifyHandler::getNotifyDiagnostics);
                get("/notify", notifyHandler::getNotifyReport);
            });
        });

        app.exception(Exception.class, (e, ctx) -> {
            LOG.error("{} {} failed", ctx.method(), ctx.path(), e);
            ctx.status(500);
            ctx.contentType("text/plain");
            ctx.result("Internal Server Error — see the terminal where REServer is running.");
        });

        app.start(7070);
        LOG.info("API docs (Swagger UI): http://localhost:7070/swagger");
    }

    private static void logStartupEnvironment() {
        Path cwd = Path.of("").toAbsolutePath();
        Path dotEnv = cwd.resolve(".env");
        LOG.info("JVM working directory (user.dir): {}", System.getProperty("user.dir"));
        LOG.info("Resolved ./.env path: {} — exists: {}", dotEnv, Files.isRegularFile(dotEnv));
        LOG.info(
            "Firestore settings present (env or .env): GOOGLE_APPLICATION_CREDENTIALS={}, FIRESTORE_PROJECT_ID={}",
            envPresent("GOOGLE_APPLICATION_CREDENTIALS"),
            envPresent("FIRESTORE_PROJECT_ID")
        );
    }

    private static boolean envPresent(String name) {
        String v = Env.get(name);
        return v != null && !v.isBlank();
    }
}
