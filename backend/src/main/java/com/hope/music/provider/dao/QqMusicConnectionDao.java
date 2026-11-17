package com.hope.music.provider.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Persistent QQ binding owned by a website user; credentials remain encrypted. */
@Repository
public class QqMusicConnectionDao {
    private final JdbcTemplate jdbc;
    public QqMusicConnectionDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Binding find(String userId) {
        return jdbc.query("SELECT account_id,nickname,encrypted_token,connected_at FROM qq_music_connection WHERE user_id=?",
                (rs, row) -> new Binding(rs.getString(1), rs.getString(2), rs.getString(3), rs.getLong(4)), userId)
                .stream().findFirst().orElse(null);
    }
    @Transactional
    public void save(String userId, String accountId, String nickname, String encryptedToken) {
        jdbc.update("DELETE FROM qq_music_connection WHERE user_id=?", userId);
        jdbc.update("INSERT INTO qq_music_connection(user_id,account_id,nickname,encrypted_token,connected_at) VALUES(?,?,?,?,?)",
                userId, accountId, nickname, encryptedToken, System.currentTimeMillis());
    }
    public void delete(String userId) { jdbc.update("DELETE FROM qq_music_connection WHERE user_id=?", userId); }

    /** Private storage row; never returned by an HTTP endpoint. */
    public record Binding(String accountId, String nickname, String encryptedToken, long connectedAt) { }
}
