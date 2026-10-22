'use client'
import Link from 'next/link'
import { useEffect, useRef, useState } from 'react'
import { QrCode, CreditCard } from 'lucide-react'
import { checkInBooking } from './check-in'
import { canPayBooking } from '@/features/booking/ownership'
import { useApi } from '@/lib/use-api'
import { RequireAuth } from '@/features/auth/require-auth'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/lib/auth-context'
import type { Facility } from '@/features/facility/types'
import { type Booking,statusLabels } from '@/features/booking/types'
import { money } from '@/features/schedule/types'
import { QrUploadButton, QuickQrCheckinModal } from './qr-checkin'
import { CounterPaymentModal } from './counter-payment-modal'
import { FacilityDemoMoneyPanel, PolicyCancellationPanel } from '@/features/booking/demo-finance'

type Access={facilityBindings:{facilityId:string;permissions:string[]}[]}
export function OperationBookings({owner=false}:{owner?:boolean}){return <RequireAuth roles={[owner?'OWNER':'STAFF']}><Loaded owner={owner}/></RequireAuth>}
function Loaded({owner}:{owner:boolean}){
 const { user } = useAuth()
 const checkingIn=useRef(false)
 const selectedFromLink=useRef(false)
 const owned=useApi<Facility[]>(owner?'/owner/facilities':null);const access=useApi<Access>(owner?null:'/users/me/access');const publicFacilities=useApi<Facility[]>(owner?null:'/facilities');const [selection,setSelection]=useState('');const [filter,setFilter]=useState('');const [page,setPage]=useState(0);const [query,setQuery]=useState('')
 const [quickQrOpen,setQuickQrOpen]=useState(false);const [cardTokens,setCardTokens]=useState<Record<string,string>>({});const [paymentModalBooking,setPaymentModalBooking]=useState<Booking|null>(null)
 const facilities=owner?owned.data:access.data?.facilityBindings.filter(b=>b.permissions.some(p=>['BOOKING_READ','BOOKING_REFUND','TRANSFER_PAYOUT'].includes(p))).map((b,index)=>({id:b.facilityId,name:publicFacilities.data?.find(f=>f.id===b.facilityId)?.name||('Cơ sở được phân công '+(index+1))}));const facilityId=selection||facilities?.[0]?.id
 const bookings=useApi<{items:Booking[];total:number;totalPages:number}>(facilityId&&(owner||access.data?.facilityBindings.find(b=>b.facilityId===facilityId)?.permissions.includes('BOOKING_READ'))?`/bookings/management/search?facilityId=${facilityId}${filter?`&status=${encodeURIComponent(filter)}`:''}${query.trim()?`&q=${encodeURIComponent(query.trim())}`:''}&page=${page}&size=20`:null);const [busy,setBusy]=useState(false);const [error,setError]=useState('');const [message,setMessage]=useState('')
 const can=(permission:string)=>owner||access.data?.facilityBindings.find(b=>b.facilityId===facilityId)?.permissions.includes(permission)
 useEffect(()=>{if(selectedFromLink.current)return;const requested=new URLSearchParams(window.location.search).get('facilityId');if(requested&&facilities?.some(f=>f.id===requested)){selectedFromLink.current=true;setSelection(requested);setPage(0)}},[facilities])
 async function action(booking:Booking,operation:string,token?:string){
  if(busy||checkingIn.current)return;
  checkingIn.current=true;
  setBusy(true);setError('');setMessage('');
  try{
   await checkInBooking(booking,token||'');
   bookings.reload();setMessage(operation==='check-in'?'Đã check-in khách.':'Đã hoàn tất booking.')
  }catch(err){setError(err instanceof Error?err.message:'Không thể thực hiện thao tác.')}finally{checkingIn.current=false;setBusy(false)}
 }
 return <div className="mx-auto max-w-6xl px-5 py-10"><div className="flex flex-wrap items-center justify-between gap-4"><h1 className="text-3xl font-black">{owner ? 'Vận hành lịch đặt' : 'Kiểm Tra Booking'}</h1><div className="flex flex-wrap items-center gap-2">{can('BOOKING_CHECK_IN')&&<Button type="button" onClick={()=>setQuickQrOpen(true)} className="rounded-full bg-emerald-600 hover:bg-emerald-700 text-white shadow-xs flex items-center gap-2 px-4 py-2 text-sm font-semibold"><QrCode className="h-4 w-4" /><span>Tải ảnh QR check-in</span></Button>}{!owner&&can('BOOKING_CREATE_COUNTER')&&facilityId&&<Link className="rounded-full border border-emerald-600 bg-white px-5 py-2 text-sm font-semibold text-emerald-700 hover:bg-emerald-50" href={`/staff/counter-booking?facilityId=${facilityId}`}>Đặt tại quầy</Link>}</div></div><div className="my-6 flex flex-wrap gap-4"><label className="grid gap-2 text-sm">Cơ sở<select className="rounded-lg border bg-white p-3" value={facilityId||''} onChange={e=>{setSelection(e.target.value);setPage(0)}} disabled={busy}>{facilities?.map(f=><option value={f.id} key={f.id}>{f.name}</option>)}</select></label><label className="grid gap-2 text-sm">Trạng thái<select className="rounded-lg border bg-white p-3" value={filter} onChange={e=>{setFilter(e.target.value);setPage(0)}}><option value="">Tất cả</option>{Object.entries(statusLabels).map(([key,label])=><option key={key} value={key}>{label}</option>)}</select></label><label className="grid gap-2 text-sm">Mã booking / tên / liên hệ<Input value={query} maxLength={180} onChange={e=>{setQuery(e.target.value);setPage(0)}}/></label><Button className="self-end" variant="outline" onClick={bookings.reload} disabled={busy}>Làm mới</Button></div>
  {(owned.loading||access.loading||publicFacilities.loading||bookings.loading)&&<p role="status">Đang tải lịch đặt…</p>}{(error||owned.error||access.error||publicFacilities.error||bookings.error)&&<p role="alert" className="my-4 text-red-700">{error||owned.error||access.error||publicFacilities.error||bookings.error}</p>}{message&&<p role="status" className="my-4 text-emerald-700">{message}</p>}{facilities?.length===0&&<p className="text-slate-500">Chưa có cơ sở trong phạm vi quản lý với quyền xem booking.</p>}{bookings.data?.items.filter(b=>!filter||b.status===filter).length===0&&<p className="rounded-xl border p-6 text-slate-500">Chưa có booking phù hợp.</p>}
  <div className="space-y-4">{bookings.data?.items.filter(b=>!filter||b.status===filter).map(b=>{
   const guestLabel = b.source === 'OFFLINE_COUNTER' ? (b.currentHolderId ? 'Khách có tài khoản' : 'Khách vãng lai') : 'Khách trực tuyến'
   const canPay = canPayBooking(b,user,Boolean(can('BOOKING_CREATE_COUNTER')))
   return <article key={b.id} className="rounded-xl border p-5"><div className="flex flex-wrap justify-between gap-3"><div><h2 className="font-bold">{new Date(b.startsAt).toLocaleString('vi-VN',{timeZone:b.priceSnapshot.timezone})} · {statusLabels[b.status]}</h2><p className="mt-2 text-sm text-slate-500">{guestLabel}{b.guestName ? ` · Khách lúc đặt: ${b.guestName}` : ''} {b.guestPhone||''} · {b.id.slice(0,8).toUpperCase()}</p><p className="mt-1 text-xs text-slate-500">Người tạo: {b.createdBy}</p>{b.bookingCode&&<p className="mt-2 text-sm">Mã check-in vận hành: <strong data-testid="operational-booking-code">{b.bookingCode}</strong></p>}</div><div><p className="font-bold">{money(b.amount,b.currency)}</p><Link className="mt-2 block text-sm text-emerald-700 underline" href={`/${owner?"owner":"staff"}/bookings/${b.id}`}>Chi tiết vận hành & điểm danh nhóm</Link></div></div>{b.status==='CONFIRMED'&&can('BOOKING_CHECK_IN')&&<form className="mt-5 flex flex-wrap items-center gap-3" onSubmit={e=>{e.preventDefault();const val=cardTokens[b.id]??String(new FormData(e.currentTarget).get('token')||'');action(b,'check-in',val)}}><div className="flex min-w-64 flex-1 items-center gap-2"><Input name="token" value={cardTokens[b.id]??''} onChange={e=>{const v=e.target.value;setCardTokens(prev=>({...prev,[b.id]:v}))}} aria-label={`Mã check-in ${b.id.slice(0,8)}`} placeholder="Mã 10 ký tự hoặc mã QR…" className="flex-1" required disabled={busy}/><QrUploadButton onTokenDetected={(token,bookingId)=>{if(bookingId&&bookingId.toLowerCase()!==b.id.toLowerCase()){if(window.confirm(`Ảnh QR này thuộc booking #${bookingId.slice(0,8).toUpperCase()} (khác với booking hiện tại #${b.id.slice(0,8).toUpperCase()}). Bạn có muốn check-in cho booking #${bookingId.slice(0,8).toUpperCase()} không?`)){action({...b,id:bookingId},'check-in',token);return;}}setCardTokens(prev=>({...prev,[b.id]:token}));action(b,'check-in',token)}} disabled={busy}/></div><Button type="submit" disabled={busy}>Check-in</Button></form>}{b.status==='CONFIRMED'&&can('BOOKING_REFUND')&&<PolicyCancellationPanel bookingId={b.id} onSuccess={bookings.reload}/>}{b.status==='PENDING'&&canPay&&<Button type="button" onClick={()=>setPaymentModalBooking(b)} className="mt-4 gap-2 bg-emerald-600 hover:bg-emerald-700 text-white font-semibold text-xs px-4 py-2 rounded-lg shadow-xs flex items-center"><CreditCard className="size-4" /><span>Thanh toán DEMO tại quầy</span></Button>}</article>
  })}</div>
 <div className="mt-5 flex items-center gap-3"><Button variant="outline" disabled={!page||bookings.loading} onClick={()=>setPage(page-1)}>Trang trước</Button><span className="text-sm">{bookings.data?.total??0} lượt đặt · Trang {page+1}</span><Button variant="outline" disabled={bookings.loading||!bookings.data||page+1>=bookings.data.totalPages} onClick={()=>setPage(page+1)}>Trang sau</Button></div>
 {facilityId&&(can('BOOKING_REFUND')||can('TRANSFER_PAYOUT'))&&<FacilityDemoMoneyPanel facilityId={facilityId} canReadBooking={Boolean(can('BOOKING_READ'))}/>}
 <QuickQrCheckinModal isOpen={quickQrOpen} onClose={()=>setQuickQrOpen(false)} onSuccess={()=>bookings.reload()}/>
 <CounterPaymentModal booking={paymentModalBooking} isOpen={Boolean(paymentModalBooking)} onClose={()=>setPaymentModalBooking(null)} onSuccess={(msg)=>{setMessage(msg);bookings.reload();}}/>
 </div>
}

