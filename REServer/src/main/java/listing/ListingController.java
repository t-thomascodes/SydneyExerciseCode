package listing;

import io.javalin.http.Context;
import property.PropertyDAO;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

public class ListingController {

    private final ListingDAO listings;
    private final PropertyDAO properties;

    public ListingController(ListingDAO listings, PropertyDAO properties) {
        this.listings = listings;
        this.properties = properties;
    }

    public void createListing(Context ctx) {
        ListingCreateRequest body = ctx.bodyValidator(ListingCreateRequest.class).get();

        if (body.propertyID == null || body.propertyID.isBlank()
            || body.listedOn == null || body.listedOn.isBlank()
            || body.price == null || body.price.isBlank()) {
            ctx.status(400);
            ctx.result("propertyID, listedOn, and price are required");
            return;
        }

        if (properties.getPropertyById(body.propertyID).isEmpty()) {
            ctx.status(404);
            ctx.result("Property not found");
            return;
        }

        LocalDate listedOn;
        long propertyId;
        long price;
        try {
            listedOn = LocalDate.parse(body.listedOn);
            propertyId = Long.parseLong(body.propertyID);
            price = Long.parseLong(body.price);
        } catch (DateTimeParseException | NumberFormatException exception) {
            ctx.status(400);
            ctx.result("listedOn must be yyyy-MM-dd; propertyID and price must be numbers");
            return;
        }

        long listingId = listings.createListing(propertyId, listedOn, price);
        ctx.status(201);
        ctx.json(java.util.Map.of("listingID", String.valueOf(listingId)));
    }

    public void addListingPrice(Context ctx, String listingIdParam) {
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
        if (body.effectiveDate == null || body.effectiveDate.isBlank()
            || body.price == null || body.price.isBlank()) {
            ctx.status(400);
            ctx.result("effectiveDate and price are required");
            return;
        }

        LocalDate effectiveDate;
        long price;
        try {
            effectiveDate = LocalDate.parse(body.effectiveDate);
            price = Long.parseLong(body.price);
        } catch (DateTimeParseException | NumberFormatException exception) {
            ctx.status(400);
            ctx.result("effectiveDate must be yyyy-MM-dd; price must be a number");
            return;
        }

        listings.addPrice(listingId, effectiveDate, price);
        ctx.status(201);
        ctx.result("Price recorded");
    }

    public void getListing(Context ctx, String listingIdParam) {
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

    public void getListingsForProperty(Context ctx, String propertyIdParam) {
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
