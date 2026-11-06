package com.devpulse.service;

import com.devpulse.dto.github.GitHubAppInstallationDto;
import com.devpulse.exception.GitHubApiException;
import com.devpulse.exception.InstallationNotFoundException;
import com.devpulse.security.GitHubAppTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
@RequiredArgsConstructor
public class GitHubAppClient {

	private final RestClient gitHubRestClient;
	private final GitHubAppTokenProvider tokenProvider;

	public GitHubAppInstallationDto getInstallation(long installationId) {
		GitHubAppInstallationDto installation = fetch(installationId);
		if (installation == null || installation.id() == null || installation.account() == null
				|| installation.account().login() == null || installation.account().type() == null
				|| installation.repositorySelection() == null) {
			throw new GitHubApiException();
		}
		return installation;
	}

	private GitHubAppInstallationDto fetch(long installationId) {
		try {
			return gitHubRestClient.get()
				.uri("/app/installations/{id}", installationId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.createAppToken())
				.retrieve()
				.body(GitHubAppInstallationDto.class);
		}
		catch (HttpClientErrorException.NotFound ex) {
			throw new InstallationNotFoundException();
		}
		catch (RestClientException ex) {
			throw new GitHubApiException();
		}
	}

}
