-- Apply explicitly. Application startup never changes the schema.
CREATE TABLE qq_music_connection (
 user_id BIGINT PRIMARY KEY,
 account_id VARCHAR(100) NOT NULL,
 nickname VARCHAR(200) NOT NULL,
 encrypted_token TEXT NOT NULL,
 connected_at BIGINT NOT NULL
);
