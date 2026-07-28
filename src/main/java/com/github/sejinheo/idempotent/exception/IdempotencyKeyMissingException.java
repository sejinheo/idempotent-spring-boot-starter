package com.github.sejinheo.idempotent.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * @Idempotent가 붙은 API인데 필수 헤더가 없는 경우. 400 Bad Request.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class IdempotencyKeyMissingException extends RuntimeException {
    public IdempotencyKeyMissingException(String headerName) {
        super("필수 헤더 [" + headerName + "] 가 없습니다.");
    }
}
