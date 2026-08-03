package com.github.sejinheo.idempotent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.sejinheo.idempotent.aspect.HttpIdempotentAspect;
import com.github.sejinheo.idempotent.spi.UserIdExtractor;
import com.github.sejinheo.idempotent.storage.IdempotencyStorage;
import com.github.sejinheo.idempotent.storage.JdbcIdempotencyStorage;
import com.github.sejinheo.idempotent.storage.RedisIdempotencyStorage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 이 라이브러리를 의존성으로 추가하기만 하면 자동으로 빈이 등록된다.
 *
 * 저장소 선택 (idempotency.storage):
 * - redis(기본): Redis SET NX 기반. 분산 환경에 적합.
 * - jdbc: DB 기반. 비즈니스 트랜잭션과 같은 트랜잭션으로 묶어 강한 멱등성 보장.
 *         결제처럼 절대 중복 실행이 안 되는 경우에 권장.
 *         사용 전 schema-mysql.sql 또는 schema-postgresql.sql로 테이블을 생성해야 한다.
 *
 * ObjectMapper: 사용자 프로젝트에 ObjectMapper 빈이 있으면 그것을 재사용하고,
 * 없으면 라이브러리 내부에서 기본 ObjectMapper를 생성해서 사용한다.
 *
 * UserIdExtractor: 반드시 빈으로 등록해야 한다. 없으면 서버가 기동되지 않는다.
 * 사용자 간 Idempotency-Key 격리는 UserIdExtractor가 반환하는 ID에 의존하므로,
 * 이 빈 없이 조용히 올라가면 정보 유출로 이어질 수 있다.
 */
@AutoConfiguration
@EnableConfigurationProperties(IdempotencyProperties.class)
public class IdempotencyAutoConfiguration {

    private ObjectMapper resolveObjectMapper(ObjectProvider<ObjectMapper> provider) {
        ObjectMapper existing = provider.getIfAvailable();
        return (existing != null) ? existing : new ObjectMapper();
    }

    @Bean
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnMissingBean(IdempotencyStorage.class)
    @ConditionalOnProperty(name = "idempotency.storage", havingValue = "redis", matchIfMissing = true)
    public IdempotencyStorage redisIdempotencyStorage(StringRedisTemplate redisTemplate,
                                                       ObjectProvider<ObjectMapper> objectMapperProvider) {
        return new RedisIdempotencyStorage(redisTemplate, resolveObjectMapper(objectMapperProvider));
    }

    @Bean
    @ConditionalOnClass(JdbcTemplate.class)
    @ConditionalOnMissingBean(IdempotencyStorage.class)
    @ConditionalOnProperty(name = "idempotency.storage", havingValue = "jdbc")
    public IdempotencyStorage jdbcIdempotencyStorage(JdbcTemplate jdbcTemplate,
                                                      ObjectProvider<ObjectMapper> objectMapperProvider) {
        return new JdbcIdempotencyStorage(jdbcTemplate, resolveObjectMapper(objectMapperProvider));
    }

    @Bean
    @ConditionalOnMissingBean
    public HttpIdempotentAspect httpIdempotentAspect(IdempotencyStorage idempotencyStorage,
                                                      IdempotencyProperties properties,
                                                      ObjectProvider<ObjectMapper> objectMapperProvider,
                                                      UserIdExtractor userIdExtractor) {
        return new HttpIdempotentAspect(idempotencyStorage, properties,
                resolveObjectMapper(objectMapperProvider), userIdExtractor);
    }
}
