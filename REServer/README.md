# REServer

Real-estate HTTP API (Javalin) backed by **Google Cloud Firestore** (document database).

## Setup

1. Create a Firebase project and enable **Firestore** (production or test mode for development).
2. Download a **service account** JSON key (Project settings → Service accounts → Generate new private key).
3. Copy `.env.example` to `.env` and set Firestore variables (and Supabase variables if you will run the migrator).
4. Build and run from this directory:

   ```bash
   mvn package
   java -cp target/REServer-1.0-SNAPSHOT-jar-with-dependencies.jar app.REServer
   ```

   Swagger UI: http://localhost:7070/swagger

## Copy all data from Supabase → Firestore (recommended)

If your course data is already in **Supabase/Postgres**, run the one-shot migrator (reads SQL tables, writes Firestore collections):

1. In `.env`, set **both**:
   - `GOOGLE_APPLICATION_CREDENTIALS` and `FIRESTORE_PROJECT_ID` (Firebase)
   - `SUPABASE_DB_URL`, `SUPABASE_DB_USER`, `SUPABASE_DB_PASSWORD` (from Supabase → Project Settings → Database → connection string / password)

2. Dry run (first 1000 rows per table):

   ```bash
   MIGRATE_ROW_LIMIT=1000 mvn -q exec:java -Dexec.mainClass=app.FirestoreSupabaseMigrator
   ```

   You must pass `-Dexec.mainClass=...` every time. If you see `Firestore seed complete`, you ran the wrong class.

3. Full migration (can take hours if you have millions of properties):

   ```bash
   mvn -q exec:java -Dexec.mainClass=app.FirestoreSupabaseMigrator
   ```

4. Migrate only some tables (comma-separated):

   ```bash
   MIGRATE_TABLES=property,listing,listing_price,purchaser,purchaser_interest,post_code_search_stat,counters \
     mvn -q exec:java -Dexec.mainClass=app.FirestoreSupabaseMigrator
   ```

Tables copied: `property`, `listing`, `listing_price`, `purchaser` (+ `person` when present), `purchaser_interest`, `post_code_search_stat`, plus `counters` for new listing/price ids.

## Other loaders

| Tool | Use when |
|------|----------|
| `app.FirestoreSupabaseMigrator` | Data is in Supabase (best for full app parity) |
| `app.FirestorePropertyCsvLoader` | Only the NSW CSV, no Supabase |
| `app.FirestoreSeed` | Tiny demo set for `/notify` smoke test |

## Data model

See [firestore/DATA_MODEL.md](firestore/DATA_MODEL.md).

## Tests

```bash
mvn test
```

Integration tests (`PropertyDAOMetricsTest`) run only when Firestore credentials are configured.
