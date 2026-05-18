package listing;

public class ListingPricePoint {
    private String effectiveDate;
    private String price;

    public ListingPricePoint() {
    }

    public ListingPricePoint(String effectiveDate, String price) {
        this.effectiveDate = effectiveDate;
        this.price = price;
    }

    public String getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(String effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }
}
