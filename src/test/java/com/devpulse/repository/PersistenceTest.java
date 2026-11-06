package com.devpulse.repository;

import com.devpulse.domain.entity.GitHubInstallation;
import com.devpulse.domain.entity.Issue;
import com.devpulse.domain.entity.IssueSeverity;
import com.devpulse.domain.entity.IssueSource;
import com.devpulse.domain.entity.IssueStatus;
import com.devpulse.domain.entity.IssueType;
import com.devpulse.domain.entity.TriageMetadata;
import com.devpulse.domain.entity.User;
import com.devpulse.support.TestcontainersConfiguration;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class PersistenceTest {

	private static final AtomicLong SEQUENCE = new AtomicLong(1);

	@Autowired
	private TestEntityManager entityManager;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private GitHubInstallationRepository installationRepository;

	@Autowired
	private IssueRepository issueRepository;

	@Autowired
	private TriageMetadataRepository triageRepository;

	@Test
	void savingIssueCascadesToTriageAndPopulatesGeneratedFields() {
		Issue issue = triagedIssue(installation());

		issueRepository.saveAndFlush(issue);

		assertThat(issue.getId()).isNotNull();
		assertThat(issue.getCreatedAt()).isNotNull();
		assertThat(issue.getUpdatedAt()).isNotNull();
		assertThat(issue.getTriageMetadata().getId()).isNotNull();
		assertThat(triageRepository.count()).isEqualTo(1);
	}

	@Test
	void jsonbColumnsRoundTrip() {
		Issue issue = issueRepository.saveAndFlush(triagedIssue(installation()));
		entityManager.clear();

		TriageMetadata loaded = issueRepository.findWithTriageById(issue.getId()).orElseThrow().getTriageMetadata();

		assertThat(loaded.getSuggestedLabels()).containsExactly("bug", "api");
		assertThat(loaded.getRawAiResponse()).contains("\"severity\"");
		assertThat(loaded.getIssueType()).isEqualTo(IssueType.BUG);
	}

	@Test
	void duplicateSourceAndExternalIdIsRejected() {
		GitHubInstallation installation = installation();
		Issue first = issue(installation);
		issueRepository.saveAndFlush(first);

		Issue duplicate = issue(installation);
		duplicate.setExternalId(first.getExternalId());

		assertThatThrownBy(() -> issueRepository.saveAndFlush(duplicate))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void deletingUserCascadesThroughInstallationIssueAndTriage() {
		GitHubInstallation installation = installation();
		issueRepository.saveAndFlush(triagedIssue(installation));
		User user = installation.getUser();
		entityManager.clear();

		// Goes through SQL so the test proves the database's ON DELETE CASCADE, not Hibernate's.
		entityManager.getEntityManager().createNativeQuery("delete from users where id = :id")
			.setParameter("id", user.getId())
			.executeUpdate();

		assertThat(installationRepository.count()).isZero();
		assertThat(issueRepository.count()).isZero();
		assertThat(triageRepository.count()).isZero();
	}

	@Test
	void fetchFinderLoadsAllIssuesAndTriageInOneStatement() {
		GitHubInstallation installation = installation();
		for (int i = 0; i < 5; i++) {
			issueRepository.save(triagedIssue(installation));
		}
		issueRepository.flush();
		entityManager.clear();

		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();

		List<Issue> issues = issueRepository.findAllWithTriageByInstallationId(installation.getId());
		issues.forEach(issue -> assertThat(issue.getTriageMetadata().getSummary()).isNotBlank());

		assertThat(issues).hasSize(5);
		assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
	}

	private GitHubInstallation installation() {
		long sequence = SEQUENCE.getAndIncrement();

		User user = new User();
		user.setEmail("user" + sequence + "@example.com");
		user.setPasswordHash("hash");
		user.setFirstName("Test");
		user.setLastName("User");
		userRepository.save(user);

		GitHubInstallation installation = new GitHubInstallation();
		installation.setUser(user);
		installation.setInstallationId(sequence);
		installation.setAccountLogin("octocat");
		installation.setAccountType("User");
		installation.setRepositorySelection("all");
		return installationRepository.saveAndFlush(installation);
	}

	private Issue issue(GitHubInstallation installation) {
		Issue issue = new Issue();
		issue.setInstallation(installation);
		issue.setSource(IssueSource.GITHUB_WEBHOOK);
		issue.setExternalId(SEQUENCE.getAndIncrement());
		issue.setRepositoryName("octocat/hello-world");
		issue.setRepositoryUrl("https://github.com/octocat/hello-world");
		issue.setReporterUsername("octocat");
		issue.setTitle("Something broke");
		return issue;
	}

	private Issue triagedIssue(GitHubInstallation installation) {
		TriageMetadata triage = new TriageMetadata();
		triage.setSeverity(IssueSeverity.HIGH);
		triage.setIssueType(IssueType.BUG);
		triage.setSummary("A summary.");
		triage.setSuggestedLabels(List.of("bug", "api"));
		triage.setRawAiResponse("{\"severity\":\"HIGH\"}");

		Issue issue = issue(installation);
		issue.setStatus(IssueStatus.TRIAGED);
		issue.setTriageMetadata(triage);
		return issue;
	}

}
