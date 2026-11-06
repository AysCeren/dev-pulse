package com.devpulse.controller;

import com.devpulse.config.GitHubAppProperties;
import com.devpulse.dto.github.InstallUrlResponse;
import com.devpulse.security.AuthenticatedUser;
import com.devpulse.service.GitHubOnboardingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/github")
@RequiredArgsConstructor
public class GitHubInstallationController {

	private static final String SETUP_ACTION_REQUEST = "request";

	private final GitHubOnboardingService onboardingService;
	private final GitHubAppProperties properties;

	/**
	 * Returns the URL rather than redirecting, because a browser navigation can't send the Authorization header.
	 */
	@GetMapping("/install-url")
	public InstallUrlResponse installUrl(@AuthenticationPrincipal AuthenticatedUser user) {
		return new InstallUrlResponse(onboardingService.createInstallUrl(user.id()));
	}

	@GetMapping("/callback")
	public ResponseEntity<?> callback(
			@RequestParam(name = "installation_id", required = false) Long installationId,
			@RequestParam(name = "setup_action", required = false) String setupAction,
			@RequestParam(required = false) String state,
			@RequestParam(required = false) String error) {

		if (error != null) {
			return redirectToOnboarding("cancelled");
		}
		// A non-admin's request awaits approval on GitHub, so there's no installation yet.
		if (SETUP_ACTION_REQUEST.equals(setupAction)) {
			return redirectToOnboarding("pending");
		}
		if (installationId == null) {
			return ResponseEntity.badRequest()
				.body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "installation_id is required"));
		}

		onboardingService.completeInstallation(installationId, state);
		return redirectToOnboarding("success");
	}

	private ResponseEntity<Void> redirectToOnboarding(String result) {
		String location = UriComponentsBuilder.fromUriString(properties.onboardingUrl())
			.queryParam("installation", result)
			.build()
			.toUriString();
		return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, location).build();
	}

}
