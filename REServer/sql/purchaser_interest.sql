-- Run once against the same Postgres/Supabase database as property + listing / listing_price.
-- Supports postcode-based purchaser interests for /notify.

create table if not exists purchaser (
    purchaser_id bigserial primary key,
    first_name text not null,
    last_name text not null,
    email text not null,
    phone text
);

create table if not exists purchaser_interest (
    purchaser_id bigint not null references purchaser (purchaser_id) on delete cascade,
    post_code text not null,
    primary key (purchaser_id, post_code)
);

-- Speed joins from interest → property (large property tables).
create index if not exists purchaser_interest_post_code_idx
    on purchaser_interest (post_code);

create index if not exists property_post_code_for_sale_idx
    on property (post_code, for_sale);
