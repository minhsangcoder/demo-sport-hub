'use client'
import { RequireAuth } from '@/features/auth/require-auth'
import { Loaded } from '@/features/operations/operations-list'
export function OperationBookings({ owner = false }: { owner?: boolean }) {
  return (
    <RequireAuth roles={[owner ? 'OWNER' : 'STAFF']}>
      <Loaded owner={owner} />
    </RequireAuth>
  )
}
