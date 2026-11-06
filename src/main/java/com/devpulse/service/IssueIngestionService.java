package com.devpulse.service;

import com.devpulse.domain.entity.GitHubInstallation;
import com.devpulse.domain.entity.Issue;
import com.devpulse.domain.entity.IssueSource;
import com.devpulse.domain.entity.IssueStatus;
import com.devpulse.dto.github.GitHubIssueDto;
import com.devpulse.dto.github.GitHubWebhookPayload;
import com.devpulse.exception.MalformedWebhookException;
import com.devpulse.repository.GitHubInstallationRepository;
import com.devpulse.repository.IssueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class IssueIngestionService {

	private final GitHubInstallationRepository installationRepository;
	private final IssueRepository issueRepository;

	/**
	 * @return the new issue's ID, or empty if the event was dropped or already stored
	 */
	@Transactional
	public Optional<UUID> ingest(GitHubWebhookPayload payload) {
		validate(payload);

		Long installationId = payload.installation().id();
		Optional<GitHubInstallation> installation = installationRepository.findByInstallationId(installationId)
			.filter(GitHubInstallation::isActive);
		if (installation.isEmpty()) {
			log.info("Dropping issue event for unknown or uninstalled installation {}", installationId);
			return Optional.empty();
		}

		GitHubIssueDto gitHubIssue = payload.issue();
		if (issueRepository.existsBySourceAndExternalId(IssueSource.GITHUB_WEBHOOK, gitHubIssue.id())) {
			return Optional.empty();
		}

		Issue issue = new Issue();
		issue.setInstallation(installation.get());
		issue.setSource(IssueSource.GITHUB_WEBHOOK);
		issue.setExternalId(gitHubIssue.id());
		issue.setRepositoryName(payload.repository().fullName());
		issue.setRepositoryUrl(payload.repository().htmlUrl());
		issue.setReporterUsername(gitHubIssue.user().login());
		issue.setReporterAvatarUrl(gitHubIssue.user().avatarUrl());
		issue.setTitle(gitHubIssue.title());
		issue.setDescription(gitHubIssue.body());
		issue.setStatus(IssueStatus.NEW);

		return Optional.of(issueRepository.saveAndFlush(issue).getId());
	}

	private void validate(GitHubWebhookPayload payload) {
		boolean valid = payload.installation() != null && payload.installation().id() != null
			&& payload.repository() != null && payload.repository().fullName() != null && payload.repository().htmlUrl() != null
			&& payload.issue() != null && payload.issue().id() != null && payload.issue().title() != null
			&& payload.issue().user() != null && payload.issue().user().login() != null;
		if (!valid) {
			throw new MalformedWebhookException("Issue event is missing required fields");
		}
	}

}
