'use client'
import type { Group } from '@/features/groups/types'
import { Status } from '@/features/integration/shared'
import { useAuth } from '@/lib/auth-context'
import { type PlayGroup } from '@/lib/business-api'
import { useApi } from '@/lib/use-api'
import { MemberQr } from './member-qr'
import { PlayWorkspace } from './play-workspace'
export function GroupExtras({ group: g }: { group: Group }) {
  const { user } = useAuth()
  const root = useApi<PlayGroup>(`/groups/${g.id}/play-group`)
  return (
    <div className="mt-6 space-y-6">
      {g.state === 'CONFIRMED' && ['CONFIRMED', 'CHECKED_IN'].includes(g.booking.status) && (
        <MemberQr id={g.id} owner={g.ownerId === user?.id} />
      )}
      <Status loading={root.loading} error={root.error} />
      {root.data && (
        <PlayWorkspace
          root={root.data}
          reload={root.reload}
          completed={g.booking.status === 'COMPLETED'}
          terminal={['COMPLETED', 'CANCELLED', 'EXPIRED'].includes(g.booking.status)}
        />
      )}
    </div>
  )
}
