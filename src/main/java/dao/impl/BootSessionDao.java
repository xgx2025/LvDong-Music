package dao.impl;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import utils.DatabaseUtil;
import java.sql.*;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Shared validation and revocation bridge for Boot opaque access tokens. Fails closed on missing schema or database failure. */
public class BootSessionDao {
    public void revoke(String access,String refresh) {
        try (Connection connection=DatabaseUtil.getConnection(); PreparedStatement statement=connection.prepareStatement(
                "UPDATE auth_sessions SET revoked=1 WHERE refresh_hash=? OR id IN (SELECT session_id FROM auth_access_tokens WHERE token_hash=?)")) {
            statement.setString(1,hash(refresh==null?"":refresh)); statement.setString(2,hash(access==null?"":access)); statement.executeUpdate();
        } catch (SQLException failure) { throw new IllegalArgumentException("会话撤销不可用"); }
    }
    private String hex(byte[] bytes) {
        StringBuilder value=new StringBuilder();
        for(byte b:bytes) value.append(String.format("%02x",b & 255));
        return value.toString();
    }
    private String hash(String token) {
        try { return hex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }
    public Claims validateAccess(String token) {
        if (!token.matches("b1a_[A-Za-z0-9_-]{43}")) throw new IllegalArgumentException("播放凭证无效");
        try {
            String hash=hex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
            try (Connection connection=DatabaseUtil.getConnection();
                 PreparedStatement statement=connection.prepareStatement(
                    "SELECT s.user_id,a.expires_at FROM auth_access_tokens a JOIN auth_sessions s ON s.id=a.session_id "
                    + "WHERE a.token_hash=? AND a.expires_at>? AND s.expires_at>? AND s.revoked=0")) {
                statement.setString(1,hash); long now=System.currentTimeMillis();
                statement.setLong(2,now); statement.setLong(3,now);
                try (ResultSet result=statement.executeQuery()) {
                    if (!result.next()) throw new IllegalArgumentException("播放凭证无效");
                    Claims claims=Jwts.claims(); claims.put("id",result.getString(1));
                    claims.setExpiration(new java.util.Date(result.getLong(2))); return claims;
                }
            }
        } catch (SQLException | NoSuchAlgorithmException failure) {
            throw new IllegalArgumentException("会话验证不可用");
        }
    }
}
