package com.groovy.backend.study.client;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.groovy.backend.client.ResilientCallExecutor;
import com.groovy.backend.common.response.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;

/**
 * MSA 전환(Tag 소유권 확정): 태그 매칭 조회("/api/studies/match")에서 tagIds가 안 주어지면
 * 로그인 유저의 선호 태그(UserTag)로 대체한다. UserTag는 identity-service가 소유한다(Tag 소유권
 * 확정, docs/Groovy_MSA_도메인경계_재검토.md 참고). 원 요청의 Authorization 헤더를 그대로 전달해
 * identity-service의 공개 API(GET /api/tags/me)를 그대로 재사용한다 — 이 용도만을 위한 내부 API를
 * 새로 만들지 않는다. leaderName/applicantName 조회에 쓰는 UserServiceClient와 동일한
 * identity-service.* 설정(application.yml)을 공유한다.
 *
 * identity-service가 다운이 아니라 느려지기만 해도 매 요청이 read-timeout만큼 블로킹되는 걸
 * 막기 위해 ResilientCallExecutor(CircuitBreaker+Retry)로 호출을 감싼다(8번 항목).
 */
@Component
public class TagPreferenceClient {

	private final RestClient restClient;
	private final ResilientCallExecutor executor;

	public TagPreferenceClient(
		@Value("${identity-service.url:http://identity-service:8081}") String identityServiceUrl,
		@Value("${identity-service.connect-timeout-ms:2000}") long connectTimeoutMs,
		@Value("${identity-service.read-timeout-ms:3000}") long readTimeoutMs
	) {
		var httpClient = java.net.http.HttpClient.newBuilder()
			.connectTimeout(Duration.ofMillis(connectTimeoutMs))
			.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

		this.restClient = RestClient.builder()
			.baseUrl(identityServiceUrl)
			.requestFactory(requestFactory)
			.build();
		this.executor = new ResilientCallExecutor("tag-preference-client");
	}

	public List<Long> getMyPreferredTagIds() {
		String authorizationHeader = currentAuthorizationHeader();
		if (authorizationHeader == null) {
			return List.of();
		}

		return executor.execute(
			() -> {
				ApiResponse<List<TagIdOnly>> response = restClient.get()
					.uri("/api/tags/me")
					.header(HttpHeaders.AUTHORIZATION, authorizationHeader)
					.retrieve()
					.body(new ParameterizedTypeReference<ApiResponse<List<TagIdOnly>>>() {
					});
				if (response == null || response.data() == null) {
					return List.<Long>of();
				}
				return response.data().stream().map(TagIdOnly::id).toList();
			},
			List::of);
	}

	private String currentAuthorizationHeader() {
		if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
			return null;
		}
		HttpServletRequest request = attributes.getRequest();
		return request.getHeader(HttpHeaders.AUTHORIZATION);
	}

	// identity-service TagResponse(id, name, category) 중 id만 읽는다 — 나머지 필드는 무시한다.
	@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
	private record TagIdOnly(Long id) {
	}
}
