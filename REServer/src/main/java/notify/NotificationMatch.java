package notify;

/**
 * One row in the purchaser notification report: a purchaser matched to a for-sale property
 * in a postcode they follow, with the resolved display price.
 */
public record NotificationMatch(
    long purchaserId,
    String email,
    String firstName,
    String lastName,
    long propertyId,
    long salePrice
) {
}
