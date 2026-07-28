package com.github.sejinheo.idempotent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 이 라이브러리를 의존성으로 추가하기만 하면, StringRedisTemplate이 클래스패스에
 * 있을 때 자동으로 IdempotencyStorage와 HttpIdempotentAspect 빈이 등록된다.
 * (별도 설정 없이도 기본값으로 동작하되, application.yml의 idempotency.*
 * 프로퍼티로 key-prefix/ttl-hours를 조정할 수 있다.)
 */
@AutoConfiguration
@ConditionalOnClass(StringRedisTemplate.class)
@EnableConfigurationProperties(IdempotencyProperties.class)
public class IdempotencyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public IdempotencyStorage idempotencyStorage(StringRedisTemplate redisTemplate,
                                                   ObjectMapper objectMapper) {
        return new RedisIdempotencyStorage(redisTemplate, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public HttpIdempotentAspect httpIdempotentAspect(IdempotencyStorage idempotencyStorage,
                                                       IdempotencyProperties properties,
                                                       ObjectMapper objectMapper) {
        return new HttpIdempotentAspect(idempotencyStorage, properties, objectMapper);
    }
}
