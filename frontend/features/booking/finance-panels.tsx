'use client'
import dynamic from 'next/dynamic'
import { useApi } from '@/lib/use-api'
import type { PaymentRuntime } from '@/features/payments/types'

const FacilityDemo = dynamic(() =>
  import('./demo-finance').then((module) => module.FacilityDemoMoneyPanel),
)
const ParticipantDemo = dynamic(() =>
  import('./demo-finance').then((module) => module.ParticipantDemoMoney),
)
const DemoCancellation = dynamic(() =>
  import('./demo-finance').then((module) => module.PolicyCancellationPanel),
)
const FacilityReal = dynamic(() =>
  import('@/features/payments/facility-money-panel').then((module) => module.FacilityMoneyPanel),
)
const ParticipantReal = dynamic(() =>
  import('@/features/payments/participant-money').then((module) => module.ParticipantMoney),
)
const RealCancellation = dynamic(() =>
  import('@/features/payments/policy-refund-panel').then((module) => module.PolicyRefundPanel),
)

export function FacilityMoneyPanel(props: { facilityId: string; canReadBooking?: boolean }) {
  const runtime = useApi<PaymentRuntime>('/payments/runtime')
  if (runtime.loading && !runtime.data)
    return (
      <p role="status" className="mt-5">
        Đang tải chế độ thu và chi…
      </p>
    )
  if (runtime.error || !runtime.data)
    return (
      <p role="alert" className="mt-5 text-red-700">
        {runtime.error || 'Chưa xác định được chế độ thanh toán.'}
      </p>
    )
  return runtime.data.mode === 'REAL' ? <FacilityReal {...props} /> : <FacilityDemo {...props} />
}
export function ParticipantMoney(props: { bookingId?: string; moneyClass?: string }) {
  const runtime = useApi<PaymentRuntime>(props.moneyClass ? null : '/payments/runtime')
  if (runtime.error)
    return (
      <p role="alert" className="text-red-700">
        {runtime.error}
      </p>
    )
  const mode = props.moneyClass ?? runtime.data?.mode
  if (!mode) return null
  return mode === 'REAL' ? (
    <ParticipantReal bookingId={props.bookingId} />
  ) : (
    <ParticipantDemo bookingId={props.bookingId} />
  )
}
export function PolicyCancellationPanel(props: {
  bookingId: string
  onSuccess: () => void
  moneyClass?: string
  guestBookingId?: string
}) {
  return props.moneyClass === 'REAL' ? (
    <RealCancellation {...props} />
  ) : (
    <DemoCancellation bookingId={props.bookingId} onSuccess={props.onSuccess} />
  )
}
