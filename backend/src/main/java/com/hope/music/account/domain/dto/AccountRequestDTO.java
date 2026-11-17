package com.hope.music.account.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Account write requests keep the existing Vue fields. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountRequestDTO(String email, String username, String password,
        String newPassword, String verificationCode, String code) { }
