package app;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import property.Property;
import property.PropertyDAO;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads sample documents into Firestore for local demos and /notify smoke tests.
 * Run from the REServer directory after configuring {@code .env}:
 * {@code mvn -q exec:java -Dexec.mainClass=app.FirestoreSeed}
 */
public final class FirestoreSeed {

    private FirestoreSeed() {
    }

    public static void main(String[] args) {
        Firestore db = FirestoreConfig.db();
        PropertyDAO properties = new PropertyDAO();

        seedProperty(properties, "1001", "2000", "750000", true);
        seedProperty(properties, "1002", "2000", "820000", true);
        seedProperty(properties, "1003", "2010", "910000", false);

        seedPurchaser(db, 1L, "alex@example.com", "Alex", "Buyer");
        seedInterest(db, 1L, "2000");

        System.out.println("Firestore seed complete (properties 1001-1003, purchaser 1, interest 2000).");
    }

    private static void seedProperty(
        PropertyDAO dao,
        String id,
        String postCode,
        String price,
        boolean forSale
    ) {
        Property property = new Property(id, postCode, price);
        property.forSale = forSale;
        if (!dao.newProperty(property)) {
            System.out.println("Property " + id + " already exists — skipped insert.");
        }
    }

    private static void seedPurchaser(
        Firestore db,
        long purchaserId,
        String email,
        String firstName,
        String lastName
    ) {
        DocumentReference ref = db.collection(FirestoreCollections.PURCHASERS)
            .document(String.valueOf(purchaserId));
        Map<String, Object> data = new HashMap<>();
        data.put("purchaserId", purchaserId);
        data.put("email", email);
        data.put("firstName", firstName);
        data.put("lastName", lastName);
        FirestoreOps.await(ref.set(data));
    }

    private static void seedInterest(Firestore db, long purchaserId, String postCode) {
        String docId = purchaserId + "_" + postCode.replaceAll("\\s+", "");
        DocumentReference ref = db.collection(FirestoreCollections.PURCHASER_INTERESTS)
            .document(docId);
        FirestoreOps.await(
            ref.set(Map.of("purchaserId", purchaserId, "postCode", postCode))
        );
    }
}
