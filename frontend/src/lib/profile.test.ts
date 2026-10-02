import { describe, expect, it } from 'vitest'
import { type Profile, describeProfile, graduationFit, loadProfile, profileQuery } from './profile'

const ME: Profile = { gradYear: 2028, gradMonth: 4, coop: 'no', degree: 'undergrad', workStatus: 'WORK_PERMIT' }

describe('profileQuery', () => {
  it('sends nothing for an empty profile', () => {
    expect(profileQuery({})).toEqual({})
  })

  it('turns a profile into the filters the API applies', () => {
    expect(profileQuery(ME)).toEqual({
      excludeCoopRequired: 'true',
      workStatus: 'WORK_PERMIT',
    })
  })

  it('keeps co-op roles for co-op students', () => {
    expect(profileQuery({ coop: 'yes', degree: 'masters' })).toEqual({})
  })

  it('never hides on rules without held-out evidence (degree level, graduation date)', () => {
    expect(profileQuery(ME)).not.toHaveProperty('excludeGraduateOnly')
    expect(profileQuery(ME)).not.toHaveProperty('graduation')
  })
})

describe('graduationFit', () => {
  it('is silent when the posting states no window or the profile has no date', () => {
    expect(graduationFit({ gradEarliest: null, gradLatest: null }, ME)).toBeNull()
    expect(graduationFit({ gradEarliest: '2027-12', gradLatest: null }, {})).toBeNull()
  })

  it('is silent when the date fits', () => {
    expect(graduationFit({ gradEarliest: '2027-12', gradLatest: '2028-08' }, ME)).toBeNull()
  })

  it('warns with the stated window when the date falls outside it', () => {
    expect(graduationFit({ gradEarliest: '2028-06', gradLatest: '2028-12' }, ME))
      .toBe('May not fit your graduation date. They ask for June 2028 to December 2028.')
    expect(graduationFit({ gradEarliest: null, gradLatest: '2027-08' }, ME))
      .toBe('May not fit your graduation date. They ask for August 2027 or earlier.')
  })
})

describe('describeProfile', () => {
  it('reads as one sentence', () => {
    expect(describeProfile(ME)).toBe(
      'Showing roles for an undergraduate graduating April 2028, not in a co-op program, with a work permit.')
  })

  it('invites setup when empty', () => {
    expect(describeProfile({})).toBeNull()
  })
})

describe('loadProfile', () => {
  it('drops values that are not allowed', () => {
    const storage = { getItem: () => JSON.stringify({ gradYear: 'x', coop: 'maybe', degree: 'phd', workStatus: 'toString' }) }
    expect(loadProfile(storage)).toEqual({ degree: 'phd' })
  })

  it('survives broken or missing storage', () => {
    expect(loadProfile({ getItem: () => '{not json' })).toEqual({})
    expect(loadProfile({ getItem: () => 'null' })).toEqual({})
    expect(loadProfile({ getItem: () => { throw new Error('blocked') } })).toEqual({})
  })
})
