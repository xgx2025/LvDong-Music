-- Private filesystem paths are never exposed through public asset endpoints.
CREATE TABLE IF NOT EXISTS musician_materials (
    user_id BIGINT NOT NULL PRIMARY KEY,
    front_path VARCHAR(1024) NOT NULL,
    back_path VARCHAR(1024) NOT NULL
);
