import { useEffect, useMemo, useRef, useState } from 'react'
import accuracyReport from './generated/accuracy.json'
import {
  type Accuracy,
  CATEGORY_LABELS,
  type FeedPage,
  type Filters,
  type PostingSummary,
  type RoleCategory,
  type Stats,
  age,
  buildQuery,
  eligibilityLines,
  fetchPostings,
  fetchStats,
  filtersFromUrl,
  heat,
  timeAgo,
} from './lib/postings'
import {
  MONTHS,
  type Profile,
  WORK_STATUS_LABELS,
  type WorkStatus,
  describeProfile,
  graduationFit,
  loadProfile,
  profileQuery,
  saveProfile,
} from './lib/profile'

const accuracy = accuracyReport as Accuracy
const YEARS = [2026, 2027, 2028, 2029, 2030, 2031]

export default function App() {
  const [filters, setFilters] = useState<Filters>(() => filtersFromUrl(window.location.search))
  const [profile, setProfile] = useState<Profile>(() => loadProfile())
  const [feed, setFeed] = useState<FeedPage | null>(null)
  const [unfiltered, setUnfiltered] = useState<number | null>(null)
  const [stats, setStats] = useState<Stats | null>(null)
  const [error, setError] = useState<string | null>(null)
  const accuracyDialog = useRef<HTMLDialogElement>(null)
  const extra = useMemo(() => profileQuery(profile), [profile])

  useEffect(() => {
    const controller = new AbortController()
    fetchStats(controller.signal).then(setStats).catch(() => { /* the headline falls back to a generic line */ })
    return () => controller.abort()
  }, [])

  useEffect(() => {
    const query = buildQuery(filters)
    window.history.replaceState(null, '', query ? `?${query}` : window.location.pathname)

    const controller = new AbortController()
    // Debounce so typing in the search box doesn't fire a request per keystroke.
    const timer = setTimeout(() => {
      fetchPostings(filters, extra, controller.signal)
        .then((page) => { setFeed(page); setError(null) })
        .catch((e: Error) => { if (e.name !== 'AbortError') setError(e.message) })
    }, 200)
    return () => { clearTimeout(timer); controller.abort() }
  }, [filters, extra])

  // How many roles the profile hides: the same search without the profile. Paging doesn't change it.
  const { q, category } = filters
  useEffect(() => {
    if (Object.keys(extra).length === 0) { setUnfiltered(null); return }
    const controller = new AbortController()
    const timer = setTimeout(() => {
      fetchPostings({ q, category, page: 0 }, {}, controller.signal, 1)
        .then((all) => setUnfiltered(all.totalItems))
        .catch(() => { /* the count is a nicety; the feed still works without it */ })
    }, 200)
    return () => { clearTimeout(timer); controller.abort() }
  }, [q, category, extra])

  const update = (patch: Partial<Filters>) => setFilters((f) => ({ ...f, page: 0, ...patch }))
  const changeProfile = (patch: Partial<Profile>) => {
    const next = { ...profile, ...patch }
    for (const key of Object.keys(patch) as (keyof Profile)[]) if (patch[key] === undefined) delete next[key]
    setProfile(next)
    saveProfile(next)
    setFilters((f) => ({ ...f, page: 0 }))
  }
  const goToPage = (page: number) => {
    setFilters((f) => ({ ...f, page }))
    window.scrollTo({ top: 0 })
  }
  const summary = describeProfile(profile)
  const hidden = feed && unfiltered !== null ? Math.max(0, unfiltered - feed.totalItems) : 0

  return (
    <main>
      <header className="masthead">
        <p className="brand">Internship Radar</p>
        <h1>
          {stats
            ? `${stats.openPostings} open internships at ${stats.companies} companies in Canada.`
            : 'Canadian tech internships, newest first.'}
        </h1>
        <p className="lede">
          {stats?.lastCheckedAt ? `Job boards checked ${timeAgo(stats.lastCheckedAt)}. ` : ''}
          Tell it about you and it hides the roles you can't get.
        </p>
      </header>

      <details className="profile" open={!summary}>
        <summary>
          <span className="profile-title">About you</span>
          <span className="profile-summary">{summary ?? 'Not set. Every posting is shown.'}</span>
        </summary>
        <div className="profile-fields">
          <fieldset>
            <legend>Graduation</legend>
            <div className="pair">
              <label>
                <span className="visually-hidden">Graduation month</span>
                <select
                  value={profile.gradMonth ?? ''}
                  onChange={(e) => changeProfile({ gradMonth: e.target.value ? Number(e.target.value) : undefined })}
                >
                  <option value="">Month</option>
                  {MONTHS.map((m, i) => <option key={m} value={i + 1}>{m}</option>)}
                </select>
              </label>
              <label>
                <span className="visually-hidden">Graduation year</span>
                <select
                  value={profile.gradYear ?? ''}
                  onChange={(e) => changeProfile({ gradYear: e.target.value ? Number(e.target.value) : undefined })}
                >
                  <option value="">Year</option>
                  {YEARS.map((y) => <option key={y} value={y}>{y}</option>)}
                </select>
              </label>
            </div>
          </fieldset>
          <fieldset>
            <legend>School co-op program</legend>
            <Choice name="coop" value={profile.coop} onChange={(coop) => changeProfile({ coop })}
              options={[['no', "I'm not in one"], ['yes', "I'm in one"]]} />
          </fieldset>
          <fieldset>
            <legend>Degree</legend>
            <Choice name="degree" value={profile.degree} onChange={(degree) => changeProfile({ degree })}
              options={[['undergrad', 'Undergraduate'], ['masters', "Master's"], ['phd', 'PhD']]} />
          </fieldset>
          <fieldset>
            <legend>Work in Canada</legend>
            <label>
              <span className="visually-hidden">Work status in Canada</span>
              <select
                value={profile.workStatus ?? ''}
                onChange={(e) => changeProfile({ workStatus: (e.target.value || undefined) as WorkStatus | undefined })}
              >
                <option value="">Prefer not to say</option>
                {Object.entries(WORK_STATUS_LABELS).map(([value, label]) => (
                  <option key={value} value={value}>{label}</option>
                ))}
              </select>
            </label>
          </fieldset>
          <p className="profile-note">
            Saved in this browser only, never sent with links you share.{' '}
            {summary && <button className="link-button" onClick={() => { setProfile({}); saveProfile({}) }}>Clear</button>}
          </p>
        </div>
      </details>

      <section className="controls" aria-label="Search postings">
        <label className="search">
          <span className="visually-hidden">Search by role or company</span>
          <input
            type="search"
            placeholder="Search by role or company"
            maxLength={100}
            value={filters.q}
            onChange={(e) => update({ q: e.target.value })}
          />
        </label>
        <label>
          <span className="visually-hidden">Role type</span>
          <select value={filters.category} onChange={(e) => update({ category: e.target.value as RoleCategory | '' })}>
            <option value="">Every role type</option>
            {Object.entries(CATEGORY_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </select>
        </label>
      </section>

      <div aria-live="polite">
        {!feed && !error && <p className="notice">Loading postings…</p>}
        {error && <p className="notice">Couldn't load postings ({error}). Check your connection, then reload the page.</p>}
        {feed && hidden > 0 && (
          <p className="notice">
            {feed.totalItems} {feed.totalItems === 1 ? 'role fits' : 'roles fit'} you.{' '}
            {hidden} hidden because they require something your profile rules out.
          </p>
        )}
        {feed && feed.totalItems > 0 && feed.items.length === 0 && (
          <p className="notice">
            This page is past the end of the results.{' '}
            <button className="link-button" onClick={() => goToPage(0)}>Go to the newest postings</button>
          </p>
        )}
        {feed && feed.totalItems === 0 && !error && (
          <p className="notice">
            {hidden > 0
              ? 'Every match is ruled out by your profile. Check it under About you.'
              : 'Nothing matches this search. Clear it or pick every role type.'}
          </p>
        )}
      </div>

      <ol className="feed">
        {feed?.items.map((p) => <PostingRow key={p.id} posting={p} profile={profile} />)}
      </ol>

      {feed && feed.totalPages > 1 && feed.items.length > 0 && (
        <nav className="pager" aria-label="Pages">
          <button disabled={feed.page === 0} onClick={() => goToPage(feed.page - 1)}>Newer</button>
          <span>Page {feed.page + 1} of {feed.totalPages}</span>
          <button disabled={feed.page + 1 >= feed.totalPages} onClick={() => goToPage(feed.page + 1)}>Older</button>
        </nav>
      )}

      <footer>
        Eligibility is read automatically from each posting.{' '}
        <button className="link-button" onClick={() => accuracyDialog.current?.showModal()}>How accurate is this?</button>
        {' '}Always confirm on the employer's page before applying.
      </footer>

      <AccuracyDialog ref={accuracyDialog} />
    </main>
  )
}

function Choice<T extends string>({ name, value, onChange, options }: {
  name: string
  value: T | undefined
  onChange: (value: T) => void
  options: [T, string][]
}) {
  return (
    <div className="choice">
      {options.map(([v, label]) => (
        <label key={v}>
          <input type="radio" name={name} value={v} checked={value === v} onChange={() => onChange(v)} />
          {label}
        </label>
      ))}
    </div>
  )
}

function PostingRow({ posting: p, profile }: { posting: PostingSummary; profile: Profile }) {
  const lines = eligibilityLines(p)
  const warning = graduationFit(p, profile)
  return (
    <li className="posting">
      <p className={`age ${heat(p.firstSeenAt)}`}>
        <time dateTime={p.firstSeenAt} title={new Date(p.firstSeenAt).toLocaleString()}>{age(p.firstSeenAt)}</time>
      </p>
      <div className="body">
        <h2><a href={p.url} target="_blank" rel="noopener noreferrer">{p.title}</a></h2>
        <p className="where">{p.company}, {p.location}</p>
        {lines.length > 0 && (
          <p className={p.coopRequirement === 'REQUIRED' || p.graduateDegreeRequired ? 'rules blocked' : 'rules'}>
            {lines.join(' ')}
          </p>
        )}
        {warning && <p className="rules warn">{warning}</p>}
        {p.skills.length > 0 && <p className="skills">Asks for {p.skills.join(', ')}</p>}
      </div>
    </li>
  )
}

const RULE_NAMES: Record<string, string> = {
  coop_required: 'Requires a co-op program',
  graduate_only: "For Master's and PhD students only",
  work_auth: 'Citizenship or sponsorship limits',
  must_return: 'Must return to school after',
  grad_window: 'Graduation date window',
}

function AccuracyDialog({ ref }: { ref: React.Ref<HTMLDialogElement> }) {
  return (
    <dialog ref={ref} className="accuracy" aria-labelledby="accuracy-title">
      <h2 id="accuracy-title">How accurate is this?</h2>
      <p>
        Every rule was checked against {accuracy.postings} real postings labelled by hand. The numbers below come
        from the {accuracy.heldOut} postings that were set aside and never used while writing the rules.
      </p>
      <table>
        <thead>
          <tr><th scope="col">Rule</th><th scope="col">Right when it says yes</th><th scope="col">Used to</th></tr>
        </thead>
        <tbody>
          {Object.entries(accuracy.rules).map(([key, r]) => {
            // Held-out numbers only: tuning-set numbers flatter the rules they were tuned on.
            const s = r.holdout
            const said = s.truePositives + s.falsePositives
            return (
              <tr key={key}>
                <th scope="row">{RULE_NAMES[key] ?? key}</th>
                <td>
                  {said === 0 ? 'Not enough held-out postings yet'
                    : `${Math.round(s.precision * 100)}% (${s.truePositives} of ${said})`}
                </td>
                <td>{r.use === 'hides' ? 'Hide roles' : 'Show, never hide'}</td>
              </tr>
            )
          })}
        </tbody>
      </table>
      <p className="fine">
        A rule may hide a posting only when it was right at least 90% of the time. When a posting doesn't
        mention a rule, the posting stays visible.
      </p>
      <form method="dialog"><button>Close</button></form>
    </dialog>
  )
}
