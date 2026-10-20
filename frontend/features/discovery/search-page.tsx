'use client'
import Link from 'next/link'
import dynamic from 'next/dynamic'
import { useRouter } from 'next/navigation'
import { useState, useEffect } from 'react'
import { useAuth } from '@/lib/auth-context'
import { MapPin,ArrowRight,SlidersHorizontal } from 'lucide-react'
import { useApi } from '@/lib/use-api'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import type { Category } from '@/features/facility/types'
import { provinces } from '@/features/facility/administrative-divisions'
import { money } from '@/features/schedule/types'
import type { DiscoveryResult } from './types'
import { CourtCard } from './court-card'
const SearchMap=dynamic(()=>import('./search-map').then(module=>module.SearchMap),{ssr:false,loading:()=> <p role="status" className="mt-6">Đang mở bản đồ…</p>})
const fields=['q','sportCategoryId','provinceCode','date','opensAfter','closesBefore','minPrice','maxPrice','latitude','longitude','radiusKm','sort']
const selectStyle='h-12 w-full rounded-md border border-input bg-white px-3 text-sm text-slate-900'
export function SearchPage({initial,today}:{initial:Record<string,string>;today:string}){
 const router=useRouter()
 const { user, ready } = useAuth()

 useEffect(() => {
   if (
     ready &&
     user &&
     user.roles.includes('STAFF') &&
     !user.roles.includes('OWNER') &&
     !user.roles.includes('ADMIN')
   ) {
     router.replace('/staff/bookings')
   }
 }, [user, ready, router])

 const filters=Object.fromEntries(fields.map(name=>[name,initial[name]??(name==='date'?today:name==='sort'?'NAME':'')]))
 const mapView=initial.view==='map'
 const pastDate=Boolean(/^\d{4}-\d{2}-\d{2}$/.test(filters.date)&&filters.date<today);if(pastDate)filters.date=today
 const [categoryId,setCategoryId]=useState(filters.sportCategoryId)
 const [position,setPosition]=useState({latitude:filters.latitude,longitude:filters.longitude});const [locating,setLocating]=useState(false);const [locationMessage,setLocationMessage]=useState('')
 function locate(){if(!navigator.geolocation){setLocationMessage('Trình duyệt không hỗ trợ vị trí. Bạn vẫn có thể tìm theo khu vực.');return}setLocating(true);setLocationMessage('');navigator.geolocation.getCurrentPosition(value=>{setPosition({latitude:String(value.coords.latitude),longitude:String(value.coords.longitude)});setLocationMessage('Đã chọn vị trí hiện tại làm điểm tìm kiếm.');setLocating(false)},()=>{setLocationMessage('Không thể lấy vị trí. Hãy tìm theo khu vực hoặc nhập điểm tìm kiếm.');setLocating(false)},{timeout:10000,maximumAge:60000})}
 const params=new URLSearchParams(Object.entries(filters).filter(([,value])=>value!==''))
 const resource=useApi<{items:DiscoveryResult[];truncated:boolean}>(`/bookings/search?${params}`);const categories=useApi<Category[]>('/sport-categories')
 function search(event:React.FormEvent<HTMLFormElement>){event.preventDefault();const form=new FormData(event.currentTarget);const query=new URLSearchParams();for(const name of fields){const value=String(form.get(name)??'').trim();if(value)query.set(name,value)}if(mapView)query.set('view','map');router.push(`/search?${query}`)}
 function viewHref(map:boolean){const query=new URLSearchParams(params);if(map)query.set('view','map');return `/search?${query}`}
 const time=(value:string,zone:string)=>new Date(value).toLocaleTimeString('vi-VN',{timeZone:zone,hour:'2-digit',minute:'2-digit'})
 return <main className="mx-auto max-w-6xl px-4 sm:px-6 py-6 sm:py-10">
  <div className="rounded-2xl sm:rounded-3xl bg-emerald-900 p-5 sm:p-8 lg:p-10 text-white"><p className="text-xs sm:text-sm font-semibold uppercase tracking-widest text-emerald-300">Lịch chơi bắt đầu tại đây</p><h1 className="mt-2 text-2xl sm:text-3xl lg:text-4xl font-black">Tìm sân cho buổi chơi tiếp theo</h1><p className="mt-3 text-sm text-emerald-100 max-w-xl">Chọn bộ môn và ngày chơi để xem sân còn trống cùng mức giá thực tế.</p>
   <form onSubmit={search} className="mt-6"><div className="grid items-end gap-3 sm:grid-cols-2 lg:grid-cols-4">
    <label className="grid gap-2 text-sm">Tên cơ sở hoặc khu vực<Input name="q" placeholder="Tên cơ sở hoặc khu vực…" maxLength={180} className="h-12 bg-white text-slate-900" defaultValue={filters.q}/></label>
    <label className="grid gap-2 text-sm">Bộ môn<select name="sportCategoryId" aria-label="Bộ môn" value={categoryId} onChange={event=>setCategoryId(event.target.value)} className={selectStyle}><option value="">Tất cả bộ môn</option>{categories.data?.filter(c=>c.active).map(c=><option value={c.id} key={c.id}>{c.name}</option>)}</select></label>
    <label className="grid gap-2 text-sm">Ngày chơi<Input name="date" type="date" defaultValue={filters.date} min={today} required className="h-12 bg-white text-slate-900"/></label><Button type="submit" className="h-12 bg-amber-400 px-6 text-emerald-950 hover:bg-amber-300">Tìm sân</Button>
   </div><details className="mt-5" open={Boolean(initial.provinceCode||initial.opensAfter||initial.minPrice||initial.latitude)}>
    <summary className="w-fit cursor-pointer text-sm font-semibold"><SlidersHorizontal className="mr-2 inline" size={16}/>Bộ lọc khu vực, giờ và giá</summary>
    <div className="mt-4 grid gap-4 rounded-2xl bg-white/10 p-5 sm:grid-cols-2 lg:grid-cols-3">
     <label className="grid gap-2 text-sm">Tỉnh / thành phố<select name="provinceCode" defaultValue={filters.provinceCode} className={selectStyle}><option value="">Tất cả tỉnh / thành phố</option>{provinces.map(province=><option key={province.code} value={province.code}>{province.name}</option>)}</select></label>
     <label className="grid gap-2 text-sm">Sắp xếp<select name="sort" defaultValue={filters.sort} className={selectStyle}><option value="NAME">Tên cơ sở</option><option value="PRICE_ASC">Giá thấp đến cao</option><option value="PRICE_DESC">Giá cao đến thấp</option><option value="DISTANCE">Khoảng cách gần nhất</option></select></label>
     <label className="grid gap-2 text-sm">Slot bắt đầu từ<Input name="opensAfter" type="time" defaultValue={filters.opensAfter} className="bg-white text-slate-900"/></label><label className="grid gap-2 text-sm">Slot kết thúc trước<Input name="closesBefore" type="time" defaultValue={filters.closesBefore} className="bg-white text-slate-900"/></label>
     <div className="grid grid-cols-2 gap-3"><label className="grid gap-2 text-sm">Giá tối thiểu / h<Input name="minPrice" type="number" min={0} step={1} defaultValue={filters.minPrice} className="bg-white text-slate-900"/></label><label className="grid gap-2 text-sm">Giá tối đa / h<Input name="maxPrice" type="number" min={0} step={1} defaultValue={filters.maxPrice} className="bg-white text-slate-900"/></label></div>
     <div className="sm:col-span-2 lg:col-span-3"><Button type="button" variant="outline" disabled={locating} onClick={locate} className="text-slate-900">{locating?'Đang xác định vị trí…':'Dùng vị trí hiện tại'}</Button>{locationMessage&&<p role="status" className="mt-2 text-sm">{locationMessage}</p>}</div>
     <label className="grid gap-2 text-sm">Vĩ độ điểm tìm kiếm<Input name="latitude" type="number" min={-90} max={90} step="any" placeholder="21.028" value={position.latitude} onChange={e=>setPosition(current=>({...current,latitude:e.target.value}))} className="bg-white text-slate-900"/></label><label className="grid gap-2 text-sm">Kinh độ điểm tìm kiếm<Input name="longitude" type="number" min={-180} max={180} step="any" placeholder="105.78" value={position.longitude} onChange={e=>setPosition(current=>({...current,longitude:e.target.value}))} className="bg-white text-slate-900"/></label><label className="grid gap-2 text-sm">Bán kính (km)<Input name="radiusKm" type="number" min={0.1} max={500} step="any" defaultValue={filters.radiusKm} className="bg-white text-slate-900"/></label>
     <p className="text-xs leading-relaxed text-emerald-100 sm:col-span-2 lg:col-span-3">Giá áp dụng theo giờ (/h). Lịch và khung giờ theo múi giờ của cơ sở. Nhập cả vĩ độ và kinh độ khi tìm theo khoảng cách.</p>
    </div></details><Link href="/search" className="mt-4 inline-block text-sm text-emerald-100 underline">Đặt lại bộ lọc</Link>
   </form></div>
  {categories.error&&<p role="alert" className="mt-5 text-sm text-red-700">Không thể tải bộ môn. <button onClick={categories.reload} className="underline">Thử lại</button></p>}
  {pastDate&&<p role="status" className="mt-5 text-sm text-amber-700">Ngày tìm kiếm đã qua. Đã chuyển sang ngày hôm nay.</p>}
  <div className="mt-8 flex flex-wrap justify-between gap-3"><h2 className="text-xl font-bold">{filters.q?`Kết quả cho “${filters.q}”`:'Sân còn trống cho ngày đã chọn'}</h2>{resource.data&&<p className="text-sm text-slate-500">{resource.data.items.length} sân · {filters.date}</p>}</div>
  <nav aria-label="Cách xem kết quả" className="mt-5 flex w-fit gap-1 rounded-xl bg-slate-100 p-1">
   <Link href={viewHref(false)} scroll={false} aria-current={!mapView?'page':undefined} className={`rounded-lg px-4 py-2 text-sm font-semibold ${!mapView?'bg-white text-emerald-800 shadow-sm':'text-slate-600 hover:text-emerald-800'}`}>Danh sách</Link>
   <Link href={viewHref(true)} scroll={false} aria-current={mapView?'page':undefined} className={`rounded-lg px-4 py-2 text-sm font-semibold ${mapView?'bg-white text-emerald-800 shadow-sm':'text-slate-600 hover:text-emerald-800'}`}>Bản đồ</Link>
  </nav>
  {resource.loading&&<p role="status" className="mt-6">Đang kiểm tra sân, giá và lịch trống…</p>}{resource.error&&<p role="alert" className="mt-6 rounded-xl bg-red-50 p-4 text-red-700">{resource.error}<button className="ml-3 underline" onClick={resource.reload}>Thử lại</button></p>}
  {resource.data?.truncated&&<p className="mt-5 text-sm text-amber-700">Đang hiển thị tối đa 100 kết quả. Hãy thêm khu vực hoặc bộ môn để thu hẹp tìm kiếm.</p>}{resource.data?.items.length===0&&<div className="my-8 rounded-2xl border p-8"><h3 className="font-bold">Chưa tìm thấy sân phù hợp</h3><p className="mt-2 text-slate-500">Thử mở rộng khu vực, khoảng giá hoặc chọn ngày và khung giờ khác.</p></div>}
  {mapView&&resource.data&&resource.data.items.length>0&&<SearchMap results={resource.data.items}/>}
  {!mapView&&<div className="mt-6 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">{resource.data?.items.map(result=><CourtCard key={result.courtId} result={result} time={time}/>)}</div>}
 </main>
}
