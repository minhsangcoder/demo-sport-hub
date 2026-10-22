'use client'
import Link from 'next/link'
import { useEffect, useState } from 'react'
import { useApi } from '@/lib/use-api'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { useFacility } from './facility-context'
import { calendarLabel, demoVnd, reportKindLabels, reportPreset, validateReportRange, type FacilityDemoReport, type ReportDay, type ReportRange } from './demo-report'

const series = [
  { key: 'courtCollected', label: 'Tiền sân', color: '#2563eb' },
  { key: 'transferFees', label: 'Phí cơ sở', color: '#059669' },
  { key: 'policyRefunded', label: 'Hoàn theo chính sách', color: '#e11d48' },
] as const
const hours = (minutes: string) => (Number(minutes) / 60).toLocaleString('vi-VN', { maximumFractionDigits: 2 })

function DailyChart({ days }: { days: ReportDay[] }) {
  const max = Math.max(1, ...days.flatMap(day => series.map(item => Number(day[item.key]))))
  const x = (index: number) => 64 + index * 620 / Math.max(1, days.length - 1)
  const y = (amount: string) => 176 - Number(amount) / max * 136
  return <div>
    <div className="mb-4 flex flex-wrap gap-5 text-sm">{series.map(item => <span key={item.key} className="inline-flex items-center gap-2"><span className="h-2.5 w-2.5 rounded-full" style={{ backgroundColor: item.color }} />{item.label}</span>)}</div>
    <div className="overflow-x-auto"><svg role="img" aria-label="Tiền sân, phí cơ sở và hoàn tiền theo ngày" viewBox="0 0 720 216" className="min-w-[500px] w-full">
      {[0, 0.5, 1].map(fraction => <g key={fraction}><line x1="64" x2="684" y1={176 - 136 * fraction} y2={176 - 136 * fraction} stroke="#e2e8f0" /><text x="56" y={180 - 136 * fraction} textAnchor="end" fontSize="10" fill="#64748b">{Math.round(max * fraction).toLocaleString('vi-VN')}</text></g>)}
      {series.map(item => <g key={item.key}><polyline points={days.map((day, index) => `${x(index)},${y(day[item.key])}`).join(' ')} fill="none" stroke={item.color} strokeWidth="2.5" />{days.map((day, index) => <circle key={day.date} cx={x(index)} cy={y(day[item.key])} r="3" fill={item.color}><title>{calendarLabel(day.date)} · {item.label}: {demoVnd(day[item.key])}</title></circle>)}</g>)}
      {days.length > 0 && <><text x="64" y="202" fontSize="11" fill="#64748b">{calendarLabel(days[0].date)}</text><text x="684" y="202" textAnchor="end" fontSize="11" fill="#64748b">{calendarLabel(days[days.length - 1].date)}</text></>}
    </svg></div>
    <details className="mt-3 text-sm"><summary className="cursor-pointer text-emerald-700">Xem số liệu theo ngày (VND)</summary><div className="mt-3 overflow-x-auto"><table className="w-full text-left text-sm"><thead><tr><th className="p-2">Ngày</th>{series.map(item => <th className="p-2" key={item.key}>{item.label}</th>)}</tr></thead><tbody>{days.map(day => <tr key={day.date} className="border-t"><td className="p-2">{calendarLabel(day.date)}</td>{series.map(item => <td className="p-2" key={item.key}>{demoVnd(day[item.key])}</td>)}</tr>)}</tbody></table></div></details>
  </div>
}

export function FacilityReport() {
  const { facility } = useFacility()
  const [range, setRange] = useState<ReportRange>(() => reportPreset(facility.timezone, '7'))
  const [draft, setDraft] = useState(range)
  const [page, setPage] = useState(0)
  const [error, setError] = useState('')
  const params = new URLSearchParams({ ...range, page: String(page), size: '25' })
  const resource = useApi<FacilityDemoReport>(`/bookings/reports/facility/${facility.id}/dashboard?${params}`)
  useEffect(() => { const initial = reportPreset(facility.timezone, '7'); setRange(initial); setDraft(initial); setPage(0); setError('') }, [facility.id, facility.timezone])
  useEffect(() => {
    const onStorage = (event: StorageEvent) => { if (event.key === 'sporthub:demo-money-changed') resource.reload() }
    window.addEventListener('sporthub:demo-money-changed', resource.reload)
    window.addEventListener('focus', resource.reload)
    window.addEventListener('storage', onStorage)
    return () => { window.removeEventListener('sporthub:demo-money-changed', resource.reload); window.removeEventListener('focus', resource.reload); window.removeEventListener('storage', onStorage) }
  }, [resource.reload])
  function apply(next: ReportRange) { const message = validateReportRange(next); setError(message); if (!message) { setRange(next); setDraft(next); setPage(0) } }
  const report = !resource.loading && !resource.error ? resource.data : null
  const available = report?.moneyAvailability !== 'UNAVAILABLE'
  const amount = (value: string) => available ? demoVnd(value) : 'Chưa có dữ liệu'
  const stats = report?.summary
  const money = stats?.money
  const timestamp = (value: string) => new Date(value).toLocaleString('vi-VN', { timeZone: facility.timezone, dateStyle: 'short', timeStyle: 'short' })
  return <section className="space-y-6">
    <div className="flex flex-wrap items-start justify-between gap-4"><div><h2 className="text-2xl font-bold">Báo cáo &amp; Doanh thu</h2><p className="mt-2 text-sm text-slate-500">Khoản thu, hoạt động sân và nghĩa vụ trả/hoàn của cơ sở.</p></div><div className="flex flex-wrap items-center gap-3"><Link href={`/owner/bookings?facilityId=${facility.id}`} className="text-sm text-emerald-700 underline">Xử lý khoản trả / hoàn</Link><Button variant="outline" onClick={resource.reload} disabled={resource.loading}>Làm mới báo cáo</Button></div></div>
    <p className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">Dữ liệu DEMO — không phát sinh giao dịch tiền thật. Phí chuyển nhượng thuộc cơ sở; SportHub không hưởng khoản này.</p>
    <form onSubmit={event => { event.preventDefault(); apply(draft) }} className="flex flex-wrap items-end gap-4 rounded-2xl border bg-white p-5">
      <label className="grid gap-2 text-sm">Từ ngày<Input name="fromDate" type="date" required value={draft.fromDate} onChange={event => setDraft({ ...draft, fromDate: event.target.value })} /></label>
      <label className="grid gap-2 text-sm">Đến hết ngày<Input name="toDate" type="date" required value={draft.toDate} onChange={event => setDraft({ ...draft, toDate: event.target.value })} /></label>
      <Button type="submit" className="bg-emerald-600 text-white">Xem báo cáo</Button>
      <div className="flex flex-wrap gap-2">{([['7', '7 ngày'], ['30', '30 ngày'], ['month', 'Tháng này']] as const).map(([preset, label]) => <Button key={preset} type="button" variant="outline" onClick={() => apply(reportPreset(facility.timezone, preset))}>{label}</Button>)}</div>
      <p className="w-full text-xs text-slate-500">Theo giờ cơ sở ({facility.timezone}). Tối đa 366 ngày. Đang xem {calendarLabel(range.fromDate)} – {calendarLabel(range.toDate)}.</p>
      {error && <p role="alert" className="w-full text-sm text-red-700">{error}</p>}
    </form>
    {resource.loading && <div role="status" className="rounded-2xl border p-8 text-slate-500">Đang tổng hợp báo cáo…</div>}
    {resource.error && <div role="alert" className="rounded-2xl border border-red-200 bg-red-50 p-5 text-red-700"><p>{resource.error}</p><Button variant="outline" className="mt-3" onClick={resource.reload}>Thử lại</Button></div>}
    {report && stats && money && <>
      {report.moneyAvailability !== 'COMPLETE' && <p role="alert" className="rounded-xl bg-amber-50 p-4 text-sm text-amber-900">{available ? 'Số tiền hiện là phần đã ghi nhận; còn khoản cần kiểm tra dữ liệu ứng dụng.' : 'Chưa tải đủ dữ liệu tiền. Hãy thử tải lại; đây không phải doanh thu bằng 0.'}<button className="ml-3 underline" onClick={resource.reload}>Thử lại</button></p>}
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">{[
        ['Tiền sân đã thu', amount(money.courtCollected), 'Theo ngày thanh toán đã áp dụng'],
        ['Phí chuyển nhượng cơ sở hưởng', amount(money.transferFees), 'Theo ngày bàn giao quyền thành công'],
        ['Tổng khoản thu kinh doanh', amount(money.operatingRevenue), 'Tiền sân + phí chuyển nhượng'],
        ['Đã hoàn theo chính sách', amount(money.policyRefunded), 'Chỉ khoản hoàn DEMO thành công trong kỳ'],
        ['Khoản thu sau hoàn', amount(money.afterRefund), 'Tổng khoản thu kinh doanh − hoàn theo chính sách'],
        ['Booking bắt đầu trong kỳ', String(stats.bookingCount), `${stats.fullyPaidBookingCount} đã thanh toán đủ · ${stats.cancelledCount} đã hủy`],
      ].map(([label, value, note]) => <article key={label} className="rounded-2xl border bg-white p-5"><h3 className="text-sm text-slate-600">{label}</h3><p className="mt-3 break-words text-2xl font-bold text-emerald-700">{value}</p><p className="mt-2 text-xs text-slate-500">{note}</p></article>)}</div>
      <div className="grid gap-4 rounded-2xl border bg-slate-50 p-5 sm:grid-cols-2 xl:grid-cols-4">{[
        ['Còn phải trả người bán', amount(money.sellerDue)], ['Còn phải hoàn khách', amount(money.refundDue)],
        ['Đã trả người bán trong kỳ', amount(money.sellerPaid)], ['Tiền mua lại đã thu trong kỳ', amount(money.transferCollected)],
      ].map(([label, value]) => <div key={label}><p className="text-sm text-slate-600">{label}</p><p className="mt-2 font-bold">{value}</p></div>)}<p className="text-xs text-slate-500 sm:col-span-2 xl:col-span-4">Công nợ đến {timestamp(report.obligationCutoff)}, gồm khoản từ kỳ trước. Tiền mua lại và khoản trả người bán là dòng tiền/nghĩa vụ riêng, không cộng thêm vào doanh thu. Hoàn do bàn giao thất bại trong kỳ: {amount(money.technicalRefunded)}.</p></div>
      <section className="rounded-2xl border bg-white p-5"><h3 className="mb-4 text-lg font-bold">Khoản thu và hoàn theo ngày</h3>{available ? <DailyChart days={report.daily} /> : <p className="text-sm text-slate-500">Biểu đồ chờ đủ dữ liệu ứng dụng.</p>}</section>
      <section className="rounded-2xl border bg-white p-5"><div className="mb-4 flex flex-wrap justify-between gap-2"><h3 className="text-lg font-bold">Hiệu suất theo sân</h3><p className="text-sm text-slate-500">{hours(stats.bookedMinutes)} giờ đặt · {hours(stats.usedMinutes)} giờ sử dụng ghi nhận</p></div><div className="overflow-x-auto"><table className="w-full min-w-[750px] text-left text-sm"><caption className="sr-only">Mỗi sân một dòng trong kỳ báo cáo đã chọn</caption><thead className="bg-slate-50 text-slate-500"><tr>{['Sân', 'Booking', 'Đủ tiền', 'Giờ đặt', 'Giờ sử dụng', 'Tiền sân', 'Phí cơ sở', 'Sau hoàn'].map(label => <th key={label} className="p-3 font-medium">{label}</th>)}</tr></thead><tbody>{report.courts.map(court => <tr key={court.courtId} className="border-t"><th scope="row" className="p-3 font-semibold">{court.courtName}</th><td className="p-3">{court.bookingCount}</td><td className="p-3">{court.fullyPaidBookingCount}</td><td className="p-3">{hours(court.bookedMinutes)}</td><td className="p-3">{hours(court.usedMinutes)}</td><td className="p-3 whitespace-nowrap">{amount(court.money.courtCollected)}</td><td className="p-3 whitespace-nowrap">{amount(court.money.transferFees)}</td><td className="p-3 whitespace-nowrap">{amount(court.money.afterRefund)}</td></tr>)}</tbody></table></div>{report.courts.length === 0 && <p className="py-5 text-sm text-slate-500">Không có phát sinh theo sân trong kỳ này.</p>}<p className="mt-4 text-xs text-slate-500">Giờ sử dụng chỉ tính sau check-in, giới hạn trong lịch đã đặt. Chưa có đủ lịch sử giờ mở cửa để tính tỷ lệ lấp đầy.</p></section>
      <section className="rounded-2xl border bg-white p-5"><h3 className="text-lg font-bold">Giao dịch và ghi nhận trong kỳ</h3><p className="mt-2 text-xs text-slate-500">Phí được tách từ tiền mua lại, không phải một khoản nộp thêm. Dòng thất bại không được tính như đã thực hiện.</p><div className="mt-4 overflow-x-auto"><table className="w-full min-w-[760px] text-left text-sm"><thead className="bg-slate-50 text-slate-500"><tr>{['Thời điểm', 'Nghiệp vụ', 'Sân / nguồn đặt', 'Số tiền', 'Kết quả', 'Booking'].map(label => <th className="p-3 font-medium" key={label}>{label}</th>)}</tr></thead><tbody>{report.transactions.items.map(item => <tr key={item.id} className="border-t"><td className="p-3 whitespace-nowrap">{timestamp(item.effectiveAt)}</td><td className="p-3">{reportKindLabels[item.kind] || 'Giao dịch cơ sở'}{!item.cashflow && <span className="mt-1 block text-xs text-slate-500">Ghi nhận phí, không thu thêm</span>}</td><td className="p-3">{item.courtName}<span className="mt-1 block text-xs text-slate-500">{item.source === 'OFFLINE_COUNTER' ? 'Tại quầy' : 'Trực tuyến'}</span></td><td className="p-3 whitespace-nowrap font-medium">{item.direction === 'OUT' ? '−' : ''}{demoVnd(item.amount)}</td><td className="p-3"><span className={item.state === 'SUCCESS' ? 'text-emerald-700' : 'text-red-700'}>{item.state === 'SUCCESS' ? 'Thành công DEMO' : 'Thất bại DEMO'}</span></td><td className="p-3"><Link className="text-emerald-700 underline" href={`/customer/bookings/${item.bookingId}`}>Xem booking</Link></td></tr>)}</tbody></table></div>{report.transactions.total === 0 && <p className="py-6 text-center text-sm text-slate-500">Không có giao dịch phát sinh trong kỳ đã chọn.</p>}<div className="mt-4 flex flex-wrap items-center justify-between gap-3 text-sm"><p>{report.transactions.total} dòng · Trang {report.transactions.page + 1}/{Math.max(1, report.transactions.totalPages)}</p><div className="flex gap-2"><Button variant="outline" disabled={page === 0} onClick={() => setPage(value => value - 1)}>Trang trước</Button><Button variant="outline" disabled={page + 1 >= report.transactions.totalPages} onClick={() => setPage(value => value + 1)}>Trang sau</Button></div></div></section>
      <p className="text-xs text-slate-500">Cập nhật {timestamp(report.asOf)}. Booking theo giờ bắt đầu; khoản thu/chi/hoàn theo thời điểm tương ứng. Xem báo cáo không thay đổi trạng thái booking hoặc thực hiện tiền.</p>
    </>}
  </section>
}
