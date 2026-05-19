package property;

import app.FirestoreCollections;
import app.FirestoreConfig;
import app.FirestoreOps;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.SetOptions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PropertyDAO {

    static final int LIST_LIMIT = 500;

    private static final String FIELD_PROPERTY_ID = "propertyId";
    private static final String FIELD_POST_CODE = "postCode";
    private static final String FIELD_PURCHASE_PRICE = "purchasePrice";
    private static final String FIELD_FOR_SALE = "forSale";
    private static final String FIELD_ACCESS_COUNT = "accessCount";

    private boolean lastResultCapped;

    public PropertyDAO() {
    }

    public boolean wasLastResultCapped() {
        return lastResultCapped;
    }

    public boolean newProperty(Property property) {
        long propertyId = Long.parseLong(property.propertyID);
        DocumentReference ref = FirestoreConfig.db()
            .collection(FirestoreCollections.PROPERTIES)
            .document(property.propertyID);

        Map<String, Object> data = new HashMap<>();
        data.put(FIELD_PROPERTY_ID, propertyId);
        data.put(FIELD_POST_CODE, property.postcode);
        data.put(FIELD_PURCHASE_PRICE, Long.parseLong(property.propertyPrice));
        data.put(FIELD_FOR_SALE, property.forSale);
        data.put(FIELD_ACCESS_COUNT, 0L);

        try {
            FirestoreOps.await(ref.create(data));
            return true;
        } catch (IllegalStateException exception) {
            if (isAlreadyExists(exception)) {
                return false;
            }
            throw new IllegalStateException("Failed to insert property", exception);
        }
    }

    public Optional<Property> getPropertyById(String propertyID) {
        try {
            DocumentSnapshot snapshot = FirestoreOps.await(
                FirestoreConfig.db()
                    .collection(FirestoreCollections.PROPERTIES)
                    .document(propertyID)
                    .get()
            );
            if (!snapshot.exists()) {
                return Optional.empty();
            }
            return Optional.of(mapSnapshot(snapshot));
        } catch (IllegalStateException exception) {
            throw new IllegalStateException("Failed to load property " + propertyID, exception);
        }
    }

    public void incrementPropertyAccessCount(String propertyID) {
        DocumentReference ref = FirestoreConfig.db()
            .collection(FirestoreCollections.PROPERTIES)
            .document(propertyID);
        try {
            DocumentSnapshot snapshot = FirestoreOps.await(ref.get());
            if (!snapshot.exists()) {
                return;
            }
            FirestoreOps.await(ref.update(FIELD_ACCESS_COUNT, FieldValue.increment(1)));
        } catch (IllegalStateException exception) {
            throw new IllegalStateException(
                "Failed to increment access count for property " + propertyID,
                exception
            );
        }
    }

    public Optional<Long> getPropertyAccessCount(String propertyID) {
        try {
            DocumentSnapshot snapshot = FirestoreOps.await(
                FirestoreConfig.db()
                    .collection(FirestoreCollections.PROPERTIES)
                    .document(propertyID)
                    .get()
            );
            if (!snapshot.exists()) {
                return Optional.empty();
            }
            Long count = snapshot.getLong(FIELD_ACCESS_COUNT);
            return Optional.of(count == null ? 0L : count);
        } catch (IllegalStateException exception) {
            throw new IllegalStateException(
                "Failed to read access count for property " + propertyID,
                exception
            );
        }
    }

    public void incrementPostCodeSearchCount(String postCode) {
        DocumentReference ref = FirestoreConfig.db()
            .collection(FirestoreCollections.POST_CODE_SEARCH_STATS)
            .document(postCode);
        try {
            FirestoreOps.await(
                ref.set(
                    Map.of("postCode", postCode, "searchCount", FieldValue.increment(1)),
                    SetOptions.merge()
                )
            );
        } catch (IllegalStateException exception) {
            throw new IllegalStateException(
                "Failed to increment search count for postcode " + postCode,
                exception
            );
        }
    }

    public Optional<Long> getPostCodeSearchCount(String postCode) {
        try {
            DocumentSnapshot snapshot = FirestoreOps.await(
                FirestoreConfig.db()
                    .collection(FirestoreCollections.POST_CODE_SEARCH_STATS)
                    .document(postCode)
                    .get()
            );
            if (!snapshot.exists()) {
                return Optional.empty();
            }
            Long count = snapshot.getLong("searchCount");
            return Optional.of(count == null ? 0L : count);
        } catch (IllegalStateException exception) {
            throw new IllegalStateException(
                "Failed to read search count for postcode " + postCode,
                exception
            );
        }
    }

    public List<Property> getPropertiesByPostCode(String postCode) {
        try {
            Query query = FirestoreConfig.db()
                .collection(FirestoreCollections.PROPERTIES)
                .whereEqualTo(FIELD_POST_CODE, postCode)
                .orderBy(FIELD_PROPERTY_ID)
                .limit(LIST_LIMIT + 1);
            return readCappedQuery(query);
        } catch (IllegalStateException exception) {
            throw new IllegalStateException("Failed to load properties for postcode " + postCode, exception);
        }
    }

    public List<String> getAllPropertyPrices() {
        lastResultCapped = false;
        List<Property> properties = getAllProperties();
        List<String> prices = new ArrayList<>(properties.size());
        for (Property property : properties) {
            prices.add(property.propertyPrice);
        }
        return prices;
    }

    public List<Property> getAllProperties() {
        try {
            Query query = FirestoreConfig.db()
                .collection(FirestoreCollections.PROPERTIES)
                .orderBy(FIELD_PROPERTY_ID)
                .limit(LIST_LIMIT + 1);
            return readCappedQuery(query);
        } catch (IllegalStateException exception) {
            throw new IllegalStateException("Failed to load properties", exception);
        }
    }

    public List<Property> getPropertiesByPriceRange(long minPrice, long maxPrice) {
        try {
            Query query = FirestoreConfig.db()
                .collection(FirestoreCollections.PROPERTIES)
                .whereGreaterThanOrEqualTo(FIELD_PURCHASE_PRICE, minPrice)
                .whereLessThanOrEqualTo(FIELD_PURCHASE_PRICE, maxPrice);
            List<Property> properties = new ArrayList<>();
            for (QueryDocumentSnapshot snapshot : FirestoreOps.await(query.get()).getDocuments()) {
                properties.add(mapSnapshot(snapshot));
            }
            properties.sort(Comparator.comparingLong(p -> Long.parseLong(p.propertyID)));
            return capList(properties);
        } catch (IllegalStateException exception) {
            throw new IllegalStateException("Failed to load properties by price range", exception);
        }
    }

    private List<Property> readCappedQuery(Query query) {
        List<Property> properties = new ArrayList<>();
        for (QueryDocumentSnapshot snapshot : FirestoreOps.await(query.get()).getDocuments()) {
            properties.add(mapSnapshot(snapshot));
        }
        return capList(properties);
    }

    private List<Property> capList(List<Property> properties) {
        lastResultCapped = false;
        if (properties.size() <= LIST_LIMIT) {
            return properties;
        }
        lastResultCapped = true;
        return List.copyOf(properties.subList(0, LIST_LIMIT));
    }

    private Property mapSnapshot(DocumentSnapshot snapshot) {
        Property property = new Property();
        Long propertyId = snapshot.getLong(FIELD_PROPERTY_ID);
        property.propertyID = propertyId == null
            ? snapshot.getId()
            : String.valueOf(propertyId);
        property.postcode = snapshot.getString(FIELD_POST_CODE);
        Long purchasePrice = snapshot.getLong(FIELD_PURCHASE_PRICE);
        property.propertyPrice = purchasePrice == null ? "0" : String.valueOf(purchasePrice);
        Boolean forSale = snapshot.getBoolean(FIELD_FOR_SALE);
        property.forSale = forSale != null && forSale;
        return property;
    }

    private static boolean isAlreadyExists(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null
                && (message.contains("ALREADY_EXISTS") || message.contains("Already exists"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
