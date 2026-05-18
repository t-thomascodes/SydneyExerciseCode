package listing;

public class ListingCreateRequest {
    private String propertyID;
    /** ISO-8601 date (yyyy-MM-dd): when the property goes on sale for this listing. */
    private String listedOn;
    /** Initial asking price for this listing (same effective date as listedOn). */
    private String price;

    public ListingCreateRequest() {
    }

    public String getPropertyID() {
        return propertyID;
    }

    public void setPropertyID(String propertyID) {
        this.propertyID = propertyID;
    }

    public String getListedOn() {
        return listedOn;
    }

    public void setListedOn(String listedOn) {
        this.listedOn = listedOn;
    }

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }
}
