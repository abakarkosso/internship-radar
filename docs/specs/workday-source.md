# Spec: Workday source

Status: parked 2026-10-02 in favour of eligible-for-me.md (coverage alone is not a differentiator: zapplyjobs already reads Workday). Still valid for later.

## Objective

Add Workday as a posting source so the radar covers Canadian banks, insurers and large tech
employers. Today the radar tracks 13 postings from 6 companies. Most of the employers in the
target market (RBC, CIBC, BMO, Manulife, Intact, TD, plus Ciena, Marvell, Autodesk, Intel) post
student roles on Workday. In the Canadian-Tech-Internships-2027 list, Workday links outnumber
every other source.

**User:** a Canadian CS/engineering/business student looking for a Winter or Summer 2027 internship.

**Success:** postings from the configured Workday sites appear in the feed with the same
eligibility extraction as other boards, within one ingestion cycle of being posted, without
putting meaningful load on employers' career sites.

## What the API actually does (verified 2026-10-02 against rbc, cibc, bmo, manulife)

- List: `POST https://{tenant}.{wdN}.myworkdayjobs.com/wday/cxs/{tenant}/{site}/jobs`
  with body `{"appliedFacets":{},"limit":20,"offset":N,"searchText":""}`.
  Returns `total` and `jobPostings[]` of `{title, externalPath, locationsText, postedOn, bulletFields}`.
- `limit` above 20 is rejected with HTTP 400. Large sites need many pages (BMO `External`: 892 jobs = 45 requests).
- `searchText` is relevance-ranked, not a filter (`co-op` on BMO still returns all 892). It can't be used to narrow results.
- The list is newest-first ("Posted Today", "Posted Yesterday").
- Multi-location jobs show `locationsText: "2 Locations"`. The real places are only in the detail.
- Detail: `GET .../wday/cxs/{tenant}/{site}{externalPath}` returns `jobPostingInfo` with
  `title, jobDescription (HTML), location, additionalLocations, externalUrl, jobReqId, timeType, startDate`.
- Several employers run a dedicated student site (`rbc/RBCEARLYTALENT1`, `cibc/campus`, `bmo/Campus`,
  `autodesk/uni`, `rockwellautomation/...-Early-Careers`), which is far smaller than their main site.

## Assumptions

1. Prefer each employer's student site where one exists. Main sites (e.g. Manulife's, 669 jobs) are paged in full.
2. The posting id is `externalPath`. It's unique per posting; `jobReqId` can be shared by reposts.
3. The title filter (student role) runs on the list response. The Canada check runs on the list
   unless `locationsText` is "N Locations", in which case it runs on the detail.
4. A posting's detail is fetched only when it is new to the radar. Known postings are refreshed from
   the list (title, still open) without re-fetching the description.
5. The posting link shown to students is `externalUrl` from the detail.
6. Requests to one Workday site are sequential with a short pause between them (250 ms). No parallel hammering.

## Design (one change to the existing architecture)

`PostingSource.fetch()` currently returns complete postings, descriptions included. For Workday
that would mean a detail request for every student posting on every run (about 40 per site every
15 minutes) even though descriptions rarely change.

Proposed: pass the ids the radar already holds into the fetch.

```java
List<RawPosting> fetch(Set<String> knownIds) throws Exception;
```

Greenhouse, Ashby and Lever ignore the argument (their list responses already include
descriptions). Workday skips detail requests for known ids and returns those postings with
`description == null`. Ingestion then refreshes them without overwriting the stored description
and eligibility. One interface change, one null-check in `Posting.refresh`, and no new abstraction.

Alternative considered: a second interface for "list, then detail" sources. Rejected for now as an
abstraction with one implementation.

## Commands

- `cd backend && ./mvnw verify`
- Manual check: run the app, confirm `Ingested workday:rbc/RBCEARLYTALENT1 ...` log lines and Workday rows in `GET /api/postings`.

## Project structure

- `backend/src/main/java/dev/abakarkosso/radar/source/WorkdaySource.java`: paging, filtering and detail fetches.
- `backend/src/main/java/dev/abakarkosso/radar/source/WorkdayParser.java`: list and detail JSON to records (pure, unit-tested).
- `backend/src/test/resources/fixtures/workday-*.json`: real responses captured from rbc and bmo.
- Config under `radar.sources.workday` as `{ tenant, host, site, company }`.

## Testing strategy

- Parser unit tests on captured fixtures: list page, detail, multi-location detail.
- `WorkdaySource` unit tests with a fake HTTP layer: pagination stops at `total`; known ids get no
  detail request; a non-student title gets no detail request; "N Locations" defers the Canada check
  to the detail; an HTTP error or a missing `jobPostings` list throws (never returns empty).
- The existing `IngestionServiceIT` gains one case: a known posting returned with a null
  description keeps its stored description and eligibility.

## Boundaries

- Always: sequential requests per site with a pause; identify as `internship-radar` in the User-Agent;
  throw on any malformed response; keep the fixtures real.
- Ask first: adding a Workday site with more than 1,000 jobs; polling Workday more often than every 15 minutes.
- Never: parallel requests to one site; logging in or touching anything behind authentication; following
  links outside the configured site.

## Success criteria

- [ ] RBC, CIBC and BMO student sites ingest, and their co-op postings appear in the feed with eligibility filled in.
- [ ] A second run with no changes makes zero detail requests.
- [ ] A posting removed from a site is closed; a failed site closes nothing.
- [ ] All existing tests still pass; new tests cover every rule in "Testing strategy".
- [ ] One ingestion cycle across the initial sites stays under 200 requests.

## Open questions

1. **Initial sites.** Proposed: rbc/RBCEARLYTALENT1, cibc/campus, bmo/Campus, manulife/MFCJH_Jobs,
   intactfc/intactfc, td/TD_Bank_Careers, autodesk/uni, ciena/Careers, thomsonreuters/External_Career_Site.
   That's banks first, then tech. Add or remove any?
2. **Terms of use.** This is the same public endpoint each company's careers page calls from the
   browser, with no login. The radar only reads public postings and links students back to the employer.
   Comfortable with that, given the politeness limits above?
