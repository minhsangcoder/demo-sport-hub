'use client'
import { useState } from 'react'
import { useApi } from '@/lib/use-api'
import { Button } from '@/components/ui/button'
import { usePeerCommand } from './use-peer-command'
import { useAuth } from '@/lib/auth-context'
type PresenceContext = {
  id: string
  courtId: string
  startsAt: string
  endsAt: string
  phase: string
}
export function PeerPresence({ facilityId }: { facilityId: string }) {
  const { user } = useAuth()
  return <PresenceContent key={`${user?.id}:${facilityId}`} facilityId={facilityId} />
}
function PresenceContent({ facilityId }: { facilityId: string }) {
  const path = `/transfers/facilities/${facilityId}/cases`
  const read = useApi<PresenceContext[]>(`${path}/presence-context`)
  const [selected, setSelected] = useState('')
  const [party, setParty] = useState('BUYER')
  const [reason, setReason] = useState('')
  const command = usePeerCommand()
  return (
    <section className="space-y-3 rounded-xl border p-5">
      <h2 className="text-lg font-bold">Ghi nhận có mặt tại sân</h2>
      <p className="text-sm">
        Cần quyền xác nhận vào sân tại cơ sở. Chỉ ghi người đã có mặt thực tế; thời điểm do server
        ghi, không nhập lùi giờ. Có mặt không đồng nghĩa đã được dùng sân.
      </p>
      {read.loading && <p role="status">Đang tải các phiên cần ghi nhận…</p>}
      {read.error && (
        <p role="alert" className="text-red-700">
          {read.error}
        </p>
      )}
      {read.data?.length === 0 && <p>Chưa có phiên cần ghi nhận.</p>}
      {Boolean(read.data?.length) && (
        <form
          className="space-y-3"
          onSubmit={(event) => {
            event.preventDefault()
            if (window.confirm('Bạn đã kiểm tra bên này có mặt tại cơ sở?'))
              void command.run(
                `${path}/${selected}/presence`,
                { party, reason: reason.trim() },
                () => {
                  setReason('')
                  void read.reload()
                },
              )
          }}
        >
          <label className="grid gap-2 text-sm">
            Phiên chuyển nhượng
            <select
              className="rounded-lg border p-3"
              value={selected}
              onChange={(event) => setSelected(event.target.value)}
              required
              disabled={command.busy || command.blocked}
            >
              <option value="">Chọn phiên cần ghi nhận</option>
              {read.data?.map((row) => (
                <option key={row.id} value={row.id}>
                  {row.id.slice(0, 8)} · sân {row.courtId.slice(0, 8)} ·{' '}
                  {new Date(row.startsAt).toLocaleString('vi-VN')}
                </option>
              ))}
            </select>
          </label>
          <label className="grid gap-2 text-sm">
            Bên có mặt
            <select
              value={party}
              onChange={(event) => setParty(event.target.value)}
              className="rounded-lg border p-3"
              disabled={command.busy || command.blocked}
            >
              <option value="BUYER">Người mua (B)</option>
              <option value="SELLER">Người bán (A)</option>
            </select>
          </label>
          <label className="grid gap-2 text-sm">
            Căn cứ ghi nhận
            <textarea
              className="rounded-lg border p-3"
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              required
              maxLength={500}
              rows={2}
              disabled={command.busy || command.blocked}
            />
          </label>
          <Button
            type="submit"
            disabled={command.busy || command.blocked || !selected || !reason.trim()}
          >
            Ghi nhận có mặt thực tế
          </Button>
        </form>
      )}
      {command.error && (
        <p role="alert" className="text-red-700">
          {command.error}
        </p>
      )}
      <Button variant="outline" onClick={read.reload}>
        Tải lại phiên cần ghi nhận
      </Button>
    </section>
  )
}
