'use client'
import { useApi } from '@/lib/use-api'
import { FacilityShell } from '@/features/facility/facility-context'
import { CourtTypes } from '@/features/facility/court-types'
import type { ApplicationDetail } from './types'

export function ApplicationCourtTypes({ id }: { id: string }) {
  const r = useApi<ApplicationDetail>(`/owner-applications/${id}`)
  if (!r.data)
    return (
      <p className="p-8" role={r.error ? 'alert' : 'status'}>
        {r.error || 'Đang tải hồ sơ…'}
      </p>
    )
  return (
    <FacilityShell id={r.data.application.facilityId} applicationId={id}>
      <CourtTypes />
    </FacilityShell>
  )
}
