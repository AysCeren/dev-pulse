package com.devpulse.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
			// No cookies or sessions are used, so there is nothing for CSRF to exploit.
			.csrf(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(requests -> requests
				// Webhooks authenticate with their HMAC signature in the controller.
				.requestMatchers(HttpMethod.POST, "/api/v1/webhooks/github").permitAll()
				.requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
				.anyRequest().denyAll())
			.build();
	}

}
