package listing;

import app.FirestoreCollections;
import app.FirestoreConfig;
import app.FirestoreCounters;
import app.FirestoreOps;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ListingDAO {

    private static final String FIELD_LISTING_ID = "listingId";
    private static final String FIELD_PROPERTY_ID = "propertyId";
    private static final String FIELD_DATE_LISTED = "dateListed";
    private static final String FIELD_FOR_SALE = "forSale";
    private static final String FIELD_PRICE_ID = "priceId";
    private static final String FIELD_PRICE_DATE = "priceDate";
    private static final String FIELD_PRICE = "price";

    public long createListing(long propertyId, LocalDate listedOn, long initialPrice) {
        Firestore db = FirestoreConfig.db();
        try {
            return FirestoreOps.await(
                db.runTransaction(transaction -> {
                    long listingId = FirestoreCounters.nextListingId(transaction, db);
                    long priceId = FirestoreCounters.nextPriceId(transaction, db);

                    DocumentReference listingRef = db.collection(FirestoreCollections.LISTINGS)
                        .document(String.valueOf(listingId));
                    Map<String, Object> listing = new HashMap<>();
                    listing.put(FIELD_LISTING_ID, listingId);
                    listing.put(FIELD_PROPERTY_ID, propertyId);
                    listing.put(FIELD_DATE_LISTED, listedOn.toString());
                    listing.put(FIELD_FOR_SALE, true);
                    transaction.set(listingRef, listing);

                    DocumentReference priceRef = listingRef
                        .collection(FirestoreCollections.LISTING_PRICES)
                        .document(String.valueOf(priceId));
                    Map<String, Object> price = new HashMap<>();
                    price.put(FIELD_PRICE_ID, priceId);
                    price.put(FIELD_PRICE_DATE, listedOn.toString());
                    price.put(FIELD_PRICE, initialPrice);
                    transaction.set(priceRef, price);

                    return listingId;
                })
            );
        } catch (IllegalStateException exception) {
            throw new IllegalStateException("Failed to create listing", exception);
        }
    }

    public void addPrice(long listingId, LocalDate effectiveDate, long price) {
        Firestore db = FirestoreConfig.db();
        DocumentReference listingRef = db.collection(FirestoreCollections.LISTINGS)
            .document(String.valueOf(listingId));
        try {
            DocumentSnapshot listing = FirestoreOps.await(listingRef.get());
            if (!listing.exists()) {
                throw new IllegalStateException("Listing not found: " + listingId);
            }
            long priceId = FirestoreCounters.nextPriceId(db);
            DocumentReference priceRef = listingRef
                .collection(FirestoreCollections.LISTING_PRICES)
                .document(String.valueOf(priceId));
            Map<String, Object> data = new HashMap<>();
            data.put(FIELD_PRICE_ID, priceId);
            data.put(FIELD_PRICE_DATE, effectiveDate.toString());
            data.put(FIELD_PRICE, price);
            FirestoreOps.await(priceRef.set(data));
        } catch (IllegalStateException exception) {
            throw new IllegalStateException("Failed to add listing price", exception);
        }
    }

    public Optional<ListingDetail> getListing(long listingId) {
        Firestore db = FirestoreConfig.db();
        DocumentReference listingRef = db.collection(FirestoreCollections.LISTINGS)
            .document(String.valueOf(listingId));
        try {
            DocumentSnapshot listing = FirestoreOps.await(listingRef.get());
            if (!listing.exists()) {
                return Optional.empty();
            }
            ListingDetail detail = new ListingDetail();
            detail.setListingID(String.valueOf(listing.getLong(FIELD_LISTING_ID)));
            detail.setPropertyID(String.valueOf(listing.getLong(FIELD_PROPERTY_ID)));
            detail.setListedOn(listing.getString(FIELD_DATE_LISTED));
            detail.setPrices(loadPrices(listingRef));
            return Optional.of(detail);
        } catch (IllegalStateException exception) {
            throw new IllegalStateException("Failed to load listing " + listingId, exception);
        }
    }

    public List<ListingDetail> getListingsForProperty(long propertyId) {
        try {
            Query query = FirestoreConfig.db()
                .collection(FirestoreCollections.LISTINGS)
                .whereEqualTo(FIELD_PROPERTY_ID, propertyId)
                .orderBy(FIELD_LISTING_ID);
            List<ListingDetail> out = new ArrayList<>();
            for (QueryDocumentSnapshot snapshot : FirestoreOps.await(query.get()).getDocuments()) {
                Long listingId = snapshot.getLong(FIELD_LISTING_ID);
                if (listingId != null) {
                    getListing(listingId).ifPresent(out::add);
                }
            }
            return out;
        } catch (IllegalStateException exception) {
            throw new IllegalStateException("Failed to list listings for property " + propertyId, exception);
        }
    }

    private List<ListingPricePoint> loadPrices(DocumentReference listingRef) {
        CollectionReference prices = listingRef.collection(FirestoreCollections.LISTING_PRICES);
        List<PriceRow> rows = new ArrayList<>();
        for (QueryDocumentSnapshot snapshot : FirestoreOps.await(prices.get()).getDocuments()) {
            Long priceId = snapshot.getLong(FIELD_PRICE_ID);
            String priceDate = snapshot.getString(FIELD_PRICE_DATE);
            Long amount = snapshot.getLong(FIELD_PRICE);
            if (priceDate != null && amount != null) {
                rows.add(new PriceRow(
                    priceId == null ? 0L : priceId,
                    LocalDate.parse(priceDate),
                    amount
                ));
            }
        }
        rows.sort(Comparator.comparing(PriceRow::date).thenComparingLong(PriceRow::priceId));
        List<ListingPricePoint> points = new ArrayList<>(rows.size());
        for (PriceRow row : rows) {
            points.add(new ListingPricePoint(row.date().toString(), String.valueOf(row.price())));
        }
        return points;
    }

    private record PriceRow(long priceId, LocalDate date, long price) {
    }
}
