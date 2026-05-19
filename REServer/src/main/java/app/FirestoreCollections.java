package app;

/** Firestore collection and field names for the REServer document model. */
public final class FirestoreCollections {

    public static final String PROPERTIES = "properties";
    public static final String LISTINGS = "listings";
    public static final String LISTING_PRICES = "prices";
    public static final String PURCHASERS = "purchasers";
    public static final String PURCHASER_INTERESTS = "purchaser_interests";
    public static final String POST_CODE_SEARCH_STATS = "post_code_search_stats";
    public static final String COUNTERS = "counters";

    public static final String COUNTER_LISTINGS = "listings";
    public static final String COUNTER_PRICE_IDS = "price_ids";

    private FirestoreCollections() {
    }
}
