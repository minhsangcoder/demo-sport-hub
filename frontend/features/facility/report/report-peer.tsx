'use client'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { demoVnd } from '../demo-report'
import { peerPhases } from '@/features/transfers/peer-types'
import type { useFacilityReport } from './use-facility-report'

const confirmations: Record<string, string> = {
  NONE: 'Chưa khai chuyển tiền',
  BUYER_DECLARED: 'B tự khai đã chuyển',
  COUNTERPARTY_ACKNOWLEDGED: 'A xác nhận đã nhận',
  OWNER_ADJUDICATED: 'Owner phân xử quyền sân',
}
export function ReportPeer({ model }: { model: ReturnType<typeof useFacilityReport> }) {
  const { report, peerPage, setPeerPage, timestamp, facility } = model
  if (report?.mode !== 'REAL') return null
  const peer = report.peer
  return (
    <section className="space-y-4 rounded-2xl border bg-white p-5">
      <h3 className="text-lg font-bold">Chuyển nhượng trực tiếp B → A</h3>
      <p className="text-sm text-slate-600">
        Tiền B trả A không phải khoản thu, phí hay công nợ của cơ sở/SportHub. Ảnh bill, A xác nhận
        hoặc Owner phân xử quyền sân không phải chứng từ ngân hàng đã xác minh.
      </p>
      {!peer || peer.availability === 'UNAVAILABLE' ? (
        <p role="alert">Chưa có dữ liệu chuyển nhượng trực tiếp; không coi là bằng 0.</p>
      ) : (
        <>
          {peer.availability !== 'COMPLETE' && (
            <p role="alert" className="text-amber-800">
              Dữ liệu chuyển nhượng chưa đầy đủ. Tải lại để đối chiếu.
            </p>
          )}
          <dl className="grid gap-3 text-sm sm:grid-cols-2 lg:grid-cols-3">
            {[
              ['B tự khai chuyển trong kỳ', demoVnd(peer.buyerDeclaredAmount)],
              ['A xác nhận nhận trong kỳ', demoVnd(peer.sellerAcknowledgedAmount)],
              ['Giá trị quyền sân Owner bàn giao', demoVnd(peer.ownerAwardedAmount)],
              ['A khai trả trực tiếp trong kỳ', demoVnd(peer.returnDeclaredAmount)],
              ['B xác nhận nhận lại trong kỳ', demoVnd(peer.returnAcknowledgedAmount)],
              [
                'Hồ sơ quyền sân / tiền chưa xong',
                `${peer.openUsageCases} / ${peer.openMoneyCases}`,
              ],
            ].map(([label, value]) => (
              <div key={label} className="rounded-lg bg-slate-50 p-3">
                <dt>{label}</dt>
                <dd className="mt-1 font-semibold">{value}</dd>
              </div>
            ))}
          </dl>
          <p className="text-xs text-slate-600">
            Tổng tiền theo ngày khai/xác nhận/phân xử trong kỳ đã chọn. Hồ sơ còn mở từ trước kỳ vẫn
            được tính theo trạng thái tại thời điểm báo cáo.
          </p>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <caption className="sr-only">
                Phiên chuyển nhượng trực tiếp, tách biệt doanh thu sân
              </caption>
              <thead>
                <tr>
                  {[
                    'Ngày mở phiên',
                    'Sân / Giá mua',
                    'Quyền sân',
                    'Mức xác nhận',
                    'Hồ sơ tiền',
                    'Chi tiết',
                  ].map((label) => (
                    <th key={label} className="p-3">
                      {label}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {peer.transactions.items.map((row) => (
                  <tr key={row.acquisitionId} className="border-t">
                    <td className="p-3">{timestamp(row.createdAt)}</td>
                    <td className="p-3">
                      {report.courts.find((court) => court.courtId === row.courtId)?.courtName ??
                        'Sân đã lưu trữ'}
                      <strong className="block">{demoVnd(row.amount)}</strong>
                      <span className="text-xs">B → A · Không thu phí chuyển nhượng</span>
                    </td>
                    <td className="p-3">{peerPhases[row.phase] ?? 'Đang đối chiếu'}</td>
                    <td className="p-3">
                      {confirmations[row.confirmationMethod] ?? 'Chưa xác minh'}
                    </td>
                    <td className="p-3">
                      {!row.moneyState
                        ? 'Chưa mở'
                        : ['OPEN', 'RETURN_REQUIRED', 'RETURN_RECORDED'].includes(row.moneyState)
                          ? 'A/B còn cần đối chiếu'
                          : 'A/B đã chốt hồ sơ'}
                    </td>
                    <td className="p-3">
                      <Link
                        className="text-emerald-700 underline"
                        href={`/owner/facilities/${facility.id}/peer-cases`}
                      >
                        Hồ sơ quyền sân
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {peer.transactions.total === 0 && (
            <p>Không có phiên chuyển nhượng trực tiếp thuộc kỳ hoặc hồ sơ còn mở.</p>
          )}
          <div className="flex flex-wrap items-center justify-between gap-3 text-sm">
            <p>
              {peer.transactions.total} phiên · Trang {peerPage + 1}/
              {Math.max(1, peer.transactions.totalPages)}
            </p>
            <div className="flex gap-2">
              <Button
                variant="outline"
                disabled={peerPage === 0}
                onClick={() => setPeerPage((value) => value - 1)}
              >
                Phiên trước
              </Button>
              <Button
                variant="outline"
                disabled={peerPage + 1 >= peer.transactions.totalPages}
                onClick={() => setPeerPage((value) => value + 1)}
              >
                Phiên sau
              </Button>
            </div>
          </div>
        </>
      )}
    </section>
  )
}
