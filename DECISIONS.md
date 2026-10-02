# Decisions

Short records of the choices that shaped this codebase, and what would change them.

## Rule-based eligibility extraction, not an LLM

Eligibility wording repeats across employers ("currently enrolled in a recognized co-op program",
"returning to full-time studies"). Regex rules are free, instant, deterministic and testable
against quotes from real postings. The cost is recall: unusual phrasing is missed and shows up as
"unspecified" rather than a wrong answer. Revisit when misses are common enough to measure; an
LLM pass could then run only on postings the rules leave unspecified.

Undergraduate wording ("Bachelor's", "undergraduate") overrides a graduate-degree mention,
because many postings list "Bachelor's, Master's or PhD" and those are open to undergrads.

## One transaction per board

Reconciling a board (insert new, refresh existing, close missing) happens in one transaction,
so a crash can't leave a board half-closed. Boards are independent: one failing board is logged
and skipped, and its postings are not closed, because "fetch failed" must never read as
"every posting was removed".

## first_seen_at is the freshness signal

Boards' own publish dates are unreliable (reposts reset them, some boards omit them). The radar
records when it first saw a posting and never moves that time, which is what students actually
need: how long the posting has been visible to other applicants. Closed postings are kept for
90 days so a posting that briefly disappears keeps its original time.

## Public JSON APIs only

Greenhouse, Ashby and Lever publish job-board JSON meant for embedding. Workday (most banks)
has no documented public API; adding it means scraping with care for rate limits and terms.
That is the next source to add, deliberately after the pipeline is solid.

## Single instance

Scheduling and the API rate limiter live in one JVM. Running two instances would double-poll
boards and split rate-limit counts. When scaling out: ShedLock (database lock) for the scheduler
and Redis for the limiter.

## One deployable image

Spring Boot serves the built React app as static files. One container, one origin, no CORS,
and a strict Content-Security-Policy (`'self'` only; fonts are self-hosted). Split the front end
onto a CDN only if traffic justifies it.

## Search is a LIKE scan

`lower(title) LIKE '%q%'` scans the open postings. At thousands of rows that is milliseconds.
At hundreds of thousands, add a `pg_trgm` GIN index rather than a search engine.
