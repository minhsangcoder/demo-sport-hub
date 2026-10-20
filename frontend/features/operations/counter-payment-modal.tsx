'use client'

import { useState } from 'react'
import { api } from '@/lib/api'
import { useAuth } from '@/lib/auth-context'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { money } from '@/features/schedule/types'
import { type Booking } from '@/features/booking/types'
import { Banknote, X, CheckCircle2, User, CreditCard } from 'lucide-react'

interface CounterPaymentModalProps {
  booking: Booking | null
  isOpen: boolean
  onClose: () => void
  onSuccess: (message: string) => void
}

export function CounterPaymentModal({
  booking,
  isOpen,
  onClose,
  onSuccess,
}: CounterPaymentModalProps) {
  const { user } = useAuth()
  const [amountCollected, setAmountCollected] = useState<number>(booking?.amount ?? 0)
  const [paymentMethod, setPaymentMethod] = useState<'CASH' | 'BANK_TRANSFER' | 'POS'>('CASH')
  const [collectorName, setCollectorName] = useState<string>(user?.fullName || 'Nhân viên quầy')
  const [note, setNote] = useState<string>('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  if (!isOpen || !booking) return null

  const paymentMethodLabels: Record<string, string> = {
    CASH: 'Tiền mặt',
    BANK_TRANSFER: 'Chuyển khoản trực tiếp',
    POS: 'Quẹt thẻ / POS',
  }

  const handleConfirmPayment = async (e: React.FormEvent) => {
    e.preventDefault()
    if (busy) return
    setBusy(true)
    setError('')

    try {
      // 1. Create payment record for booking
      const payment = await api<{ id: string }>('/payments', {
        method: 'POST',
        headers: { 'Idempotency-Key': crypto.randomUUID() },
        body: JSON.stringify({
          bookingId: booking.id,
          provider: 'DEMO',
        }),
      })

      // 2. Complete payment
      await api(`/payments/${payment.id}/demo/complete`, {
        method: 'POST',
        body: JSON.stringify({ outcome: 'SUCCESS' }),
      })

      const methodLabel = paymentMethodLabels[paymentMethod]
      const msg = `Đã ghi nhận thanh toán ${money(amountCollected || booking.amount, booking.currency)} (${methodLabel}) bởi ${collectorName}. Booking #${booking.id.slice(0, 8).toUpperCase()} đã được xác nhận!`
      onSuccess(msg)
      onClose()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không thể ghi nhận thanh toán tại quầy.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-xs">
      <div className="relative w-full max-w-lg rounded-2xl bg-white p-6 shadow-2xl dark:bg-slate-900 border border-slate-100 dark:border-slate-800 animate-in fade-in zoom-in-95 duration-150">
        <button
          onClick={onClose}
          disabled={busy}
          className="absolute right-4 top-4 rounded-full p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-600 dark:hover:bg-slate-800"
          aria-label="Đóng"
        >
          <X className="size-5" />
        </button>

        <div className="flex items-center gap-3 pb-3 border-b border-slate-100 dark:border-slate-800">
          <div className="flex size-10 items-center justify-center rounded-xl bg-emerald-100 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300">
            <Banknote className="size-5" />
          </div>
          <div>
            <h2 className="text-lg font-bold text-slate-900 dark:text-white">
              Ghi nhận thanh toán tại quầy
            </h2>
            <p className="text-xs text-slate-500">
              Quyền BOOKING_PAYMENT_COUNTER · Khách trả tiền trực tiếp
            </p>
          </div>
        </div>

        {/* Booking Snapshot */}
        <div className="my-4 rounded-xl bg-slate-50 p-4 dark:bg-slate-800/60 text-sm space-y-1.5 border border-slate-100 dark:border-slate-800">
          <div className="flex justify-between items-center">
            <span className="text-slate-500">Mã booking:</span>
            <span className="font-mono font-bold text-slate-900 dark:text-white">
              #{booking.id.slice(0, 8).toUpperCase()}
            </span>
          </div>
          <div className="flex justify-between items-center">
            <span className="text-slate-500">Khách hàng:</span>
            <span className="font-medium text-slate-800 dark:text-slate-200">
              {booking.guestName || 'Khách tại quầy'} {booking.guestPhone ? `· ${booking.guestPhone}` : ''}
            </span>
          </div>
          <div className="flex justify-between items-center">
            <span className="text-slate-500">Thời gian:</span>
            <span className="font-medium text-slate-800 dark:text-slate-200">
              {new Date(booking.startsAt).toLocaleString('vi-VN')}
            </span>
          </div>
          <div className="flex justify-between items-center pt-1.5 border-t border-slate-200 dark:border-slate-700">
            <span className="font-semibold text-slate-700 dark:text-slate-300">Tổng tiền cần thu:</span>
            <span className="text-lg font-black text-emerald-600 dark:text-emerald-400">
              {money(booking.amount, booking.currency)}
            </span>
          </div>
        </div>

        {error && (
          <p role="alert" className="mb-4 rounded-lg bg-red-50 p-3 text-xs font-semibold text-red-700 dark:bg-red-950/50 dark:text-red-300">
            {error}
          </p>
        )}

        <form onSubmit={handleConfirmPayment} className="space-y-4">
          <div className="grid gap-3 sm:grid-cols-2">
            <label className="grid gap-1.5 text-xs font-bold text-slate-700 dark:text-slate-300">
              Số tiền đã thu *
              <Input
                type="number"
                min={0}
                step={1000}
                value={amountCollected || booking.amount}
                onChange={(e) => setAmountCollected(Number(e.target.value))}
                required
                disabled={busy}
                className="font-bold text-emerald-700"
              />
            </label>

            <label className="grid gap-1.5 text-xs font-bold text-slate-700 dark:text-slate-300">
              Phương thức thanh toán *
              <select
                className="h-10 rounded-lg border border-slate-200 bg-white px-3 text-xs font-semibold text-slate-800 focus:ring-2 focus:ring-emerald-500 dark:border-slate-800 dark:bg-slate-900 dark:text-white"
                value={paymentMethod}
                onChange={(e) => setPaymentMethod(e.target.value as any)}
                disabled={busy}
              >
                <option value="CASH">Tiền mặt (CASH)</option>
                <option value="BANK_TRANSFER">Chuyển khoản trực tiếp</option>
                <option value="POS">Quẹt thẻ POS</option>
              </select>
            </label>
          </div>

          <label className="grid gap-1.5 text-xs font-bold text-slate-700 dark:text-slate-300">
            Người thu tiền (Nhân viên phụ trách) *
            <Input
              value={collectorName}
              onChange={(e) => setCollectorName(e.target.value)}
              placeholder="Họ tên nhân viên thu tiền"
              required
              disabled={busy}
            />
          </label>

          <label className="grid gap-1.5 text-xs font-bold text-slate-700 dark:text-slate-300">
            Ghi chú giao dịch (tùy chọn)
            <Input
              value={note}
              onChange={(e) => setNote(e.target.value)}
              placeholder="Ví dụ: Đã nhận đủ tiền mặt từ khách vãng lai"
              disabled={busy}
            />
          </label>

          <div className="flex items-center justify-end gap-3 pt-3">
            <Button
              type="button"
              variant="outline"
              onClick={onClose}
              disabled={busy}
              className="text-xs"
            >
              Hủy
            </Button>
            <Button
              type="submit"
              disabled={busy}
              className="gap-2 bg-emerald-600 hover:bg-emerald-700 text-white font-bold text-xs px-5 py-2.5 rounded-xl shadow-md"
            >
              <CheckCircle2 className="size-4" />
              <span>{busy ? 'Đang ghi nhận…' : 'Xác nhận đã thu tiền'}</span>
            </Button>
          </div>
        </form>
      </div>
    </div>
  )
}
