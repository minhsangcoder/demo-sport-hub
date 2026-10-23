/** Vietnamese presentation labels. Wire values, permissions and identifiers stay unchanged. */
import { labels } from './messages/status-labels.messages'

export function displayLabel(value: unknown, fallback = 'Chưa xác định'): string {
  return typeof value === 'string' && Object.hasOwn(labels, value) ? labels[value] : fallback
}

export function timezoneLabel(timezone?: string | null): string {
  if (!timezone) return 'giờ của cơ sở'
  if (['Asia/Ho_Chi_Minh', 'Asia/Saigon'].includes(timezone)) return 'giờ Việt Nam (UTC+7)'
  if (['UTC', 'Etc/UTC', 'Etc/GMT'].includes(timezone)) return 'giờ quốc tế (UTC)'
  try {
    return (
      new Intl.DateTimeFormat('vi-VN', { timeZone: timezone, timeZoneName: 'longGeneric' })
        .formatToParts(new Date())
        .find((part) => part.type === 'timeZoneName')?.value ?? 'giờ của cơ sở'
    )
  } catch {
    return 'giờ của cơ sở'
  }
}

import { fieldLabels } from './messages/field-labels.messages'

export function fieldLabel(field: string): string {
  const key = field.replace(/[_\-.]/g, '').toLowerCase()
  return Object.hasOwn(fieldLabels, key) ? fieldLabels[key] : 'Thông tin bổ sung'
}
