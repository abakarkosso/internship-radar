# Labelling guide

Each posting is labelled from its own text (title plus description) as published. The label is
what the posting *says*, not what the employer might do in practice. Labels are judged by reading
the text. They are never copied from the extractor's output, since that's what they measure.

| Field | Label `true` / value when the posting says... | Otherwise |
|---|---|---|
| `coop_required` | You must be registered in a school co-op program or completing an approved co-op work term (including "provide your co-op coordinator's details"). | `false`, also when co-op students are merely welcome |
| `graduate_only` | Applicants must be pursuing (or have) a Master's or PhD, or the role is titled as a PhD/Master's role. | `false` if undergraduates are eligible anywhere in the text |
| `must_return` | You must return to school after the term, or graduate after it ends, or recent graduates are excluded. | `false` |
| `grad_window` | A graduation date range: `{"earliest": "YYYY-MM", "latest": "YYYY-MM"}`, either end may be null. Seasons: Spring = 05, Summer = 08, Fall = 12. Winter is ambiguous (Canadian Jan-Apr term vs US December), so it takes the wider reading: 04 as a lower bound, 12 as an upper bound. "Class of YYYY" = 01 to 12. | `null` |
| `work_auth` | `CITIZEN_OR_PR` if only citizens/permanent residents qualify; `NO_SPONSORSHIP` if the employer refuses to sponsor a permit. | `UNSPECIFIED`, including plain "must be eligible to work in Canada" |

Mark `"confidence": "low"` when the wording is ambiguous, so the owner reviews it first.

The split is fixed by id: ids whose hash falls in the last quarter are the held-out set, which is never
used while tuning patterns. Precision is reported on the positive class for the three yes/no rules and
`work_auth != UNSPECIFIED`, and as exact-match on `grad_window` when the label is non-null.

## Corrections log

2026-10-02, second pass over every posting with full-length context (the first pass truncated long
run-on sentences at 230 characters). Labels were re-judged from the text alone, not from predictions:

- `4ac1ed7cca` (Amazon): `must_return` true. "a minimum of one quarter/semester/trimester remaining in their studies after their internship concludes".
- `c3663db1fc`, `1f2a2d499c`, `835232dfaf`, `3572f192d3` (Robinhood): `grad_window` 2027-12 to 2028-05; the clause was in a run-on paragraph.
- `45a1be01db` (Rockwell OTTO): `grad_window` null. "December 2027 or beyond" is listed under *Preferred*, not a requirement.

2026-10-02, labelling convention change after code review: "Winter" now reads as April when it is a lower
bound and December when it is an upper bound (the wider window, since this rule only warns). Applied to every
posting using the phrase, regardless of split: the 8 Robinhood postings with "Winter 2027 or Spring 2028" move
from 2027-12 to 2027-04 as their earliest date.

## Excerpts instead of full postings

2026-10-02: each posting's `text` is now its title plus only the sentences relevant to eligibility (co-op,
degree, graduation, return to school, work authorization, term). The rest of the job ad is dropped, since
the full text belongs to the employer and is available at `source_url`. Measured accuracy was identical
before and after the change, so the published numbers still hold.

## security_clearance (added 2026-10-03)

`true` when the posting requires Canadian Controlled Goods Program registration or clearance, or a government
security clearance or assessment. A "police clearance certificate" for work-permit holders is a background
check, not a security clearance, and does not count. The excerpts now also keep these sentences.
