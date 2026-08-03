package com.github.sejinheo.idempotent;

import com.github.sejinheo.idempotent.annotation.Idempotent;
import com.github.sejinheo.idempotent.spi.UserIdExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * JdbcIdempotencyStorage 통합 테스트.
 * 실제 MySQL Testcontainers로 실행하여 프로덕션 환경과 동일한 동작을 보장한다.
 */
@Testcontainers
@SpringBootTest(classes = JdbcIdempotencyStorageTest.JdbcTestConfig.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "idempotency.storage=jdbc",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-mysql.sql"
})
class JdbcIdempotencyStorageTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withUsername("test")
            .withPassword("test")
            .withDatabaseName("idempotent_test");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearTable() {
        jdbcTemplate.update("DELETE FROM idempotency_keys");
        JdbcTestController.callCount.set(0);
    }

    @Test
    void 첫_요청은_정상_실행() throws Exception {
        mockMvc.perform(post("/jdbc-test/dto")
                        .header("Idempotency-Key", "key-001")
                        .header("X-User-Id", "user-A")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"apple"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("apple"));

        assertThat(JdbcTestController.callCount.get()).isEqualTo(1);
    }

    @Test
    void 같은_키_같은_바디_재요청시_캐시된_응답_반환하고_실제_메서드는_한번만_실행() throws Exception {
        String body = """
                {"name":"apple"}
                """;

        mockMvc.perform(post("/jdbc-test/dto")
                        .header("Idempotency-Key", "key-002")
                        .header("X-User-Id", "user-A")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("apple"));

        mockMvc.perform(post("/jdbc-test/dto")
                        .header("Idempotency-Key", "key-002")
                        .header("X-User-Id", "user-A")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("apple"));

        assertThat(JdbcTestController.callCount.get()).isEqualTo(1);
    }

    @Test
    void 같은_키_다른_바디_422() throws Exception {
        mockMvc.perform(post("/jdbc-test/dto")
                        .header("Idempotency-Key", "key-003")
                        .header("X-User-Id", "user-A")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"apple"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/jdbc-test/dto")
                        .header("Idempotency-Key", "key-003")
                        .header("X-User-Id", "user-A")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"banana"}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void 다른_사용자는_같은_키여도_각자_독립_실행() throws Exception {
        String body = """
                {"name":"apple"}
                """;

        mockMvc.perform(post("/jdbc-test/dto")
                        .header("Idempotency-Key", "shared-key")
                        .header("X-User-Id", "user-A")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/jdbc-test/dto")
                        .header("Idempotency-Key", "shared-key")
                        .header("X-User-Id", "user-B")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        assertThat(JdbcTestController.callCount.get()).isEqualTo(2);
    }

    record TestRequest(String name) {}
    record TestResponse(String result) {}

    @RestController
    @RequestMapping("/jdbc-test")
    static class JdbcTestController {

        static final AtomicInteger callCount = new AtomicInteger();

        @Idempotent
        @PostMapping("/dto")
        public ResponseEntity<TestResponse> dto(@RequestBody TestRequest request) {
            callCount.incrementAndGet();
            return ResponseEntity.ok(new TestResponse(request.name()));
        }
    }

    @Configuration
    @EnableAutoConfiguration
    static class JdbcTestConfig {

        @Bean
        JdbcTestController jdbcTestController() {
            return new JdbcTestController();
        }

        @Bean
        UserIdExtractor userIdExtractor() {
            return request -> request.getHeader("X-User-Id");
        }
    }
}
