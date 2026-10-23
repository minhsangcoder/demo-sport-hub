'use client'
import { Button } from '@/components/ui/button'
import { Card, Status, useMutation } from '@/features/integration/shared'
import { api } from '@/lib/api'
import { displayLabel } from '@/lib/display-labels'
import { translateError } from '@/lib/error-translator'
import { useApi } from '@/lib/use-api'
type TransferSync = {
  state: string
  workflow: string
  attempts: number
  retryable: boolean
  lastErrorCode?: string
}
export function TransferSynchronization({ id }: { id: string }) {
  const r = useApi<TransferSync>(`/transfers/${id}/synchronization`),
    a = useMutation()
  return (
    <Card title="Tiến độ bàn giao quyền sử dụng">
      <Status loading={r.loading} error={r.error || a.error} />
      {r.data && (
        <>
          <p>
            {displayLabel(r.data.state)} · {displayLabel(r.data.workflow)}
          </p>
          <p className="text-sm text-slate-500">
            {r.data.lastErrorCode ? `${translateError(r.data.lastErrorCode)} · ` : ''}
            {r.data.attempts} lần xử lý. Thanh toán thành công cần bàn giao thành công để cấp quyền
            sử dụng.
          </p>
          {r.data.retryable && (
            <Button
              disabled={a.busy}
              onClick={() =>
                a.run(
                  () => api(`/transfers/${id}/synchronization/retry`, { method: 'POST' }),
                  r.reload,
                )
              }
            >
              Thử bàn giao lại sau lỗi kết nối
            </Button>
          )}
        </>
      )}
    </Card>
  )
}
