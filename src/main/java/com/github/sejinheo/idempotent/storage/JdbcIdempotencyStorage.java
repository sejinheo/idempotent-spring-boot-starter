package com.github.sejinheo.idempotent.storage;

import com.github.sejinheo.idempotent.exception.IdempotencyStorageException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * JDBC 기반 구현체. INSERT IGNORE(MySQL) + 유니크 제약으로 원자적 선점을 보장한다.
 *
 * Redis 구현체와의 차이:
 * - 비즈니스 트랜잭션(@Transactional)과 같은 DB 트랜잭션으로 묶을 수 있어
 *   "커밋은 됐는데 COMPLETED 기록이 없는" 구조적 공백이 사라진다.
 * - 결제처럼 절대 중복 실행이 안 되는 경우에 권장한다.
 *
 * 사용 전 schema-mysql.sql(또는 schema-postgresql.sql)로 테이블을 생성해야 한다.
 * TTL은 expires_at 컬럼으로 관리하며, 만료 행 정리는 별도 배치나 스케줄러가 담당한다.
 */
public class JdbcIdempotencyStorage implements IdempotencyStorage {

    private final JdbcTemplate jdbcTemplate;

    public JdbcIdempotencyStorage(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<IdempotencyResult> find(String key) {
        try {
            String sql = """
                    SELECT status, body_hash, http_status, body_json, schema_version
                    FROM idempotency_keys
                    WHERE idempotency_key = ? AND expires_at > NOW()
                    """;
            List<IdempotencyResult> results = jdbcTemplate.query(sql, this::mapRow, key);
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (DataAccessException e) {
            throw new IdempotencyStorageException("DB 조회 실패", e);
        }
    }

    @Override
    public boolean tryClaim(String key, IdempotencyResult inProgress, Duration ttl) {
        try {
            // INSERT IGNORE: 유니크 제약(idempotency_key)으로 원자적 선점
            // affected rows == 1이면 선점 성공, 0이면 이미 존재
            String sql = """
                    INSERT IGNORE INTO idempotency_keys
                        (idempotency_key, status, body_hash, http_status, body_json, schema_version, expires_at)
                    VALUES (?, ?, ?, NULL, NULL, ?, ?)
                    """;
            LocalDateTime expiresAt = LocalDateTime.now().plus(ttl);
            int affected = jdbcTemplate.update(sql,
                    key,
                    inProgress.getStatus().name(),
                    inProgress.getBodyHash(),
                    inProgress.getSchemaVersion(),
                    Timestamp.valueOf(expiresAt));
            return affected == 1;
        } catch (DataAccessException e) {
            throw new IdempotencyStorageException("DB 선점 실패", e);
        }
    }

    @Override
    public void complete(String key, IdempotencyResult completed, Duration ttl) {
        try {
            String sql = """
                    UPDATE idempotency_keys
                    SET status = ?, http_status = ?, body_json = ?, schema_version = ?, expires_at = ?
                    WHERE idempotency_key = ?
                    """;
            LocalDateTime expiresAt = LocalDateTime.now().plus(ttl);
            jdbcTemplate.update(sql,
                    completed.getStatus().name(),
                    completed.getHttpStatus(),
                    completed.getBodyJson(),
                    completed.getSchemaVersion(),
                    Timestamp.valueOf(expiresAt),
                    key);
        } catch (DataAccessException e) {
            throw new IdempotencyStorageException("DB 결과 저장 실패", e);
        }
    }

    @Override
    public void release(String key) {
        try {
            jdbcTemplate.update("DELETE FROM idempotency_keys WHERE idempotency_key = ?", key);
        } catch (DataAccessException e) {
            throw new IdempotencyStorageException("DB 키 해제 실패", e);
        }
    }

    private IdempotencyResult mapRow(ResultSet rs, int rowNum) throws java.sql.SQLException {
        IdempotencyResult result = new IdempotencyResult();
        result.setStatus(IdempotencyResult.Status.valueOf(rs.getString("status")));
        result.setBodyHash(rs.getString("body_hash"));
        result.setHttpStatus(rs.getObject("http_status", Integer.class));
        result.setBodyJson(rs.getString("body_json"));
        result.setSchemaVersion(rs.getInt("schema_version"));
        return result;
    }
}
