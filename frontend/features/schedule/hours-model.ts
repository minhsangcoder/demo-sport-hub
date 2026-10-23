'use client'
import { type Hours } from '@/features/schedule/types'

export type Interval = Pick<Hours, 'dayOfWeek' | 'opensAt' | 'closesAt' | 'slotMinutes'>
export type TimeRange = { opensAt: string; closesAt: string }
export type WeekdayException = { dayOfWeek: number; closed: boolean; ranges: TimeRange[] }
export interface ParsedSchedule {
  defaultRanges: TimeRange[]
  weekdayExceptions: WeekdayException[]
}
export function parseInitialSchedule(initial: Interval[]): ParsedSchedule {
  if (!initial || initial.length === 0) {
    return { defaultRanges: [{ opensAt: '06:00', closesAt: '22:00' }], weekdayExceptions: [] }
  }
  const dayMap = new Map<number, TimeRange[]>()
  for (let d = 1; d <= 7; d++) dayMap.set(d, [])
  for (const item of initial) {
    const list = dayMap.get(item.dayOfWeek) || []
    list.push({ opensAt: item.opensAt.slice(0, 5), closesAt: item.closesAt.slice(0, 5) })
    dayMap.set(item.dayOfWeek, list)
  }
  for (let d = 1; d <= 7; d++) {
    dayMap.get(d)!.sort((a, b) => a.opensAt.localeCompare(b.opensAt))
  }
  const sigMap = new Map<string, number>()
  for (let d = 1; d <= 7; d++) {
    const list = dayMap.get(d)!
    const sig =
      list.length === 0 ? 'CLOSED' : list.map((r) => `${r.opensAt}-${r.closesAt}`).join(';')
    sigMap.set(sig, (sigMap.get(sig) || 0) + 1)
  }
  let dominantSig = ''
  let maxCount = -1
  for (const [sig, count] of sigMap.entries()) {
    if (sig !== 'CLOSED' && count > maxCount) {
      maxCount = count
      dominantSig = sig
    }
  }
  if (!dominantSig) dominantSig = 'CLOSED'
  let defaultRanges: TimeRange[] = [{ opensAt: '06:00', closesAt: '22:00' }]
  if (dominantSig !== 'CLOSED') {
    defaultRanges = dominantSig.split(';').map((part) => {
      const [opensAt, closesAt] = part.split('-')
      return { opensAt, closesAt }
    })
  }
  const weekdayExceptions: WeekdayException[] = []
  for (let d = 1; d <= 7; d++) {
    const list = dayMap.get(d)!
    const sig =
      list.length === 0 ? 'CLOSED' : list.map((r) => `${r.opensAt}-${r.closesAt}`).join(';')
    if (sig !== dominantSig) {
      if (sig === 'CLOSED') {
        weekdayExceptions.push({
          dayOfWeek: d,
          closed: true,
          ranges: [{ opensAt: '06:00', closesAt: '22:00' }],
        })
      } else {
        weekdayExceptions.push({ dayOfWeek: d, closed: false, ranges: list.slice(0, 2) })
      }
    }
  }
  return { defaultRanges, weekdayExceptions }
}
