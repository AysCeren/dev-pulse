package com.devpulse.repository;

import com.devpulse.domain.entity.Issue;
import com.devpulse.domain.entity.IssueSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IssueRepository extends JpaRepository<Issue, UUID> {

	boolean existsBySourceAndExternalId(IssueSource source, Long externalId);

	// The inverse side of a @OneToOne can't stay lazy, so fetching triage up front avoids one extra query per issue.
	@Query("select i from Issue i left join fetch i.triageMetadata where i.id = :id")
	Optional<Issue> findWithTriageById(UUID id);

	@Query("select i from Issue i left join fetch i.triageMetadata where i.installation.id = :installationId order by i.createdAt desc")
	List<Issue> findAllWithTriageByInstallationId(UUID installationId);

}
