'use client'
import { displayLabel } from '@/lib/display-labels'
import Image from 'next/image'
import Link from 'next/link'
import { MapPin, Navigation } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { api } from '@/lib/api'
import { useApi } from '@/lib/use-api'
import { useAuth } from '@/lib/auth-context'
import { RequireAuth } from '@/features/auth/require-auth'
import { Button } from '@/components/ui/button'
import { money } from '@/features/schedule/types'
import type { Facility, Court } from '@/features/facility/types'
import { type BookingDetail, type Payment, statusLabels } from './types'
import { Countdown } from './countdown'
import { operationsWorkspace, ownsBooking } from './ownership'
import { ParticipantDemoMoney, PolicyCancellationPanel } from './demo-finance'
export function BookingDetailPage({ id }: { id: string }) {
  return (
    <RequireAuth>
      <Loaded id={id} />
    </RequireAuth>
  )
}
function Loaded({ id }: { id: string }) {
  const router = useRouter()
  const { user } = useAuth()
  const resource = useApi<BookingDetail>(`/bookings/${id}`)
  const [paymentKey] = useState(() => crypto.randomUUID())
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const b = resource.data?.booking
  const facility = useApi<Facility>(b ? `/facilities/${b.facilityId}` : null)
  const courts = useApi<Court[]>(b ? `/facilities/${b.facilityId}/courts` : null)
  const owned = useApi<{ id: string }[]>(user?.roles.includes('OWNER') ? '/owner/facilities' : null)
  useEffect(() => {
    if (b?.status !== 'CONFIRMED') return
    const timer = setInterval(resource.reload, 60000)
    return () => clearInterval(timer)
  }, [b?.status, resource.reload])
  async function pay() {
    if (busy) return
    setBusy(true)
    setError('')
    try {
      const p = await api<Payment>('/payments', {
        method: 'POST',
        headers: { 'Idempotency-Key': paymentKey },
        body: JSON.stringify({ bookingId: id }),
      })
      router.push(`/customer/payments/${p.id}`)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không thể tạo thanh toán.')
    } finally {
      setBusy(false)
    }
  }
  async function cancel() {
    if (!window.confirm('Hủy lượt giữ chỗ chưa thanh toán và giải phóng khung giờ?')) return
    setBusy(true)
    setError('')
    try {
      await api(`/bookings/${id}/cancel`, {
        method: 'POST',
        body: JSON.stringify({ reason: 'Khách hủy lượt giữ chỗ chưa thanh toán' }),
      })
      resource.reload()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không thể hủy giữ chỗ.')
    } finally {
      setBusy(false)
    }
  }
  if (resource.loading)
    return (
      <p role="status" className="p-8">
        Đang tải lượt đặt…
      </p>
    )
  if (resource.error || !b)
    return (
      <p role="alert" className="p-8 text-red-700">
        {resource.error}
      </p>
    )
  const detail = resource.data!
  const timezone = b.priceSnapshot.timezone
  const isOwner = ownsBooking(b, user)
  const workspace = operationsWorkspace(
    user,
    b.facilityId,
    owned.data?.map((f) => f.id),
  )
  const isTransferred =
    detail.history.some((h) => h.action === 'TRANSFER_COMPLETED') ||
    Boolean(b.currentHolderId && b.customerId && b.currentHolderId !== b.customerId)
  const backHref = !isOwner && workspace ? `/${workspace}/bookings` : '/customer/bookings'
  async function cancelConfirmedDirect() {
    if (
      !window.confirm(
        'LƯU Ý QUAN TRỌNG: Lượt đặt trực tiếp KHÔNG ĐƯỢC HOÀN TIỀN khi tự hủy.\n\nNếu bạn không thể đến sân, hãy chọn "Đăng tin chuyển nhượng lượt đặt" để sang nhượng lại.\n\nBạn có chắc chắn muốn hủy và giải phóng khung giờ chơi (hoàn 0đ)?',
      )
    )
      return
    setBusy(true)
    setError('')
    try {
      await api(`/bookings/${id}/cancel`, {
        method: 'POST',
        body: JSON.stringify({ reason: 'Khách tự hủy lượt đặt trực tiếp (không hoàn tiền)' }),
      })
      resource.reload()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không thể hủy lượt đặt.')
    } finally {
      setBusy(false)
    }
  }
  return (
    <div className="mx-auto max-w-4xl px-5 py-10">
      <Link className="text-sm text-slate-500" href={backHref}>
        Lịch đặt / {id.slice(0, 8).toUpperCase()}
      </Link>
      <div className="my-6 flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-3xl font-black">{statusLabels[b.status] || displayLabel(b.status)}</h1>
        <span className="rounded-full bg-emerald-50 px-4 py-2 text-sm text-emerald-700">
          {b.currency} · {money(b.amount, b.currency)}
        </span>
      </div>
      <section className="rounded-2xl border p-6">
        <h2 className="text-xl font-bold">
          {facility.data?.name || 'Cơ sở'} ·{' '}
          {courts.data?.find((c) => c.id === b.courtId)?.name || 'Sân'}
        </h2>
        <p className="mt-3 text-sm text-slate-500">
          {new Date(b.startsAt).toLocaleString('vi-VN', { timeZone: timezone })} →{' '}
          {new Date(b.endsAt).toLocaleTimeString('vi-VN', { timeZone: timezone })}
        </p>
        <p className="mt-2 break-all text-xs text-slate-400">Mã lượt đặt {id}</p>
        {facility.data && (
          <div className="mt-4 flex flex-wrap items-center justify-between gap-3 rounded-xl bg-slate-50 p-3.5 text-sm">
            <div className="flex items-center gap-2 text-slate-600">
              <MapPin className="h-4 w-4 shrink-0 text-rose-600" />
              <span>
                {facility.data.addressLine}, {facility.data.ward}, {facility.data.province}
              </span>
            </div>
            <Link
              href={`/facilities/${facility.data.id}/directions?bookingId=${id}`}
              className="inline-flex items-center gap-1.5 font-semibold text-rose-700 hover:text-rose-800 hover:underline"
            >
              <Navigation className="h-3.5 w-3.5" />
              <span>Chỉ đường</span>
            </Link>
          </div>
        )}
        <div className="mt-5 space-y-2 border-t pt-4">
          {b.priceSnapshot.segments.map((s) => (
            <div className="flex justify-between text-sm" key={s.startsAt}>
              <span>{s.ruleLabel}</span>
              <span>{money(s.amount || 0, s.currency)}</span>
            </div>
          ))}
        </div>
      </section>
      {b.status === 'CONFIRMED' && isOwner && (
        <div className="mt-6 space-y-4">
          {isTransferred ? (
            <>
              <div className="rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-900">
                <strong>Lượt đặt nhận qua chuyển nhượng:</strong> Bạn là người mua lại lượt đặt này.
                Nếu hủy hợp lệ trước giờ chơi, bạn sẽ được hoàn tiền theo tỷ lệ chính sách cơ sở
                tính trên số tiền bạn đã mua lại.
              </div>
              <PolicyCancellationPanel bookingId={id} onSuccess={resource.reload} />
            </>
          ) : (
            <section className="space-y-3 rounded-2xl border border-slate-200 bg-slate-50 p-5">
              <h3 className="font-semibold text-slate-800">Tùy chọn khi không thể tham gia</h3>
              <p className="text-sm text-slate-600">
                Theo chính sách cơ sở,{' '}
                <strong>người đặt trực tiếp không áp dụng hoàn tiền khi tự hủy</strong>. Quý khách
                có thể đăng chuyển nhượng để sang nhượng lại cho người khác trên hệ thống.
              </p>
              <div className="flex flex-wrap items-center gap-3 pt-1">
                <Link
                  href={`/customer/bookings/${id}/transfer`}
                  className="inline-block rounded-full bg-emerald-600 px-5 py-2.5 text-sm font-semibold text-white hover:bg-emerald-700"
                >
                  Đăng tin chuyển nhượng lượt đặt
                </Link>
                <Button
                  variant="outline"
                  className="border-rose-300 text-rose-700 hover:bg-rose-50"
                  onClick={cancelConfirmedDirect}
                  disabled={busy}
                >
                  Hủy lượt đặt (Không hoàn tiền)
                </Button>
              </div>
            </section>
          )}
        </div>
      )}
      <ParticipantDemoMoney bookingId={id} />
      {b.status === 'PENDING' && isOwner && (
        <div className="mt-6 space-y-4">
          <Countdown expiresAt={b.holdExpiresAt} />
          <div className="flex flex-wrap gap-3">
            <Button onClick={pay} disabled={busy} className="bg-emerald-600 text-white">
              Thanh toán
            </Button>
            <Button onClick={cancel} disabled={busy} variant="outline">
              Hủy giữ chỗ
            </Button>
          </div>
        </div>
      )}
      {b.bookingCode && isOwner && (
        <p className="mt-4 text-sm">
          Mã xác nhận vào sân thủ công: <strong>{b.bookingCode}</strong>
        </p>
      )}
      {detail.checkinToken && isOwner && (
        <section className="mt-6 rounded-2xl bg-emerald-50 p-6">
          <h2 className="text-xl font-bold">Mã xác nhận vào sân của bạn</h2>
          <p className="mt-3 text-sm text-emerald-900">
            Mã tự hết hạn sau 2 phút. Mở trang khi đến sân để nhận mã mới.
          </p>
          {detail.checkinQrSvg && (
            <Image
              unoptimized
              src={`data:image/svg+xml,${encodeURIComponent(detail.checkinQrSvg)}`}
              width={240}
              height={240}
              alt="QR xác nhận vào sân lượt đặt"
              className="mt-5 rounded-xl bg-white p-3"
            />
          )}
          <details className="mt-4 text-sm">
            <summary>Mã dùng khi nhập thủ công</summary>
            <p className="mt-3 break-all rounded-lg bg-white p-3 font-mono text-xs">
              {detail.checkinToken}
            </p>
          </details>
          <button onClick={resource.reload} className="mt-4 text-sm text-emerald-700 underline">
            Làm mới mã
          </button>
        </section>
      )}
      {error && (
        <p role="alert" className="mt-5 text-red-700">
          {error}
        </p>
      )}
      <section className="mt-8">
        <h2 className="text-xl font-bold">Lịch sử lượt đặt</h2>
        <ol className="mt-5 space-y-4 border-l-2 border-emerald-100 pl-5">
          {detail.history.map((h) => (
            <li key={h.id}>
              <p className="text-sm font-semibold">
                {statusLabels[h.action] || displayLabel(h.action, 'Đã cập nhật lượt đặt')}
              </p>
              <p className="mt-1 text-xs text-slate-500">
                {new Date(h.createdAt).toLocaleString('vi-VN')}
              </p>
            </li>
          ))}
        </ol>
      </section>
    </div>
  )
}
