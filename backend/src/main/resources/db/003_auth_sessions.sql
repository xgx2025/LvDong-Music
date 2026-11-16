-- Apply explicitly to an isolated test database first. No automatic startup DDL.
CREATE TABLE auth_sessions (
 id VARCHAR(36) PRIMARY KEY,
 user_id BIGINT NOT NULL,
 refresh_hash CHAR(64) NOT NULL UNIQUE,
 expires_at BIGINT NOT NULL,
 revoked INT NOT NULL DEFAULT 0
);
CREATE TABLE auth_access_tokens (
 token_hash CHAR(64) PRIMARY KEY,
 session_id VARCHAR(36) NOT NULL,
 expires_at BIGINT NOT NULL,
 FOREIGN KEY (session_id) REFERENCES auth_sessions(id)
);
CREATE INDEX idx_auth_access_session ON auth_access_tokens(session_id);
