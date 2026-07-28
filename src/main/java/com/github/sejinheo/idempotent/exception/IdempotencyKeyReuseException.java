package com.github.sejinheo.idempotent.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 같은 Idempotency-Key로 이전과 다른 요청 바디가 들어온 경우.
 * 클라이언트가 키를 잘못 재사용한 것으로 간주하고 캐시된 응답을 재생하지 않는다.
 * 422 Unprocessable Entity.
 */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class IdempotencyKeyReuseException extends RuntimeException {
    public IdempotencyKeyReuseException(String key) {
        super("Idempotency-Key [" + key + "] 가 다른 요청 바디와 함께 재사용되었습니다.");
    }
}
