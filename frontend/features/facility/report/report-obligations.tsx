'use client'
import type { useFacilityReport } from '@/features/facility/report/use-facility-report'
export function ReportObligations({
  model,
}: {
  model: Pick<ReturnType<typeof useFacilityReport>, 'amount' | 'money' | 'timestamp' | 'report'>
}) {
  const { amount, money, timestamp, report } = model
  if (!money || !report) return null
  return (
    <div className="grid gap-4 rounded-2xl border bg-slate-50 p-5 sm:grid-cols-2 xl:grid-cols-4">
      {[
        ['Còn phải trả người bán', amount(money.sellerDue)],
        ['Còn phải hoàn khách', amount(money.refundDue)],
        ['Đã trả người bán trong kỳ', amount(money.sellerPaid)],
        ['Tiền mua lại đã thu trong kỳ', amount(money.transferCollected)],
        ...(report.mode === 'REAL'
          ? [
              [
                'Ngân hàng đã nhận trong kỳ',
                money.bankCollected === undefined ? 'Chưa có dữ liệu' : amount(money.bankCollected),
              ],
              [
                'Phần thu chưa áp dụng tại mốc chốt',
                money.unappliedCollected === undefined
                  ? 'Chưa có dữ liệu'
                  : amount(money.unappliedCollected),
              ],
            ]
          : []),
      ].map(([label, value]) => (
        <div key={label}>
          <p className="text-sm text-slate-600">{label}</p>
          <p className="mt-2 font-bold">{value}</p>
        </div>
      ))}
      <p className="text-xs text-slate-500 sm:col-span-2 xl:col-span-4">
        Công nợ đến {timestamp(report.obligationCutoff)}, gồm khoản từ kỳ trước. Tiền mua lại và
        khoản trả người bán là dòng tiền/nghĩa vụ riêng, không cộng thêm vào doanh thu. Hoàn kỹ
        thuật trong kỳ: {amount(money.technicalRefunded)}.
      </p>
    </div>
  )
}
