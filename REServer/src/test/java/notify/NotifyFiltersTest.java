package notify;

import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotifyFiltersTest {

    @Test
    void none_hasNoThresholds() {
        assertTrue(NotifyFilters.NONE.minAccess().isEmpty());
        assertTrue(NotifyFilters.NONE.effectiveMinPostcodeSearches().isEmpty());
    }

    @Test
    void fromQueryParams_parsesThresholds() {
        NotifyFilters filters = NotifyFilters.fromQueryParams("5", "10", null);
        assertEquals(OptionalLong.of(5), filters.minAccess());
        assertEquals(OptionalLong.of(10), filters.effectiveMinPostcodeSearches());
    }

    @Test
    void hotPostcodesOnly_defaultsMinPostcodeSearchesToOne() {
        NotifyFilters filters = NotifyFilters.fromQueryParams(null, null, "true");
        assertTrue(filters.hotPostcodesOnly());
        assertEquals(OptionalLong.of(1), filters.effectiveMinPostcodeSearches());
    }

    @Test
    void fromQueryParams_rejectsInvalidNumbers() {
        assertThrows(IllegalArgumentException.class, () -> NotifyFilters.fromQueryParams("x", null, null));
    }

    @Test
    void fromQueryParams_rejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> NotifyFilters.fromQueryParams("-1", null, null));
    }
}
