import { api } from '@/lib/api'
import type { Booking, BookingDetail, Payment } from '@/features/booking/types'

type Attempt = { key: string; payment?: Payment; completed: boolean }

// Each payer/booking keeps its own attempt across modal closes and network retries.
export class CounterPaymentSession {
  private attempts = new Map<string, Attempt>()

  async confirm(booking: Booking, payerId: string): Promise<boolean> {
    const identity = `${payerId}:${booking.id}`
    let attempt = this.attempts.get(identity)
    if (!attempt) {
      attempt = { key: crypto.randomUUID(), completed: false }
      this.attempts.set(identity, attempt)
    }
    if (!attempt.payment) {
      attempt.payment = await api<Payment>('/payments', {
        method: 'POST', headers: { 'Idempotency-Key': attempt.key },
        body: JSON.stringify({ bookingId: booking.id, provider: 'DEMO' }),
      })
    }
    if (!attempt.completed) {
      const payment = attempt.payment.status === 'SUCCESS' ? attempt.payment : await api<Payment>(`/payments/${attempt.payment.id}/demo/complete`, {
        method: 'POST', body: JSON.stringify({ outcome: 'SUCCESS' }),
      })
      if (payment.status === 'FAILED' || payment.status === 'EXPIRED') {
        this.attempts.delete(identity)
        throw new Error('Thanh toán DEMO thất bại hoặc hết hạn. Bạn có thể thử lại.')
      }
      if (payment.status !== 'SUCCESS') throw new Error('Thanh toán DEMO chưa được xác nhận. Hãy thử kiểm tra lại.')
      attempt.payment = payment
      attempt.completed = true
    }
    for (let i = 0; i < 15; i++) {
      const detail = await api<BookingDetail>(`/bookings/${booking.id}`)
      if (detail.booking.status === 'CONFIRMED') return true
      if (detail.booking.status !== 'PENDING') throw new Error('Booking không còn chờ xác nhận. Vui lòng kiểm tra lịch sử và đối soát thanh toán.')
      if (i < 14) await new Promise(resolve => setTimeout(resolve, 600))
    }
    return false
  }
}
