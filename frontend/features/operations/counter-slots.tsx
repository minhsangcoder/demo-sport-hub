'use client'
import { RequireAuth } from '@/features/auth/require-auth'
import { AvailabilityPage } from '@/features/discovery/availability-page'
import { useSearchParams } from 'next/navigation'
export function CounterSlots({ courtId }: { courtId: string }) {
  const params = useSearchParams()
  const facilityId = params.get('facilityId')
  return (
    <RequireAuth roles={['STAFF']}>
      {facilityId ? (
        <AvailabilityPage facilityId={facilityId} courtId={courtId} counter />
      ) : (
        <p className="p-8">Thiếu cơ sở. Quay lại màn hình vận hành.</p>
      )}
    </RequireAuth>
  )
}
