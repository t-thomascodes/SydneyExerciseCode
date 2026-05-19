package app;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Transaction;

public final class FirestoreCounters {

    private static final String FIELD_NEXT = "next";

    private FirestoreCounters() {
    }

    public static long nextListingId(Firestore db) {
        return FirestoreOps.await(
            db.runTransaction(transaction -> nextListingId(transaction, db))
        );
    }

    public static long nextPriceId(Firestore db) {
        return FirestoreOps.await(
            db.runTransaction(transaction -> nextPriceId(transaction, db))
        );
    }

    public static long nextListingId(Transaction transaction, Firestore db) {
        return nextId(transaction, db, FirestoreCollections.COUNTER_LISTINGS);
    }

    public static long nextPriceId(Transaction transaction, Firestore db) {
        return nextId(transaction, db, FirestoreCollections.COUNTER_PRICE_IDS);
    }

    private static long nextId(Transaction transaction, Firestore db, String counterName) {
        DocumentReference counter = db.collection(FirestoreCollections.COUNTERS).document(counterName);
        Long current = readNext(transaction, counter);
        long assigned = current == null ? 1L : current + 1L;
        transaction.set(counter, java.util.Map.of(FIELD_NEXT, assigned));
        return assigned;
    }

    private static Long readNext(Transaction transaction, DocumentReference counter) {
        DocumentSnapshot snapshot = FirestoreOps.await(transaction.get(counter));
        if (!snapshot.exists()) {
            return null;
        }
        Long value = snapshot.getLong(FIELD_NEXT);
        if (value != null) {
            return value;
        }
        Double asDouble = snapshot.getDouble(FIELD_NEXT);
        return asDouble == null ? null : asDouble.longValue();
    }
}
