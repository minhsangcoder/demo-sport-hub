'use client'
import { useRef, useState } from 'react'
import { api, ApiError } from '@/lib/api'
import { useApi } from '@/lib/use-api'
import { useAuth } from '@/lib/auth-context'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { money } from '@/features/schedule/types'
import { ManualOperationPanel } from './manual-operation-panel'
import { BankDestinationForm } from './bank-destination-form'
import {
  obligationKinds,
  obligationLabels,
  type Finance,
  type Obligation,
  type Operation,
} from './finance-types'

export function FacilityMoneyPanel({
  facilityId,
}: {
  facilityId: string
  canReadBooking?: boolean
}) {
  const { user } = useAuth()
  return <MoneyPanel key={`${user?.id}:${facilityId}`} facilityId={facilityId} />
}
function MoneyPanel({ facilityId }: { facilityId: string }) {
  const { user } = useAuth()
  const resource = useApi<Finance>(`/bookings/facility-finance/facility/${facilityId}`)
  const [operation, setOperation] = useState<Operation | null>(null)
  const [busy, setBusy] = useState('')
  const [error, setError] = useState('')
  const [reason, setReason] = useState('')
  const intents = useRef<Record<string, { body: string; key: string }>>({})
  async function open(item: Obligation, takeover = false) {
    if (busy) return
    setBusy(item.id)
    setError('')
    try {
      let next: Operation
      if (item.operationId) {
        if (takeover) {
          if (!reason.trim())
            throw new Error('Nhập lý do Owner tiếp nhận để đối soát giao dịch đã chuyển.')
          await api(`/bookings/facility-finance/operations/${item.operationId}/review`, {
            method: 'POST',
            headers: { 'Idempotency-Key': crypto.randomUUID() },
            body: JSON.stringify({
              version: item.operationVersion,
              action: 'TAKE_OVER',
              reason: reason.trim(),
              confirmedNotTransferred: false,
            }),
          })
        }
        next = await api<Operation>(`/bookings/facility-finance/operations/${item.operationId}`)
      } else {
        const body = JSON.stringify({ version: item.version })
        intents.current[item.id] ??= { body, key: crypto.randomUUID() }
        const intent = intents.current[item.id]
        next = await api<Operation>(`/bookings/facility-finance/obligations/${item.id}/prepare`, {
          method: 'POST',
          headers: { 'Idempotency-Key': intent.key },
          body: intent.body,
        })
      }
      setOperation(next)
      void resource.reload()
    } catch (failure) {
      if (failure instanceof ApiError && [401, 403].includes(failure.status)) setOperation(null)
      setError(
        failure instanceof Error
          ? failure.message
          : 'Chưa mở được lệnh. Tải lại để kiểm tra người đang xử lý.',
      )
      void resource.reload()
    } finally {
      setBusy('')
    }
  }
  return (
    <section className="mt-6 space-y-4 rounded-2xl border bg-white p-5 text-slate-900">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-lg font-bold">Trả người chuyển nhượng và hoàn tiền</h2>
        <Button variant="outline" onClick={resource.reload} disabled={Boolean(busy)}>
          Tải lại nghĩa vụ
        </Button>
      </div>
      <p className="text-sm text-slate-600">
        Cơ sở chuyển khoản bằng ứng dụng ngân hàng, sau đó nhập mã giao dịch để ghi nhận. Mỗi khoản
        chỉ có một người nhận xử lý. Người mua đã được bàn giao sân không cần chờ khoản chi này.
      </p>
      {resource.loading && <p role="status">Đang tải khoản phải thực hiện…</p>}
      {(error || resource.error) && (
        <p role="alert" className="text-red-700">
          {error || resource.error}
        </p>
      )}
      {user?.roles.includes('OWNER') && (
        <label className="grid gap-2 text-sm">
          Lý do tiếp nhận đối soát (khi người khác đang xử lý)
          <Input
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            maxLength={500}
            disabled={Boolean(busy)}
          />
        </label>
      )}
      {resource.data?.obligations.map((item) => (
        <article key={item.id} className="space-y-3 rounded-xl border p-4 text-sm">
          <h3 className="font-semibold">
            {obligationKinds[item.kind] ?? 'Khoản phải trả'}: {money(Number(item.amount))}
          </h3>
          <p>
            Lượt đặt #{item.bookingId.slice(0, 8).toUpperCase()} ·{' '}
            {obligationLabels[item.state] ?? 'Đang đồng bộ'}
          </p>
          {item.guestBookingId && (
            <p>
              Người nhận là khách vãng lai của lượt đặt; nhân viên thu tiền không phải người hưởng.
            </p>
          )}
          {item.state === 'AWAITING_BANK_DETAILS' && !item.guestBookingId && (
            <p>Đang chờ người nhận tự bổ sung tài khoản trong lịch sử khoản trả/hoàn của họ.</p>
          )}
          {item.guestBookingId && ['AWAITING_BANK_DETAILS', 'UNPAID'].includes(item.state) && (
            <GuestDestination item={item} onSaved={resource.reload} />
          )}
          {item.state === 'MANUALLY_RECORDED' ? (
            <p className="text-emerald-800">
              Cơ sở xác nhận đã chuyển
              {item.confirmedAt ? ` · ${new Date(item.confirmedAt).toLocaleString('vi-VN')}` : ''}
            </p>
          ) : (
            <div className="flex flex-wrap gap-3">
              {item.state === 'UNPAID' && (
                <Button onClick={() => open(item)} disabled={Boolean(busy)}>
                  Nhận xử lý và mở QR chuyển khoản
                </Button>
              )}
              {item.operationId && item.claimedBy === user?.id && (
                <Button variant="outline" onClick={() => open(item)} disabled={Boolean(busy)}>
                  Mở lại lệnh đang xử lý
                </Button>
              )}
              {item.operationId && item.claimedBy !== user?.id && (
                <p className="text-amber-800">Đang được người khác xử lý. Không chuyển thêm.</p>
              )}
              {item.operationId && user?.roles.includes('OWNER') && item.claimedBy !== user.id && (
                <Button
                  variant="outline"
                  onClick={() => open(item, true)}
                  disabled={Boolean(busy) || !reason.trim()}
                >
                  Owner tiếp nhận để đối soát
                </Button>
              )}
            </div>
          )}
        </article>
      ))}
      {!resource.loading && resource.data?.obligations.length === 0 && (
        <p className="text-sm text-slate-500">
          Chưa có khoản cần chuyển trong phạm vi quyền của bạn.
        </p>
      )}
      {operation && (
        <ManualOperationPanel
          key={operation.id}
          operation={operation}
          onChanged={(next) => {
            setOperation(next)
            void resource.reload()
          }}
          onClose={() => {
            setOperation(null)
            void resource.reload()
          }}
        />
      )}
      <p className="text-xs text-slate-600">
        Chỉ dùng QR đang hiển thị cho người nhận xử lý. QR đã lưu bên ngoài không thể thu hồi. Nếu
        chuyển sai tài khoản hoặc nghi trùng, giữ trạng thái đối soát và liên hệ ngân hàng; không
        tạo lệnh chi mới để bù tự động.
      </p>
    </section>
  )
}
function GuestDestination({ item, onSaved }: { item: Obligation; onSaved: () => void }) {
  const [revision, setRevision] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  async function save() {
    if (!revision || busy) return
    setBusy(true)
    setError('')
    try {
      await api(`/bookings/facility-finance/obligations/${item.id}/destination`, {
        method: 'POST',
        headers: { 'Idempotency-Key': crypto.randomUUID() },
        body: JSON.stringify({
          version: item.version,
          destinationRevisionId: revision,
          consentReference: crypto.randomUUID(),
          reason: 'Khách vãng lai đồng ý dùng tài khoản nhận hoàn tại cơ sở',
        }),
      })
      onSaved()
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : 'Chưa cập nhật được tài khoản khách.')
      onSaved()
    } finally {
      setBusy(false)
    }
  }
  return (
    <div>
      <BankDestinationForm
        guestBookingId={item.guestBookingId!}
        value={revision}
        onChange={setRevision}
        disabled={busy}
      />
      <Button variant="outline" onClick={save} disabled={!revision || busy}>
        Xác nhận tài khoản khách đã cung cấp
      </Button>
      {error && (
        <p role="alert" className="text-red-700">
          {error}
        </p>
      )}
    </div>
  )
}
