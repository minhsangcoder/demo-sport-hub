'use client'
import {useRef,useState} from 'react'
import Link from 'next/link'
import {useApi} from '@/lib/use-api'
import {checkInBooking} from '@/features/operations/check-in'
import {canPayBooking,operationsWorkspace} from '@/features/booking/ownership'
import {Button} from '@/components/ui/button'
import {Input} from '@/components/ui/input'
import {money} from '@/features/schedule/types'
import type {BookingDetail} from '@/features/booking/types'
import {useAuth} from '@/lib/auth-context'
import {StaffGroupAttendance} from './group'
import {Frame,Card,Status,useMutation} from './shared'
import {QrUploadButton} from '@/features/operations/qr-checkin'
import {CounterPaymentModal} from '@/features/operations/counter-payment-modal'
import {CreditCard} from 'lucide-react'

type Operations={detail:BookingDetail;counterGuest?:{guestName:string;guestPhone:string};statusAudit:{id:number;status:string;changeSource:string;recordedAt:string;previousStatus?:string;actorId?:string;bookingVersion:number}[]}

export function OperationalDetail({id}:{id:string}){
  const r=useApi<Operations>(`/bookings/${id}/operations`),a=useMutation(),{user}=useAuth(),access=useApi<{facilityBindings:{facilityId:string;permissions:string[]}[]}>("/users/me/access"),owned=useApi<{id:string}[]>(user?.roles.includes("OWNER")?"/owner/facilities":null);
  const [token,setToken]=useState('')
  const [paymentOpen,setPaymentOpen]=useState(false)
  const checkingIn=useRef(false)
  const can=(permission:string)=>Boolean(owned.data?.some(f=>f.id===r.data?.detail.booking.facilityId)||access.data?.facilityBindings.some(f=>f.facilityId===r.data?.detail.booking.facilityId&&f.permissions.includes(permission)));
  const b = r.data?.detail.booking;
  const guestLabel = b?.source === 'OFFLINE_COUNTER' ? (b.currentHolderId ? 'Khách có tài khoản' : 'Khách vãng lai') : 'Khách trực tuyến';
  const canPay = b ? canPayBooking(b,user,can('BOOKING_CREATE_COUNTER')) : false;
  const workspace = b ? operationsWorkspace(user,b.facilityId,owned.data?.map(f=>f.id)) : null;

  async function performCheckIn(raw: string) {
    const trimmed = raw.trim();
    if (!trimmed || !b || checkingIn.current) return;
    checkingIn.current=true;
    try {
      await checkInBooking(b,trimmed);
      setToken('');
      r.reload();
    } finally { checkingIn.current=false; }
  }

  function handleQrDetected(detectedToken: string, detectedBookingId: string | null) {
    if (detectedBookingId && detectedBookingId.toLowerCase() !== id.toLowerCase()) {
      alert(`Lưu ý: Mã QR này thuộc về booking #${detectedBookingId.slice(0,8).toUpperCase()}, không khớp với booking #${id.slice(0,8).toUpperCase()} hiện tại!`);
      return;
    }
    setToken(detectedToken);
    void a.run(() => performCheckIn(detectedToken));
  }

  return (
    <Frame title="Chi tiết vận hành booking">
      <Link href={`/${workspace||'staff'}/bookings`} className="text-emerald-700 underline">Về lịch vận hành</Link>
      <Status loading={r.loading} error={r.error||a.error}/>
      {r.data && b && (
        <>
          <Card title={b.status}>
            <p>{new Date(b.startsAt).toLocaleString('vi-VN',{timeZone:b.priceSnapshot.timezone})}</p>
            <p>{guestLabel}{b.guestName ? ` · Khách lúc đặt: ${b.guestName}` : ''} {b.guestPhone||''}</p>
            {b.currentHolderId&&<p className="text-xs text-slate-500">Tài khoản sở hữu hiện tại: {b.currentHolderId}</p>}
            <p className="text-xs text-slate-500">Người tạo booking: {b.createdBy}</p>
            {b.bookingCode&&<p>Mã check-in vận hành: <strong data-testid="operational-booking-code">{b.bookingCode}</strong></p>}
            {can('BOOKING_CHECK_IN') && b.status === 'CONFIRMED' && (
              <form className="space-y-3" onSubmit={e => { e.preventDefault(); const val = token || String(new FormData(e.currentTarget).get('token') || ''); void a.run(() => performCheckIn(val)); }}>
                <div className="flex flex-wrap items-end gap-3">
                  <div className="grid flex-1 gap-2 text-sm">
                    <div className="flex items-center justify-between">
                      <span className="font-semibold text-slate-700">Mã check-in 10 ký tự hoặc QR khách cung cấp</span>
                      <QrUploadButton onTokenDetected={handleQrDetected} disabled={a.busy}/>
                    </div>
                    <Input name="token" value={token} onChange={e=>setToken(e.target.value)} placeholder="Nhập mã check-in 10 ký tự hoặc bấm Tải ảnh QR ở trên" required disabled={a.busy}/>
                  </div>
                  <Button type="submit" disabled={a.busy} className="self-end">Check-in booking</Button>
                </div>
              </form>
            )}
            {canPay && b.status === 'PENDING' && (
              <Button type="button" onClick={()=>setPaymentOpen(true)} className="gap-2 bg-emerald-600 hover:bg-emerald-700 text-white font-semibold text-xs px-4 py-2 rounded-lg flex items-center mt-3">
                <CreditCard className="size-4" />
                <span>Thanh toán DEMO tại quầy</span>
              </Button>
            )}
          </Card>
          <Card title="Giá chốt & lịch sử vận hành">
            <p>{money(b.amount,b.currency)}</p>
            <p className="text-sm text-slate-500">Nguồn {b.source} · Giá chốt giữ nguyên sau thay đổi bảng giá hoặc chuyển nhượng.</p>
            <ol className="space-y-3">
              {r.data.statusAudit.map(entry=>(
                <li key={entry.id} className="text-sm">
                  <strong>{entry.previousStatus?entry.previousStatus+' → ':''}{entry.status}</strong>
                  <p>{new Date(entry.recordedAt).toLocaleString('vi-VN',{timeZone:r.data!.detail.booking.priceSnapshot.timezone})} · {entry.changeSource} · Phiên bản {entry.bookingVersion}</p>
                  {entry.actorId&&<p className="break-all text-xs text-slate-500">Người thực hiện {entry.actorId}</p>}
                </li>
              ))}
            </ol>
            <details>
              <summary>Giá từng slot tại lúc đặt</summary>
              {b.priceSnapshot.segments.map(segment=>(
                <p className="mt-2 text-sm" key={segment.startsAt}>
                  {new Date(segment.startsAt).toLocaleString('vi-VN',{timeZone:r.data!.detail.booking.priceSnapshot.timezone})} · {money(segment.amount??0,segment.currency)}
                </p>
              ))}
            </details>
          </Card>
          <StaffGroupAttendance id={id} allowed={can('BOOKING_CHECK_IN')} readable={can('BOOKING_READ')}/>
          <CounterPaymentModal booking={b} isOpen={paymentOpen} onClose={()=>setPaymentOpen(false)} onSuccess={()=>r.reload()}/>
        </>
      )}
    </Frame>
  );
}
