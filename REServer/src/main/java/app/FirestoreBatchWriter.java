package app;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteBatch;

import java.util.Map;

/** Commits Firestore writes in batches of up to 500 operations. */
final class FirestoreBatchWriter {

    private static final int FIRESTORE_BATCH_MAX = 500;

    private final Firestore db;
    private final int batchSize;
    private final String label;
    private WriteBatch batch;
    private int inBatch;
    private long committed;

    FirestoreBatchWriter(Firestore db, String label) {
        this(db, label, batchSizeFromEnv());
    }

    FirestoreBatchWriter(Firestore db, String label, int batchSize) {
        this.db = db;
        this.label = label;
        this.batchSize = Math.min(FIRESTORE_BATCH_MAX, Math.max(1, batchSize));
        this.batch = db.batch();
        this.inBatch = 0;
        this.committed = 0;
    }

    void set(DocumentReference ref, Map<String, Object> fields) {
        batch.set(ref, fields);
        inBatch++;
        if (inBatch >= batchSize) {
            flush();
        }
    }

    long flush() {
        if (inBatch == 0) {
            return committed;
        }
        FirestoreOps.await(batch.commit());
        committed += inBatch;
        inBatch = 0;
        batch = db.batch();
        return committed;
    }

    long committedCount() {
        return committed + inBatch;
    }

    void logProgressIfNeeded(long nextThreshold) {
        long total = committedCount();
        if (total >= nextThreshold) {
            System.out.println("  " + label + ": " + total + " documents written …");
        }
    }

    private static int batchSizeFromEnv() {
        String raw = System.getenv("LOADER_BATCH_SIZE");
        if (raw == null || raw.isBlank()) {
            return FIRESTORE_BATCH_MAX;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            return FIRESTORE_BATCH_MAX;
        }
    }
}
