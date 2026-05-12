-- Replace any earlier test table so columns match nsw_property_data.csv.
drop table if exists property;

create table property (
    property_id bigint primary key,
    download_date date,
    council_name text,
    purchase_price bigint,
    address text,
    post_code text,
    property_type text,
    strata_lot_number text,
    property_name text,
    area text,
    area_type text,
    contract_date date,
    settlement_date date,
    zoning text,
    nature_of_property text,
    primary_purpose text,
    legal_description text,
    for_sale boolean not null default false
);

create index property_post_code_idx on property (post_code);
create index property_purchase_price_idx on property (purchase_price);
