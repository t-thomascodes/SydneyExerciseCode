-- Property view counts and postcode search popularity (no FKs to property).

alter table property
    add column if not exists access_count bigint not null default 0;

create table if not exists post_code_search_stat (
    post_code text primary key,
    search_count bigint not null default 0
);
