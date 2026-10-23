'use client'
import { CourtTypes } from './court-types'
import { FacilityShell } from './facility-context'

export function FacilityCourtTypesRoute({ id }: { id: string }) {
  return (
    <FacilityShell id={id}>
      <CourtTypes />
    </FacilityShell>
  )
}
