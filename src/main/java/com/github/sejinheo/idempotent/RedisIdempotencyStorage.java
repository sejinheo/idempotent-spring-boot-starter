package com.github.sejinheo.idempotent;

import com.github.sejinheo.idempotent.exception.IdempotencyStorageException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis 기반 구현체. SET NX(setIfAbsent)로 원자적 선점을 하기 때문에
 * REJECT 전략만 지원하는 v1 범위에서는 별도의 분산락 토큰이나 Lua 스크립트가
 * 필요 없다 (WAIT 전략을 지원하려면 락 토큰 기반의 더 복잡한 구조가 필요해진다).
 *
 * FAIL_CLOSED 정책: Redis 접근 중 예외가 발생하면 그대로 던져서
 * 상위(Aspect)에서 요청을 실패 처리하게 한다.
 */
public class RedisIdempotencyStorage implements IdempotencyStorage {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisIdempotencyStorage(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<IdempotencyResult> find(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, IdempotencyResult.class));
        } catch (DataAccessException e) {
            throw new IdempotencyStorageException("Redis 조회 실패", e);
        } catch (Exception e) {
            throw new IdempotencyStorageException("멱등성 결과 역직렬화 실패", e);
        }
    }

    @Override
    public boolean tryClaim(String key, IdempotencyResult inProgress, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(inProgress);
            ValueOperations<String, String> ops = redisTemplate.opsForValue();
            Boolean success = ops.setIfAbsent(key, json, ttl);
            return Boolean.TRUE.equals(success);
        } catch (DataAccessException e) {
            throw new IdempotencyStorageException("Redis 락 선점 실패", e);
        } catch (Exception e) {
            throw new IdempotencyStorageException("멱등성 결과 직렬화 실패", e);
        }
    }

    @Override
    public void complete(String key, IdempotencyResult completed, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(completed);
            redisTemplate.opsForValue().set(key, json, ttl);
        } catch (DataAccessException e) {
            throw new IdempotencyStorageException("Redis 결과 저장 실패", e);
        } catch (Exception e) {
            throw new IdempotencyStorageException("멱등성 결과 직렬화 실패", e);
        }
    }

    @Override
    public void release(String key) {
        try {
            redisTemplate.delete(key);
        } catch (DataAccessException e) {
            throw new IdempotencyStorageException("Redis 키 해제 실패", e);
        }
    }
}
