package com.hope.music.account.service;

/** Injectable delivery boundary; production implementation uses configured SMTP. */
public interface VerificationMailSender {
    void sendCode(String email, String code);
}
