export type RoleCategory = 'SOFTWARE' | 'DATA' | 'AI_ML' | 'QUANT' | 'PRODUCT' | 'HARDWARE' | 'OTHER'
export type CoopRequirement = 'REQUIRED' | 'NOT_REQUIRED' | 'UNSPECIFIED'

export interface PostingSummary {
  id: number
  company: string
  title: string
  location: string
  url: string
  category: RoleCategory
  coopRequirement: CoopRequirement
  mustReturnToSchool: boolean
  graduateDegreeRequired: boolean
  gradEarliest: string | null
  gradLatest: string | null
  workAuthorization: 'CITIZEN_OR_PR' | 'NO_SPONSORSHIP' | 'UNSPECIFIED'
  termMonths: number[]
  skills: string[]
  firstSeenAt: string
}

export interface FeedPage {
  items: PostingSummary[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export interface Filters {
  q: string
  category: RoleCategory | ''
  /** Only postings first seen in the last 48 hours, when applying early matters most. */
  recent: boolean
  page: number
}

export const CATEGORY_LABELS: Record<RoleCategory, string> = {
  SOFTWARE: 'Software',
  DATA: 'Data',
  AI_ML: 'AI / ML',
  QUANT: 'Quant',
  PRODUCT: 'Product',
  HARDWARE: 'Hardware',
  OTHER: 'Other',
}

/** Only send the filters that are set, so URLs stay short and shareable. */
export function buildQuery(f: Filters, extra: Record<string, string> = {}): string {
  const params = new URLSearchParams()
  if (f.q.trim()) params.set('q', f.q.trim())
  if (f.category) params.set('category', f.category)
  if (f.recent) params.set('postedWithinHours', '48')
  if (f.page > 0) params.set('page', String(f.page))
  for (const [k, v] of Object.entries(extra)) params.set(k, v)
  return params.toString()
}

/** "just now", "3h ago", "2d ago". Freshness is the point of the app, so it is shown on every row. */
export function timeAgo(iso: string, now: Date = new Date()): string {
  const minutes = Math.floor((now.getTime() - new Date(iso).getTime()) / 60_000)
  if (minutes < 1) return 'just now'
  if (minutes < 60) return `${minutes}m ago`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}h ago`
  return `${Math.floor(hours / 24)}d ago`
}

/** The feed's age column: "now", "36m", "3h", "2d". The word "ago" is implied by the column. */
export function age(iso: string, now: Date = new Date()): string {
  const label = timeAgo(iso, now)
  return label === 'just now' ? 'now' : label.replace(' ago', '')
}

export type Heat = 'hot' | 'warm' | 'cold'

/** Applying in the first 24-48h matters most, so age is shown as heat: hot under 6h, warm under 48h. */
export function heat(iso: string, now: Date = new Date()): Heat {
  const hours = (now.getTime() - new Date(iso).getTime()) / 3_600_000
  return hours < 6 ? 'hot' : hours < 48 ? 'warm' : 'cold'
}

/** Eligibility as plain sentences a student can act on, most decisive first. */
export function eligibilityLines(p: PostingSummary): string[] {
  const lines: string[] = []
  if (p.graduateDegreeRequired) lines.push("For Master's or PhD students.")
  if (p.coopRequirement === 'REQUIRED') lines.push('Requires a school co-op program.')
  if (p.coopRequirement === 'NOT_REQUIRED') lines.push('Open to students outside co-op.')
  if (p.mustReturnToSchool) lines.push('You must return to school after the term.')
  if (p.workAuthorization === 'CITIZEN_OR_PR') lines.push('Citizens and permanent residents only.')
  if (p.workAuthorization === 'NO_SPONSORSHIP') lines.push('No visa sponsorship.')
  if (p.termMonths.length > 0) lines.push(`${p.termMonths.join(' or ')} month term.`)
  return lines
}

export interface RuleScore {
  precision: number
  recall: number
  truePositives: number
  falsePositives: number
  falseNegatives: number
}

export interface Accuracy {
  postings: number
  heldOut: number
  rules: Record<string, { holdout: RuleScore; all: RuleScore; use: 'hides' | 'warns' }>
}

export interface Stats {
  openPostings: number
  companies: number
  lastCheckedAt: string | null
}

export async function fetchStats(signal?: AbortSignal): Promise<Stats> {
  const res = await fetch('/api/postings/stats', { signal })
  if (!res.ok) throw new Error(`API returned ${res.status}`)
  return res.json()
}

/** Filters live in the URL so a search can be bookmarked or shared. */
export function filtersFromUrl(search: string): Filters {
  const params = new URLSearchParams(search)
  const category = params.get('category') ?? ''
  return {
    q: params.get('q') ?? '',
    category: (category in CATEGORY_LABELS ? category : '') as RoleCategory | '',
    recent: params.get('postedWithinHours') === '48',
    page: Math.min(10_000, Math.max(0, Math.floor(Number(params.get('page')) || 0))),
  }
}

export async function fetchPostings(f: Filters, extra: Record<string, string>, signal?: AbortSignal, size?: number): Promise<FeedPage> {
  const query = buildQuery(f, size ? { ...extra, size: String(size) } : extra)
  const res = await fetch(`/api/postings?${query}`, { signal })
  if (!res.ok) throw new Error(`API returned ${res.status}`)
  return res.json()
}
