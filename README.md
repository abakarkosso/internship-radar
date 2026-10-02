# Internship Radar

[![CI](https://github.com/abakarkosso/internship-radar/actions/workflows/ci.yml/badge.svg)](https://github.com/abakarkosso/internship-radar/actions/workflows/ci.yml)

Canadian tech internships the moment they're posted, with eligibility read for you.

Students lose internships two ways: they apply after the first wave of applicants has been
reviewed, or they spend time on roles they were never eligible for. Internship Radar polls
company job boards every 15 minutes and reads each description for the rules that decide
eligibility: whether a school co-op program is required, whether the role is for Master's or
PhD students, whether you must return to school afterwards, the graduation window, and the
term length.

## What it does

- Polls public Greenhouse, Ashby and Lever job boards on a schedule and keeps only student roles located in Canada.
- Extracts eligibility rules and requested skills from each description with tested, rule-based patterns.
- Tracks when each posting was first seen and closes postings that disappear from their board.
- Serves a fast, filterable feed: hide co-op-only roles, hide graduate-only roles, filter by role type, search.

## Stack

| Layer | Choice |
|---|---|
| API and ingestion | Java 21, Spring Boot 4, Spring Data JPA |
| Database | PostgreSQL 17, schema managed by Flyway |
| Front end | React 19, TypeScript, Vite |
| Tests | JUnit 5, Testcontainers (real PostgreSQL), Vitest |
| Delivery | Docker (single image), GitHub Actions CI |

## Architecture

```
 Greenhouse ─┐
 Ashby ──────┼─▶ HttpBoardSource ─▶ InternshipFilter ─▶ EligibilityExtractor ─▶ PostgreSQL
 Lever ──────┘     (every 15 min)    (student + Canada)   (co-op, degree, term)      │
                                                                                     ▼
                         React feed  ◀──────────────  GET /api/postings  ◀──  Specification filters
```

Each board is fetched and reconciled in its own transaction: new postings are inserted with
`first_seen_at`, existing ones are refreshed, and postings missing from a successful fetch are
closed. A board that fails is skipped without closing its postings.

## Run it locally

Requirements: Java 21, Node 24, Docker.

```bash
docker compose up -d db                  # PostgreSQL on localhost:5433
cd backend && ./mvnw spring-boot:run     # API on :8080, starts ingesting after 10 seconds
cd frontend && npm install && npm run dev   # UI on :5173, proxies /api to :8080
```

Or run the production image: `docker compose --profile full up --build`, then open http://localhost:8080.

## Tests

```bash
cd backend && ./mvnw verify    # unit tests plus Testcontainers integration tests (needs Docker)
cd frontend && npm test
```

Eligibility rules are tested against wording quoted from real postings, so a change that breaks
"applicants do not need to be in a registered co-op program" fails the build.

## API

`GET /api/postings` takes `q`, `category` (`SOFTWARE`, `DATA`, `AI_ML`, `QUANT`, `PRODUCT`,
`HARDWARE`, `OTHER`), `excludeCoopRequired`, `excludeGraduateOnly`, `page`, `size` (max 100).
Results are sorted newest first.

`GET /api/postings/stats` returns the open posting count, company count, and when the boards were
last checked. Errors use RFC 9457 problem details. The API is rate limited to 120 requests per
minute per client.

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5433/radar` | JDBC URL |
| `DATABASE_USER` / `DATABASE_PASSWORD` | `radar` / `radar` | Database credentials |
| `PORT` | `8080` | HTTP port |
| `SPRING_PROFILES_ACTIVE` | unset (`prod` in the image) | `prod` turns on JSON logs and trusts the platform's forwarded headers |
| `FORWARD_HEADERS_STRATEGY` | `none` (`native` under `prod`) | `native` trusts X-Forwarded-For only from private-network proxies. Set `none` if nothing sits in front of the app |

Boards to track are listed under `radar.sources` in `backend/src/main/resources/application.yml`.

Design notes and trade-offs are in [DECISIONS.md](DECISIONS.md).
