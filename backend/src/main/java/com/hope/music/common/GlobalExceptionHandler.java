package com.hope.music.common;

import com.hope.music.search.controller.SearchController.LegacyResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;
import java.util.TreeMap;

/** Sanitized errors: SQL, credentials and filesystem paths never enter responses. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new TreeMap<>();
        if (ex instanceof MethodArgumentNotValidException validation) {
            validation.getBindingResult().getFieldErrors().forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        } else if (ex instanceof org.springframework.web.method.annotation.HandlerMethodValidationException validation) {
            validation.getParameterValidationResults().forEach(result -> errors.put(
                    result.getMethodParameter().getParameterName(), "参数不符合约束"));
        }
        String message = status.is5xxServerError() ? "服务暂不可用" : "请求参数或路径无效";
        if (ex instanceof org.springframework.web.server.ResponseStatusException business
                && business.getReason() != null && (status.is4xxClientError() || status.value() == 503)) {
            message = business.getReason();
        }
        return new ResponseEntity<>(new Result<>(status.value(), message, errors), headers, status);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> unexpected(Exception exception, WebRequest request) {
        // Do not log SQL exception text: JDBC URLs can carry sensitive configuration.
        LOG.error("Request failed: {}", exception.getClass().getSimpleName());
        if (request instanceof ServletWebRequest servlet && servlet.getRequest().getRequestURI().endsWith("/searchServlet")) {
            return ResponseEntity.ok(new LegacyResult(false, "查询失败"));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new Result<>(500, "服务暂不可用", null));
    }
}
