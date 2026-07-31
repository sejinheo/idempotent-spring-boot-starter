package com.github.sejinheo.idempotent;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 현재 요청에서 사용자 식별자를 추출하는 전략 인터페이스.
 *
 * <p>이 라이브러리는 서로 다른 사용자가 우연히 같은 Idempotency-Key를 보냈을 때
 * 키 충돌이 일어나지 않도록 Redis 키에 사용자 ID를 포함시킨다.
 * 사용자 ID를 꺼내는 방법은 인증 방식(JWT, 세션, API Key 등)마다 다르므로
 * 이 인터페이스를 구현해서 빈으로 등록하면 된다.</p>
 *
 * <pre>{@code
 * @Bean
 * public UserIdExtractor userIdExtractor() {
 *     return request -> {
 *         // 예: JWT Claims에서 subject 추출
 *         String token = request.getHeader("Authorization").substring(7);
 *         return Jwts.parser()...parseClaimsJws(token).getBody().getSubject();
 *     };
 * }
 * }</pre>
 *
 * <p><strong>주의:</strong> 이 빈이 등록되지 않으면 애플리케이션 기동이 실패한다.
 * 빠진 채로 조용히 올라가면 사용자 간 키 격리가 깨져서 정보 유출로 이어질 수 있다.</p>
 */
@FunctionalInterface
public interface UserIdExtractor {

    /**
     * 현재 HTTP 요청에서 사용자 식별자를 반환한다.
     *
     * @param request 현재 HTTP 요청
     * @return 사용자를 고유하게 식별할 수 있는 문자열 (null이면 안 된다)
     */
    String extract(HttpServletRequest request);
}
