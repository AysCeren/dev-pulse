# DevPulse

Event-driven GitHub issue triage backend. GitHub webhooks are verified, stored, classified asynchronously with the Claude API, and saved to PostgreSQL.

- **Spec:** [docs/specifications.md](docs/specifications.md). It targets older versions, so check its "Deviations from Spec" section before copying its code.
- **Interview notes:** [docs/interview-prep.md](docs/interview-prep.md)

## Stack

Java 21, Spring Boot 4.1.2, Gradle 9.8.2 (Kotlin DSL, version catalog), PostgreSQL 16, Flyway, Hibernate 7, Testcontainers.

Boot 4 specifics that differ from the spec's snippets:
- Jackson 3 is the default: `ObjectMapper` lives in `tools.jackson.databind`, while annotations stay in `com.fasterxml.jackson.annotation`.
- `jjwt-jackson` also brings in Jackson 2, so both are on the runtime classpath.
- Starters are modular, for example `spring-boot-starter-webmvc` and `spring-boot-starter-data-jpa-test`.

## Commands

```bash
./gradlew clean :bootJar   # builds exactly one jar in build/libs/
./gradlew :test            # known failure until Phase 2.1: no test datasource yet
```

## Local development

Everything runs directly on the machine during development. Docker images are the last step (Phase 6).

- **Database:** a local PostgreSQL 16 on port 5433, because PostgreSQL 14 already uses 5432.
- **Redis:** not needed until the caching phase.
- **Running the app:** `./gradlew :bootRun` needs `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` set in the shell or IDE run configuration. Spring does not load `.env` by itself. Later phases add `GITHUB_WEBHOOK_SECRET` and `ANTHROPIC_API_KEY`.
- **Example:** `DB_URL=jdbc:postgresql://localhost:5433/devpulse`
- **Tests:** from Phase 2 onwards, tests use Testcontainers, so Docker Desktop must be running. No project Docker files are involved, and tests don't touch the local database.

## Project layout

Root package `com.devpulse`:

| Package | Contents |
|---|---|
| `config` | `@Configuration` and `@ConfigurationProperties` classes |
| `controller` | HTTP layer only: no business logic or repository access |
| `service` | Business logic and transaction boundaries |
| `repository` | Spring Data interfaces |
| `domain.entity` | JPA entities and their enums |
| `dto` | Records for external contracts (`dto.github`, `dto.claude`) |
| `security` | Signature verification and the filter chain |
| `exception` | Custom exceptions and error handling |

Migrations go in `src/main/resources/db/migration`. `plugin/` is the sample from `gradle init` and is not part of DevPulse: don't build on it, and leave it unless the owner decides to remove it.

## Backend standards

- Use constructor injection (`@RequiredArgsConstructor`), never field injection.
- Use typed `@ConfigurationProperties` rather than `@Value`. Secrets come only from environment variables.
- Keep transactions short, with **no remote calls inside a transaction**.
- Never return entities from an API. Map them to DTOs.
- Change the schema only through new Flyway migrations. Never edit an applied one. `ddl-auto` stays `validate`.
- Handle errors consistently, and don't swallow exceptions or invent results to hide a failure.
- Never log secrets or issue bodies. Include `X-GitHub-Delivery` in webhook logs.
- One public type per file. Match the existing code style (tabs in Java and Gradle files).

## Review checklist

Check every diff for:
- **N+1 queries:** use `JOIN FETCH` or batch fetching. The inverse side of `Issue.triageMetadata` does not stay lazy.
- **Timing attacks:** compare signatures with `MessageDigest.isEqual`, never `equals`.
- **Raw payload integrity:** verify signatures over the raw `byte[]` body, before parsing.
- **Schema and entity alignment:** migrations and mappings must match, including constraints and JSONB types.
- **Tenant isolation:** drop webhooks with no active installation.

## Working agreement

This project is mentored, and the owner is preparing for interviews.

1. Claude writes all code except the Docker files, one step at a time, and runs the step's gate.
2. Claude stops and summarizes what changed, the key decisions, and any deviations from the spec.
3. The owner reviews, and they discuss it. Claude explains anything asked about.
4. Claude updates `docs/interview-prep.md`, logs any deviations in the spec, and suggests a commit message.
5. The owner commits. **Claude never runs `git commit` or `git push`.**
6. Claude starts the next step only when told to.

Each phase ends with a short mock interview.

**The owner's files:** `Dockerfile`, `docker-compose.yml`, `.env.example`, all planned for Phase 6. Don't edit these unless asked, and don't make earlier phases depend on them.

**The owner works in this repo at the same time.** Re-read any file before overwriting it, and check `git status` before starting a step.

**Not yet in scope:** read API, JWT and auth endpoints, the GitHub callback, caching, frontend. Raise these for discussion rather than building them.
