'use client'

import { Button } from '@/components/ui/button'
import { RefundPolicyNotice } from '@/features/booking/refund-policy-notice'
import type { Booking } from '@/features/booking/types'
import { money } from '@/features/schedule/types'
import { useAuth } from '@/lib/auth-context'
import { Banknote,X } from 'lucide-react'
import { useRef,useState } from 'react'
import { CounterPaymentSession } from './counter-payment'

interface CounterPaymentModalProps {
  booking: Booking | null
  isOpen: boolean
  onClose: () => void
  onSuccess: (message: string) => void
}

export function CounterPaymentModal(props: CounterPaymentModalProps) {
  const { user } = useAuth()
  const [session] = useState(() => new CounterPaymentSession())
  if (!props.isOpen || !props.booking || !user) return null
  return <PaymentForm key={`${user.id}:${props.booking.id}`} {...props} booking={props.booking} payerId={user.id} session={session}/>
}

function PaymentForm({ booking, payerId, session, onClose, onSuccess }: CounterPaymentModalProps & {
  booking: Booking; payerId: string; session: CounterPaymentSession
}) {
  const pending = useRef(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')

  async function confirm(event: React.FormEvent) {
    event.preventDefault()
    if (pending.current) return
    pending.current = true
    setBusy(true)
    setError('')
    try {
      const confirmed = await session.confirm(booking, payerId)
      if (confirmed) {
        onSuccess(`Thanh toán mô phỏng ${money(booking.amount, booking.currency)} đã thành công. lượt đặt #${booking.id.slice(0, 8).toUpperCase()} đã được xác nhận.`)
        onClose()
      } else {
        setMessage('Thanh toán mô phỏng đã thành công, lượt đặt đang chờ hệ thống xác nhận. Bạn có thể kiểm tra lại mà không tạo giao dịch mới.')
      }
    } catch (error) {
      setError(error instanceof Error ? error.message : 'Không thể thực hiện thanh toán mô phỏng.')
    } finally {
      pending.current = false
      setBusy(false)
    }
  }

  return <div role="dialog" aria-modal="true" aria-labelledby="counter-payment-title" className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-xs">
    <div className="relative w-full max-w-lg rounded-2xl border bg-white p-6 shadow-2xl dark:bg-slate-900">
      <button onClick={onClose} disabled={busy} aria-label="Đóng" className="absolute right-4 top-4 text-slate-400"><X className="size-5"/></button>
      <div className="flex items-center gap-3 border-b pb-3">
        <Banknote className="size-7 text-emerald-600"/>
        <div><h2 id="counter-payment-title" className="text-lg font-bold">Thanh toán mô phỏng tại quầy</h2><p className="text-xs text-slate-500">Mô phỏng thanh toán, không phát sinh giao dịch tiền thật.</p></div>
      </div>
      <div className="my-4 space-y-2 rounded-xl bg-slate-50 p-4 text-sm dark:bg-slate-800">
        <p>Lượt đặt: <strong>#{booking.id.slice(0, 8).toUpperCase()}</strong></p>
        <p>{booking.guestName || 'Khách có tài khoản'} {booking.guestPhone || ''}</p>
        <p>{new Date(booking.startsAt).toLocaleString('vi-VN', { timeZone: booking.priceSnapshot.timezone })}</p>
        <p>Số tiền mô phỏng: <strong>{money(booking.amount, booking.currency)}</strong></p>
      </div>
      <RefundPolicyNotice policy={booking.priceSnapshot.refundPolicy} />
      {error && <p role="alert" className="mb-4 rounded-lg bg-red-50 p-3 text-sm text-red-700">{error}</p>}
      {message && <p role="status" className="mb-4 rounded-lg bg-amber-50 p-3 text-sm text-amber-900">{message}</p>}
      <form onSubmit={confirm} className="flex justify-end gap-3">
        <Button type="button" variant="outline" onClick={onClose} disabled={busy}>Đóng</Button>
        <Button type="submit" disabled={busy} className="bg-emerald-600 text-white">{busy ? 'Đang kiểm tra…' : message ? 'Kiểm tra xác nhận lượt đặt' : 'Mô phỏng thanh toán thành công'}</Button>
      </form>
    </div>
  </div>
}
