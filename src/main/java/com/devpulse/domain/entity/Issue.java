package com.devpulse.domain.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "issues")
@Getter
@Setter
@NoArgsConstructor
public class Issue {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(updatable = false, nullable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "installation_id", nullable = false)
	private GitHubInstallation installation;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50)
	private IssueSource source;

	@Column(name = "external_id")
	private Long externalId;

	@Column(name = "repository_name", nullable = false)
	private String repositoryName;

	@Column(name = "repository_url", nullable = false, length = 500)
	private String repositoryUrl;

	@Column(name = "reporter_username", nullable = false)
	private String reporterUsername;

	@Column(name = "reporter_avatar_url", length = 500)
	private String reporterAvatarUrl;

	@Column(nullable = false, length = 500)
	private String title;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50)
	private IssueStatus status = IssueStatus.NEW;

	@OneToOne(mappedBy = "issue", cascade = CascadeType.ALL, fetch = FetchType.LAZY, optional = true)
	private TriageMetadata triageMetadata;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public void setTriageMetadata(TriageMetadata triageMetadata) {
		this.triageMetadata = triageMetadata;
		if (triageMetadata != null) {
			triageMetadata.setIssue(this);
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Issue issue = (Issue) o;
		return id != null && Objects.equals(id, issue.id);
	}

	@Override
	public int hashCode() {
		return getClass().hashCode();
	}

}
