ALTER TABLE forum_messages ADD COLUMN image_url VARCHAR(500);
ALTER TABLE forum_messages ADD COLUMN file_url VARCHAR(500);
ALTER TABLE forum_messages ADD COLUMN file_name VARCHAR(255);
ALTER TABLE forum_messages ADD COLUMN file_size BIGINT;
ALTER TABLE forum_messages ADD COLUMN file_type VARCHAR(100);
ALTER TABLE forum_messages ALTER COLUMN text DROP NOT NULL;
