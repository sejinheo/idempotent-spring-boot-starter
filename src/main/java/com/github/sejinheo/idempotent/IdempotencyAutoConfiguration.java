package com.github.sejinheo.idempotent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
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
 *
 * ObjectMapper: 사용자 프로젝트에 ObjectMapper 빈이 있으면 그것을 재사용하고,
 * 없으면 라이브러리 내부에서 기본 ObjectMapper를 생성해서 사용한다.
 * 사용자가 별도 JacksonConfig를 만들 필요가 없다.
 *
 * UserIdExtractor: 반드시 빈으로 등록해야 한다. 없으면 서버가 기동되지 않는다.
 * 사용자 간 Idempotency-Key 격리는 UserIdExtractor가 반환하는 ID에 의존하므로,
 * 이 빈 없이 조용히 올라가면 정보 유출로 이어질 수 있다.
 */
@AutoConfiguration
@ConditionalOnClass(StringRedisTemplate.class)
@EnableConfigurationProperties(IdempotencyProperties.class)
public class IdempotencyAutoConfiguration {

    private ObjectMapper resolveObjectMapper(ObjectProvider<ObjectMapper> provider) {
        ObjectMapper existing = provider.getIfAvailable();
        return (existing != null) ? existing : new ObjectMapper();
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotencyStorage idempotencyStorage(StringRedisTemplate redisTemplate,
                                                   ObjectProvider<ObjectMapper> objectMapperProvider) {
        return new RedisIdempotencyStorage(redisTemplate, resolveObjectMapper(objectMapperProvider));
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
