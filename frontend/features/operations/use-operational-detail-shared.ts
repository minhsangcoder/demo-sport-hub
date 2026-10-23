import type { BookingDetail } from '@/features/booking/types'
export type Operations = {
  detail: BookingDetail
  counterGuest?: { guestName: string; guestPhone: string }
  statusAudit: {
    id: number
    status: string
    changeSource: string
    recordedAt: string
    previousStatus?: string
    actorId?: string
    bookingVersion: number
  }[]
}
