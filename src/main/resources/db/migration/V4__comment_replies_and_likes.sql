ALTER TABLE post_comments
    ADD COLUMN parent_comment_id UUID REFERENCES post_comments(id) ON DELETE CASCADE;

CREATE INDEX idx_post_comments_parent ON post_comments (parent_comment_id);

CREATE TABLE comment_likes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    comment_id      UUID NOT NULL REFERENCES post_comments(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (comment_id, user_id)
);

CREATE INDEX idx_comment_likes_comment ON comment_likes (comment_id);
