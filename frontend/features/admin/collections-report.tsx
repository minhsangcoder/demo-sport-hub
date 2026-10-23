'use client'
import { Card, Status } from '@/features/integration/shared'
import { money } from '@/features/schedule/types'
import { displayLabel } from '@/lib/display-labels'
import { useApi } from '@/lib/use-api'
export function CollectionsReport() {
  const r = useApi<{
      collections: {
        purpose: string
        provider: string
        currency: string
        payment_count: number
        gross_amount: number
      }[]
      settledRevenue: null
      payout: null
    }>('/admin/payments/reports/collections?timezone=Asia%2FHo_Chi_Minh'),
    facilities = useApi<{ regions: Record<string, string | number>[] }>(
      '/admin/facilities/statistics',
    )
  return (
    <Card title="Thanh toán thành công theo thời điểm trả tiền">
      <Status loading={r.loading || facilities.loading} error={r.error || facilities.error} />
      <p className="text-sm text-amber-800">
        Các khoản thanh toán mô phỏng thành công theo ngày trả tiền. Doanh thu và nghĩa vụ trả/hoàn
        thuộc từng cơ sở; xem phần giám sát ở trên hoặc báo cáo của cơ sở.
      </p>
      {r.data?.collections.map((x, i) => (
        <p key={i}>
          {displayLabel(x.purpose)} · {displayLabel(x.provider)} ·{' '}
          {money(x.gross_amount, x.currency)}
        </p>
      ))}
      <p className="text-sm text-slate-500">Thống kê cơ sở theo tỉnh và trạng thái:</p>
      {facilities.data?.regions.map((x, i) => (
        <p key={i} className="text-sm">
          {x.province} · {displayLabel(x.status)} · {x.facility_count} cơ sở
        </p>
      ))}
    </Card>
  )
}
