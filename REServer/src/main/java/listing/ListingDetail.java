package listing;

import java.util.ArrayList;
import java.util.List;

public class ListingDetail {
    public String listingID;
    public String propertyID;
    public String listedOn;
    public List<ListingPricePoint> prices = new ArrayList<>();

    public ListingDetail() {
    }
}
