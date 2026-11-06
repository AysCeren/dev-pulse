# Discussion Notes

Decisions made while building without stopping for review. Each one is open to change.

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
