# Internship Radar

Spring Boot 4 (Java 21) API and ingestion in `backend/`, React 19 + TypeScript in `frontend/`, PostgreSQL via Flyway.
Read README.md for running it and DECISIONS.md before changing architecture.

## Commands

- Backend, all tests (needs Docker for Testcontainers): `cd backend && ./mvnw verify`
- Frontend: `cd frontend && npm test && npx tsc -b && npm run lint && npm run build`
- Local DB: `docker compose up -d db` (port 5433; 5432 is taken by a native Postgres on this machine)
- Production image: `docker compose --profile full up --build`, then open http://localhost:8080

## How to work here: use the installed skills

Pick the skill for the phase and say which one you're using.

| Work | Skill |
|---|---|
| New feature or significant change | `spec-driven-development`, then `planning-and-task-breakdown` |
| Writing code | `incremental-implementation` + `test-driven-development` (failing test first) |
| Keeping it small | `ponytail` (stdlib and native first, shortest diff) |
| UI | `frontend-design` for visual direction, `frontend-ui-engineering` for accessibility and states |
| API shape | `api-and-interface-design` |
| Library or framework details (Spring Boot 4, Jackson 3, React 19) | `source-driven-development` |
| Bug | `debugging-and-error-recovery` |
| Before calling it done | `code-review-and-quality` or `/code-review`, `security-and-hardening` or `/security-review`, `ponytail-review` |
| Logs and metrics | `observability-and-instrumentation` |
| Commits | `git-workflow-and-versioning` |
| CI | `ci-cd-and-automation` |
| Decisions | `documentation-and-adrs`: add an entry to DECISIONS.md |
| Deploying | `shipping-and-launch` |

## Rules that bit before

- Eligibility rules in `EligibilityExtractor` must be pinned by a test that quotes real posting wording.
- A parser that can't find its jobs list must throw. Returning an empty list closes every posting on that board.
- Spring Boot 4 uses Jackson 3 (`tools.jackson.*`) and the modular test starters (`org.springframework.boot.webmvc.test.autoconfigure`).
- Never trust a link from a board without the http(s) check in `InternshipFilter`.
