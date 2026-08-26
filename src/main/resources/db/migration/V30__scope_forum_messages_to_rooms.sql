-- Forum is moving from a single shared room to multiple topic rooms — old
-- messages have no room to belong to, so per product decision we wipe them
-- rather than inventing a synthetic "General" room to backfill into.
DELETE FROM forum_message_reports;
DELETE FROM forum_message_likes;
DELETE FROM forum_messages;

ALTER TABLE forum_messages ADD COLUMN room_id UUID NOT NULL REFERENCES forum_rooms(id) ON DELETE CASCADE;

CREATE INDEX idx_forum_messages_room_created_at ON forum_messages(room_id, created_at DESC);
