package com.devpulse.service;

import com.devpulse.config.GitHubAppProperties;
import com.devpulse.domain.entity.GitHubInstallation;
import com.devpulse.domain.entity.User;
import com.devpulse.dto.github.GitHubAppInstallationDto;
import com.devpulse.exception.InstallationAlreadyClaimedException;
import com.devpulse.exception.InstallationClaimExpiredException;
import com.devpulse.exception.InvalidInstallStateException;
import com.devpulse.repository.GitHubInstallationRepository;
import com.devpulse.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubInstallationService {

	private final GitHubInstallationRepository installationRepository;
	private final UserRepository userRepository;
	private final GitHubAppProperties properties;

	/**
	 * Links an installation that GitHub has confirmed exists to a DevPulse user.
	 */
	@Transactional
	public void claim(UUID userId, GitHubAppInstallationDto details) {
		GitHubInstallation installation = installationRepository.findByInstallationId(details.id())
			.map(existing -> requireOwnedBy(existing, userId))
			.orElseGet(() -> newClaim(userId, details));

		installation.setAccountLogin(details.account().login());
		installation.setAccountType(details.account().type());
		installation.setRepositorySelection(details.repositorySelection());
		installation.setUninstalledAt(null);
		installationRepository.saveAndFlush(installation);
		log.info("Linked installation {} to user {}", details.id(), userId);
	}

	@Transactional
	public void markUninstalled(Long installationId) {
		installationRepository.findByInstallationId(installationId).ifPresent(installation -> {
			installation.setUninstalledAt(Instant.now());
			log.info("Marked installation {} as uninstalled", installationId);
		});
	}

	private GitHubInstallation requireOwnedBy(GitHubInstallation installation, UUID userId) {
		if (!installation.getUser().getId().equals(userId)) {
			throw new InstallationAlreadyClaimedException();
		}
		return installation;
	}

	private GitHubInstallation newClaim(UUID userId, GitHubAppInstallationDto details) {
		// The callback's installation_id can be edited by the user. Only accepting recent installations
		// stops someone claiming an older one that was never linked. See the discussion notes.
		if (details.createdAt() == null || details.createdAt().isBefore(Instant.now().minus(properties.claimWindow()))) {
			throw new InstallationClaimExpiredException();
		}
		User user = userRepository.findById(userId).orElseThrow(InvalidInstallStateException::new);

		GitHubInstallation installation = new GitHubInstallation();
		installation.setUser(user);
		installation.setInstallationId(details.id());
		return installation;
	}

}
