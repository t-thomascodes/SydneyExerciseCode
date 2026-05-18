-- Seed person rows and link purchaser.person_id (person + purchaser schema).
-- Run once in Supabase SQL editor.
--
-- Prerequisites:
--   - purchaser rows exist with purchaser_id 1 .. 10000 (or at least N rows you seed)
--   - person table empty (or truncate first)
--
-- Clean slate (optional):
--   truncate person restart identity cascade;
--   update purchaser set person_id = null;

with names as (
    select
        gs,
        (array[
            'James','Mary','John','Patricia','Robert','Jennifer','Michael','Linda',
            'William','Elizabeth','David','Barbara','Richard','Susan','Joseph','Jessica',
            'Thomas','Sarah','Charles','Karen','Daniel','Nancy','Matthew','Lisa',
            'Anthony','Margaret','Mark','Betty','Donald','Sandra'
        ])[1 + floor(random() * 30)::int] as first_name,
        (array[
            'Smith','Johnson','Williams','Brown','Jones','Garcia','Miller','Davis',
            'Rodriguez','Martinez','Hernandez','Lopez','Gonzalez','Wilson','Anderson',
            'Thomas','Taylor','Moore','Jackson','Martin','Lee','Perez','Thompson',
            'White','Harris','Sanchez','Clark','Ramirez','Lewis','Robinson'
        ])[1 + floor(random() * 30)::int] as last_name
    from generate_series(1, 10000) as gs
),
enriched as (
    select
        gs,
        first_name,
        last_name,
        lower(first_name || '.' || last_name || gs::text || '@gmail.com') as email,
        (400000000 + floor(random() * 99999999)::int) as phone_num
    from names
),
inserted_person as (
    insert into person (first_name, last_name, email, phone_num)
    select first_name, last_name, email, phone_num
    from enriched
    order by gs
    returning person_id
),
person_by_seq as (
    select person_id, row_number() over (order by person_id) as seq
    from inserted_person
)
update purchaser p
set person_id = pb.person_id
from person_by_seq pb
where p.purchaser_id = pb.seq
  and p.person_id is distinct from pb.person_id;

-- If purchaser was emptied too, run this AFTER the block above (uses same person rows):
-- insert into purchaser (purchaser_id, person_id)
-- select row_number() over (order by person_id), person_id from person;
-- select setval(pg_get_serial_sequence('purchaser', 'purchaser_id'), (select max(purchaser_id) from purchaser));
--
-- purchaser_interest still needs postcodes — re-seed if that was cleared too.
--
-- Verify:
-- select count(*) from person;
-- select count(*) from purchaser where person_id is not null;
-- select p.purchaser_id, per.first_name, per.email
-- from purchaser p join person per on per.person_id = p.person_id limit 5;
