'use client'
import Link from 'next/link'
import { useEffect,useState } from 'react'
import { useRouter } from 'next/navigation'
import { api } from '@/lib/api'
import { useApi } from '@/lib/use-api'
import { useAuth } from '@/lib/auth-context'
import { RequireAuth } from '@/features/auth/require-auth'
import { Button } from '@/components/ui/button'
import { money } from '@/features/schedule/types'
import type { Payment,BookingDetail } from './types'
import {PaymentSynchronization} from '@/features/integration/synchronization'
import { Countdown } from './countdown'
import { bookingResultPath } from './ownership'
import { RefundPolicyNotice } from './refund-policy-notice'
import { notifyDemoMoneyChanged } from './demo-finance'
import { TransferFinanceNotice } from '@/features/transfers/finance-notice'
import type { Acquisition } from '@/features/transfers/types'
export function PaymentPage({id}:{id:string}){return <RequireAuth><Loaded id={id}/></RequireAuth>}
function Loaded({id}:{id:string}){
 const router=useRouter();const {user}=useAuth();const resource=useApi<Payment>(`/payments/${id}`);const [busy,setBusy]=useState(false);const [error,setError]=useState('');const [waiting,setWaiting]=useState(false)
 const bookingResource=useApi<BookingDetail>(resource.data?.bookingId&&resource.data.purpose!=='TRANSFER'?`/bookings/${resource.data.bookingId}`:null)
 const acquisitionResource=useApi<Acquisition>(resource.data?.acquisitionId?`/transfers/acquisitions/${resource.data.acquisitionId}`:null)
 const owned=useApi<{id:string}[]>(user?.roles.includes('OWNER')?'/owner/facilities':null)
 useEffect(()=>{if(!waiting||!resource.data)return;const p=resource.data;let active=true;let attempts=0;const timer=setInterval(async()=>{try{const detail=await api<BookingDetail>(`/bookings/${p.bookingId}`);const target=bookingResultPath(p,detail.booking,user,owned.data?.map(f=>f.id));if(active&&detail.booking.status==='CONFIRMED'&&target){clearInterval(timer);router.push(target)}else if(active&&++attempts>=15){clearInterval(timer);setWaiting(false);setError('Thanh toán đã nhận, đang đối soát trạng thái booking. Mở lịch đặt hoặc thử tải lại.')}}catch(err){if(active){clearInterval(timer);setWaiting(false);setError(err instanceof Error?err.message:'Không thể tải trạng thái.')}}},1000);return()=>{active=false;clearInterval(timer)}},[waiting,resource.data,router,user,owned.data])
 async function complete(outcome:'SUCCESS'|'FAILED'){if(busy||waiting)return;setBusy(true);setError('');try{const p=await api<Payment>(`/payments/${id}/demo/complete`,{method:'POST',body:JSON.stringify({outcome})});resource.reload();notifyDemoMoneyChanged();if(p.status==='SUCCESS'){if(p.purpose==='TRANSFER'){router.push(`/customer/transfers/acquisitions/${p.acquisitionId}`)}else if(p.purpose==='GROUP_CONTRIBUTION'){router.push(`/customer/groups/${p.bookingId}`)}else setWaiting(true)}}catch(err){setError(err instanceof Error?err.message:'Không thể thực hiện thanh toán demo.')}finally{setBusy(false)}}
 if(resource.loading)return <p role="status" className="p-8">Đang tải giao dịch…</p>
 if(resource.error||!resource.data)return <p role="alert" className="p-8 text-red-700">{resource.error}</p>
 const p=resource.data;const resultPath=bookingResultPath(p,bookingResource.data?.booking,user,owned.data?.map(f=>f.id))
 return <div className="mx-auto max-w-xl px-5 py-10"><p className="text-sm font-bold text-amber-700">MÔI TRƯỜNG THANH TOÁN DEMO</p><h1 className="mt-4 text-3xl font-black">Thanh toán lượt đặt</h1><p className="mt-3 text-sm text-slate-500">Không phát sinh giao dịch tiền thật. Kết quả được xử lý qua backend.</p><section className="my-6 space-y-4 rounded-2xl border p-6"><p className="text-sm text-slate-500">Mã giao dịch <span className="break-all">{p.providerReference}</span></p><p className="text-3xl font-bold text-emerald-700">{money(p.amount,p.currency)}</p><p className="text-sm">Trạng thái: {p.status}</p>{p.status==='PENDING'&&<Countdown expiresAt={p.expiresAt}/>}</section>
  {bookingResource.data&&<RefundPolicyNotice policy={bookingResource.data.booking.priceSnapshot.refundPolicy}/>}
  {acquisitionResource.data?.financeTerms&&<TransferFinanceNotice terms={acquisitionResource.data.financeTerms}/>}
  {p.status==='SUCCESS'&&p.purpose!=='TRANSFER'&&<PaymentSynchronization id={id}/>}
  {error&&<p role="alert" className="mb-4 rounded-lg bg-red-50 p-4 text-sm text-red-700">{error}</p>}{waiting&&<p role="status" className="mb-4 text-emerald-700">Đã nhận thanh toán. Đang xác nhận booking…</p>}
  {p.status==='PENDING'&&<div className="grid gap-3"><Button className="h-12 bg-emerald-600 text-white" onClick={()=>complete('SUCCESS')} disabled={busy||waiting}>{busy?'Đang xử lý…':'Thanh toán DEMO'}</Button><Button variant="outline" onClick={()=>complete('FAILED')} disabled={busy||waiting}>Giả lập thanh toán thất bại</Button></div>}
  {bookingResource.error&&<p role="alert" className="my-4 text-red-700">{bookingResource.error}<button className="ml-3 underline" onClick={bookingResource.reload}>Tải lại booking</button></p>}
  {p.status==='SUCCESS'&&!waiting&&resultPath&&<Link href={resultPath} className="block rounded-full bg-emerald-600 p-3 text-center text-sm text-white">Xem kết quả đặt sân</Link>}{p.status==='FAILED'&&resultPath&&<Link className="text-emerald-700 underline" href={resultPath}>Xem giao dịch và thử lại</Link>}
 </div>
}
