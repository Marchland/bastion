alter table post_syndications
    add column source_url text,
    add column desired boolean not null default true;
