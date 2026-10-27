'use client'
import dynamic from 'next/dynamic'
import { useState } from 'react'
import { useAuth } from '@/lib/auth-context'
import { useApi } from '@/lib/use-api'
import { RequireAuth } from '@/features/auth/require-auth'
import { useFacility } from '@/features/facility/facility-context'
import { Button } from '@/components/ui/button'
import { PeerPresence } from './peer-presence'
import { peerPhases, type PeerAttempt } from './peer-types'
const CasePanel = dynamic(() => import('./peer-case-panel'), {
  loading: () => <p role="status">Đang mở hồ sơ…</p>,
})
export function PeerOwnerCases() {
  const { facility } = useFacility()
  const { user } = useAuth()
  return (
    <RequireAuth roles={['OWNER']}>
      <Loaded key={`${user?.id}:${facility.id}`} facilityId={facility.id} />
    </RequireAuth>
  )
}
function Loaded({ facilityId }: { facilityId: string }) {
  const [after, setAfter] = useState('')
  const [selected, setSelected] = useState('')
  const read = useApi<PeerAttempt[]>(
    `/transfers/facilities/${facilityId}/cases?size=20${after ? `&after=${after}` : ''}`,
  )
  return (
    <main className="space-y-6">
      <h1 className="text-2xl font-bold">Tranh chấp chuyển nhượng</h1>
      <p>
        Tiền chuyển thẳng giữa khách. Chủ cơ sở chỉ phân xử quyền sân, không thu hoặc hoàn khoản
        P2P.
      </p>
      <PeerPresence facilityId={facilityId} />
      {read.loading && <p role="status">Đang tải hồ sơ của cơ sở…</p>}
      {read.error && (
        <p role="alert" className="text-red-700">
          {read.error}
        </p>
      )}
      {read.data?.length === 0 && <p>Chưa có hồ sơ.</p>}
      <ul className="grid gap-3 sm:grid-cols-2">
        {read.data?.map((row) => (
          <li key={row.id}>
            <button
              type="button"
              onClick={() => setSelected(row.id)}
              className="w-full rounded-xl border p-4 text-left"
            >
              <strong>{peerPhases[row.phase]}</strong>
              <p className="text-sm">
                Phiên {row.id.slice(0, 8)} · {new Date(row.usageDecisionBy).toLocaleString('vi-VN')}
              </p>
            </button>
          </li>
        ))}
      </ul>
      <div className="flex gap-3">
        <Button variant="outline" onClick={read.reload}>
          Tải lại hồ sơ
        </Button>
        {after && (
          <Button variant="outline" onClick={() => setAfter('')}>
            Trang đầu
          </Button>
        )}
        {read.data?.length === 20 && (
          <Button variant="outline" onClick={() => setAfter(read.data!.at(-1)!.id)}>
            Trang tiếp
          </Button>
        )}
      </div>
      {selected && <CasePanel key={selected} acquisitionId={selected} facilityId={facilityId} />}
    </main>
  )
}
