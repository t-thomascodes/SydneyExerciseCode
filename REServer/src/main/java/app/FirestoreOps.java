package app;

import com.google.api.core.ApiFuture;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class FirestoreOps {

    private static final long TIMEOUT_SECONDS = 60;

    private FirestoreOps() {
    }

    public static <T> T await(ApiFuture<T> future) {
        try {
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Firestore operation interrupted", exception);
        } catch (ExecutionException | TimeoutException exception) {
            Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
            throw new IllegalStateException("Firestore operation failed", cause);
        }
    }
}
