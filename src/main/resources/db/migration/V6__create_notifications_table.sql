CREATE TABLE notifications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    actor_id        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type            VARCHAR(30) NOT NULL,
    post_id         UUID REFERENCES posts(id) ON DELETE CASCADE,
    comment_id      UUID REFERENCES post_comments(id) ON DELETE CASCADE,
    read            BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_recipient ON notifications (recipient_id, created_at DESC);
