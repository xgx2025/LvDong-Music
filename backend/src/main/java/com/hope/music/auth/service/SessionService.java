package com.hope.music.auth.service;

import com.hope.music.auth.dao.SessionDao;
import com.hope.music.auth.util.PasswordUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Boot is the sole issuer of opaque b1a/b1r credentials; WAR only validates access. */
@Service
public class SessionService {
    private final SessionDao dao;
    private static final SecureRandom RANDOM = new SecureRandom();
    public SessionService(SessionDao dao) { this.dao = dao; }
    public static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static String token(String prefix) { byte[] b = new byte[32]; RANDOM.nextBytes(b); return prefix+Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
    private static ResponseStatusException unauthorized() { return new ResponseStatusException(HttpStatus.UNAUTHORIZED,"登录凭证已失效"); }
    @Transactional
    public Map<String,Object> login(String email,String password) {
        if(email==null || password==null || email.length()>254) throw unauthorized();
        var user=dao.user(email);
        boolean valid=false;
        try { valid=user!=null && PasswordUtil.verify(password,(String)user.get("password")); } catch(IllegalArgumentException ignored) { }
        if(!valid) throw unauthorized();
        String id=(String)user.get("id"), old=(String)user.remove("password");
        if(PasswordUtil.isMD5Hash(old)) dao.upgradePassword(id,old,PasswordUtil.hash(password));
        String sid=UUID.randomUUID().toString(), refresh=token("b1r_");
        dao.create(sid,id,digest(refresh),System.currentTimeMillis()+604800000L);
        return Map.of("success",true,"user",user,"tokens",issueAccess(sid,refresh));
    }
    private Map<String,Object> issueAccess(String sid,String refresh) {
        String access=token("b1a_"); dao.access(digest(access),sid,System.currentTimeMillis()+1800000L);
        return Map.of("accessToken",access,"refreshToken",refresh,"expiresIn",1800);
    }
    @Transactional
    public Map<String,Object> refresh(String refresh) {
        if(refresh==null || !refresh.matches("b1r_[A-Za-z0-9_-]{43}")) throw unauthorized();
        var row=dao.refreshSession(digest(refresh),System.currentTimeMillis());
        if(row==null || dao.profile((String)row.get("userId"))==null) throw unauthorized();
        return Map.of("success",true,"tokens",issueAccess((String)row.get("id"),refresh));
    }
    public String identity(String authorization) {
        if(authorization==null || !authorization.matches("Bearer b1a_[A-Za-z0-9_-]{43}")) throw unauthorized();
        String id=dao.identity(digest(authorization.substring(7)),System.currentTimeMillis());
        if(id==null) throw unauthorized(); return id;
    }
    public Map<String,Object> profile(String authorization) {
        var user=dao.profile(identity(authorization)); if(user==null) throw unauthorized();
        return Map.of("success",true,"user",user);
    }
    @Transactional
    public Map<String,Object> logout(String authorization,String refresh) {
        String access=authorization!=null && authorization.startsWith("Bearer ") ? authorization.substring(7) : "";
        dao.revoke(digest(access),digest(refresh==null?"":refresh)); return Map.of("success",true);
    }
}
