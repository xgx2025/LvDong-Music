package com.hope.music.media.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Media sources remain private and require an approved song on every request. */
@Repository
public class MediaDao {
    private final JdbcTemplate jdbc;
    public MediaDao(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public String source(String id) { return jdbc.query("SELECT audio_url FROM songs WHERE id=? AND state=1",(rs,n)->rs.getString(1),id).stream().findFirst().orElse(null); }
}
