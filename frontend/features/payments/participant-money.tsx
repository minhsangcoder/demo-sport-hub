'use client'
import { useState } from 'react'
import { api } from '@/lib/api'
import { useApi } from '@/lib/use-api'
import { useAuth } from '@/lib/auth-context'
import { Button } from '@/components/ui/button'
import { money } from '@/features/schedule/types'
import { BankDestinationForm } from './bank-destination-form'
import { obligationKinds, obligationLabels, type Finance, type Obligation } from './finance-types'

export function ParticipantMoney({ bookingId }: { bookingId?: string }) {
  const { user } = useAuth()
  const resource = useApi<Finance>(
    user?.roles.includes('CUSTOMER') ? '/bookings/facility-finance/mine' : null,
  )
  if (!user?.roles.includes('CUSTOMER')) return null
  const obligations =
    resource.data?.obligations.filter((item) => !bookingId || item.bookingId === bookingId) ?? []
  const settlements =
    resource.data?.settlements.filter((item) => !bookingId || item.bookingId === bookingId) ?? []
  return (
    <section className="mt-6 space-y-4 rounded-2xl border bg-white p-5 text-slate-900">
      <div className="flex items-center justify-between gap-4">
        <h2 className="font-bold">Khoản cơ sở trả và hoàn của bạn</h2>
        <Button variant="outline" onClick={resource.reload}>
          Tải lại
        </Button>
      </div>
      {resource.loading && <p role="status">Đang tải nghĩa vụ của cơ sở…</p>}
      {resource.error && (
        <p role="alert" className="text-red-700">
          {resource.error}
        </p>
      )}
      {settlements.map((item) => (
        <p key={item.acquisitionId} className="rounded-xl bg-slate-50 p-4 text-sm">
          {item.sellerId === user.id
            ? `Cơ sở phải trả bạn ${money(Number(item.sellerNetAmount))}, sau phí cơ sở ${money(Number(item.feeAmount))}.`
            : 'Bạn nhận quyền sử dụng sau khi thanh toán đủ và bàn giao; không chờ cơ sở trả người bán.'}
        </p>
      ))}
      {obligations.map((item) => (
        <RecipientObligation key={`${user.id}:${item.id}`} item={item} onSaved={resource.reload} />
      ))}
      {!resource.loading && !resource.error && obligations.length + settlements.length === 0 && (
        <p className="text-sm text-slate-500">Chưa có khoản trả hoặc hoàn cho bạn.</p>
      )}
      <p className="text-xs text-slate-600">
        Tiền chi được cơ sở xác nhận bằng mã giao dịch; đây không phải xác thực tự động của ngân
        hàng. Nếu tiền chưa đến, liên hệ cơ sở đối soát.
      </p>
    </section>
  )
}
function RecipientObligation({ item, onSaved }: { item: Obligation; onSaved: () => void }) {
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
          reason: 'Người nhận chọn tài khoản để cơ sở chuyển tiền',
        }),
      })
      setRevision('')
      onSaved()
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : 'Không thể cập nhật tài khoản nhận.')
      onSaved()
    } finally {
      setBusy(false)
    }
  }
  return (
    <article className="space-y-3 rounded-xl border p-4 text-sm">
      <h3 className="font-semibold">
        {obligationKinds[item.kind] ?? 'Khoản phải trả'}: {money(Number(item.amount))}
      </h3>
      <p>{obligationLabels[item.state] ?? 'Đang đồng bộ'}</p>
      {item.confirmedAt && (
        <p>
          Cơ sở xác nhận ngày {new Date(item.confirmedAt).toLocaleString('vi-VN')} · mã{' '}
          {item.bankReference}
        </p>
      )}
      {['AWAITING_BANK_DETAILS', 'UNPAID'].includes(item.state) && (
        <>
          <BankDestinationForm value={revision} onChange={setRevision} disabled={busy} />
          <Button variant="outline" disabled={!revision || busy} onClick={save}>
            Đồng ý dùng tài khoản này nhận khoản tiền
          </Button>
        </>
      )}
      {error && (
        <p role="alert" className="text-red-700">
          {error}
        </p>
      )}
    </article>
  )
}
