package com.hope.music.account.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Missing SMTP configuration or failed delivery never reports success. */
@Component
public class SmtpVerificationMailSender implements VerificationMailSender {
    private final ObjectProvider<JavaMailSender> senders;
    private final String from;
    public SmtpVerificationMailSender(ObjectProvider<JavaMailSender> senders,
            @Value("${app.mail.from:}") String from) { this.senders = senders; this.from = from; }
    public void sendCode(String email, String code) {
        var sender = senders.getIfAvailable();
        if (sender == null || from.isBlank()) throw unavailable();
        var message = new SimpleMailMessage();
        message.setFrom(from); message.setTo(email); message.setSubject("律动音乐验证码");
        message.setText("您的验证码为：" + code + "，10分钟内有效。如非本人操作，请忽略此邮件。");
        try { sender.send(message); }
        catch (RuntimeException ex) { throw unavailable(); }
    }
    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "验证码发送失败，请稍后重试");
    }
}
