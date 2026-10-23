'use client'
import { Button } from '@/components/ui/button'
import { Card, Status, useMutation } from '@/features/integration/shared'
import { api } from '@/lib/api'
import { displayLabel } from '@/lib/display-labels'
import { translateError } from '@/lib/error-translator'
import { useApi } from '@/lib/use-api'
type PaymentSync = {
  state: 'AWAITING_BOOKING' | 'ACKNOWLEDGED' | 'REQUIRES_REVIEW'
  attempts: number
  last_error_code?: string
  booking_status?: string
}
export function PaymentSynchronization({ id }: { id: string }) {
  const r = useApi<PaymentSync>(`/payments/${id}/synchronization`),
    a = useMutation()
  const retry =
    r.data?.state === 'AWAITING_BOOKING' ||
    ['BOOKING_UNAVAILABLE', 'BOOKING_RECEIPT_MISSING'].includes(r.data?.last_error_code ?? '')
  return (
    <Card title="Đồng bộ kết quả với lượt đặt">
      <Status loading={r.loading} error={r.error || a.error} />
      {r.data && (
        <>
          <p>
            {r.data.state === 'ACKNOWLEDGED'
              ? 'Lượt đặt đã tiếp nhận kết quả thanh toán'
              : r.data.state === 'REQUIRES_REVIEW'
                ? 'Cần rà soát kết quả trước khi xác nhận lượt đặt'
                : 'Đang chờ lượt đặt tiếp nhận kết quả'}
          </p>
          <p className="text-sm text-slate-500">
            {r.data.last_error_code ? `${translateError(r.data.last_error_code)} · ` : ''}
            {displayLabel(r.data.booking_status)} · {r.data.attempts} lần xử lý
          </p>
          {retry && (
            <Button
              disabled={a.busy}
              onClick={() =>
                a.run(
                  () => api(`/payments/${id}/synchronization/retry`, { method: 'POST' }),
                  r.reload,
                )
              }
            >
              Thử đồng bộ lại
            </Button>
          )}
        </>
      )}
    </Card>
  )
}
