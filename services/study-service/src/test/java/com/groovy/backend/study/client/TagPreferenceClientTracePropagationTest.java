package com.groovy.backend.study.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration;
import org.springframework.boot.micrometer.tracing.autoconfigure.MicrometerTracingAutoConfiguration;
import org.springframework.boot.opentelemetry.autoconfigure.OpenTelemetrySdkAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientObservationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groovy.backend.study.config.TracingConfig;
import com.sun.net.httpserver.HttpServer;

/**
 * IR-315 / IR-310: study-service -> identity-service 호출(태그 선호도 조회)에 W3C traceparent가
 * 실제로 실리는지 검증한다. TagPreferenceClient가 RestClient.builder() 정적 팩토리 대신 DI로 받은
 * RestClient.Builder를 쓰는지가 이 테스트의 핵심.
 */
class TagPreferenceClientTracePropagationTest {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private HttpServer stubServer;

	@AfterEach
	void tearDown() {
		if (stubServer != null) {
			stubServer.stop(0);
		}
		RequestContextHolder.resetRequestAttributes();
	}

	@Test
	void 나가는_요청에_W3C_traceparent_헤더가_실린다() throws Exception {
		AtomicReference<String> capturedTraceparent = new AtomicReference<>();
		int port = startStubServerCapturingTraceparent(capturedTraceparent);

		var request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer test-token");
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

		new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(
				RestClientAutoConfiguration.class,
				RestClientObservationAutoConfiguration.class,
				ObservationAutoConfiguration.class,
				MicrometerTracingAutoConfiguration.class,
				OpenTelemetrySdkAutoConfiguration.class))
			.withUserConfiguration(TracingConfig.class)
			.withPropertyValues(
				"management.otlp.tracing.endpoint=http://localhost:4318/v1/traces",
				"spring.application.name=study-service")
			.run(context -> {
				RestClient.Builder restClientBuilder = context.getBean(RestClient.Builder.class);
				TagPreferenceClient client = new TagPreferenceClient(restClientBuilder, "http://localhost:" + port, 2000, 3000);
				client.getMyPreferredTagIds();
			});

		String traceparent = capturedTraceparent.get();
		assertThat(traceparent).isNotNull();
		assertThat(traceparent).matches("^00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}$");
	}

	private int startStubServerCapturingTraceparent(AtomicReference<String> traceparentHolder) throws Exception {
		String responseJson = objectMapper.writeValueAsString(Map.of(
			"status", "SUCCESS",
			"message", "내 선호 태그 조회에 성공했습니다.",
			"data", List.of(Map.of("id", 1, "name", "온라인", "category", "STUDY_MODE"))
		));
		HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.createContext("/api/tags/me", exchange -> {
			traceparentHolder.set(exchange.getRequestHeaders().getFirst("traceparent"));
			byte[] bytes = responseJson.getBytes();
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, bytes.length);
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
