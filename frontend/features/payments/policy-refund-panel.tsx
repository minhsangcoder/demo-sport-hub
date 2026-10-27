'use client'
import { useRef, useState } from 'react'
import { api } from '@/lib/api'
import { useAuth } from '@/lib/auth-context'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { money } from '@/features/schedule/types'
import { BankDestinationForm } from './bank-destination-form'

type Quote = {
  id: string
  baseAmount: string
  rateBps: number
  targetAmount: string
  expiresAt: string
  allocations: { paymentId: string; payerId: string; baseAmount: string; refundAmount: string }[]
}
export function PolicyRefundPanel(props: {
  bookingId: string
  onSuccess: () => void
  guestBookingId?: string
}) {
  const { user } = useAuth()
  return <RefundPanel key={`${user?.id}:${props.bookingId}`} {...props} />
}
function RefundPanel({
  bookingId,
  onSuccess,
  guestBookingId,
}: {
  bookingId: string
  onSuccess: () => void
  guestBookingId?: string
}) {
  const { user } = useAuth()
  const [quote, setQuote] = useState<Quote | null>(null)
  const [banks, setBanks] = useState<Record<string, string>>({})
  const [reason, setReason] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const intent = useRef<{ body: string; key: string } | null>(null)
  async function preview() {
    if (busy) return
    setBusy(true)
    setError('')
    setQuote(null)
    setBanks({})
    try {
      setQuote(await api<Quote>(`/bookings/${bookingId}/cancellation-quote`, { method: 'POST' }))
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : 'Chưa tính được khoản hoàn.')
    } finally {
      setBusy(false)
    }
  }
  async function cancel() {
    if (busy || !quote || !reason.trim()) return
    const body = JSON.stringify({
      quoteId: quote.id,
      reason: reason.trim(),
      bankDestinations: Object.entries(banks)
        .filter(([, revision]) => revision)
        .map(([paymentId, destinationRevisionId]) => ({ paymentId, destinationRevisionId })),
    })
    if (intent.current?.body !== body) intent.current = { body, key: crypto.randomUUID() }
    setBusy(true)
    setError('')
    try {
      await api(`/bookings/${bookingId}/cancel-with-policy`, {
        method: 'POST',
        headers: { 'Idempotency-Key': intent.current.key },
        body,
      })
      setQuote(null)
      onSuccess()
    } catch (failure) {
      setError(
        failure instanceof Error
          ? failure.message
          : 'Chưa xác định kết quả hủy. Kiểm tra lại bằng cùng yêu cầu trước khi tạo mới.',
      )
    } finally {
      setBusy(false)
    }
  }
  const yours =
    quote?.allocations.filter(
      (item) => Number(item.refundAmount) > 0 && (guestBookingId || item.payerId === user?.id),
    ) ?? []
  const missing = yours.some((item) => !banks[item.paymentId])
  return (
    <section className="mt-5 space-y-4 rounded-xl border border-rose-200 bg-white p-5 text-slate-900">
      <h3 className="font-semibold text-rose-800">Hủy theo chính sách hoàn của cơ sở</h3>
      <p className="text-sm">
        Khoản hoàn vận hành hợp lệ tính trên tiền đã được cơ sở ghi nhận. Không áp dụng hoàn tiền
        khi người mua lại tự hủy lượt đặt đã chuyển nhượng.
      </p>
      <Button variant="outline" onClick={preview} disabled={busy}>
        Xem khoản hoàn khi hủy
      </Button>
      {quote && (
        <>
          <p className="text-sm">
            Tiền làm căn cứ: {money(Number(quote.baseAmount))} · tỷ lệ hoàn{' '}
            {(quote.rateBps / 100).toLocaleString('vi-VN')}% · được hoàn{' '}
            <strong>{money(Number(quote.targetAmount))}</strong>.
          </p>
          {yours.map((item) => (
            <div key={item.paymentId}>
              <p className="text-sm">
                Khoản của {guestBookingId ? 'khách vãng lai' : 'bạn'}:{' '}
                {money(Number(item.refundAmount))}
              </p>
              <BankDestinationForm
                value={banks[item.paymentId] ?? ''}
                guestBookingId={guestBookingId}
                onChange={(revision) =>
                  setBanks((previous) => ({ ...previous, [item.paymentId]: revision }))
                }
                disabled={busy}
              />
            </div>
          ))}
          {quote.allocations.some(
            (item) => Number(item.refundAmount) > 0 && item.payerId !== user?.id,
          ) &&
            !guestBookingId && (
              <p className="text-sm text-amber-800">
                Mỗi người trả tiền sẽ nhận khoản hoàn riêng và tự bổ sung tài khoản của mình. Bạn
                không nhập tài khoản thay thành viên khác.
              </p>
            )}
          <label className="grid gap-2 text-sm">
            Lý do hủy
            <Input
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              maxLength={500}
              disabled={busy}
            />
          </label>
          <p className="text-xs text-slate-600">
            Báo giá có hiệu lực đến {new Date(quote.expiresAt).toLocaleTimeString('vi-VN')}. Xác
            nhận hủy sẽ khóa quyền sử dụng ngay; khoản hoàn có thể còn chờ cơ sở chuyển.
          </p>
          <Button
            onClick={cancel}
            disabled={busy || missing || !reason.trim()}
            className="bg-rose-600 text-white"
          >
            {busy ? 'Đang ghi nhận…' : 'Đồng ý khoản hoàn và hủy lượt đặt'}
          </Button>
        </>
      )}
      {error && (
        <p role="alert" className="text-sm text-red-700">
          {error}
        </p>
      )}
    </section>
  )
}
