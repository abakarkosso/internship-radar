/**
 * The student's situation, kept in their browser only. It never goes in the URL, so a shared
 * search link doesn't carry someone's graduation date or work status.
 */
export type WorkStatus = 'CITIZEN_OR_PR' | 'WORK_PERMIT' | 'NEEDS_SPONSORSHIP'

export interface Profile {
  gradYear?: number
  gradMonth?: number
  coop?: 'yes' | 'no'
  degree?: 'undergrad' | 'masters' | 'phd'
  workStatus?: WorkStatus
}

const KEY = 'radar.profile'

export const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August',
  'September', 'October', 'November', 'December']

export const WORK_STATUS_LABELS: Record<WorkStatus, string> = {
  CITIZEN_OR_PR: 'Canadian citizen or permanent resident',
  WORK_PERMIT: 'Work permit, no sponsorship needed',
  NEEDS_SPONSORSHIP: 'I would need visa sponsorship',
}

/**
 * Only rules measured at 90%+ precision on held-out postings become server filters (see accuracy.json).
 * Graduate-only and graduation date are shown on each posting instead of hiding it.
 */
export function profileQuery(p: Profile): Record<string, string> {
  const q: Record<string, string> = {}
  if (p.coop === 'no') q.excludeCoopRequired = 'true'
  if (p.workStatus) q.workStatus = p.workStatus
  return q
}

/** A warning when the posting's stated graduation window excludes the student, or null. */
export function graduationFit(
  posting: { gradEarliest: string | null; gradLatest: string | null },
  p: Profile,
): string | null {
  if (!p.gradYear || !p.gradMonth || (!posting.gradEarliest && !posting.gradLatest)) return null
  const mine = `${p.gradYear}-${String(p.gradMonth).padStart(2, '0')}`
  const tooEarly = posting.gradEarliest !== null && mine < posting.gradEarliest
  const tooLate = posting.gradLatest !== null && mine > posting.gradLatest
  if (!tooEarly && !tooLate) return null
  const window = posting.gradEarliest && posting.gradLatest
    ? `${monthYear(posting.gradEarliest)} to ${monthYear(posting.gradLatest)}`
    : posting.gradEarliest ? `${monthYear(posting.gradEarliest)} or later` : `${monthYear(posting.gradLatest!)} or earlier`
  return `May not fit your graduation date. They ask for ${window}.`
}

export function describeProfile(p: Profile): string | null {
  const parts: string[] = []
  const degree = { undergrad: 'an undergraduate', masters: "a Master's student", phd: 'a PhD student' }
  parts.push(p.degree ? degree[p.degree] : 'a student')
  if (p.gradYear && p.gradMonth) parts[0] += ` graduating ${MONTHS[p.gradMonth - 1]} ${p.gradYear}`
  if (p.coop) parts.push(p.coop === 'yes' ? 'in a co-op program' : 'not in a co-op program')
  if (p.workStatus) {
    parts.push({ CITIZEN_OR_PR: 'a citizen or permanent resident', WORK_PERMIT: 'with a work permit',
      NEEDS_SPONSORSHIP: 'needing visa sponsorship' }[p.workStatus])
  }
  if (Object.keys(p).length === 0) return null
  return `Showing roles for ${parts.join(', ')}.`
}

/** Reads the stored profile, keeping only allowed values. Storage can be blocked or tampered with. */
export function loadProfile(storage?: Pick<Storage, 'getItem'>): Profile {
  let raw: unknown
  try {
    // Even reading window.localStorage throws when the browser blocks storage.
    raw = JSON.parse((storage ?? window.localStorage).getItem(KEY) ?? '{}')
  } catch {
    return {}
  }
  if (typeof raw !== 'object' || raw === null) return {}
  return sanitize(raw as Record<string, unknown>)
}

function sanitize(raw: Record<string, unknown>): Profile {
  const p: Profile = {}
  if (Number.isInteger(raw.gradYear) && (raw.gradYear as number) >= 2025 && (raw.gradYear as number) <= 2035) p.gradYear = raw.gradYear as number
  if (Number.isInteger(raw.gradMonth) && (raw.gradMonth as number) >= 1 && (raw.gradMonth as number) <= 12) p.gradMonth = raw.gradMonth as number
  if (raw.coop === 'yes' || raw.coop === 'no') p.coop = raw.coop
  if (raw.degree === 'undergrad' || raw.degree === 'masters' || raw.degree === 'phd') p.degree = raw.degree
  if (typeof raw.workStatus === 'string' && Object.hasOwn(WORK_STATUS_LABELS, raw.workStatus)) p.workStatus = raw.workStatus as WorkStatus
  return p
}

export function saveProfile(p: Profile, storage?: Pick<Storage, 'setItem'>): void {
  try {
    (storage ?? window.localStorage).setItem(KEY, JSON.stringify(p))
  } catch {
    // Private mode or blocked storage: the profile still works for this visit.
  }
}

function monthYear(ym: string): string {
  const [year, month] = ym.split('-').map(Number)
  return `${MONTHS[month - 1]} ${year}`
}
