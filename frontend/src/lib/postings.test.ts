import { describe, expect, it } from 'vitest'
import {
  type PostingSummary, age, buildQuery, eligibilityLines, filtersFromUrl, heat, timeAgo,
} from './postings'

describe('buildQuery', () => {
  it('omits filters that are not set', () => {
    expect(buildQuery({ q: '  ', category: '', recent: false, page: 0 })).toBe('')
  })

  it('includes every filter that is set', () => {
    expect(buildQuery({ q: ' stripe ', category: 'SOFTWARE', recent: true, page: 2 }, { excludeCoopRequired: 'true' }))
      .toBe('q=stripe&category=SOFTWARE&postedWithinHours=48&page=2&excludeCoopRequired=true')
  })
})

describe('timeAgo', () => {
  const now = new Date('2026-10-02T12:00:00Z')

  it.each([
    ['2026-10-02T11:59:30Z', 'just now'],
    ['2026-10-02T11:15:00Z', '45m ago'],
    ['2026-10-02T09:00:00Z', '3h ago'],
    ['2026-09-29T12:00:00Z', '3d ago'],
  ])('%s is %s', (iso, expected) => {
    expect(timeAgo(iso, now)).toBe(expected)
  })
})

describe('heat', () => {
  const now = new Date('2026-10-02T12:00:00Z')

  it.each([
    ['2026-10-02T07:00:00Z', 'hot'],
    ['2026-10-01T00:00:00Z', 'warm'],
    ['2026-09-30T11:00:00Z', 'cold'],
  ])('%s is %s', (iso, expected) => {
    expect(heat(iso, now)).toBe(expected)
  })
})

describe('eligibilityLines', () => {
  const base: PostingSummary = {
    id: 1, company: 'Acme', title: 'Intern', location: 'Toronto, ON', url: 'https://x.test',
    category: 'SOFTWARE', coopRequirement: 'UNSPECIFIED', mustReturnToSchool: false, graduateDegreeRequired: false, gradEarliest: null, gradLatest: null, workAuthorization: 'UNSPECIFIED',
    termMonths: [], skills: [], firstSeenAt: '2026-10-02T00:00:00Z',
  }

  it('says nothing when the posting says nothing', () => {
    expect(eligibilityLines(base)).toEqual([])
  })

  it('leads with a graduate-only rule', () => {
    expect(eligibilityLines({ ...base, graduateDegreeRequired: true, coopRequirement: 'NOT_REQUIRED' })[0])
      .toBe("For Master's or PhD students.")
  })

  it('states work authorization limits', () => {
    expect(eligibilityLines({ ...base, workAuthorization: 'NO_SPONSORSHIP' })).toEqual(['No visa sponsorship.'])
  })

  it('puts the co-op rule first', () => {
    expect(eligibilityLines({ ...base, coopRequirement: 'REQUIRED', mustReturnToSchool: true, termMonths: [4, 8] }))
      .toEqual(['Requires a school co-op program.', 'You must return to school after the term.', '4 or 8 month term.'])
  })
})

describe('age', () => {
  const now = new Date('2026-10-02T12:00:00Z')

  it('drops "ago" for the column', () => {
    expect(age('2026-10-02T11:24:00Z', now)).toBe('36m')
    expect(age('2026-10-02T11:59:59Z', now)).toBe('now')
  })
})

describe('filtersFromUrl', () => {
  it('round-trips with buildQuery', () => {
    const f = { q: 'stripe', category: 'DATA' as const, recent: true, page: 1 }
    expect(filtersFromUrl(buildQuery(f))).toEqual(f)
  })

  it('ignores junk', () => {
    expect(filtersFromUrl('?page=1.7').page).toBe(1)
    expect(filtersFromUrl('?page=99999999').page).toBe(10_000)
    expect(filtersFromUrl('?category=<script>&page=-4')).toEqual(
      { q: '', category: '', recent: false, page: 0 })
  })
})
