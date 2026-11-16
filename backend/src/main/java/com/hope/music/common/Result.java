package com.hope.music.common;

/** Versioned API envelope. */
public record Result<T>(int code, String message, T data) {
    public static <T> Result<T> success(T data) {
        return new Result<>(0, "操作成功", data);
    }
}
