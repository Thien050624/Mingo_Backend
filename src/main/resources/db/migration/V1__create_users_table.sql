CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(20)  NOT NULL DEFAULT 'USER',
    display_name    VARCHAR(100),
    gender          VARCHAR(20),
    avatar_url      VARCHAR(500),
    cover_url       VARCHAR(500),
    bio             VARCHAR(500),
    work            VARCHAR(150),
    location        VARCHAR(150),
    onboarded       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_email ON users (email);
