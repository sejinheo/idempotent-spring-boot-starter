package com.github.sejinheo.idempotent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UserIdExtractor 빈이 없을 때 서버 기동이 실패하는지 검증한다.
 */
@Testcontainers
class UserIdExtractorMissingTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Test
    void UserIdExtractor_빈이_없으면_애플리케이션_기동에_실패한다() {
        assertThatThrownBy(() ->
                new org.springframework.boot.builder.SpringApplicationBuilder(NoExtractorConfig.class)
                        .run()
        ).satisfies(ex -> {
            // 원인 체인 어딘가에 UnsatisfiedDependencyException이 있어야 한다
            Throwable cause = ex;
            boolean found = false;
            while (cause != null) {
                if (cause instanceof org.springframework.beans.factory.UnsatisfiedDependencyException) {
                    found = true;
                    break;
                }
                cause = cause.getCause();
            }
            assertThat(found)
                    .as("UserIdExtractor 빈이 없으면 UnsatisfiedDependencyException이 발생해야 한다")
                    .isTrue();
        });
    }

    @Configuration
    @EnableAutoConfiguration
    @TestPropertySource(properties = "spring.main.web-application-type=servlet")
    static class NoExtractorConfig {

        // UserIdExtractor 빈을 등록하지 않음 → 기동 실패 유도

        @Bean
        LettuceConnectionFactory redisConnectionFactory() {
            return new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        }
    }
}
