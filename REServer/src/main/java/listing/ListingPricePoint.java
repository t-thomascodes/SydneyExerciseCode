package listing;

public class ListingPricePoint {
    public String effectiveDate;
    public String price;

    public ListingPricePoint() {
    }

    public ListingPricePoint(String effectiveDate, String price) {
        this.effectiveDate = effectiveDate;
        this.price = price;
    }
}
