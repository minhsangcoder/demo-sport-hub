'use client'
import { type Booking } from '@/features/booking/types'
import type { Facility } from '@/features/facility/types'
import { checkInBooking } from '@/features/operations/check-in'
import { Access } from '@/features/operations/operation-types'
import { useAuth } from '@/lib/auth-context'
import { useApi } from '@/lib/use-api'
import { useRef, useState } from 'react'
export function useOperationBookings({ owner }: { owner: boolean }) {
  const { user } = useAuth()
  const checkingIn = useRef(false)
  const owned = useApi<Facility[]>(owner ? '/owner/facilities' : null)
  const access = useApi<Access>(owner ? null : '/users/me/access')
  const publicFacilities = useApi<Facility[]>(owner ? null : '/facilities')
  const [selection, setSelection] = useState(() =>
    typeof window === 'undefined'
      ? ''
      : (new URLSearchParams(window.location.search).get('facilityId') ?? ''),
  )
  const [filter, setFilter] = useState('')
  const [page, setPage] = useState(0)
  const [query, setQuery] = useState('')
  const [quickQrOpen, setQuickQrOpen] = useState(false)
  const [cardTokens, setCardTokens] = useState<Record<string, string>>({})
  const [paymentModalBooking, setPaymentModalBooking] = useState<Booking | null>(null)
  const facilities = owner
    ? owned.data
    : access.data?.facilityBindings
        .filter((b) =>
          b.permissions.some((p) =>
            ['BOOKING_READ', 'BOOKING_REFUND', 'TRANSFER_PAYOUT'].includes(p),
          ),
        )
        .map((b, index) => ({
          id: b.facilityId,
          name:
            publicFacilities.data?.find((f) => f.id === b.facilityId)?.name ||
            'Cơ sở được phân công ' + (index + 1),
        }))
  const facilityId =
    (facilities?.some((f) => f.id === selection) ? selection : '') || facilities?.[0]?.id
  const bookings = useApi<{ items: Booking[]; total: number; totalPages: number }>(
    facilityId &&
      (owner ||
        access.data?.facilityBindings
          .find((b) => b.facilityId === facilityId)
          ?.permissions.includes('BOOKING_READ'))
      ? `/bookings/management/search?facilityId=${facilityId}${filter ? `&status=${encodeURIComponent(filter)}` : ''}${query.trim() ? `&q=${encodeURIComponent(query.trim())}` : ''}&page=${page}&size=20`
      : null,
  )
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const can = (permission: string) =>
    owner ||
    access.data?.facilityBindings
      .find((b) => b.facilityId === facilityId)
      ?.permissions.includes(permission)
  async function action(booking: Booking, operation: string, token?: string) {
    if (busy || checkingIn.current) return
    checkingIn.current = true
    setBusy(true)
    setError('')
    setMessage('')
    try {
      await checkInBooking(booking, token || '')
      bookings.reload()
      setMessage(operation === 'check-in' ? 'Đã xác nhận khách vào sân.' : 'Đã hoàn tất lượt đặt.')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không thể thực hiện thao tác.')
    } finally {
      checkingIn.current = false
      setBusy(false)
    }
  }

  return {
    owner,
    user,
    checkingIn,
    owned,
    access,
    publicFacilities,
    selection,
    setSelection,
    filter,
    setFilter,
    page,
    setPage,
    query,
    setQuery,
    quickQrOpen,
    setQuickQrOpen,
    cardTokens,
    setCardTokens,
    paymentModalBooking,
    setPaymentModalBooking,
    facilities,
    facilityId,
    bookings,
    busy,
    setBusy,
    error,
    setError,
    message,
    setMessage,
    can,
    action,
  }
}
