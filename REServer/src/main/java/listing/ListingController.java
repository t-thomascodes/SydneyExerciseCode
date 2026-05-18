package listing;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import property.PropertyDAO;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

public class ListingController {

    private final ListingDAO listings;
    private final PropertyDAO properties;

    @SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Constructor stores injected DAO references by design (same as Spring-style wiring)."
    )
    public ListingController(ListingDAO listings, PropertyDAO properties) {
        this.listings = listings;
        this.properties = properties;
    }

    @OpenApi(
        summary = "Create a listing for a property",
        operationId = "createListing",
        path = "/listing",
        methods = HttpMethod.POST,
        tags = {"Listing"},
        requestBody = @OpenApiRequestBody(content = {@OpenApiContent(from = ListingCreateRequest.class)}),
        responses = {
            @OpenApiResponse(status = "201", description = "JSON object with listingID"),
            @OpenApiResponse(status = "400", description = "Missing or invalid fields"),
            @OpenApiResponse(status = "404", description = "Property not found")
        }
    )
    public void createListing(Context ctx) {
        ListingCreateRequest body = ctx.bodyValidator(ListingCreateRequest.class).get();

        if (body.getPropertyID() == null || body.getPropertyID().isBlank()
            || body.getListedOn() == null || body.getListedOn().isBlank()
            || body.getPrice() == null || body.getPrice().isBlank()) {
            ctx.status(400);
            ctx.result("propertyID, listedOn, and price are required");
            return;
        }

        if (properties.getPropertyById(body.getPropertyID()).isEmpty()) {
            ctx.status(404);
            ctx.result("Property not found");
            return;
        }

        LocalDate listedOn;
        long propertyId;
        long price;
        try {
            listedOn = LocalDate.parse(body.getListedOn());
            propertyId = Long.parseLong(body.getPropertyID());
            price = Long.parseLong(body.getPrice());
        } catch (DateTimeParseException | NumberFormatException exception) {
            ctx.status(400);
            ctx.result("listedOn must be yyyy-MM-dd; propertyID and price must be numbers");
            return;
        }

        long listingId = listings.createListing(propertyId, listedOn, price);
        ctx.status(201);
        ctx.json(java.util.Map.of("listingID", String.valueOf(listingId)));
    }

    @OpenApi(
        summary = "Add a dated price point to a listing",
        operationId = "addListingPrice",
        path = "/listing/{listingID}/price",
        methods = HttpMethod.POST,
        tags = {"Listing"},
        pathParams = {@OpenApiParam(name = "listingID", type = String.class)},
        requestBody = @OpenApiRequestBody(content = {@OpenApiContent(from = ListingPriceUpdate.class)}),
        responses = {
            @OpenApiResponse(status = "201", description = "Plain text confirmation"),
            @OpenApiResponse(status = "400", description = "Invalid id or body"),
            @OpenApiResponse(status = "404", description = "Listing not found")
        }
    )
    public void addListingPrice(Context ctx) {
        String listingIdParam = ctx.pathParam("listingID");
        long listingId;
        try {
            listingId = Long.parseLong(listingIdParam);
        } catch (NumberFormatException exception) {
            ctx.status(400);
            ctx.result("Invalid listing id");
            return;
        }

        if (listings.getListing(listingId).isEmpty()) {
            ctx.status(404);
            ctx.result("Listing not found");
            return;
        }

        ListingPriceUpdate body = ctx.bodyValidator(ListingPriceUpdate.class).get();
        if (body.getEffectiveDate() == null || body.getEffectiveDate().isBlank()
            || body.getPrice() == null || body.getPrice().isBlank()) {
            ctx.status(400);
            ctx.result("effectiveDate and price are required");
            return;
        }

        LocalDate effectiveDate;
        long price;
        try {
            effectiveDate = LocalDate.parse(body.getEffectiveDate());
            price = Long.parseLong(body.getPrice());
        } catch (DateTimeParseException | NumberFormatException exception) {
            ctx.status(400);
            ctx.result("effectiveDate must be yyyy-MM-dd; price must be a number");
            return;
        }

        listings.addPrice(listingId, effectiveDate, price);
        ctx.status(201);
        ctx.result("Price recorded");
    }

    @OpenApi(
        summary = "Get listing detail including price history",
        operationId = "getListing",
        path = "/listing/{listingID}",
        methods = HttpMethod.GET,
        tags = {"Listing"},
        pathParams = {@OpenApiParam(name = "listingID", type = String.class)},
        responses = {
            @OpenApiResponse(status = "200", content = {@OpenApiContent(from = ListingDetail.class)}),
            @OpenApiResponse(status = "400", description = "Invalid listing id"),
            @OpenApiResponse(status = "404", description = "Listing not found")
        }
    )
    public void getListing(Context ctx) {
        String listingIdParam = ctx.pathParam("listingID");
        long listingId;
        try {
            listingId = Long.parseLong(listingIdParam);
        } catch (NumberFormatException exception) {
            ctx.status(400);
            ctx.result("Invalid listing id");
            return;
        }

        Optional<ListingDetail> listing = listings.getListing(listingId);
        if (listing.isEmpty()) {
            ctx.status(404);
            ctx.result("Listing not found");
        } else {
            ctx.status(200);
            ctx.json(listing.get());
        }
    }

    @OpenApi(
        summary = "List all listings for a property",
        operationId = "getListingsForProperty",
        path = "/property/{propertyID}/listings",
        methods = HttpMethod.GET,
        tags = {"Listing"},
        pathParams = {@OpenApiParam(name = "propertyID", type = String.class)},
        responses = {
            @OpenApiResponse(status = "200", content = {@OpenApiContent(from = ListingDetail[].class)}),
            @OpenApiResponse(status = "400", description = "Invalid property id"),
            @OpenApiResponse(status = "404", description = "Property not found")
        }
    )
    public void getListingsForProperty(Context ctx) {
        String propertyIdParam = ctx.pathParam("propertyID");
        if (properties.getPropertyById(propertyIdParam).isEmpty()) {
            ctx.status(404);
            ctx.result("Property not found");
            return;
        }

        long propertyId;
        try {
            propertyId = Long.parseLong(propertyIdParam);
        } catch (NumberFormatException exception) {
            ctx.status(400);
            ctx.result("Invalid property id");
            return;
        }

        List<ListingDetail> rows = listings.getListingsForProperty(propertyId);
        ctx.status(200);
        ctx.json(rows);
    }
}
