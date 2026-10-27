import type { Booking, BookingDetail, Payment } from '@/features/booking/types'
import { api } from '@/lib/api'

type Attempt = { key: string; payment?: Payment; completed: boolean }

// Each payer/booking keeps its own attempt across modal closes and network retries.
export class CounterPaymentSession {
  constructor(private readonly currentUser?: string) {}
  private attempts = new Map<string, Attempt>()

  async create(booking: Booking, payerId: string): Promise<Payment> {
    if (this.currentUser && this.currentUser !== payerId)
      throw new Error('Phiên người thu tiền đã thay đổi. Mở lại thao tác bằng tài khoản hiện tại.')
    const identity = `${payerId}:${booking.id}`
    let attempt = this.attempts.get(identity)
    if (!attempt) {
      attempt = { key: crypto.randomUUID(), completed: false }
      this.attempts.set(identity, attempt)
    }
    if (!attempt.payment) {
      attempt.payment = await api<Payment>('/payments', {
        method: 'POST',
        headers: { 'Idempotency-Key': attempt.key },
        body: JSON.stringify({ bookingId: booking.id }),
      })
    }
    return attempt.payment
  }
  async confirm(booking: Booking, payerId: string): Promise<boolean> {
    const payment = await this.create(booking, payerId)
    if (payment.provider !== 'DEMO')
      throw new Error('Dùng QR chuyển khoản và chờ máy chủ xác minh tiền vào cơ sở.')
    const attempt = this.attempts.get(`${payerId}:${booking.id}`)!
    if (!attempt.completed) {
      const completed =
        payment.status === 'SUCCESS'
          ? payment
          : await api<Payment>(`/payments/${payment.id}/demo/complete`, {
              method: 'POST',
              body: JSON.stringify({ outcome: 'SUCCESS' }),
            })
      if (completed.status === 'FAILED' || completed.status === 'EXPIRED') {
        this.attempts.delete(`${payerId}:${booking.id}`)
        throw new Error('Thanh toán mô phỏng thất bại hoặc hết hạn. Bạn có thể thử lại.')
      }
      if (completed.status !== 'SUCCESS')
        throw new Error('Thanh toán mô phỏng chưa được xác nhận. Hãy thử kiểm tra lại.')
      attempt.payment = completed
      attempt.completed = true
    }
    for (let i = 0; i < 15; i++) {
      const detail = await api<BookingDetail>(`/bookings/${booking.id}`)
      if (detail.booking.status === 'CONFIRMED') return true
      if (detail.booking.status !== 'PENDING')
        throw new Error(
          'Lượt đặt không còn chờ xác nhận. Vui lòng kiểm tra lịch sử và đối soát thanh toán.',
        )
      if (i < 14) await new Promise((resolve) => setTimeout(resolve, 600))
    }
    return false
  }
}
