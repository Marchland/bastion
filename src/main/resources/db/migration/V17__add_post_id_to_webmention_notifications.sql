alter table webmention_notifications
    add column post_id uuid references posts(id);

create index idx_webmention_notifications_post_id
    on webmention_notifications (post_id);
