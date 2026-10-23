'use client'
import { Frame, Status } from '@/features/integration/shared'
import { type PlayGroup } from '@/lib/business-api'
import { useApi } from '@/lib/use-api'
import { PlayWorkspace } from './play-workspace'
export function KeptGroupPage({ id }: { id: string }) {
  const r = useApi<PlayGroup>(`/groups/play-groups/${id}`)
  return (
    <Frame title="Nhóm chơi được giữ lại">
      <Status loading={r.loading} error={r.error} />
      {r.data && <PlayWorkspace root={r.data} reload={r.reload} completed terminal />}
    </Frame>
  )
}
