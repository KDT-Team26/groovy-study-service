package com.groovy.backend.study.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

/**
 * MSA 전환(Tag 소유권 확정): TagPreferenceClient가 이제 legacy-monolith가 아니라
 * identity-service를 호출한다. Provider(identity-service) 쪽 응답 모양은
 * {@code com.groovy.backend.identity.controller.TagController#getMyTags}가 반환하는
 * {@code ApiResponse.of("SUCCESS", ..., List<TagResponse(id,name,category)>)} 그대로다.
 *
 * UserServiceClientContractTest와 동일한 방식(JDK 내장 HttpServer 스텁)으로, TagPreferenceClient가
 * 이 모양에서 tagId 목록만 정확히 뽑아내는지 검증한다.
 */
class TagPreferenceClientContractTest {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private HttpServer stubServer;

	@AfterEach
	void tearDown() {
		if (stubServer != null) {
			stubServer.stop(0);
		}
	}

	@Test
	void identity_service의_tags_me_응답_모양을_흉내낸_스텁에서_선호_태그_id_목록을_읽을_수_있다() throws Exception {
		// identity-service TagController#getMyTags가 반환하는 ApiResponse.of("SUCCESS", ...,
		// List<TagResponse>) — TagResponse는 (id, name, category) 3개 필드를 갖지만
		// TagPreferenceClient는 id만 읽는다.
		String responseJson = objectMapper.writeValueAsString(Map.of(
			"status", "SUCCESS",
			"message", "내 선호 태그 조회에 성공했습니다.",
			"data", List.of(
				Map.of("id", 1, "name", "온라인", "category", "STUDY_MODE"),
				Map.of("id", 2, "name", "자율 운영", "category", "OPERATING_POLICY")
			)
		));
		int port = startStubServer(responseJson, 200);

		TagPreferenceClient client = new TagPreferenceClient("http://localhost:" + port, 2000, 3000);

		List<Long> tagIds = withAuthorizationHeader(() -> client.getMyPreferredTagIds());

		assertThat(tagIds).containsExactlyInAnyOrder(1L, 2L);
	}

	@Test
	void identity_service가_죽어있으면_예외_대신_빈_목록을_반환한다() throws Exception {
		TagPreferenceClient client = new TagPreferenceClient("http://localhost:1", 200, 200);

		List<Long> tagIds = withAuthorizationHeader(() -> client.getMyPreferredTagIds());

		assertThat(tagIds).isEmpty();
	}

	// TagPreferenceClient는 RequestContextHolder에서 원 요청의 Authorization 헤더를 읽는다
	// (원 요청이 없으면 빈 목록을 바로 반환) — 테스트에서는 목 요청 컨텍스트를 잠깐 세팅해준다.
	private List<Long> withAuthorizationHeader(java.util.function.Supplier<List<Long>> call) {
		var request = new org.springframework.mock.web.MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer test-token");
		var attributes = new org.springframework.web.context.request.ServletRequestAttributes(request);
		org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(attributes);
		try {
			return call.get();
		} finally {
			org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
		}
	}

	private int startStubServer(String responseBody, int status) throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.createContext("/api/tags/me", exchange -> {
			byte[] bytes = responseBody.getBytes();
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(status, bytes.length);
			try (var out = exchange.getResponseBody()) {
				out.write(bytes);
			}
		});
		server.start();
		this.stubServer = server;
		TimeUnit.MILLISECONDS.sleep(50);
		return server.getAddress().getPort();
	}
}
