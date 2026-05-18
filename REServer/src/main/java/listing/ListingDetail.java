package listing;

import java.util.ArrayList;
import java.util.List;

public class ListingDetail {
    private String listingID;
    private String propertyID;
    private String listedOn;
    private List<ListingPricePoint> prices = new ArrayList<>();

    public ListingDetail() {
    }

    public String getListingID() {
        return listingID;
    }

    public void setListingID(String listingID) {
        this.listingID = listingID;
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

    public List<ListingPricePoint> getPrices() {
        return List.copyOf(prices);
    }

    public void setPrices(List<ListingPricePoint> prices) {
        this.prices = prices != null ? new ArrayList<>(prices) : new ArrayList<>();
    }
}
