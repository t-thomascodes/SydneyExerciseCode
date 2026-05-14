-- Run this against your Supabase/Postgres database once (or use an equivalent migration).
-- Matches course ERD: one row per sale period in listing; price history in listing_price.

create table if not exists listing (
    listing_id bigserial primary key,
    property_id bigint not null references property (property_id) on delete restrict,
    datelisted date not null,
    forsale boolean not null default true
);

create table if not exists listing_price (
    price_id bigserial primary key,
    listing_id bigint not null references listing (listing_id) on delete cascade,
    price_date date not null,
    price bigint not null
);

create index if not exists listing_price_listing_id_price_date_idx
    on listing_price (listing_id, price_date, price_id);
