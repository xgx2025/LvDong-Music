package com.hope.music.account.service;

import com.hope.music.account.dao.AccountDao;
import com.hope.music.account.domain.dto.AccountRequestDTO;
import com.hope.music.auth.service.SessionService;
import com.hope.music.auth.util.PasswordUtil;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Single-use durable codes, bounded attempts and atomic account writes. */
@Service
public class AccountService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final AccountDao dao;
    private final VerificationMailSender mail;
    public AccountService(AccountDao dao, VerificationMailSender mail) { this.dao = dao; this.mail = mail; }

    private static ResponseStatusException bad(String text) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, text); }
    private static String email(String input) {
        String email = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
        // The existing users.email column is VARCHAR(25); do not truncate or silently change the live schema.
        if (email.length() > 25 || !email.matches("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+"))
            throw bad("请输入有效邮箱，长度不能超过25个字符");
        return email;
    }
    private static void password(String input) {
        if (input == null || input.length() < 8 || input.getBytes(StandardCharsets.UTF_8).length > 72)
            throw bad("密码至少8个字符，且UTF-8编码不能超过72字节");
    }
    private static Object invalidCode() { return Map.of("success", false, "message", "验证码无效或已过期"); }

    @Transactional
    public Object sendCode(AccountRequestDTO body) {
        String email = email(body.email());
        var previous = dao.lockCode(email);
        long now = System.currentTimeMillis();
        if (now - ((Number) previous.get("last_sent_at")).longValue() < 60000)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "请等待60秒后再发送验证码");
        String code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1000000));
        String nonce = UUID.randomUUID().toString();
        dao.saveCode(email, SessionService.digest(nonce + ":" + code), nonce, now);
        mail.sendCode(email, code);
        return Map.of("success", true, "message", "验证码已发送");
    }

    private boolean consumeCode(String email, String code) {
        var row = dao.lockCode(email);
        if (((Number) row.get("consumed")).intValue() != 0 || ((Number) row.get("attempts")).intValue() >= 5
                || ((Number) row.get("expires_at")).longValue() <= System.currentTimeMillis()) return false;
        String hash = (String) row.get("code_hash"), nonce = (String) row.get("nonce");
        boolean valid = code != null && code.matches("[0-9]{6}") && hash != null
                && MessageDigest.isEqual(hash.getBytes(StandardCharsets.UTF_8),
                        SessionService.digest(nonce + ":" + code).getBytes(StandardCharsets.UTF_8));
        dao.useCode(email, valid);
        return valid;
    }

    @Transactional
    public Object verifyCode(AccountRequestDTO body) {
        return consumeCode(email(body.email()), body.code()) ? Map.of("success", true) : invalidCode();
    }

    @Transactional
    public Object register(AccountRequestDTO body) {
        String email = email(body.email()), username = body.username() == null ? "" : body.username().trim();
        if (username.length() < 2 || username.length() > 20) throw bad("用户名长度必须在2至20个字符之间");
        password(body.password());
        if (!consumeCode(email, body.verificationCode())) return invalidCode();
        if (dao.emailExists(email) || dao.usernameExists(username)) throw new ResponseStatusException(HttpStatus.CONFLICT, "邮箱或用户名已被注册");
        long id;
        do { id = RANDOM.nextLong() & Long.MAX_VALUE; } while (id == 0);
        try { dao.insertUser(id, username, email, PasswordUtil.hash(body.password())); }
        catch (DuplicateKeyException ex) { throw new ResponseStatusException(HttpStatus.CONFLICT, "邮箱或用户名已被注册"); }
        return Map.of("success", true);
    }

    @Transactional
    public Object resetPassword(AccountRequestDTO body) {
        String email = email(body.email()); password(body.newPassword());
        if (!consumeCode(email, body.verificationCode())) return invalidCode();
        if (!dao.emailExists(email)) throw bad("该邮箱尚未注册");
        dao.resetPassword(email, PasswordUtil.hash(body.newPassword()));
        return Map.of("success", true);
    }
}
