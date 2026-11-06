# **DevPulse: Architecture & Technical Specification**

Production Architecture & Technical Implementation Reference

# **Project Overview & Mission**

DevPulse is an enterprise-grade, event-driven intelligent triage engine designed to automate incoming developer issue streams. By interfacing directly with GitHub's webhook ecosystem, DevPulse intercepts issue-lifecycle events, executes asynchronous natural language classification and severity enrichment using the Anthropic Claude API, and makes categorized issue data queryable in real-time.

```
 GitHub Event     HMAC-SHA256          Raw Event Persisted
┌────────────┐   ─────────────► ┌────────────────────────────────┐
│ GitHub App │   (202 Accepted) │  DevPulse Ingestion Controller │
└────────────┘                  └────────────────┬───────────────┘
                                                 │ Async Dispatch
                                                 ▼
┌──────────────────┐  Enriched Schema   ┌────────────────────────────────┐
│ PostgreSQL 16 DB │ ◄───────────────── │ Async Triage Engine (Claude)   │
└──────────────────┘                    └────────────────┬───────────────┘
                                                         │ Invalidate
                                                         ▼
                                        ┌────────────────────────────────┐
                                        │ Redis Distributed Cache Layer  │
                                        └────────────────────────────────┘
```

The system is engineered around four primary operational guarantees:

* Sub-second HTTP acknowledgment: Webhook events acknowledge GitHub delivery within 202 Accepted semantics well within GitHub's strict 10-second drop boundary.  
* Zero-trust security: Incoming webhooks validate against cryptographic HMAC-SHA256 digests; all internal REST boundaries demand JWT assertion.  
* Resilient data enrichment: AI interactions are decoupled asynchronously through bounded worker thread pools, backed by structured JSON schema enforcement and database transaction safety.  
* High-performance querying: High-frequency issue endpoints leverage Redis for distributed sub-millisecond retrieval with write-through cache invalidation.

# **Issue \#1: Build Tool & Dependency Management**

DevPulse is standardized on Gradle 8.x utilizing the Kotlin DSL (`build.gradle.kts`). The choice of Gradle over legacy imperative configuration scripts or declarative XML is driven by strict enterprise requirements:

* Incremental compilation and remote build caching minimize CI/CD turnaround times across multi-module development environments.  
* Statically typed Kotlin DSL (`build.gradle.kts`) ensures IDE auto-completion, compile-time build configuration safety, and centralized dependency governance via Gradle Version Catalogs (`libs.versions.toml`).  
* Spring Boot's layered JAR capabilities are directly integrated into Gradle's task graph, optimizing container image generation for multi-stage Docker builds:

```
# Multi-stage extraction leveraging Spring Boot Layered JARs
FROM eclipse-temurin:21-jre-alpine AS builder
WORKDIR /application
ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} application.jar
RUN java -Djarmode=layertools -jar application.jar extract

FROM eclipse-temurin:21-jre-alpine
WORKDIR /application
COPY --from=builder /application/dependencies/ ./
COPY --from=builder /application/spring-boot-loader/ ./
COPY --from=builder /application/snapshot-dependencies/ ./
COPY --from=builder /application/application/ ./
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
```

# **Issue \#2: Core Dependency Baseline**

The technical foundation targets modern cloud environments, leveraging Java 21 LTS runtime enhancements (such as pattern matching, records, and virtual threads) and Spring Boot 3.3+.

| Component | Technology | Selection Rationale |
| :---- | :---- | :---- |
| Runtime Environment | Java 21 LTS (Eclipse Temurin) | Long-term support, performance improvements, record patterns, and Virtual Thread runtime availability. |
| Core Framework | Spring Boot 3.3+ | Native AOT support, Jakarta EE 10 baseline, unified observability via Micrometer. |
| Ingress / Web Layer | Spring MVC (`spring-boot-starter-web`) | Established enterprise HTTP handling, robust validation hooks, seamless Jackson JSON binding. |
| Validation | Jakarta Bean Validation (`spring-boot-starter-validation`) | Declarative schema validation on incoming request boundaries (`@Valid`, `@NotNull`). |
| Security & Identity | Spring Security 6 & JJWT (`0.12.5`) | Stateless token lifecycle, secure filter chains, RFC-7519 compliant cryptographic JWT signing. |
| Persistence & Data | Spring Data JPA & Hibernate 6 | Automated repository abstraction, dirty checking, dynamic statement batching. |
| Database Engine | PostgreSQL 16 Driver (`org.postgresql:postgresql`) | Mature JSONB column storage, robust indexing, high-concurrency ACID transactions. |
| Cache & Session | Spring Data Redis | High-speed distributed caching, ephemeral session state, cache-aside annotations (`@Cacheable`). |
| Database Migration | Flyway Core & Flyway Database PostgreSQL | Deterministic version-controlled schema evolution; eliminates runtime schema divergence. |
| HTTP Client | Spring 6 `RestClient` | Modern fluent blocking HTTP abstraction with minimal overhead compared to legacy `RestTemplate`. |
| Infrastructure Testing | Testcontainers PostgreSQL / Redis | Reproducible integration suites validating code against production-identical Docker images. |
| Developer Ergonomics | Project Lombok | Eliminates boilerplate code across JPA domain entities while maintaining strict encapsulation. |
| Production Telemetry | Spring Boot Actuator | Exposes health, readiness, liveness, and Prometheus metrics out of the box. |

# **Issue \#3: PostgreSQL Database Schema & Domain Entity Design**

## **Relational Schema (Flyway V1\_\_initial\_schema.sql)**

The schema utilizes PostgreSQL-native UUIDs for all primary keys to eliminate enumeration attacks and optimize distributed insertion without collision.

```
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'ROLE_DEVELOPER',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE github_installations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    installation_id BIGINT NOT NULL UNIQUE,
    account_login VARCHAR(255) NOT NULL,
    account_type VARCHAR(50) NOT NULL,
    repository_selection VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE issues (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source VARCHAR(50) NOT NULL,
    external_id BIGINT,
    repository_name VARCHAR(255) NOT NULL,
    repository_url VARCHAR(500) NOT NULL,
    reporter_username VARCHAR(255) NOT NULL,
    reporter_avatar_url VARCHAR(500),
    title VARCHAR(500) NOT NULL,
    description TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'NEW',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE triage_metadata (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    issue_id UUID NOT NULL UNIQUE REFERENCES issues(id) ON DELETE CASCADE,
    severity VARCHAR(50) NOT NULL,
    issue_type VARCHAR(50) NOT NULL,
    summary TEXT NOT NULL,
    suggested_labels JSONB NOT NULL DEFAULT '[]'::jsonb,
    raw_ai_response JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_issues_status ON issues(status);
CREATE INDEX idx_issues_external_id ON issues(external_id);
CREATE INDEX idx_triage_metadata_severity ON triage_metadata(severity);
CREATE INDEX idx_github_installations_user ON github_installations(user_id);
```

## **Domain Entities**

Domain entities implement strict equals and hashCode contracts anchored to their immutable UUID business identifiers, avoiding collection issues within Hibernate persistence contexts.

### **Issue Entity**

```java
package com.devpulse.domain.entity;

import jakarta.persistence.*;
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
```

### **TriageMetadata Entity**

a  
package com.devpulse.domain.entity;

import jakarta.persistence.\*;  
import lombok.Getter;  
import lombok.NoArgsConstructor;  
import lombok.Setter;  
import org.hibernate.annotations.CreationTimestamp;  
import org.hibernate.annotations.JdbcTypeCode;  
import org.hibernate.type.SqlTypes;

import java.time.Instant;  
import java.util.Objects;  
import java.util.UUID;

@Entity  
@Table(name \= "triage\_metadata")  
@Getter  
@Setter  
@NoArgsConstructor  
public class TriageMetadata {

```
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

@Column(name = "issue_type", nullable = false, length = 50)
private String issueType;

@Column(nullable = false, columnDefinition = "TEXT")
private String summary;

@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "suggested_labels", columnDefinition = "jsonb")
private String suggestedLabels;

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
```

}

````

## Issue #4: Webhook Ingestion, DTOs & Cryptographic Verification

DevPulse ingests webhooks through a cryptographically guarded gateway controller. Requests must validate the `X-Hub-Signature-256` header, which provides an HMAC-SHA256 signature calculated over the verbatim raw payload body using the shared webhook secret.

### Webhook DTO Contracts (Immutable Java Records)

```java
package com.devpulse.dto.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubWebhookPayload(
    String action,
    GitHubIssueDto issue,
    GitHubRepositoryDto repository,
    GitHubSenderDto sender,
    GitHubInstallationDto installation
) {}

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubIssueDto(
    Long id,
    Integer number,
    String title,
    String body,
    String state,
    @JsonProperty("html_url") String htmlUrl,
    GitHubSenderDto user
) {}

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubRepositoryDto(
    Long id,
    String name,
    @JsonProperty("full_name") String fullName,
    @JsonProperty("html_url") String htmlUrl
) {}

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubSenderDto(
    Long id,
    String login,
    @JsonProperty("avatar_url") String avatarUrl
) {}

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubInstallationDto(
    Long id
) {}
````

## **Cryptographic Signature Verification Service**

To mitigate timing attacks that exploit string-comparison latency variations, DevPulse uses `MessageDigest.isEqual` over constant-time byte comparisons.

```java
package com.devpulse.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class GitHubSignatureVerifier {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String PREFIX = "sha256=";

    @Value("${github.webhook.secret}")
    private String webhookSecret;

    public boolean isValidSignature(String payload, String signatureHeader) {
        if (signatureHeader == null || !signatureHeader.startsWith(PREFIX) || payload == null) {
            return false;
        }

        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKey = new SecretKeySpec(
                webhookSecret.getBytes(StandardCharsets.UTF_8), 
                HMAC_SHA256
            );
            mac.init(secretKey);
            
            byte[] computedHash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = PREFIX + HexFormat.of().formatHex(computedHash);

            return MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                signatureHeader.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            return false;
        }
    }
}
```

## **Webhook Ingress Controller**

The controller consumes the raw JSON `String` payload directly to guarantee byte-for-byte fidelity with the remote HMAC computation.

```java
package com.devpulse.controller;

import com.devpulse.dto.github.GitHubWebhookPayload;
import com.devpulse.security.GitHubSignatureVerifier;
import com.devpulse.service.WebhookProcessingOrchestrator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks/github")
@RequiredArgsConstructor
public class GitHubWebhookController {

    private final GitHubSignatureVerifier signatureVerifier;
    private final WebhookProcessingOrchestrator orchestrator;
    private final ObjectMapper objectMapper;

    @PostMapping
    public ResponseEntity<Void> handleWebhook(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(value = "X-GitHub-Event", required = false) String eventType,
            @RequestBody String rawPayload) {

        if (!signatureVerifier.isValidSignature(rawPayload, signature)) {
            log.warn("Invalid GitHub webhook signature received.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!"issues".equals(eventType)) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).build();
        }

        try {
            GitHubWebhookPayload payload = objectMapper.readValue(rawPayload, GitHubWebhookPayload.class);
            if (!"opened".equals(payload.action())) {
                return ResponseEntity.status(HttpStatus.ACCEPTED).build();
            }

            orchestrator.dispatchAsyncTriage(payload);
            return ResponseEntity.status(HttpStatus.ACCEPTED).build();
        } catch (Exception e) {
            log.error("Failed to parse GitHub webhook payload", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
}
```

# **Issue \#4.1: GitHub App Integration (Zero-Config Installation)**

DevPulse uses the GitHub App model to simplify authorization, replace high-privilege personal access tokens, and enforce repository-level isolation across tenants.

```
┌──────────────┐         1. Redirect to Install          ┌──────────────┐
│ DevPulse Web │ ──────────────────────────────────────► │  GitHub App  │
└──────┬───────┘                                         └──────┬───────┘
       │                                                        │
       │           2. Complete Flow & Callback Redirect         │
       │ ◄──────────────────────────────────────────────────────┘
       ▼
┌────────────────────────────────┐
│ /api/v1/github/callback        │
│ (?installation_id=...&code=..) │
└──────┬─────────────────────────┘
       │
       │ 3. Query App Installation Details (Octokit/REST)
       ▼
┌────────────────────────────────┐       4. Persist Tenant Mapping
│ DevPulse Application Core      │ ──────────────────────────────────────►  [PostgreSQL]
└────────────────────────────────┘                                    github_installations
```

## **Installation Flow Lifecycle**

1. App Discovery & Authorization: Authenticated users click **Install DevPulse**, which initiates a browser redirect to GitHub's authorization endpoint:  
   `https://github.com/apps/{app-slug}/installations/new`  
2. Redirect Callback: After repository delegation, GitHub redirects the browser back to:  
   `/api/v1/github/callback?installation_id={id}&setup_action=install`  
3. Identity Association: DevPulse's callback endpoint intercepts the parameters, extracts the caller's JWT identity context, resolves account-level metadata via GitHub's App API, and writes an entry into `github_installations`.

## **Multi-Tenancy Isolation Model**

* Repository boundaries map to unique `installation_id` foreign keys.  
* When incoming webhooks arrive, payloads without a matching `github_installations` record are dropped immediately, isolating operations to authorized tenants.

# **Issue \#4.2: Authentication, Authorization & Identity Architecture**

DevPulse enforces a decoupled, multi-layered identity model that separates application-level user authentication from GitHub platform integration boundaries.

## **Distinct Authentication Layers & Native JWT Rationale**

The platform cleanly segregates Application-Level Authentication from Integration-Level Authorization:

* **Application-Level Authentication (Native JWT Auth):** Internal users register and authenticate directly against DevPulse using their email and a password salted and hashed via BCrypt. DevPulse issues RFC-7519 compliant JSON Web Tokens carrying role-based claims (`ROLE_DEVELOPER` and `ROLE_ADMIN`). Stateless security filters validate these tokens on every protected REST boundary.  
* **Integration-Level Authorization (GitHub App Model):** Interaction with GitHub repositories does not rely on user identity delegation; instead, it is mediated exclusively through GitHub App installation grants.

Native JWT authentication was purposefully selected over pure third-party OAuth delegation to satisfy key architectural goals: reinforcing fundamental identity and credential-hardening practices, decoupling organizational roles from individual VCS profiles, and maintaining sovereign multi-tenant access controls independent of GitHub platform availability.

## **Server-to-Server vs. User-to-Server Dynamics**

DevPulse operates strictly via server-to-server webhook ingestion and installation tokens, rather than acting on behalf of individual users:

* **Zero User OAuth Tokens:** Users never grant personal user-to-server OAuth tokens. This removes the risk of leaked personal access scopes and prevents pipeline interruptions caused by credential churn or developer offboarding.  
* **Server-to-Server Installation Dynamics:** Webhook deliveries are verified through cryptographic shared secrets (HMAC-SHA256) at the application boundary, and any outbound operations use short-lived GitHub App installation access tokens scoped strictly to authorized repositories.

## **GitHub Installation & Callback Details**

The installation flow integrates with GitHub's native permission and authorization engine:

4. **Installation Dispatch:** Authenticated DevPulse users initiate the setup by being redirected to `https://github.com/apps/{app-slug}/installations/new`.  
5. **GitHub Authentication Interception:** If the browser session is unauthenticated, GitHub prompts the user to sign in prior to surfacing the installation dialog.  
6. **Organization Permission Enforcement:** GitHub strictly gates installation based on repository and organization permissions. Organization owners can execute the installation immediately, whereas non-admin members are routed to GitHub's "Request" workflow pending administrator approval.  
7. **Callback Reconciliation:** Upon successful authorization, GitHub redirects to `GET /api/v1/github/callback?installation_id={id}&setup_action=install`. The controller resolves the active caller's JWT context from the request session and associates the unique `installation_id` with the authenticated DevPulse `User` entity in the database.

## **Account Mapping & Identity Linkage**

A DevPulse user's registration email does **not** need to match their GitHub email address. Identity linkage is established exclusively through the relational foreign key pairing (`user_id` ◄──► `installation_id`) persisted in `github_installations`. This allows developers to use distinct corporate emails for internal tooling while binding personal or shared GitHub accounts.

## **Edge Cases & Fail-safes**

The ingestion and callback services handle lifecycle anomalies and edge conditions resiliently:

* **Cancelled Installations:** If a user aborts GitHub authorization, the callback is either never invoked or returns error query parameters. The gateway rejects incomplete requests and redirects the user back to the application onboarding view without creating orphaned state.  
* **Missing Installation Parameters:** Callback requests missing the `installation_id` parameter trigger an immediate `400 Bad Request` response, preventing partial tenant registration.  
* **Uninstallation Lifecycle Webhooks:** When an admin removes an app via GitHub, an `installation.deleted` webhook event is dispatched. DevPulse intercepts this event, cascades the deletion or deactivates the corresponding `github_installations` record, and invalidates cached tenant associations.

# **Issue \#5: Claude API Integration & Asynchronous Triage Engine**

## **Contract Definitions (Claude DTOs)**

```java
package com.devpulse.dto.claude;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ClaudeApiRequest(
    String model,
    @JsonProperty("max_tokens") int maxTokens,
    String system,
    List<ClaudeMessage> messages
) {}

public record ClaudeMessage(String role, String content) {}

public record ClaudeApiResponse(
    String id,
    String type,
    String role,
    List<ContentBlock> content
) {
    public record ContentBlock(String type, String text) {}
}

public record IssueTriageResult(
    String severity,
    @JsonProperty("issue_type") String issueType,
    String summary,
    @JsonProperty("suggested_labels") List<String> suggestedLabels
) {}
```

## **RestClient Integration Configuration**

The outbound HTTP connection pools and read timeouts are configured explicitly to protect downstream resources.

```java
package com.devpulse.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class ClaudeClientConfig {

    @Value("${anthropic.api.url:https://api.anthropic.com}")
    private String apiUrl;

    @Value("${anthropic.api.key}")
    private String apiKey;

    @Bean
    public RestClient claudeRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(15_000);

        return RestClient.builder()
            .baseUrl(apiUrl)
            .requestFactory(factory)
            .defaultHeader("x-api-key", apiKey)
            .defaultHeader("anthropic-version", "2023-06-01")
            .defaultHeader("content-type", "application/json")
            .build();
    }
}
```

## **Prompt Engineering and Triage Implementation**

The service uses Claude's system prompt configuration to enforce clean JSON responses, avoiding Markdown wrapping so the output can be parsed directly.

````java
package com.devpulse.service;

import com.devpulse.dto.claude.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClaudeTriageService {

    private final RestClient claudeRestClient;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
        You are DevPulse's automated issue-triage engine. Analyze the title and body of the submitted GitHub issue.
        Respond ONLY with a valid, raw JSON object matching this schema without any markdown formatting or wrapper:
        {
          "severity": "CRITICAL" | "HIGH" | "MEDIUM" | "LOW",
          "issue_type": "BUG" | "FEATURE_REQUEST" | "PERFORMANCE" | "SECURITY" | "DOCUMENTATION",
          "summary": "1-2 sentence executive technical summary",
          "suggested_labels": ["label1", "label2"]
        }
        Do not include ```json or any prefix/suffix characters. Return pure parseable JSON.
        """;

    public IssueTriageResult triageIssue(String title, String body) {
        String userPrompt = "Title: " + title + "\n\nDescription:\n" + (body != null ? body : "No description provided.");

        ClaudeApiRequest request = new ClaudeApiRequest(
            "claude-3-5-sonnet-20241022",
            1024,
            SYSTEM_PROMPT,
            List.of(new ClaudeMessage("user", userPrompt))
        );

        try {
            ClaudeApiResponse response = claudeRestClient.post()
                .uri("/v1/messages")
                .body(request)
                .retrieve()
                .body(ClaudeApiResponse.class);

            if (response == null || response.content().isEmpty()) {
                throw new IllegalStateException("Empty response body received from Claude API");
            }

            String rawJson = response.content().getFirst().text().trim();
            return objectMapper.readValue(rawJson, IssueTriageResult.class);
        } catch (Exception ex) {
            log.error("AI triage evaluation failed. Applying fallback triage baseline.", ex);
            return new IssueTriageResult("MEDIUM", "BUG", "Fallback generated due to inference failure.", List.of("triage-failed"));
        }
    }
}
````

## **Asynchronous Orchestration & Cache Invalidation**

The orchestration layer handles the full ingestion cycle: saving the initial webhook data, scheduling background AI analysis, writing the final enriched schema to PostgreSQL, and evicting stale Redis cache keys.

```java
package com.devpulse.service;

import com.devpulse.domain.entity.*;
import com.devpulse.dto.claude.IssueTriageResult;
import com.devpulse.dto.github.GitHubWebhookPayload;
import com.devpulse.repository.IssueRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookProcessingOrchestrator {

    private final ClaudeTriageService triageService;
    private final IssueRepository issueRepository;
    private final ObjectMapper objectMapper;

    @Async("triageTaskExecutor")
    @Transactional
    @CacheEvict(value = "issues", allEntries = true)
    public void dispatchAsyncTriage(GitHubWebhookPayload payload) {
        var ghIssue = payload.issue();
        
        Issue issue = new Issue();
        issue.setSource(IssueSource.GITHUB_WEBHOOK);
        issue.setExternalId(ghIssue.id());
        issue.setRepositoryName(payload.repository().fullName());
        issue.setRepositoryUrl(payload.repository().htmlUrl());
        issue.setReporterUsername(ghIssue.user().login());
        issue.setReporterAvatarUrl(ghIssue.user().avatarUrl());
        issue.setTitle(ghIssue.title());
        issue.setDescription(ghIssue.body());
        issue.setStatus(IssueStatus.NEW);

        IssueTriageResult result = triageService.triageIssue(ghIssue.title(), ghIssue.body());

        TriageMetadata metadata = new TriageMetadata();
        metadata.setSeverity(IssueSeverity.valueOf(result.severity()));
        metadata.setIssueType(result.issueType());
        metadata.setSummary(result.summary());

        try {
            metadata.setSuggestedLabels(objectMapper.writeValueAsString(result.suggestedLabels()));
            metadata.setRawAiResponse(objectMapper.writeValueAsString(result));
        } catch (JsonProcessingException e) {
            metadata.setSuggestedLabels("[]");
            metadata.setRawAiResponse("{}");
        }

        issue.setTriageMetadata(metadata);
        issue.setStatus(IssueStatus.TRIAGED);

        issueRepository.save(issue);
        log.info("Successfully triaged issue ID {} for repository {}", issue.getId(), issue.getRepositoryName());
    }
}
```

```java
package com.devpulse.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncExecutionConfig {

    @Bean(name = "triageTaskExecutor")
    public Executor triageTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(250);
        executor.setThreadNamePrefix("DevPulse-Triage-");
        executor.initialize();
        return executor;
    }
}
```

# **Guidelines for Claude Code Agent & Plan Mode Usage (specification.md Execution)**

When executing implementation plans derived from this specification, the Claude Code Agent must operate according to strict execution protocols:

## **Contract-First Execution Protocols**

All modifications must adhere strictly to predefined contracts before generating business logic:

* **Data & API Contracts:** Generate and validate Flyway schema migrations, DTO records, and database entities prior to introducing service-layer orchestrators.  
* **Signature Verifications:** Any modifications to ingress webhooks must preserve raw payload integrity and avoid intermediate serialization that alters byte buffers.

## **Bounded Task Slicing for Plan Mode**

To prevent context drift and ensure deterministic outcomes during plan mode execution, tasks must be sliced into isolated, independently verifiable boundaries:

* **Atomic Increments:** Structure plans into distinct modules: (1) Migration & Persistence, (2) DTOs & Security Configuration, (3) Service Logic & Clients, and (4) Integration Testing.  
* **Verification Gates:** Require passing targeted unit and integration tests (e.g., Testcontainers runs) before transitioning between planned phases.

## **Code Review & Verification Directives**

Before accepting changes, perform rigorous `git diff` audits targeting critical systemic failure modes:

* **N+1 Query Detection:** Verify JPA relationship fetch strategies; enforce explicit JOIN FETCH or batch fetching on entity relationships like `Issue` and `TriageMetadata`.  
* **Timing Attack Audits:** Ensure all signature verifications use `MessageDigest.isEqual` and that no short-circuit string comparisons (`equals`) are introduced into authentication pathways.  
* **Schema & Entity Alignment:** Confirm zero divergence between Flyway SQL migrations and Hibernate entity mapping annotations (such as column constraints, UUID generators, and JSONB type converters).

# **Deviations from Spec**

The running record of where the implementation intentionally differs from this document. Where this section and the text above disagree, this section wins.

| Area | Spec says | Implementation | Why | Status |
| :---- | :---- | :---- | :---- | :---- |
| Framework | Spring Boot 3.3+, Hibernate 6, Security 6 | Spring Boot 4.1.2, Hibernate 7, Security 7 | New project, so start on the current line. Jackson 3 changes the `ObjectMapper` package to `tools.jackson.databind` | Applied |
| Build tool | Gradle 8.x | Gradle 9.8.2 | Matches the generated wrapper | Applied |
| Packaging | Default jar tasks | Plain jar disabled | `build/libs/*.jar` must match one file for the Dockerfile | Applied |
| Dockerfile | `-Djarmode=layertools` | `-Djarmode=tools extract --layers`, with matching `COPY` and `ENTRYPOINT` | `layertools` is deprecated | Planned (Phase 6) |
| Delivery order | Containerized build described from Issue #1 | Runs locally on PostgreSQL 16 throughout development. Docker image and compose setup are the last step | Keeps early phases simple. Testcontainers stays for tests, so Docker Desktop is still needed to run them | Applied |
| Schema | Enables `uuid-ossp` | Removed | `gen_random_uuid()` is built in since PG13 | Applied |
| Schema | `issues` has no tenant link | `issues.installation_id` NOT NULL FK, plus index on `(installation_id, created_at DESC)` | Tenant isolation is otherwise impossible | Applied |
| Schema | Non-unique index on `external_id` | `UNIQUE (source, external_id)` | GitHub redelivers webhooks, which would create duplicates | Applied |
| Schema | Uninstall "cascades or deactivates" | `github_installations.uninstalled_at` | Deactivating keeps issue history | Applied |
| Entities | `issueType` as `String`, `suggestedLabels` as nullable `String` | `IssueType` enum, non-null `List<String>` | Matches the schema and the triage contract | Applied |
| Status | Enum values undefined | `NEW`, `TRIAGED`, `TRIAGE_FAILED` | Failures must be visible | Applied |
| Signature | Verified over a `String` body | Verified over raw `byte[]` | Converting to `String` can alter bytes | Applied |
| Triage flow | Saves after the Claude call, in one transaction | Saves `NEW`, dispatches after commit, calls Claude outside any transaction | Avoids holding a connection during the call, and losing events | Applied |
| Triage failure | Saves a fabricated `MEDIUM`/`BUG` result as `TRIAGED` | Marks `TRIAGE_FAILED` | Fabricated data looks real | Applied |
| Claude client | Hardcoded `claude-3-5-sonnet-20241022`, API version `2023-06-01`, unpooled client | Configurable `claude-opus-5-5`, API version `2026-10-21`, pooled JDK client, 60s read timeout | The hardcoded model is outdated, and thinking makes calls slower | Applied |
| Claude output | JSON requested in the prompt, reads the first content block | Enforced with `output_config.format`, checks `stop_reason`, finds the text block | A prompt can't guarantee JSON, and thinking blocks can come first | Applied |
| Prompt safety | Issue text sent as plain prompt | Issue text marked as untrusted, and output validated against enums | Anyone can write an issue, so it can contain prompt injection | Applied |
| Refusals | Not handled | `fallbacks: "default"` with the `server-side-fallback-2026-07-01` beta header | A safety classifier decline is retried on another model | Applied |
| Auth endpoints | Native JWT and BCrypt described, with no endpoints specified | `POST /api/v1/auth/register` and `/login`. 12-72 character passwords, emails stored lowercase, 1-hour tokens | The spec needs these but doesn't define them | Applied |
| GitHub callback | Reads the caller's JWT from the request | A signed, 15-minute `state` from `GET /api/v1/github/install-url` identifies the user, and the installation is verified with GitHub's API | A browser redirect from GitHub carries no `Authorization` header | Applied |
| Installation claims | Not specified | Rejects an installation linked to another user (409), and first-time claims older than 1 hour (409) | `installation_id` in the callback URL can be edited by the user | Applied |
| Callback results | Redirects to "the onboarding view" | Redirects to `github.app.onboarding-url` with `?installation=success`, `cancelled` or `pending` | No frontend exists yet, so the URL is configurable | Applied |
| Cache eviction | `@CacheEvict` on the orchestrator | Not built, and Redis isn't used yet | Nothing is cached, because no read API exists | Deferred |
| Webhook security | No filter chain defined for this slice | JWT filter chain. Public: webhook, register, login, callback, `/actuator/health`. Other actuator paths need `ROLE_ADMIN`, other `/api/v1/**` paths need a token, and everything else is denied | The default chain blocks GitHub's `POST`, and the webhook and callback authenticate in other ways | Applied |

## **Known Gaps**

* Issues left in `NEW` after a crash or full queue are not recovered yet. A recovery job needs locking across instances.
* An installation can be claimed by a different user within the 1-hour window if they obtain its ID. Closing this fully needs a GitHub user token, which the spec rules out.
* No read API is defined, so triaged issues can only be read from the database. Caching waits for this.
* A 429 or 529 from Claude marks an issue `TRIAGE_FAILED` with no retry.
* The Claude and GitHub integrations have only run against fake servers, not the real APIs.
* Only the `opened` action is handled, so issue edits and closes are not reflected.

# **Project Roadmap**

Issues #1 to #5 are built, except the Docker part of Issue #1. The issues below are everything still open, in the recommended order. Each one has its own branch, and new behavior comes with tests. Issue numbers follow on from the sections above, and all are **Not started**.

| Stage | Issues | Why this order |
| :---- | :---- | :---- |
| **1. Stabilize** | #6 Repository cleanup, #7 CI, #8 Real integration testing | Proves what exists works against the real APIs before more is built on it |
| **2. Make it reliable and usable** | #9 Triage reliability, #10 Read API, #11 Security hardening | Fixes known failure modes, and makes the triaged data available |
| **3. Optimize and extend** | #12 Caching, #13 Issue lifecycle, #14 Triage quality and cost | Each depends on stage 2, or on real usage data |
| **4. Run it with confidence** | #15 Observability, #16 Testing depth, #17 API documentation, #18 Data lifecycle | Makes the system measurable, verifiable and safe to hold real data |
| **5. Ship it** | #19 Frontend, #20 Docker, #21 Deployment, #22 Portfolio and interview prep | Docker stays the last build step, as agreed |

# **Issue \#6: Repository Cleanup**

**Goal:** a clean baseline on `main`.

- [ ] Merge the stacked feature branches into `main`, oldest first.
- [ ] Decide whether to remove `plugin/`, `include("plugin")` and the `junit-jupiter` catalog entry.
- [ ] Fix the broken Markdown in the spec's `TriageMetadata` section.
- [ ] Move `SupportSignatures` to the `support` test package, and give it a clearer name.
- [ ] Add a `README.md` covering the architecture, setup, and how to run and test.
- [ ] Protect `main`, so changes arrive through reviewed pull requests.

**Done when:** `main` holds all the work, and `./gradlew clean build` passes from a fresh clone.

# **Issue \#7: Continuous Integration**

**Goal:** every change is tested automatically.

- [ ] Add a GitHub Actions workflow that runs `./gradlew build` on pushes and pull requests.
- [ ] Cache Gradle dependencies, and publish test reports when a run fails.
- [ ] Add dependency vulnerability scanning and automated update pull requests.
- [ ] Add secret scanning.
- [ ] Add a coverage report, and decide on a minimum threshold.

**Done when:** a failing test blocks a pull request from merging.

# **Issue \#8: Real Integration Testing**

**Goal:** replace assumptions with evidence. So far, Claude and GitHub have only been faked.

- [ ] Install PostgreSQL 16 locally, and run the app against it.
- [ ] Register a GitHub App with its Setup URL, the Issues and Installation events, and a converted PKCS#8 key.
- [ ] Set up a tunnel such as smee.io, and an Anthropic API key.
- [ ] Run the full flow: register, install the app, open a real issue, and confirm it reaches `TRIAGED`.
- [ ] Confirm the live request shape, including `output_config.format`, `fallbacks` and the beta header.
- [ ] Test uninstalling, and redelivering from GitHub's delivery page.
- [ ] Replace the hand-written fixtures with captured real payloads, with sensitive values removed.

**Done when:** a real GitHub issue is triaged by the real Claude API, and anything found is fixed.

# **Issue \#9: Triage Reliability**

**Goal:** no issue gets stuck or fails permanently because of a temporary problem.

- [ ] Retry 429, 5xx and 529 responses with exponential backoff and jitter, honoring `retry-after`.
- [ ] Add a migration for attempt tracking (attempt count, last error, last attempt time).
- [ ] Add a recovery job for stuck `NEW` issues and retryable `TRIAGE_FAILED` issues, using `FOR UPDATE SKIP LOCKED` or ShedLock so that several instances are safe.
- [ ] Set a maximum number of attempts, so a permanent failure stops consuming API calls.
- [ ] Add a way to retry a failed issue manually.
- [ ] Handle unexpected worker exceptions, which currently leave an issue in `NEW`.
- [ ] Make `recordSuccess` safe when two workers finish the same issue.
- [ ] Decide how to handle very large issue bodies. They must not be silently truncated.
- [ ] Decide what happens when the database is down during a webhook. GitHub doesn't retry failed deliveries automatically, so consider a job that redelivers them through GitHub's API.
- [ ] Decide whether a circuit breaker is justified during long Claude outages.

**Done when:** tests show that issues recover after a Claude outage, a full queue, and an app restart.

# **Issue \#10: Read API**

**Goal:** make triaged issues queryable, which is the spec's stated mission and isn't met yet.

- [ ] Define the API contract first, along with its response DTOs.
- [ ] `GET /api/v1/issues`: paginated, sorted, and filtered by status, severity, type and repository.
- [ ] `GET /api/v1/issues/{id}`.
- [ ] `GET /api/v1/github/installations`.
- [ ] Scope every query to the authenticated user's installations. Another tenant's issue must return 404, not 403.
- [ ] Add a query-count test for each endpoint, and review indexes against the real filters.
- [ ] Limit the maximum page size.
- [ ] Decide what `ROLE_ADMIN` can see.
- [ ] Decide how teams share access. An installation has a single owner today, so organization members can't see its issues.

**Done when:** a user can read and filter their own issues, and tests prove they can't reach anyone else's.

# **Issue \#11: Security Hardening**

**Goal:** close the known gaps before real users arrive.

**Authentication**
- [ ] Rate limit login and registration, and add account lockout or backoff.
- [ ] Add refresh tokens and token revocation.
- [ ] Add email verification and password reset.
- [ ] Enforce BCrypt's 72-**byte** limit, since the current check counts characters.
- [ ] Decide how to prevent registration from revealing which emails exist.
- [ ] Add a safe way to create the first admin, and to change roles.

**API and webhooks**
- [ ] Set a maximum request body size.
- [ ] Decide whether to reject replayed `X-GitHub-Delivery` IDs.
- [ ] Configure CORS once the frontend's origin is known.
- [ ] Review security headers, and enforce TLS in deployed environments.
- [ ] Give Prometheus scraping a suitable credential, because `/actuator/prometheus` requires `ROLE_ADMIN` today.

**Secrets and keys**
- [ ] Plan rotation for the JWT key, webhook secret and GitHub App key. The JWT `kid` header supports this.
- [ ] Decide whether to accept the remaining 1-hour installation claim risk, or require GitHub user authorization.

**Review**
- [ ] Write a threat model.
- [ ] Add an audit log for logins, role changes, and installation links.
- [ ] Test prompt injection with hostile issue text.
- [ ] Run an OWASP Top 10 review of the API.

**Done when:** the threat model has an owner and a decision for every risk, and each fix has a test.

# **Issue \#12: Caching**

**Goal:** make frequent reads faster, as the mission promises, without serving stale or cross-tenant data. Depends on Issue #10.

- [ ] Measure read performance first, so the cache is justified and the spec's "sub-millisecond" claim gets tested.
- [ ] Decide what to cache, with each entry's TTL.
- [ ] Include the user or tenant in every cache key.
- [ ] Cache DTOs, not entities, and choose a serializer that works with Jackson 3.
- [ ] Evict **after commit**, for example with `@TransactionalEventListener`. The spec's `@CacheEvict` on an async transactional method can evict too early, and a read can then cache stale data again.
- [ ] Evict per tenant, not with the spec's `allEntries = true`, which clears every tenant's data on each new issue.
- [ ] Evict when an installation is uninstalled.
- [ ] Keep serving reads from PostgreSQL if Redis is down.
- [ ] Re-enable the Redis health check, and decide whether Redis being down should mark the app unhealthy.
- [ ] Add a Redis Testcontainer, and tests for hits, eviction and tenant isolation.
- [ ] Track the hit rate.

**Done when:** measurements show a real improvement, and tests prove there's no stale or cross-tenant data.

# **Issue \#13: Issue Lifecycle**

**Goal:** keep stored data in step with GitHub. Only `issues.opened` and `installation.deleted` are handled today.

- [ ] Handle `issues` actions: `edited`, `closed`, `reopened`, `deleted`, `transferred`.
- [ ] Decide when an edit should trigger another triage, given the cost.
- [ ] Handle events arriving out of order.
- [ ] Handle `installation` actions: `suspend`, `unsuspend`.
- [ ] Handle `installation_repositories`, and repository renames.
- [ ] Decide whether to import issues that existed before installation.
- [ ] Decide whether to write labels back to GitHub. This needs installation access tokens and write permission. Because issue text can steer the model, it should require human approval first.
- [ ] Decide whether to send notifications, such as for `CRITICAL` issues.

**Done when:** closing or editing an issue on GitHub is reflected in DevPulse.

# **Issue \#14: Triage Quality and Cost**

**Goal:** know how good the classifications are, and what they cost.

- [ ] Build a labeled evaluation set of real issues.
- [ ] Compare `claude-opus-5-5` and `claude-haiku-5-5` on accuracy and cost, then choose a default.
- [ ] Tune `effort` and `max-tokens` using those results.
- [ ] Store the model used on each triage result, so results stay comparable after a change.
- [ ] Record token usage and cost per issue, and set a budget alert.
- [ ] Decide between `RestClient` and Anthropic's official Java SDK, which provides typed errors and retries.
- [ ] Consider the Batch API for backlogs, and prompt caching if the prompt grows.
- [ ] Let users correct a classification, and feed corrections into the evaluation set.

**Done when:** the model choice is backed by measured accuracy and cost.

# **Issue \#15: Observability**

**Goal:** detect and diagnose problems without reading the database.

- [ ] Add metrics: webhooks by event and result, triage duration and outcome, queue depth and rejections, Claude errors by status, and issues stuck in `NEW`.
- [ ] Use structured JSON logs in deployed environments.
- [ ] Carry the delivery ID and issue ID through async work with MDC.
- [ ] Add distributed tracing across the webhook, worker and outbound calls.
- [ ] Separate liveness from readiness, including database checks.
- [ ] Build a dashboard and alerts for failure rate, queue saturation and connection pool exhaustion.
- [ ] Confirm that logs contain no secrets or issue bodies.

**Done when:** an engineer can trace a single delivery from receipt to triage result, and an alert fires on failure.

# **Issue \#16: Testing Depth**

**Goal:** cover what the current 72 tests don't. Each issue above adds its own tests, and this covers the cross-cutting gaps.

- [ ] **Concurrency:** test the real races, which are currently untested: two simultaneous deliveries, registrations and installation claims.
- [ ] **Load:** measure the spec's sub-second acknowledgment under load with k6 or Gatling, and find the point where the queue and connection pool saturate.
- [ ] **Failure:** test with the database, Redis and Claude down, slow, or restarting.
- [ ] **Architecture:** use ArchUnit to enforce rules, such as controllers not using repositories and packages not forming cycles.
- [ ] **Coverage:** add JaCoCo reports, and consider mutation testing on the security code.
- [ ] **Security:** add scanning such as OWASP ZAP against a running instance.
- [ ] **Contract:** add a scheduled, low-cost run against the real APIs to catch changes the fakes can't.
- [ ] **Migrations:** test each migration against a database containing data.
- [ ] **Speed:** reduce the number of separate Spring contexts and containers as the suite grows.

**Done when:** the spec's performance guarantees have measured numbers, and every race has a test.

# **Issue \#17: API Documentation**

**Goal:** make the API usable without reading the code.

- [ ] Publish an OpenAPI description, with Swagger UI restricted outside development.
- [ ] Document the error format, status codes, authentication and pagination.
- [ ] Decide on a versioning and deprecation policy for `/api/v1`.
- [ ] Check the OpenAPI description in CI, so it can't drift from the code.

**Done when:** a frontend developer can integrate using only the published documentation.

# **Issue \#18: Data Lifecycle and Privacy**

**Goal:** handle stored data responsibly.

- [ ] Set retention periods for issues, `raw_ai_response`, and uninstalled tenants' data. Soft-deleted installations are currently kept forever.
- [ ] Add account deletion and data export.
- [ ] Document the personal data held, such as emails, GitHub usernames, and avatars.
- [ ] Tell users that issue text, including from private repositories, is sent to Anthropic, and check that this is acceptable.
- [ ] Set up backups, and test a restore.
- [ ] Define a zero-downtime migration policy.

**Done when:** retention rules are automated, and a restore has been tested.

# **Issue \#19: Frontend**

**Goal:** give users an interface. Depends on Issues #10 and #17.

- [ ] Choose a stack, and how to store tokens safely in the browser.
- [ ] Build registration, login, and the GitHub onboarding page behind `github.app.onboarding-url`.
- [ ] Show friendly callback errors, which are raw JSON today.
- [ ] Build the issue list with filters, the issue detail page, and the installation list.
- [ ] Configure CORS with Issue #11, and add end-to-end browser tests.

**Done when:** a new user can sign up, install the app, and see a triaged issue without using the API directly.

# **Issue \#20: Docker**

**Goal:** package the app, completing Issue #1. This is the owner's work and the last build step.

- [ ] Write the `Dockerfile` using `-Djarmode=tools extract --layers`, with matching `COPY` and `ENTRYPOINT` lines.
- [ ] Run as a non-root user, and check memory settings and image scanning.
- [ ] Write `docker-compose.yml` with the app, PostgreSQL 16 and Redis.
- [ ] Add healthchecks and startup ordering. `/actuator/health` is public and shows status only.
- [ ] Write `.env.example` with every required variable and no real values.
- [ ] Add a `.dockerignore`.
- [ ] Build the image in CI.

**Done when:** `docker compose up` runs the whole system from a fresh clone.

# **Issue \#21: Deployment**

**Goal:** run DevPulse somewhere real. Depends on Issue #20.

- [ ] Choose a hosting platform and environments, such as staging and production.
- [ ] Add automated deployment from CI, with a rollback plan.
- [ ] Store secrets in a secrets manager, and use managed PostgreSQL and Redis.
- [ ] Configure TLS and a stable webhook URL.
- [ ] Size the connection pool and thread pool together, based on Issue #16's load results.
- [ ] Test graceful shutdown, since queued tasks live in memory.
- [ ] Decide whether virtual threads help, now that Java 21 makes them available.
- [ ] Decide whether to move queued work to a durable broker such as SQS or Kafka, based on measured need.
- [ ] Write runbooks for common failures.

**Done when:** a change reaches production through the pipeline, and can be rolled back.

# **Issue \#22: Portfolio and Interview Prep**

**Goal:** be able to present and defend this project.

- [ ] Go through the mock interview questions in `docs/interview-prep.md` together.
- [ ] Write architecture decision records for the main choices.
- [ ] Add a system diagram that matches what was actually built. The one at the top of this spec differs.
- [ ] Prepare a short demo and a two-minute project summary.
- [ ] Be ready to explain the spec problems found, and the trade-offs and remaining risks.

**Done when:** you can explain any part of the system and defend its trade-offs without notes.

## **Decisions Needed From the Owner**

| Decision | Issue | Needed before |
| :---- | :---- | :---- |
| Remove `plugin/`? | #6 | Cleanup |
| Which model should be the default? | #14 | Production, after evaluation |
| Accept the installation claim risk, or require GitHub user authorization? | #11 | Real users |
| How should organization teams share access? | #10 | Read API |
| Should labels be written back to GitHub? | #13 | Issue lifecycle |
| `RestClient` or Anthropic's SDK? | #14 | Before adding retries in #9, since the SDK includes them |
| Where should it be hosted? | #21 | Deployment |
