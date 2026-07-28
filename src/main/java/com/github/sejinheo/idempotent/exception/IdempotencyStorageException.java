package com.github.sejinheo.idempotent.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Redis 접근 실패 등 저장소 레벨 장애. FAIL_CLOSED 정책에 따라
 * 요청 자체를 실패 처리한다. 503 Service Unavailable.
 */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class IdempotencyStorageException extends RuntimeException {
    public IdempotencyStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
