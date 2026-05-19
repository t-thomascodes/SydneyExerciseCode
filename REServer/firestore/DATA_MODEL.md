# Firestore data model

Maps the original Postgres / Supabase schema to Firestore collections.

## Collections

| Collection | Document ID | Fields | Replaces (SQL) |
|------------|-------------|--------|----------------|
| `properties` | `propertyId` (string) | `propertyId`, `postCode`, `purchasePrice`, `forSale`, `accessCount` | `property` |
| `listings` | `listingId` (string) | `listingId`, `propertyId`, `dateListed`, `forSale` | `listing` |
| `listings/{id}/prices` | `priceId` (string) | `priceId`, `priceDate`, `price` | `listing_price` |
| `purchasers` | `purchaserId` (string) | `purchaserId`, `email`, `firstName`, `lastName` | `purchaser` + `person` (denormalized) |
| `purchaser_interests` | `{purchaserId}_{postCode}` | `purchaserId`, `postCode` | `purchaser_interest` |
| `post_code_search_stats` | postcode string | `postCode`, `searchCount` | `post_code_search_stat` |
| `counters` | `listings`, `price_ids` | `next` | sequences for `listing_id`, `price_id` |

## Indexes

Create composite indexes in the Firebase console when queries fail with a link in the error:

- `properties`: `postCode` + `propertyId` (postcode search)
- `properties`: `purchasePrice` + `propertyId` (optional; price-range queries sort in memory)
- `listings`: `propertyId` + `listingId` (listings for property)

## Loading data

**Recommended:** run `app.FirestoreSupabaseMigrator` from the REServer directory (see root `README.md`). It reads your Supabase tables and writes all collections above.

Manual steps if you export CSVs instead:

1. **Properties** — one document per row; document ID = `property_id`.
2. **Listings / prices** — listing document + subcollection rows per `listing_price`.
3. **Purchasers** — copy email and names from `person` when `person_id` is set.
4. **Interests** — one document per `(purchaser_id, post_code)` pair.

Run `app.FirestoreSeed` for a minimal smoke-test dataset only.
