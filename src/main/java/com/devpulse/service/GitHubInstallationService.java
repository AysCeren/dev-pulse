package com.devpulse.service;

import com.devpulse.repository.GitHubInstallationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubInstallationService {

	private final GitHubInstallationRepository installationRepository;

	@Transactional
	public void markUninstalled(Long installationId) {
		installationRepository.findByInstallationId(installationId).ifPresent(installation -> {
			installation.setUninstalledAt(Instant.now());
			log.info("Marked installation {} as uninstalled", installationId);
		});
	}

}
