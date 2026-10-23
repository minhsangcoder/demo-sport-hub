'use client'
import { StaffGroupAttendance } from '@/features/groups/staff-attendance'
import { Frame, Status } from '@/features/integration/shared'
import { CounterPaymentModal } from '@/features/operations/counter-payment-modal'
import { OperationBookingCard } from '@/features/operations/operation-booking-card'
import { OperationHistory } from '@/features/operations/operation-history'
import { useOperationalDetail } from '@/features/operations/use-operational-detail'
import Link from 'next/link'
export function OperationalDetail({ id }: { id: string }) {
  const model = useOperationalDetail({ id })
  const { workspace, r, a, b, can, paymentOpen, setPaymentOpen } = model
  return (
    <Frame title="Chi tiết vận hành lượt đặt">
      <Link href={`/${workspace || 'staff'}/bookings`} className="text-emerald-700 underline">
        Về lịch vận hành
      </Link>
      <Status loading={r.loading} error={r.error || a.error} />
      {r.data && b && (
        <>
          <OperationBookingCard model={model} />
          <OperationHistory model={model} />
          <StaffGroupAttendance
            id={id}
            allowed={can('BOOKING_CHECK_IN')}
            readable={can('BOOKING_READ')}
          />
          <CounterPaymentModal
            booking={b}
            isOpen={paymentOpen}
            onClose={() => setPaymentOpen(false)}
            onSuccess={() => r.reload()}
          />
        </>
      )}
    </Frame>
  )
}
