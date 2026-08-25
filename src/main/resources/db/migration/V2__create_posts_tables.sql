CREATE TABLE posts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content         VARCHAR(5000) NOT NULL DEFAULT '',
    visibility      VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_posts_author ON posts (author_id);
CREATE INDEX idx_posts_created_at ON posts (created_at DESC);

CREATE TABLE post_images (
    post_id         UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    position        INTEGER NOT NULL,
    url             VARCHAR(500) NOT NULL,
    PRIMARY KEY (post_id, position)
);

CREATE TABLE post_comments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id         UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    author_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content         VARCHAR(2000) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_post_comments_post ON post_comments (post_id);

CREATE TABLE post_reactions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id         UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type            VARCHAR(20) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (post_id, user_id)
);

CREATE INDEX idx_post_reactions_post ON post_reactions (post_id);
