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

## A rule must earn the right to hide a posting

Wrongly hiding a role a student could get is the worst failure this app can make, so eligibility
rules are gated by measured accuracy, not by confidence in the regex. `EligibilityEvalTest` measures
every rule on hand-labelled real postings (`backend/src/test/resources/eval/`). A rule may hide postings
only with at least one held-out prediction and 90%+ held-out precision; otherwise it is shown on the
posting and never hides it. On 2026-10-02 that meant co-op required and work authorization hide;
graduate-only (no held-out examples yet) and graduation window (86%, 6 of 7) only show.

The CI test also keeps `frontend/src/generated/accuracy.json` in step, so the accuracy page on the
site can't drift from what the code does.

## The held-out split is never used for tuning

The labelled set is split 75/25 by a hash of each posting's id. Rules were fixed only from tuning-split
mistakes. When a held-out mistake was found (a "Preferred" graduation date read as a requirement), the
rule was left alone and the rule was downgraded to show-only instead, because tuning on held-out
examples would make the published numbers meaningless. Label corrections are logged in
`LABELLING.md` and were made from re-reading the text, never from looking at predictions.

## The student profile stays in the browser

Graduation date, co-op status, degree and work status live in `localStorage`. Only the derived filter
values travel with each feed request, nothing is stored server-side, and the profile is kept out of the
URL so a shared search link doesn't carry someone's situation. That keeps the app account-free: there
is no personal data to protect, so no login, password storage or retention rules yet. Accounts arrive
with the first feature that needs them (email alerts).
