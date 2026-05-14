package notify;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link NotifyController#isKnownFormat(String)} used by {@code GET /notify} for the
 * {@code format} query parameter. Unknown values must yield 400 before touching the database.
 */
class NotifyControllerTest {

    /** Default and explicit {@code text} / {@code html} (any case, trimmed) are accepted. */
    @Test
    void isKnownFormat_acceptsNullBlankTextAndHtml() {
        assertTrue(NotifyController.isKnownFormat(null));
        assertTrue(NotifyController.isKnownFormat(""));
        assertTrue(NotifyController.isKnownFormat("   "));
        assertTrue(NotifyController.isKnownFormat("text"));
        assertTrue(NotifyController.isKnownFormat("HTML"));
        assertTrue(NotifyController.isKnownFormat(" html "));
    }

    /** Values that are not {@code text} or {@code html} are rejected so the handler can return 400. */
    @Test
    void isKnownFormat_rejectsUnknown() {
        assertFalse(NotifyController.isKnownFormat("json"));
        assertFalse(NotifyController.isKnownFormat("txt"));
    }
}
