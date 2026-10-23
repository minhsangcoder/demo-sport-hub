'use client'
import { useFacility } from '@/features/facility/facility-store'
import { Card, Status } from '@/features/integration/shared'
import { money } from '@/features/schedule/types'
import { type ActivityReport } from '@/lib/business-api'
import { displayLabel } from '@/lib/display-labels'
import { useApi } from '@/lib/use-api'
export function ActivityReportPanel() {
  const { facility } = useFacility(),
    r = useApi<ActivityReport>(
      `/bookings/reports/facility/${facility.id}/activity?timezone=${encodeURIComponent(facility.timezone)}`,
    )
  return (
    <Card title="Hoạt động và khoản thu đã ghi nhận">
      <Status loading={r.loading} error={r.error} />
      <p className="text-sm text-slate-500">
        30 ngày gần nhất; tiền theo thời điểm thanh toán, thời gian sử dụng được cắt trong kỳ. Chưa
        phải doanh thu đã tất toán; không tính tỷ lệ lấp đầy khi thiếu mẫu số lịch mở cửa lịch sử.
      </p>
      {r.data?.usage.map((x, i) => (
        <p key={i} className="text-sm">
          Sân {String(x.court_id).slice(0, 8)} · {displayLabel(x.status)} · {x.booked_minutes} phút
          đã đặt
        </p>
      ))}
      {r.data?.appliedCollections.map((x, i) => (
        <p key={i} className="text-sm">
          {displayLabel(x.purpose)} · {x.payment_count} khoản · {money(Number(x.gross_amount))}
        </p>
      ))}
    </Card>
  )
}
