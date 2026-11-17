package com.hope.music.account.controller;

import com.hope.music.account.domain.dto.AccountRequestDTO;
import com.hope.music.account.service.AccountService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

/** Public account operations; verification codes never enter responses. */
@RestController
@RequestMapping("/api/v1/account")
public class AccountController {
    private final AccountService service;
    public AccountController(AccountService service) { this.service = service; }
    @ModelAttribute
    public void preventCaching(HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }
    @PostMapping("/verification-code")
    public Object sendCode(@RequestBody AccountRequestDTO body) { return service.sendCode(body); }
    @PostMapping("/verification")
    public Object verifyCode(@RequestBody AccountRequestDTO body) { return service.verifyCode(body); }
    @PostMapping("/registration")
    public Object register(@RequestBody AccountRequestDTO body) { return service.register(body); }
    @PostMapping("/password-reset")
    public Object resetPassword(@RequestBody AccountRequestDTO body) { return service.resetPassword(body); }
}
