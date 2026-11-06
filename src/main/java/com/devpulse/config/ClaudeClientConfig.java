package com.devpulse.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration(proxyBeanMethods = false)
public class ClaudeClientConfig {

	private static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

	@Bean
	public RestClient claudeRestClient(AnthropicProperties properties) {
		// The JDK client keeps a connection pool, unlike SimpleClientHttpRequestFactory.
		HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(properties.connectTimeout())
			.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.readTimeout());

		return RestClient.builder()
			.baseUrl(properties.url())
			.requestFactory(requestFactory)
			.defaultHeader("x-api-key", properties.key())
			.defaultHeader("anthropic-version", properties.version())
			.defaultHeader("anthropic-beta", FALLBACK_BETA)
			.defaultHeader("content-type", MediaType.APPLICATION_JSON_VALUE)
			.build();
	}

}
