package property;

import app.DatabaseConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for property access and postcode search counters.
 * Requires Supabase credentials in the environment or {@code REServer/.env}.
 */
@EnabledIf("property.PropertyDAOMetricsTest#databaseConfigured")
class PropertyDAOMetricsTest {

    private static final long TEST_PROPERTY_ID = 9_999_999_000_001L;

    private final PropertyDAO dao = new PropertyDAO();

    public static boolean databaseConfigured() {
        try (var connection = DatabaseConfig.connect()) {
            return connection != null;
        } catch (Exception exception) {
            return false;
        }
    }

    @BeforeAll
    static void ensureTestProperty() {
        if (!databaseConfigured()) {
            return;
        }
        PropertyDAO setup = new PropertyDAO();
        setup.newProperty(new Property(String.valueOf(TEST_PROPERTY_ID), "2000", "1"));
    }

    @Test
    void incrementPropertyAccessCount_incrementsReadCount() {
        long before = dao.getPropertyAccessCount(String.valueOf(TEST_PROPERTY_ID)).orElse(0L);

        dao.incrementPropertyAccessCount(String.valueOf(TEST_PROPERTY_ID));
        dao.incrementPropertyAccessCount(String.valueOf(TEST_PROPERTY_ID));

        long after = dao.getPropertyAccessCount(String.valueOf(TEST_PROPERTY_ID)).orElseThrow();
        assertEquals(before + 2, after);
    }

    @Test
    void incrementPostCodeSearchCount_upsertsAndIncrements() {
        String postCode = "METRICS" + System.nanoTime();

        assertTrue(dao.getPostCodeSearchCount(postCode).isEmpty());

        dao.incrementPostCodeSearchCount(postCode);
        assertEquals(1L, dao.getPostCodeSearchCount(postCode).orElseThrow());

        dao.incrementPostCodeSearchCount(postCode);
        assertEquals(2L, dao.getPostCodeSearchCount(postCode).orElseThrow());
    }
}
