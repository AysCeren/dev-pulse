package com.devpulse.repository;

import com.devpulse.domain.entity.GitHubInstallation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GitHubInstallationRepository extends JpaRepository<GitHubInstallation, UUID> {

	Optional<GitHubInstallation> findByInstallationId(Long installationId);

}
