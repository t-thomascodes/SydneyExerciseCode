package app;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FirestoreConfig {

    private static volatile Firestore firestore;

    private FirestoreConfig() {
    }

    public static Firestore db() {
        if (firestore == null) {
            synchronized (FirestoreConfig.class) {
                if (firestore == null) {
                    firestore = initialize();
                }
            }
        }
        return firestore;
    }

    private static Firestore initialize() {
        String credentialsPath = requiredEnv("GOOGLE_APPLICATION_CREDENTIALS");
        Path credentialsFile = Path.of(credentialsPath);
        if (!Files.isRegularFile(credentialsFile)) {
            throw new IllegalStateException(
                "Firebase credentials file not found: " + credentialsFile.toAbsolutePath()
            );
        }

        try (FileInputStream stream = new FileInputStream(credentialsFile.toFile())) {
            FirebaseOptions.Builder builder = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(stream));

            String projectId = Env.get("FIRESTORE_PROJECT_ID");
            if (projectId != null && !projectId.isBlank()) {
                builder.setProjectId(projectId.trim());
            }

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(builder.build());
            }
            return FirestoreClient.getFirestore();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to initialize Firestore", exception);
        }
    }

    private static String requiredEnv(String name) {
        String value = Env.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "Missing required setting: " + name + ". Set it in the environment or in REServer/.env"
            );
        }
        return value.trim();
    }

    /** True when credentials path is set and the file exists (used by integration tests). */
    public static boolean isConfigured() {
        String credentialsPath = Env.get("GOOGLE_APPLICATION_CREDENTIALS");
        if (credentialsPath == null || credentialsPath.isBlank()) {
            return false;
        }
        try {
            db();
            return true;
        } catch (Exception exception) {
            return false;
        }
    }
}
