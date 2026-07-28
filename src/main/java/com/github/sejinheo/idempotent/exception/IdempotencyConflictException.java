package com.github.sejinheo.idempotent.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 같은 키로 처리 중인 요청이 있는데 또 요청이 들어온 경우 (REJECT 전략).
 * 409 Conflict.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String message) {
        super(message);
    }
}
