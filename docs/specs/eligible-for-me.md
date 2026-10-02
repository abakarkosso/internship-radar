# Spec: "Eligible for me" and measured accuracy

Status: done 2026-10-02. Shipped as a portfolio project (not deployed). Replaces Workday as the next feature; see
`workday-source.md` (parked, still valid for later).

## Objective

Other tools list Canadian internships, send alerts, and even claim to extract eligibility
(zapplyjobs, bhat-pranav/internship-tracker, Francklin9999/canada-internship). None of them answers
the question a student actually has, *"which of these can I get?"*, and none publishes how often
its eligibility reading is right.

Internship Radar becomes: **the internships you can actually get, ranked, with measured accuracy.**

**User:** a student in Canada who knows their own situation (graduation date, co-op or not,
degree level, work authorization) and wants to stop reading postings they're excluded from.

**Success:**
1. A student sets a profile once and the feed shows only roles they're eligible for, with the
   reason when a role is excluded.
2. The eligibility extractor's accuracy is measured on a labelled set of real postings and shown
   on the site, per rule.
3. The measured accuracy is at least 90% on each rule before it is published.

## Scope

In:
- **Profile** (stored in the browser only, no accounts): graduation year and term, in co-op (yes/no),
  degree level (undergrad, Master's, PhD), authorized to work in Canada without sponsorship (yes/no).
- **Eligibility match** per posting against the profile: eligible, not eligible (with reason), or unknown.
- **Two new extracted rules:** graduation window (e.g. "graduating between Oct 2027 and Sep 2029") and
  work authorization ("no sponsorship", "Canadian citizen or permanent resident").
- **Evaluation set:** about 200 real postings with hand-checked labels per rule, stored in the repo,
  and a test that computes precision and recall per rule and fails if a rule drops below its bar.
- **Accuracy page** on the site showing the latest numbers and what "unknown" means.

Out (later):
- Accounts, email alerts, resume upload and skill-gap ranking.
- Workday and more boards (coverage).
- An LLM fallback for postings the rules can't read. The evaluation set makes it possible to measure
  whether one would help before building it.

## Assumptions

1. The profile lives in `localStorage`. Only the derived filter values travel with each feed request and
   nothing is stored or logged server-side, so no accounts or retention rules are needed yet.
2. "Unknown" is a first-class answer. A posting that says nothing about co-op is shown as eligible
   with a quiet "not stated" note. It is never hidden for that reason.
3. Labels are per rule, from the posting text only. The label is what the posting says, not what the
   employer might do in practice.
4. The evaluation set is built from the 125 postings already collected in
   `~/career-ops/research/postings` plus live ones, so it reflects the real mix of employers.
5. Accuracy is reported as precision and recall per rule on the positive class (e.g. "co-op required").
   Precision matters most: wrongly hiding a role a student could get is the worst failure.

## Design

- **Backend:** `EligibilityExtractor` gains `gradWindow` (as a start and end year-month, not free
  text) and `workAuthorization` (`NO_SPONSORSHIP`, `CITIZEN_OR_PR`, `UNSPECIFIED`). New columns via
  Flyway V3. The API exposes them on `PostingSummary`.
- **Matching:** the feed is paginated on the server, so filtering must happen there or page counts
  break. The browser keeps the profile and sends only the filter values with each request
  (`excludeCoopRequired`, `excludeGraduateOnly`, `graduation=2028-04`, `workStatus=...`). Nothing is
  stored server-side. A pure TypeScript `reasons(posting)` explains each row in plain words.
  (Changed from client-side matching during planning: client-side filtering would show "25 results"
  on a page that had 3 left after filtering.)
- **Evaluation:** `backend/src/test/resources/eval/labels.jsonl`, one line per posting:
  `{ id, source_url, text, labels: { coop_required, graduate_only, must_return, grad_window, work_auth } }`.
  `EligibilityEvalTest` runs the extractor over every line, prints a per-rule table, and asserts each
  rule's precision is at least 0.90. The same numbers are written to a JSON file that the accuracy page reads.
- **UI:** a profile panel ("About you") replaces the two hide-checkboxes. Each row shows why it is or
  isn't a match in plain words. A small "How accurate is this?" link goes to the accuracy page.

## Testing strategy

- Unit tests for each new extractor rule, each quoting real posting wording (existing convention).
- Vitest tests for `reasons()` and profile-to-query mapping, covering every rule.
- `EligibilityEvalTest` is the accuracy gate in CI.
- One Playwright-free manual check: set a profile, confirm the counts change and reasons read correctly.

## Boundaries

- Always: labels come from the posting text; the labelling guide is written down before labelling starts.
- Ask first: publishing any accuracy number below 90%; adding any server-side storage of profile data.
- Never: hide a posting because a rule is unknown; tune patterns on the evaluation set without also
  holding out a test split (otherwise the number is meaningless).

## Success criteria

- [ ] A profile set to "graduating 2028, not in co-op, undergrad, no sponsorship needed" hides co-op-only
      and graduate-only roles and keeps everything else, with reasons shown.
- [ ] Graduation-window and work-authorization rules exist, with tests quoting real postings.
- [ ] Every full posting collected so far (about 140) labelled, split into tuning (75%) and held-out (25%) sets.
      (200 was the target; 140 is all the real data available on 2026-10-02. Grow it as postings arrive.)
- [ ] Held-out precision of at least 90% for each rule, reported in CI and on the accuracy page.
- [ ] All existing tests pass.

## Resolved questions

1. **Labelling:** Claude pre-labels from the posting text and flags low-confidence labels; the owner
   spot-checks them. Labels are judged from the text, never generated by the extractor being measured.
2. **Profile fields:** graduation month, co-op, degree level and work status. No location (the radar is Canada-only).

## Tasks

Ordered by dependency. Each ends with a check.

1. **Graduation window rule.** `GradWindow` (earliest and latest `YearMonth`) parsed from the five real
   phrasings. Check: unit tests quoting each phrasing.
2. **Work authorization rule.** `CITIZEN_OR_PR`, `NO_SPONSORSHIP`, `UNSPECIFIED`, with "executive sponsor" and
   EEO "citizenship" boilerplate not matching. Check: unit tests.
3. **Persist and filter.** Flyway V3 columns, `PostingSummary` fields, API params `graduation` and `workStatus`.
   Check: `PostingApiIT` cases for each filter, plus unknown values staying visible.
4. **Evaluation set.** Labelling guide, labels for all collected postings, 75/25 split. Check: file validates.
5. **Accuracy gate.** `EligibilityEvalTest` computes per-rule precision and recall, fails under 90% precision on
   the held-out split, and keeps `frontend/src/generated/accuracy.json` current. Check: CI.
6. **Profile UI.** "About you" panel in `localStorage`, filters sent with each request, plain-word reasons per row.
   Check: Vitest for reasons and query building, plus a screenshot review.
7. **Accuracy page.** A native `<dialog>` that reads `accuracy.json`. Check: screenshot.
8. **Review and ship.** `/code-review`, security-and-hardening, ponytail-review, docs, atomic commits, CI green.

## Progress (2026-10-02)

Tasks 1-7 are done. Task 8's code review found 10 issues; the 8 extractor and parser fixes are in, with tests
(60 rule tests pass). Remaining work:

### Task 9: Finish the review fixes
**Description:** Close the last four review findings so the accuracy claim and the UI are both honest.
**Acceptance criteria:**
- [ ] A rule may hide postings only with at least one held-out prediction and 90%+ held-out precision.
      `graduate_only` (0 held-out predictions) becomes show-only, and the UI stops sending `excludeGraduateOnly`.
- [ ] The accuracy dialog never falls back to tuning-set numbers; it says "Not enough held-out postings yet".
- [ ] Labelling guide and the 8 Robinhood labels follow the Winter convention (April as a lower bound, December as an upper bound).
- [ ] The "N hidden" count is fetched only when search, role type or profile change, not on every page.
- [ ] Dead code removed: the server `graduation` filter and its test (the UI only warns on graduation).
**Verification:** `./mvnw verify`, `npm test`, regenerated `accuracy.json` passes the stale-file check.
**Files:** `EligibilityEvalTest.java`, `labels.jsonl`, `LABELLING.md`, `PostingFilters.java`, `PostingController.java`,
`PostingApiIT.java`, `App.tsx`, `profile.ts` (+ tests). **Scope:** Medium.

### Checkpoint
- [ ] All backend and frontend tests pass; production image builds; screenshots re-checked.

### Task 10: Document the decisions
**Description:** DECISIONS.md entries for accuracy-gated rules, the held-out discipline, and browser-only profiles;
README sections for the profile, the accuracy page and the new API parameters.
**Verification:** Docs match the code (API params, rule behaviours). **Scope:** Small.

### Task 11: Ship
**Description:** Atomic commits (extractor rules; evaluation set and gate; profile UI; docs), push, CI green.
**Verification:** GitHub Actions passes both jobs. **Scope:** Small.

### Later (owner)
- Spot-check the 11 labels marked `"confidence": "low"` in `labels.jsonl`.
- Grow the held-out set with new postings, then re-measure `graduate_only` and `grad_window`.
