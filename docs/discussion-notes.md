# Discussion Notes

Decisions made while building without stopping for review. Each one is open to change.

## Workflow

- **Stacked branches:** each step branches from the previous one, because nothing is merged to `main` yet. Merge them in order, oldest first. I don't merge to `main` and haven't opened pull requests. Tell me if "take pull" meant pull requests.
- **Docker Desktop:** I started it, because Testcontainers needs it for the tests.
- **Mock interviews:** these can't be live while I work alone, so each phase's questions are in `docs/interview-prep.md` to go through together.
- **`.DS_Store`:** I untracked it, as we discussed.

## Persistence

- **JSON mapping relies on what's on the classpath:** the JSONB round-trip test passes, but `jjwt-jackson` also brings in Jackson 2, so I haven't established which Jackson version Hibernate uses for JSON. If JJWT's Jackson module is ever removed, that test will show whether it still works.
