'use client'
import { Card } from '@/features/integration/shared'
import type { useOperationalDetail } from '@/features/operations/use-operational-detail'
import { money } from '@/features/schedule/types'
import { displayLabel } from '@/lib/display-labels'
export function OperationHistory({
  model,
}: {
  model: Pick<ReturnType<typeof useOperationalDetail>, 'b' | 'r'>
}) {
  const { b, r } = model
  if (!b || !r.data) return null
  return (
    <Card title="Giá chốt & lịch sử vận hành">
      <p>{money(b.amount, b.currency)}</p>
      <p className="text-sm text-slate-500">
        Nguồn {displayLabel(b.source)} · Giá chốt giữ nguyên sau thay đổi bảng giá hoặc chuyển
        nhượng.
      </p>
      <ol className="space-y-3">
        {r.data.statusAudit.map((entry) => (
          <li key={entry.id} className="text-sm">
            <strong>
              {entry.previousStatus ? displayLabel(entry.previousStatus) + ' → ' : ''}
              {displayLabel(entry.status)}
            </strong>
            <p>
              {new Date(entry.recordedAt).toLocaleString('vi-VN', {
                timeZone: r.data!.detail.booking.priceSnapshot.timezone,
              })}{' '}
              · {displayLabel(entry.changeSource)} · Phiên bản {entry.bookingVersion}
            </p>
            {entry.actorId && (
              <p className="break-all text-xs text-slate-500">Người thực hiện {entry.actorId}</p>
            )}
          </li>
        ))}
      </ol>
      <details>
        <summary>Giá từng khung giờ tại lúc đặt</summary>
        {b.priceSnapshot.segments.map((segment) => (
          <p className="mt-2 text-sm" key={segment.startsAt}>
            {new Date(segment.startsAt).toLocaleString('vi-VN', {
              timeZone: r.data!.detail.booking.priceSnapshot.timezone,
            })}{' '}
            · {money(segment.amount ?? 0, segment.currency)}
          </p>
        ))}
      </details>
    </Card>
  )
}
