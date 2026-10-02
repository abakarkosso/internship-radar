import { useEffect, useState } from 'react'
import {
  CATEGORY_LABELS,
  type FeedPage,
  type Filters,
  type PostingSummary,
  type RoleCategory,
  type Stats,
  buildQuery,
  eligibilityLines,
  fetchPostings,
  fetchStats,
  filtersFromUrl,
  age,
  heat,
  timeAgo,
} from './lib/postings'

export default function App() {
  const [filters, setFilters] = useState<Filters>(() => filtersFromUrl(window.location.search))
  const [feed, setFeed] = useState<FeedPage | null>(null)
  const [stats, setStats] = useState<Stats | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    fetchStats(controller.signal).then(setStats).catch(() => { /* the headline falls back to the feed count */ })
    return () => controller.abort()
  }, [])

  useEffect(() => {
    const query = buildQuery(filters)
    window.history.replaceState(null, '', query ? `?${query}` : window.location.pathname)

    const controller = new AbortController()
    // Debounce so typing in the search box doesn't fire a request per keystroke.
    const timer = setTimeout(() => {
      fetchPostings(filters, controller.signal)
        .then((page) => { setFeed(page); setError(null) })
        .catch((e: Error) => { if (e.name !== 'AbortError') setError(e.message) })
    }, 200)
    return () => { clearTimeout(timer); controller.abort() }
  }, [filters])

  const update = (patch: Partial<Filters>) => setFilters((f) => ({ ...f, page: 0, ...patch }))
  const goToPage = (page: number) => {
    setFilters((f) => ({ ...f, page }))
    window.scrollTo({ top: 0 })
  }

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
          Apply in the first two days, while recruiters are still reading.
        </p>
      </header>

      <section className="controls" aria-label="Filter postings">
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
          <select
            value={filters.category}
            onChange={(e) => update({ category: e.target.value as RoleCategory | '' })}
          >
            <option value="">Every role type</option>
            {Object.entries(CATEGORY_LABELS).map(([value, label]) => (
              <option key={value} value={value}>{label}</option>
            ))}
          </select>
        </label>
        <label className="check">
          <input
            type="checkbox"
            checked={filters.excludeCoopRequired}
            onChange={(e) => update({ excludeCoopRequired: e.target.checked })}
          />
          Hide roles that require a co-op program
        </label>
        <label className="check">
          <input
            type="checkbox"
            checked={filters.excludeGraduateOnly}
            onChange={(e) => update({ excludeGraduateOnly: e.target.checked })}
          />
          Hide roles for Master's and PhD students
        </label>
      </section>

      <div aria-live="polite">
        {!feed && !error && <p className="notice">Loading postings…</p>}
        {error && (
          <p className="notice">Couldn't load postings ({error}). Check your connection, then reload the page.</p>
        )}
        {feed && feed.totalItems > 0 && feed.items.length === 0 && (
          <p className="notice">
            This page is past the end of the results.{' '}
            <button className="link-button" onClick={() => goToPage(0)}>Go to the newest postings</button>
          </p>
        )}
        {feed && feed.totalItems === 0 && !error && (
          <p className="notice">
            Nothing matches these filters. Clear the search or turn off a filter to see more.
          </p>
        )}
      </div>

      <ol className="feed">
        {feed?.items.map((p) => <PostingRow key={p.id} posting={p} />)}
      </ol>

      {feed && feed.totalPages > 1 && feed.items.length > 0 && (
        <nav className="pager" aria-label="Pages">
          <button disabled={feed.page === 0} onClick={() => goToPage(feed.page - 1)}>Newer</button>
          <span>Page {feed.page + 1} of {feed.totalPages}</span>
          <button disabled={feed.page + 1 >= feed.totalPages} onClick={() => goToPage(feed.page + 1)}>Older</button>
        </nav>
      )}

      <footer>
        Eligibility is read automatically from each posting. Always confirm on the employer's page before applying.
      </footer>
    </main>
  )
}

function PostingRow({ posting: p }: { posting: PostingSummary }) {
  const lines = eligibilityLines(p)
  return (
    <li className="posting">
      <p className={`age ${heat(p.firstSeenAt)}`}>
        <time dateTime={p.firstSeenAt} title={new Date(p.firstSeenAt).toLocaleString()}>
          {age(p.firstSeenAt)}
        </time>
      </p>
      <div className="body">
        <h2>
          <a href={p.url} target="_blank" rel="noopener noreferrer">{p.title}</a>
        </h2>
        <p className="where">{p.company}, {p.location}</p>
        {lines.length > 0 && (
          <p className={p.coopRequirement === 'REQUIRED' || p.graduateDegreeRequired ? 'rules blocked' : 'rules'}>{lines.join(' ')}</p>
        )}
        {p.skills.length > 0 && <p className="skills">Asks for {p.skills.join(', ')}</p>}
      </div>
    </li>
  )
}
