package com.hope.music.account.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;

/** Account and verification persistence; transaction ownership remains in services. */
@Repository
public class AccountDao {
    private final JdbcTemplate jdbc;
    public AccountDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Map<String, Object> lockCode(String email) {
        jdbc.update("INSERT INTO email_verifications(email) VALUES(?) ON DUPLICATE KEY UPDATE email=email", email);
        return jdbc.queryForMap("SELECT code_hash,nonce,expires_at,last_sent_at,attempts,consumed FROM email_verifications WHERE email=? FOR UPDATE", email);
    }
    public void saveCode(String email, String hash, String nonce, long now) {
        jdbc.update("UPDATE email_verifications SET code_hash=?,nonce=?,expires_at=?,last_sent_at=?,attempts=0,consumed=0 WHERE email=?",
                hash, nonce, now + 600000, now, email);
    }
    public void useCode(String email, boolean valid) {
        jdbc.update("UPDATE email_verifications SET attempts=attempts+1,consumed=? WHERE email=?", valid ? 1 : 0, email);
    }
    public boolean emailExists(String email) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email=?", Long.class, email) > 0;
    }
    public boolean usernameExists(String username) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username=?", Long.class, username) > 0;
    }
    public void insertUser(long id, String username, String email, String password) {
        jdbc.update("INSERT INTO users(id,username,email,password,gender,state,bio,avatar,account_status,created_at) VALUES(?,?,?,?,?,0,?,?,0,CURRENT_TIMESTAMP)",
                id, username, email, password, "未知", "简介待解锁，故事正在加载中…", "static/images/userAvatars/default.png");
    }
    public void resetPassword(String email, String hash) {
        jdbc.update("UPDATE users SET password=? WHERE email=?", hash, email);
        jdbc.update("UPDATE auth_sessions SET revoked=1 WHERE user_id IN (SELECT id FROM users WHERE email=?)", email);
    }
}
