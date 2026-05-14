package listing;

public class ListingCreateRequest {
    public String propertyID;
    /** ISO-8601 date (yyyy-MM-dd): when the property goes on sale for this listing. */
    public String listedOn;
    /** Initial asking price for this listing (same effective date as listedOn). */
    public String price;

    public ListingCreateRequest() {
    }
}
