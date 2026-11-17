-- Apply explicitly to the shared business database. No automatic schema changes.
CREATE TABLE IF NOT EXISTS email_verifications (
    email VARCHAR(254) NOT NULL PRIMARY KEY,
    code_hash CHAR(64),
    nonce CHAR(36),
    expires_at BIGINT NOT NULL DEFAULT 0,
    last_sent_at BIGINT NOT NULL DEFAULT 0,
    attempts INT NOT NULL DEFAULT 0,
    consumed INT NOT NULL DEFAULT 1
);
