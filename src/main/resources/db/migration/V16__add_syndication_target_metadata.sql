alter table post_syndications
    add column target_name text,
    add column sort_order integer not null default 0;

update post_syndications
set target_name = target_uid
where target_name is null;

alter table post_syndications
    alter column target_name set not null;
