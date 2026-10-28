alter table posts
    add column version bigint not null default 0;
