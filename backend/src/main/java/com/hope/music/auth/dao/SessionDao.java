package com.hope.music.auth.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;

/** Persistent session boundary shared with the WAR; raw credentials are never stored. */
@Repository
public class SessionDao {
    private final JdbcTemplate jdbc;
    public SessionDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Map<String,Object> user(String email) {
        return jdbc.query("SELECT id,username,password,email,avatar,gender,bio,created_at FROM users WHERE email=? FOR UPDATE",
            (rs,n) -> { Map<String,Object> u = new HashMap<>();
                for (String key : List.of("id","username","password","email","avatar","gender","bio")) u.put(key,rs.getString(key));
                u.put("registerTime",rs.getString("created_at"));
                Object avatar=u.get("avatar");
                if(avatar instanceof String path && !(path.startsWith("/api/v1/assets/") || path.startsWith("static/") || path.startsWith("/static/") || path.startsWith("https://") || path.startsWith("http://"))) u.put("avatar",null);
                return u; }, email).stream().findFirst().orElse(null);
    }
    public Map<String,Object> profile(String id) {
        return jdbc.query("SELECT id,username,email,avatar,gender,bio,created_at FROM users WHERE id=?",
            (rs,n) -> { Map<String,Object> u = new HashMap<>();
                for (String key : List.of("id","username","email","avatar","gender","bio")) u.put(key,rs.getString(key));
                u.put("registerTime",rs.getString("created_at"));
                Object avatar=u.get("avatar");
                if(avatar instanceof String path && !(path.startsWith("/api/v1/assets/") || path.startsWith("static/") || path.startsWith("/static/") || path.startsWith("https://") || path.startsWith("http://"))) u.put("avatar",null);
                return u; }, id).stream().findFirst().orElse(null);
    }
    public void upgradePassword(String id,String previous,String hash) {
        jdbc.update("UPDATE users SET password=? WHERE id=? AND password=?",hash,id,previous);
    }
    public void create(String sid,String uid,String refresh,long expiry) {
        jdbc.update("INSERT INTO auth_sessions(id,user_id,refresh_hash,expires_at,revoked) VALUES(?,?,?,?,0)",sid,uid,refresh,expiry);
    }
    public void access(String hash,String sid,long expiry) {
        jdbc.update("INSERT INTO auth_access_tokens(token_hash,session_id,expires_at) VALUES(?,?,?)",hash,sid,expiry);
    }
    public String identity(String hash,long now) {
        return jdbc.query("SELECT s.user_id FROM auth_access_tokens a JOIN auth_sessions s ON s.id=a.session_id "
            + "WHERE a.token_hash=? AND a.expires_at>? AND s.expires_at>? AND s.revoked=0",
            (rs,n)->rs.getString(1),hash,now,now).stream().findFirst().orElse(null);
    }
    public Map<String,Object> refreshSession(String hash,long now) {
        return jdbc.query("SELECT id,user_id FROM auth_sessions WHERE refresh_hash=? AND revoked=0 AND expires_at>? FOR UPDATE",
            (rs,n)->Map.<String,Object>of("id",rs.getString(1),"userId",rs.getString(2)),hash,now).stream().findFirst().orElse(null);
    }
    public void revoke(String access,String refresh) {
        jdbc.update("UPDATE auth_sessions SET revoked=1 WHERE refresh_hash=? OR id IN "
            + "(SELECT session_id FROM auth_access_tokens WHERE token_hash=?)",refresh,access);
    }
}
