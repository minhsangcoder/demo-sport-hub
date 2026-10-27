'use client'
import type { useFacilityReport } from '@/features/facility/report/use-facility-report'
export function ReportKpis({
  model,
}: {
  model: Pick<ReturnType<typeof useFacilityReport>, 'amount' | 'money' | 'stats' | 'mode'>
}) {
  const { amount, money, stats, mode } = model
  if (!money || !stats) return null
  return (
    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
      {[
        ['Tiền sân đã thu', amount(money.courtCollected), 'Theo ngày thanh toán đã áp dụng'],
        [
          'Phí chuyển nhượng cơ sở hưởng',
          amount(money.transferFees),
          'Theo ngày bàn giao quyền thành công',
        ],
        [
          'Tổng khoản thu kinh doanh',
          amount(money.operatingRevenue),
          'Tiền sân + phí chuyển nhượng',
        ],
        [
          'Đã hoàn theo chính sách',
          amount(money.policyRefunded),
          mode === 'REAL'
            ? 'Theo xác nhận chuyển khoản của cơ sở trong kỳ'
            : 'Chỉ khoản hoàn mô phỏng thành công trong kỳ',
        ],
        [
          'Khoản thu sau hoàn',
          amount(money.afterRefund),
          'Tổng khoản thu kinh doanh − hoàn theo chính sách',
        ],
        [
          'Lượt đặt bắt đầu trong kỳ',
          String(stats.bookingCount),
          `${stats.fullyPaidBookingCount} đã thanh toán đủ · ${stats.cancelledCount} đã hủy`,
        ],
      ].map(([label, value, note]) => (
        <article key={label} className="rounded-2xl border bg-white p-5">
          <h3 className="text-sm text-slate-600">{label}</h3>
          <p className="mt-3 break-words text-2xl font-bold text-emerald-700">{value}</p>
          <p className="mt-2 text-xs text-slate-500">{note}</p>
        </article>
      ))}
    </div>
  )
}
