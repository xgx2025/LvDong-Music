-- Apply explicitly. Imported metadata does not contain audio files or music credentials.
CREATE TABLE qq_playlist (
 id VARCHAR(36) PRIMARY KEY,
 user_id BIGINT NOT NULL,
 source_account_id VARCHAR(100) NOT NULL,
 provider_playlist_id VARCHAR(100) NOT NULL,
 name VARCHAR(200) NOT NULL,
 cover_url VARCHAR(1000),
 song_count INT NOT NULL,
 imported_at BIGINT NOT NULL,
 UNIQUE(user_id,source_account_id,provider_playlist_id)
);
CREATE INDEX idx_qq_playlist_user ON qq_playlist(user_id);
CREATE TABLE qq_playlist_song (
 playlist_id VARCHAR(36) NOT NULL,
 position INT NOT NULL,
 song_mid VARCHAR(100) NOT NULL,
 metadata TEXT NOT NULL,
 PRIMARY KEY(playlist_id,position),
 FOREIGN KEY(playlist_id) REFERENCES qq_playlist(id) ON DELETE CASCADE
);
