package com.devpulse.service;

import com.devpulse.config.GitHubAppProperties;
import com.devpulse.dto.github.GitHubAppInstallationDto;
import com.devpulse.exception.InstallationAlreadyClaimedException;
import com.devpulse.exception.InvalidInstallStateException;
import com.devpulse.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

/**
 * Not transactional, because it calls GitHub before GitHubInstallationService writes to the database.
 */
@Service
@RequiredArgsConstructor
public class GitHubOnboardingService {

	private final JwtService jwtService;
	private final GitHubAppClient gitHubAppClient;
	private final GitHubInstallationService installationService;
	private final GitHubAppProperties properties;

	public String createInstallUrl(UUID userId) {
		return UriComponentsBuilder.fromUriString(properties.installUrl())
			.pathSegment(properties.slug(), "installations", "new")
			.queryParam("state", jwtService.issueInstallState(userId))
			.build()
			.toUriString();
	}

	public void completeInstallation(long installationId, String state) {
		UUID userId = jwtService.parseInstallState(state == null ? "" : state)
			.orElseThrow(InvalidInstallStateException::new);

		GitHubAppInstallationDto details = gitHubAppClient.getInstallation(installationId);

		try {
			installationService.claim(userId, details);
		}
		catch (DataIntegrityViolationException ex) {
			// Two users can claim at the same moment, and the unique constraint stops the second.
			throw new InstallationAlreadyClaimedException();
		}
	}

}
