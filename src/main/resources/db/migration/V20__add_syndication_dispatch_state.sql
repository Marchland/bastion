alter table post_syndications
    add column pending_action      varchar(16),
    add column pending_since       timestamptz,
    add column pending_token       uuid,
    add column last_synced_updated text;

create index idx_post_syndications_pending_since
    on post_syndications (pending_since)
    where pending_action is not null;
