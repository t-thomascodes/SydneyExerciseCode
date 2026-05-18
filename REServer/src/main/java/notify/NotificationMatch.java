package notify;

/**
 * One row in the purchaser notification report: a purchaser matched to a for-sale property
 * in a postcode they follow, with resolved price and popularity metrics.
 */
public record NotificationMatch(
    long purchaserId,
    String email,
    String firstName,
    String lastName,
    long propertyId,
    String postCode,
    long salePrice,
    long accessCount,
    long postcodeSearchCount
) {
}
