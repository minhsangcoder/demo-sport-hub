'use client'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, Status, useMutation } from '@/features/integration/shared'
import { QrUploadButton } from '@/features/operations/qr-checkin'
import { businessApi, type Attendance } from '@/lib/business-api'
import { useApi } from '@/lib/use-api'
import { useState } from 'react'

export function StaffGroupAttendance({
  id,
  allowed,
  readable = allowed,
}: {
  id: string
  allowed: boolean
  readable?: boolean
}) {
  const r = useApi<Attendance[]>(readable ? `/groups/${id}/attendance` : null),
    a = useMutation()
  const [memberToken, setMemberToken] = useState('')
  if (!readable || !r.data?.length) return null
  return (
    <Card title="Điểm danh theo thành viên">
      <Status error={r.error || a.error} />
      <ul>
        {r.data.map((m) => (
          <li key={m.member_id} className="py-2 text-sm">
            {m.display_name} · {m.checked_in_at ? 'Đã có mặt' : 'Chưa điểm danh'}
          </li>
        ))}
      </ul>
      {allowed && (
        <>
          <form
            className="space-y-3"
            onSubmit={(e) => {
              e.preventDefault()
              const val = memberToken || String(new FormData(e.currentTarget).get('token') || '')
              void a.run(() => businessApi.scanMember(id, val), r.reload)
            }}
          >
            <div className="flex flex-wrap items-end gap-3">
              <div className="grid flex-1 gap-2 text-sm">
                <div className="flex items-center justify-between">
                  <span className="font-semibold text-slate-700">Mã QR thành viên</span>
                  <QrUploadButton
                    onTokenDetected={(token) => {
                      setMemberToken(token)
                      void a.run(() => businessApi.scanMember(id, token), r.reload)
                    }}
                    disabled={a.busy}
                  />
                </div>
                <Input
                  name="token"
                  value={memberToken}
                  onChange={(e) => setMemberToken(e.target.value)}
                  placeholder="Nhập mã thành viên hoặc tải ảnh QR ở trên"
                  required
                  maxLength={1500}
                />
              </div>
              <Button type="submit" className="self-end" disabled={a.busy}>
                Quét mã thành viên
              </Button>
            </div>
          </form>
          <form
            className="grid gap-3 sm:grid-cols-2"
            onSubmit={(e) => {
              e.preventDefault()
              const f = new FormData(e.currentTarget)
              void a.run(
                () =>
                  businessApi.manualMember(id, String(f.get('memberId')), String(f.get('reason'))),
                r.reload,
              )
            }}
          >
            <label className="grid gap-2 text-sm">
              Thành viên thực tế có mặt
              <select name="memberId" className="rounded-lg border p-3">
                {r.data
                  .filter((m) => !m.checked_in_at)
                  .map((m) => (
                    <option key={m.member_id} value={m.member_id}>
                      {m.display_name}
                    </option>
                  ))}
              </select>
            </label>
            <label className="grid gap-2 text-sm">
              Lý do xác nhận thủ công
              <Input name="reason" required minLength={20} maxLength={500} />
            </label>
            <Button disabled={a.busy || r.data.every((m) => m.checked_in_at)}>
              Xác nhận người có mặt
            </Button>
          </form>
        </>
      )}
    </Card>
  )
}
