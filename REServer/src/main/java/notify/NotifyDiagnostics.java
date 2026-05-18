package notify;

import java.util.List;

/**
 * JSON payload for {@code GET /notify/diag}: why {@code /notify} may be empty and whether data lines up.
 */
public record NotifyDiagnostics(
    long purchaserCount,
    long purchaserInterestCount,
    long forSalePropertyCount,
    /** Rows that would match on postcode + for_sale (same trim rule as /notify), without purchaser name join. */
    long interestToForSalePropertyPairCount,
    List<String> sampleInterestPostcodesTrimmed,
    List<String> sampleForSalePropertyPostcodesTrimmed,
    List<String> hints
) {
    public NotifyDiagnostics {
        sampleInterestPostcodesTrimmed =
            sampleInterestPostcodesTrimmed == null ? List.of() : List.copyOf(sampleInterestPostcodesTrimmed);
        sampleForSalePropertyPostcodesTrimmed =
            sampleForSalePropertyPostcodesTrimmed == null
                ? List.of()
                : List.copyOf(sampleForSalePropertyPostcodesTrimmed);
        hints = hints == null ? List.of() : List.copyOf(hints);
    }
}
