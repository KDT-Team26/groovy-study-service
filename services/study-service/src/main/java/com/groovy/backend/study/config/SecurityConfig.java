package com.groovy.backend.study.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.groovy.backend.common.auth.JwtAuthenticationEntryPoint;
import com.groovy.backend.security.JwtAuthenticationFilter;
import com.groovy.backend.security.TokenProvider;

import lombok.RequiredArgsConstructor;

/**
 * MSA 전환(study-service 추출): groovy(레거시) SecurityConfig의 스터디 관련 permitAll 규칙을
 * 그대로 옮겨온다 — 스터디 목록/상세 조회는 비로그인도 가능하지만 태그 매칭 조회("/match")는
 * 로그인 필수(groovy/.../global/config/SecurityConfig.java 참고).
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

	private static final String[] PERMIT_ALL_PATTERNS = {
		"/actuator/health",
		"/actuator/prometheus"
	};

	private static final String[] PERMIT_ALL_GET_PATTERNS = {
		"/api/studies",
		"/api/studies/{studyId}",
		"/api/studies/summary"
	};

	private final TokenProvider tokenProvider;
	private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.csrf(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(PERMIT_ALL_PATTERNS).permitAll()
				// "/api/studies/{studyId}"가 단일 세그먼트 와일드카드라 "/api/studies/match"도
				// 삼켜버리므로, 태그 매칭 조회(로그인 필수)는 permitAll 패턴보다 먼저 명시한다.
				.requestMatchers(HttpMethod.GET, "/api/studies/match").authenticated()
				.requestMatchers(HttpMethod.GET, PERMIT_ALL_GET_PATTERNS).permitAll()
				.anyRequest().authenticated())
			.exceptionHandling(handler -> handler.authenticationEntryPoint(jwtAuthenticationEntryPoint))
			// groovy와 동일한 이유: 비로그인 조회에서 @AuthenticationPrincipal이 null이어야
			// "미로그인"과 "로그인"을 구분할 수 있다(익명 필터가 채우는 기본 principal 대신).
			.anonymous(AbstractHttpConfigurer::disable)
			.addFilterBefore(new JwtAuthenticationFilter(tokenProvider), UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}
}
