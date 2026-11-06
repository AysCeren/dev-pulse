# Discussion Notes

Decisions made while building without stopping for review. Each one is open to change.

## Start here: the most important decisions

1. **Installation claiming has a small remaining risk.** Someone who learns a new installation's ID could claim it within the 1-hour window. Fully closing this needs a GitHub user token, which the spec rules out. Is that acceptable?
2. **Model choice affects your bill.** The default is Opus 5.5, and Haiku 5.5 costs about 40x less. See "Claude integration" below.
3. **Should a read API come next?** Triaged issues can only be read from the database today, which means the spec's "queryable" goal isn't met yet.
4. **`plugin/` is still in the repo.** It's still unused and still builds. I'd remove it.

## Branches to merge, in this order

1. `chore/workflow-and-notes`
2. `feat/schema-migration`
3. `feat/domain-entities`
4. `feat/dto-contracts`
5. `feat/claude-triage-service`
6. `feat/triage-pipeline`
7. `feat/webhook-endpoint`
8. `feat/jwt-auth`
9. `feat/github-app-installation`

Each branch builds on the one before it, so merging the last one brings in all of them.

## Workflow

- **Stacked branches:** each step branches from the previous one, because nothing is merged to `main` yet. Merge them in order, oldest first. I don't merge to `main` and haven't opened pull requests. Tell me if "take pull" meant pull requests.
- **Docker Desktop:** I started it, because Testcontainers needs it for the tests.
- **Mock interviews:** these can't be live while I work alone, so each phase's questions are in `docs/interview-prep.md` to go through together.
- **`.DS_Store`:** I untracked it, as we discussed.

## Persistence

- **JSON mapping relies on what's on the classpath:** the JSONB round-trip test passes, but `jjwt-jackson` also brings in Jackson 2, so I haven't established which Jackson version Hibernate uses for JSON. If JJWT's Jackson module is ever removed, that test will show whether it still works.

## Claude integration

- **Raw HTTP instead of the SDK:** Anthropic recommends its official Java SDK, but the spec asks for `RestClient` and hand-written DTOs, so I followed the spec. The SDK would give you typed errors and built-in retries. Worth discussing.
- **Model cost:** the default is `claude-opus-5-5` ($4/$20 per MTok). `claude-haiku-5-5` ($0.10/$0.50) is far cheaper and may be good enough for classification, but I didn't change models on cost grounds without asking. Set `anthropic.api.model` to switch, ideally after comparing results on real issues.
- **Fallbacks are on:** the request sends `fallbacks: "default"` and a beta header, so a safety decline gets retried on another model. Billing can differ when that happens. Remove it if you don't want a beta feature.
- **No retries yet:** a 429 or 529 marks the issue `TRIAGE_FAILED` straight away, even though both are retryable. A burst of issues could fail unnecessarily. I'd add retries alongside the recovery job.
- **Never tested against the real API:** all tests use a fake server, because I have no API key to use here. The first real call is part of the manual end-to-end test.

## Authentication

- **JJWT with Jackson 3:** resolved. `jjwt-jackson` uses its own Jackson 2 internally, and tokens work in the tests.
- **Missing protections:** no rate limiting or lockout on login, no email verification, no refresh tokens or revocation. All are worth adding before real users, and rate limiting comes first.
- **Admin users:** there's no way to create a `ROLE_ADMIN` user except updating the database by hand, and `/actuator/prometheus` needs that role. A monitoring system would need its own access.
- **Password length:** the 72 limit counts characters, while BCrypt's limit is in bytes. A long password with multi-byte characters gets cut off silently.

## GitHub App

- **You need to set these up for real use:** register a GitHub App, set its Setup URL to `/api/v1/github/callback`, enable "Redirect on update", and subscribe to the Issues and Installation events. Then provide `GITHUB_APP_ID`, `GITHUB_APP_SLUG`, `GITHUB_APP_PRIVATE_KEY` and `GITHUB_WEBHOOK_SECRET`.
- **Private key format:** GitHub issues PKCS#1 keys, and the app expects PKCS#8. Convert with `openssl pkcs8 -topk8 -nocrypt -in github-app.pem`.
- **Failed first link:** if the callback fails and the user retries over an hour later, the claim is rejected and they must reinstall. Is 1 hour the right window?
- **Onboarding URL:** `github.app.onboarding-url` points to a placeholder (`http://localhost:3000/onboarding`) until a frontend exists. Callback errors return JSON to the browser for the same reason.
- **Webhooks before linking:** issues opened before the callback completes are dropped, not stored, because no tenant exists yet.

## Testing

- **What was verified:** 72 automated tests, then the real jar over real HTTP with PostgreSQL 16: register, login, install URL, callback, a signed webhook with non-ASCII text, a bad signature, a redelivery, and the issue reaching `TRIAGED`. Without secrets, the app refuses to start and names the missing property.
- **What wasn't:** the real Claude and GitHub APIs (both were fakes), and your local PostgreSQL 16, which isn't installed yet.

## Not built

- **Read API and caching:** the spec doesn't define the endpoints, and Redis has nothing to cache without them.
- **Recovery job and retries:** see "Claude integration" and Known Gaps in the spec.
- **Issue edits and closes:** only `opened` is handled.
