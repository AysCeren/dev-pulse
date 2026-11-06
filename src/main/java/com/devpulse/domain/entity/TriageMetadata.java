package com.devpulse.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "triage_metadata")
@Getter
@Setter
@NoArgsConstructor
public class TriageMetadata {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(updatable = false, nullable = false)
	private UUID id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "issue_id", nullable = false, unique = true)
	private Issue issue;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50)
	private IssueSeverity severity;

	@Enumerated(EnumType.STRING)
	@Column(name = "issue_type", nullable = false, length = 50)
	private IssueType issueType;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String summary;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "suggested_labels", columnDefinition = "jsonb", nullable = false)
	private List<String> suggestedLabels = new ArrayList<>();

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "raw_ai_response", columnDefinition = "jsonb", nullable = false)
	private String rawAiResponse;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		TriageMetadata that = (TriageMetadata) o;
		return id != null && Objects.equals(id, that.id);
	}

	@Override
	public int hashCode() {
		return getClass().hashCode();
	}

}
