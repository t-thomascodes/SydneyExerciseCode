package notify;

import java.util.OptionalLong;

/**
 * Optional query filters for {@code GET /notify} (popularity thresholds).
 */
public record NotifyFilters(OptionalLong minAccess, OptionalLong minPostcodeSearches, boolean hotPostcodesOnly) {

    public static final NotifyFilters NONE = new NotifyFilters(OptionalLong.empty(), OptionalLong.empty(), false);

    public static NotifyFilters fromQueryParams(String minAccess, String minPostcodeSearches, String hotPostcodesOnly) {
        OptionalLong minAccessValue = parseOptionalNonNegative(minAccess, "minAccess");
        OptionalLong minPostcodeValue = parseOptionalNonNegative(minPostcodeSearches, "minPostcodeSearches");
        boolean hotOnly = hotPostcodesOnly != null
            && ("true".equalsIgnoreCase(hotPostcodesOnly.trim()) || "1".equals(hotPostcodesOnly.trim()));
        return new NotifyFilters(minAccessValue, minPostcodeValue, hotOnly);
    }

    /**
     * Postcode search threshold applied in SQL: explicit param, else {@code 1} when {@code hotPostcodesOnly}.
     */
    public OptionalLong effectiveMinPostcodeSearches() {
        if (minPostcodeSearches.isPresent()) {
            return minPostcodeSearches;
        }
        if (hotPostcodesOnly) {
            return OptionalLong.of(1L);
        }
        return OptionalLong.empty();
    }

    private static OptionalLong parseOptionalNonNegative(String raw, String paramName) {
        if (raw == null || raw.isBlank()) {
            return OptionalLong.empty();
        }
        try {
            long value = Long.parseLong(raw.trim());
            if (value < 0) {
                throw new IllegalArgumentException(paramName + " must be non-negative");
            }
            return OptionalLong.of(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(paramName + " must be a number", exception);
        }
    }
}
