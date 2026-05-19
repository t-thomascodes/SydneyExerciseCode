package notify;

import app.FirestoreCollections;
import app.FirestoreConfig;
import app.FirestoreOps;
import app.PostcodeUtil;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.QueryDocumentSnapshot;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Loads purchaser ↔ for-sale property matches using Firestore collections and in-memory
 * joins (equivalent to the original Postgres CTE + join query).
 */
public class NotifyDAO {

    public static final int NOTIFY_MAX_ROWS = 50_000;

    private static final String FIELD_PURCHASER_ID = "purchaserId";
    private static final String FIELD_EMAIL = "email";
    private static final String FIELD_FIRST_NAME = "firstName";
    private static final String FIELD_LAST_NAME = "lastName";
    private static final String FIELD_POST_CODE = "postCode";
    private static final String FIELD_PROPERTY_ID = "propertyId";
    private static final String FIELD_PURCHASE_PRICE = "purchasePrice";
    private static final String FIELD_FOR_SALE = "forSale";
    private static final String FIELD_ACCESS_COUNT = "accessCount";
    private static final String FIELD_SEARCH_COUNT = "searchCount";
    private static final String FIELD_LISTING_ID = "listingId";
    private static final String FIELD_PRICE_ID = "priceId";
    private static final String FIELD_PRICE_DATE = "priceDate";
    private static final String FIELD_PRICE = "price";

    private boolean lastFetchTruncated;

    public boolean wasLastFetchTruncated() {
        return lastFetchTruncated;
    }

    public List<NotificationMatch> fetchPurchaseNotifications() {
        return fetchPurchaseNotifications(NotifyFilters.NONE);
    }

    public List<NotificationMatch> fetchPurchaseNotifications(NotifyFilters filters) {
        lastFetchTruncated = false;
        NotifyFilters effective = filters == null ? NotifyFilters.NONE : filters;

        try {
            List<PurchaserRow> purchasers = loadPurchasers();
            List<InterestRow> interests = loadInterests();
            List<PropertyRow> forSaleProperties = loadForSaleProperties();
            Map<String, Long> searchCounts = loadPostCodeSearchCounts();
            Map<Long, Long> salePriceByProperty = buildSalePriceByProperty();

            Map<Long, List<InterestRow>> interestsByPurchaser = new HashMap<>();
            for (InterestRow interest : interests) {
                interestsByPurchaser
                    .computeIfAbsent(interest.purchaserId(), ignored -> new ArrayList<>())
                    .add(interest);
            }

            List<NotificationMatch> matches = new ArrayList<>();
            purchasers.sort(Comparator.comparingLong(PurchaserRow::purchaserId));

            for (PurchaserRow purchaser : purchasers) {
                for (InterestRow interest : interestsByPurchaser.getOrDefault(
                    purchaser.purchaserId(),
                    List.of()
                )) {
                    for (PropertyRow property : forSaleProperties) {
                        if (!PostcodeUtil.matchesTrimmed(property.postCode(), interest.postCode())) {
                            continue;
                        }
                        long accessCount = property.accessCount() == null ? 0L : property.accessCount();
                        long postcodeSearchCount = lookupSearchCount(searchCounts, property.postCode());
                        if (effective.minAccess().isPresent()
                            && accessCount < effective.minAccess().getAsLong()) {
                            continue;
                        }
                        if (effective.effectiveMinPostcodeSearches().isPresent()
                            && postcodeSearchCount < effective.effectiveMinPostcodeSearches().getAsLong()) {
                            continue;
                        }
                        long salePrice = salePriceByProperty.getOrDefault(
                            property.propertyId(),
                            property.purchasePrice()
                        );
                        matches.add(
                            new NotificationMatch(
                                purchaser.purchaserId(),
                                purchaser.email(),
                                purchaser.firstName(),
                                purchaser.lastName(),
                                property.propertyId(),
                                PostcodeUtil.trim(property.postCode()),
                                salePrice,
                                accessCount,
                                postcodeSearchCount
                            )
                        );
                    }
                }
            }

            matches.sort(
                Comparator.comparingLong(NotificationMatch::purchaserId)
                    .thenComparing(NotificationMatch::accessCount, Comparator.reverseOrder())
                    .thenComparingLong(NotificationMatch::propertyId)
            );

            if (matches.size() > NOTIFY_MAX_ROWS) {
                lastFetchTruncated = true;
                return List.copyOf(matches.subList(0, NOTIFY_MAX_ROWS));
            }
            return matches;
        } catch (IllegalStateException exception) {
            throw new IllegalStateException("Failed to load purchase notifications", exception);
        }
    }

    public NotifyDiagnostics fetchDiagnostics() {
        try {
            List<PurchaserRow> purchasers = loadPurchasers();
            List<InterestRow> interests = loadInterests();
            List<PropertyRow> forSaleProperties = loadForSaleProperties();

            long pairMatchCount = 0L;
            for (InterestRow interest : interests) {
                for (PropertyRow property : forSaleProperties) {
                    if (PostcodeUtil.matchesTrimmed(property.postCode(), interest.postCode())) {
                        pairMatchCount++;
                    }
                }
            }

            List<String> sampleInterest = distinctTrimmedPostcodes(
                interests.stream().map(InterestRow::postCode).toList()
            );
            List<String> sampleForSale = distinctTrimmedPostcodes(
                forSaleProperties.stream().map(PropertyRow::postCode).toList()
            );

            List<String> hints = buildHints(
                purchasers.size(),
                interests.size(),
                forSaleProperties.size(),
                pairMatchCount
            );

            return new NotifyDiagnostics(
                purchasers.size(),
                interests.size(),
                forSaleProperties.size(),
                pairMatchCount,
                sampleInterest,
                sampleForSale,
                hints
            );
        } catch (IllegalStateException exception) {
            throw new IllegalStateException("Failed to load /notify diagnostics", exception);
        }
    }

    private Map<Long, Long> buildSalePriceByProperty() {
        Map<Long, ListingWithPrices> listingsById = loadListingsWithPrices();
        Map<Long, List<ListingWithPrices>> byProperty = new HashMap<>();
        for (ListingWithPrices listing : listingsById.values()) {
            byProperty.computeIfAbsent(listing.propertyId(), ignored -> new ArrayList<>()).add(listing);
        }

        Map<Long, Long> salePriceByProperty = new HashMap<>();
        for (Map.Entry<Long, List<ListingWithPrices>> entry : byProperty.entrySet()) {
            Optional<ListingWithPrices> chosen = entry.getValue().stream()
                .filter(listing -> !listing.prices().isEmpty())
                .max(Comparator.comparingLong(ListingWithPrices::listingId));
            chosen.ifPresent(listing -> salePriceByProperty.put(entry.getKey(), latestPrice(listing.prices())));
        }
        return salePriceByProperty;
    }

    private static long latestPrice(List<PricePoint> prices) {
        return prices.stream()
            .max(Comparator.comparing(PricePoint::date).thenComparingLong(PricePoint::priceId))
            .map(PricePoint::price)
            .orElseThrow();
    }

    private Map<Long, ListingWithPrices> loadListingsWithPrices() {
        Map<Long, ListingWithPrices> listings = new HashMap<>();
        for (QueryDocumentSnapshot listingDoc : FirestoreOps.await(
            FirestoreConfig.db().collection(FirestoreCollections.LISTINGS).get()
        ).getDocuments()) {
            Long listingId = listingDoc.getLong(FIELD_LISTING_ID);
            Long propertyId = listingDoc.getLong(FIELD_PROPERTY_ID);
            if (listingId == null || propertyId == null) {
                continue;
            }
            List<PricePoint> prices = new ArrayList<>();
            for (QueryDocumentSnapshot priceDoc : FirestoreOps.await(
                listingDoc.getReference().collection(FirestoreCollections.LISTING_PRICES).get()
            ).getDocuments()) {
                Long priceId = priceDoc.getLong(FIELD_PRICE_ID);
                String priceDate = priceDoc.getString(FIELD_PRICE_DATE);
                Long price = priceDoc.getLong(FIELD_PRICE);
                if (priceDate != null && price != null) {
                    prices.add(
                        new PricePoint(
                            priceId == null ? 0L : priceId,
                            LocalDate.parse(priceDate),
                            price
                        )
                    );
                }
            }
            listings.put(listingId, new ListingWithPrices(listingId, propertyId, prices));
        }
        return listings;
    }

    private List<PurchaserRow> loadPurchasers() {
        List<PurchaserRow> rows = new ArrayList<>();
        for (QueryDocumentSnapshot snapshot : FirestoreOps.await(
            FirestoreConfig.db().collection(FirestoreCollections.PURCHASERS).get()
        ).getDocuments()) {
            Long purchaserId = snapshot.getLong(FIELD_PURCHASER_ID);
            if (purchaserId == null) {
                purchaserId = parseLongOrNull(snapshot.getId());
            }
            if (purchaserId == null) {
                continue;
            }
            rows.add(
                new PurchaserRow(
                    purchaserId,
                    nullToEmpty(snapshot.getString(FIELD_EMAIL)),
                    nullToEmpty(snapshot.getString(FIELD_FIRST_NAME)),
                    nullToEmpty(snapshot.getString(FIELD_LAST_NAME))
                )
            );
        }
        return rows;
    }

    private List<InterestRow> loadInterests() {
        List<InterestRow> rows = new ArrayList<>();
        for (QueryDocumentSnapshot snapshot : FirestoreOps.await(
            FirestoreConfig.db().collection(FirestoreCollections.PURCHASER_INTERESTS).get()
        ).getDocuments()) {
            Long purchaserId = snapshot.getLong(FIELD_PURCHASER_ID);
            String postCode = snapshot.getString(FIELD_POST_CODE);
            if (purchaserId == null || postCode == null) {
                continue;
            }
            rows.add(new InterestRow(purchaserId, postCode));
        }
        return rows;
    }

    private List<PropertyRow> loadForSaleProperties() {
        List<PropertyRow> rows = new ArrayList<>();
        for (QueryDocumentSnapshot snapshot : FirestoreOps.await(
            FirestoreConfig.db()
                .collection(FirestoreCollections.PROPERTIES)
                .whereEqualTo(FIELD_FOR_SALE, true)
                .get()
        ).getDocuments()) {
            Long propertyId = snapshot.getLong(FIELD_PROPERTY_ID);
            if (propertyId == null) {
                propertyId = parseLongOrNull(snapshot.getId());
            }
            if (propertyId == null) {
                continue;
            }
            Long purchasePrice = snapshot.getLong(FIELD_PURCHASE_PRICE);
            rows.add(
                new PropertyRow(
                    propertyId,
                    snapshot.getString(FIELD_POST_CODE),
                    purchasePrice == null ? 0L : purchasePrice,
                    snapshot.getLong(FIELD_ACCESS_COUNT)
                )
            );
        }
        return rows;
    }

    private Map<String, Long> loadPostCodeSearchCounts() {
        Map<String, Long> counts = new HashMap<>();
        for (QueryDocumentSnapshot snapshot : FirestoreOps.await(
            FirestoreConfig.db().collection(FirestoreCollections.POST_CODE_SEARCH_STATS).get()
        ).getDocuments()) {
            String postCode = snapshot.getString(FIELD_POST_CODE);
            if (postCode == null) {
                postCode = snapshot.getId();
            }
            Long searchCount = snapshot.getLong(FIELD_SEARCH_COUNT);
            long count = searchCount == null ? 0L : searchCount;
            counts.put(postCode, count);
            counts.put(PostcodeUtil.trim(postCode), count);
        }
        return counts;
    }

    private static long lookupSearchCount(Map<String, Long> counts, String postCode) {
        if (postCode == null) {
            return 0L;
        }
        Long exact = counts.get(postCode);
        if (exact != null) {
            return exact;
        }
        return counts.getOrDefault(PostcodeUtil.trim(postCode), 0L);
    }

    private static List<String> distinctTrimmedPostcodes(List<String> postcodes) {
        Set<String> ordered = new LinkedHashSet<>();
        postcodes.stream()
            .map(PostcodeUtil::trim)
            .filter(pc -> !pc.isEmpty())
            .sorted()
            .forEach(ordered::add);
        return ordered.stream().limit(20).toList();
    }

    private static List<String> buildHints(
        long purchaserCount,
        long interestCount,
        long forSaleCount,
        long pairMatchCount
    ) {
        List<String> hints = new ArrayList<>();
        if (purchaserCount == 0) {
            hints.add("Collection purchasers has no documents.");
        }
        if (interestCount == 0) {
            hints.add("Collection purchaser_interests has no documents — nothing to match to postcodes.");
        }
        if (forSaleCount == 0) {
            hints.add("No property documents have forSale = true — /notify only includes for-sale properties.");
        }
        if (interestCount > 0 && forSaleCount > 0 && pairMatchCount == 0) {
            hints.add(
                "At least one interest and one for-sale property exist, but trimmed postcodes never match."
                    + " Compare sampleInterestPostcodesTrimmed vs sampleForSalePropertyPostcodesTrimmed"
                    + " (format, spacing, or different regions)."
            );
        }
        if (pairMatchCount > 0) {
            hints.add(
                "Postcode + for_sale join finds "
                    + pairMatchCount
                    + " (interest, property) pair(s); /notify should surface these with purchaser details."
            );
        }
        if (hints.isEmpty()) {
            hints.add("No issues detected from counts alone.");
        }
        return List.copyOf(hints);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static Long parseLongOrNull(String value) {
        try {
            return value == null ? null : Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private record PurchaserRow(long purchaserId, String email, String firstName, String lastName) {
    }

    private record InterestRow(long purchaserId, String postCode) {
    }

    private record PropertyRow(long propertyId, String postCode, long purchasePrice, Long accessCount) {
    }

    private record ListingWithPrices(long listingId, long propertyId, List<PricePoint> prices) {
    }

    private record PricePoint(long priceId, LocalDate date, long price) {
    }
}
