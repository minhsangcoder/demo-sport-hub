'use client'
import Link from 'next/link'
import { useState } from 'react'
import {ImpactNotice,readImpact,type ConfigurationImpact} from '@/features/integration/configuration-impact'
import { api } from '@/lib/api'
import { useApi } from '@/lib/use-api'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { useFacility } from '@/features/facility/facility-context'
import type { Court } from '@/features/facility/types'
import { weekdays,type Hours,type ScheduleException } from './types'
type Interval=Pick<Hours,'dayOfWeek'|'opensAt'|'closesAt'|'slotMinutes'>
type TimeRange={opensAt:string;closesAt:string}
type WeekdayException={dayOfWeek:number;closed:boolean;ranges:TimeRange[]}

interface ParsedSchedule{defaultRanges:TimeRange[];weekdayExceptions:WeekdayException[]}

function parseInitialSchedule(initial:Interval[]):ParsedSchedule{
 if(!initial||initial.length===0){
  return{defaultRanges:[{opensAt:'06:00',closesAt:'22:00'}],weekdayExceptions:[]}
 }
 const dayMap=new Map<number,TimeRange[]>()
 for(let d=1;d<=7;d++)dayMap.set(d,[])
 for(const item of initial){
  const list=dayMap.get(item.dayOfWeek)||[]
  list.push({opensAt:item.opensAt.slice(0,5),closesAt:item.closesAt.slice(0,5)})
  dayMap.set(item.dayOfWeek,list)
 }
 for(let d=1;d<=7;d++){
  dayMap.get(d)!.sort((a,b)=>a.opensAt.localeCompare(b.opensAt))
 }
 const sigMap=new Map<string,number>()
 for(let d=1;d<=7;d++){
  const list=dayMap.get(d)!
  const sig=list.length===0?'CLOSED':list.map(r=>`${r.opensAt}-${r.closesAt}`).join(';')
  sigMap.set(sig,(sigMap.get(sig)||0)+1)
 }
 let dominantSig=''
 let maxCount=-1
 for(const[sig,count] of sigMap.entries()){
  if(sig!=='CLOSED'&&count>maxCount){maxCount=count;dominantSig=sig}
 }
 if(!dominantSig)dominantSig='CLOSED'
 let defaultRanges:TimeRange[]=[{opensAt:'06:00',closesAt:'22:00'}]
 if(dominantSig!=='CLOSED'){
  defaultRanges=dominantSig.split(';').map(part=>{
   const[opensAt,closesAt]=part.split('-')
   return{opensAt,closesAt}
  })
 }
 const weekdayExceptions:WeekdayException[]=[]
 for(let d=1;d<=7;d++){
  const list=dayMap.get(d)!
  const sig=list.length===0?'CLOSED':list.map(r=>`${r.opensAt}-${r.closesAt}`).join(';')
  if(sig!==dominantSig){
   if(sig==='CLOSED'){
    weekdayExceptions.push({dayOfWeek:d,closed:true,ranges:[{opensAt:'06:00',closesAt:'22:00'}]})
   }else{
    weekdayExceptions.push({dayOfWeek:d,closed:false,ranges:list.slice(0,2)})
   }
  }
 }
 return{defaultRanges,weekdayExceptions}
}

export function SchedulePage(){
 const {facility,basePath}=useFacility();const hours=useApi<Hours[]>(`/schedules/facilities/${facility.id}/hours`)
 const courts=useApi<Court[]>(`/owner/facilities/${facility.id}/courts`);const [scope,setScope]=useState('');const [feedback,setFeedback]=useState('')
 return <section className="space-y-6"><div className="flex flex-wrap items-center justify-between gap-4"><div><h2 className="text-2xl font-bold">Lịch mở cửa</h2><p className="mt-2 text-sm text-slate-500">Giờ theo {facility.timezone}. Sân dùng lịch cơ sở khi chưa có lịch riêng.</p></div><Link className="rounded-full bg-emerald-600 px-5 py-2 text-sm text-white" href={`${basePath}/schedule/preview`}>Xem trước lịch và giá</Link></div>
  <label className="grid max-w-sm gap-2 text-sm">Phạm vi lịch<select className="rounded-lg border bg-white p-3" value={scope} onChange={e=>{setScope(e.target.value);setFeedback('')}}><option value="">Toàn cơ sở</option>{courts.data?.map(c=><option value={c.id} key={c.id}>{c.name}</option>)}</select></label>
  {(hours.error||courts.error)&&<p role="alert" className="text-red-700">{hours.error||courts.error}<button className="ml-3 underline" onClick={()=>{hours.reload();courts.reload()}}>Thử lại</button></p>}
  {hours.loading?<p role="status">Đang tải lịch…</p>:hours.data&&<HoursEditor onSaved={setFeedback} key={scope+JSON.stringify(hours.data)} facilityId={facility.id} courtId={scope} initial={hours.data.filter(h=>(h.courtId||'')===scope)} reload={hours.reload}/>}
  {feedback&&<p role="status" className="text-sm text-emerald-700">{feedback}</p>}<Exceptions key={scope} facilityId={facility.id} courtId={scope}/>
 </section>
}

function HoursEditor({facilityId,courtId,initial,reload,onSaved}:{facilityId:string;courtId:string;initial:Interval[];reload:()=>void;onSaved:(message:string)=>void}){
 const parsed=parseInitialSchedule(initial)
 const [impact,setImpact]=useState<ConfigurationImpact|null>(null)
 const [ranges,setRanges]=useState<TimeRange[]>(parsed.defaultRanges)
 const [weekdayExceptions,setWeekdayExceptions]=useState<WeekdayException[]>(parsed.weekdayExceptions)
 const [busy,setBusy]=useState(false)
 const [error,setError]=useState('')

 function updateRange(index:number,key:keyof TimeRange,value:string){
  setRanges(ranges.map((r,i)=>i===index?{...r,[key]:value}:r))
 }
 function addRange(){
  if(ranges.length>=2)return
  const firstEnd=ranges[0]?.closesAt||'12:00'
  const newStart=firstEnd<'13:00'?'13:00':firstEnd
  const newEnd='22:00'>newStart?'22:00':'23:00'
  setRanges([...ranges,{opensAt:newStart,closesAt:newEnd}])
 }
 function removeRange(index:number){
  setRanges(ranges.filter((_,i)=>i!==index))
 }

 function addWeekdayException(){
  const usedDays=new Set(weekdayExceptions.map(e=>e.dayOfWeek))
  const preference=[6,7,5,1,2,3,4]
  const nextDay=preference.find(d=>!usedDays.has(d))||[1,2,3,4,5,6,7].find(d=>!usedDays.has(d))
  if(!nextDay)return
  setWeekdayExceptions([
   ...weekdayExceptions,
   {dayOfWeek:nextDay,closed:false,ranges:ranges.map(r=>({...r}))}
  ])
 }
 function removeWeekdayException(index:number){
  setWeekdayExceptions(weekdayExceptions.filter((_,i)=>i!==index))
 }
 function updateExceptionDay(index:number,newDay:number){
  setWeekdayExceptions(weekdayExceptions.map((e,i)=>i===index?{...e,dayOfWeek:newDay}:e))
 }
 function updateExceptionClosed(index:number,closed:boolean){
  setWeekdayExceptions(weekdayExceptions.map((e,i)=>i===index?{...e,closed}:e))
 }
 function updateExceptionRange(excIndex:number,rangeIndex:number,key:keyof TimeRange,val:string){
  setWeekdayExceptions(weekdayExceptions.map((e,i)=>{
   if(i!==excIndex)return e
   const updatedRanges=e.ranges.map((r,ri)=>ri===rangeIndex?{...r,[key]:val}:r)
   return{...e,ranges:updatedRanges}
  }))
 }
 function addExceptionRange(excIndex:number){
  setWeekdayExceptions(weekdayExceptions.map((e,i)=>{
   if(i!==excIndex||e.ranges.length>=2)return e
   const firstEnd=e.ranges[0]?.closesAt||'12:00'
   const newStart=firstEnd<'13:00'?'13:00':firstEnd
   const newEnd='22:00'>newStart?'22:00':'23:00'
   return{...e,ranges:[...e.ranges,{opensAt:newStart,closesAt:newEnd}]}
  }))
 }
 function removeExceptionRange(excIndex:number,rangeIndex:number){
  setWeekdayExceptions(weekdayExceptions.map((e,i)=>{
   if(i!==excIndex)return e
   return{...e,ranges:e.ranges.filter((_,ri)=>ri!==rangeIndex)}
  }))
 }

 async function save(e:React.FormEvent){
  e.preventDefault();if(busy)return;setBusy(true);setError('');onSaved('');setImpact(null)
  if(ranges.length===0){setError('Vui lòng thiết lập ít nhất một khoảng thời gian mở cửa chung.');setBusy(false);return}
  const toMinutes=(t:string)=>{const [h,m]=t.split(':').map(Number);return h*60+(m||0)}

  for(let i=0;i<ranges.length;i++){
   const r=ranges[i]
   if(!r.opensAt||!r.closesAt){setError(`Giờ mở cửa chung - Khoảng ${i+1}: Vui lòng nhập đầy đủ giờ.`);setBusy(false);return}
   if(r.opensAt>=r.closesAt){setError(`Giờ mở cửa chung - Khoảng ${i+1}: Giờ mở cửa (${r.opensAt}) phải trước giờ đóng cửa (${r.closesAt}).`);setBusy(false);return}
   if((toMinutes(r.closesAt)-toMinutes(r.opensAt))%60!==0){setError(`Giờ mở cửa chung - Khoảng ${i+1}: Thời lượng phải là bội số của 60 phút.`);setBusy(false);return}
  }
  if(ranges.length===2){
   const [r1,r2]=ranges
   if(r1.opensAt<r2.closesAt&&r2.opensAt<r1.closesAt){setError('Hai khoảng thời gian mở cửa chung không được trùng lặp nhau.');setBusy(false);return}
  }

  for(const exc of weekdayExceptions){
   const dayName=weekdays[exc.dayOfWeek-1]
   if(!exc.closed){
    if(exc.ranges.length===0){setError(`Ngoại lệ ${dayName}: Vui lòng thiết lập ít nhất một khoảng giờ hoặc chọn Đóng cửa cả ngày.`);setBusy(false);return}
    for(let i=0;i<exc.ranges.length;i++){
     const r=exc.ranges[i]
     if(!r.opensAt||!r.closesAt){setError(`Ngoại lệ ${dayName} - Khoảng ${i+1}: Vui lòng nhập đầy đủ giờ.`);setBusy(false);return}
     if(r.opensAt>=r.closesAt){setError(`Ngoại lệ ${dayName} - Khoảng ${i+1}: Giờ mở cửa (${r.opensAt}) phải trước giờ đóng cửa (${r.closesAt}).`);setBusy(false);return}
     if((toMinutes(r.closesAt)-toMinutes(r.opensAt))%60!==0){setError(`Ngoại lệ ${dayName} - Khoảng ${i+1}: Thời lượng phải là bội số của 60 phút.`);setBusy(false);return}
    }
    if(exc.ranges.length===2){
     const [r1,r2]=exc.ranges
     if(r1.opensAt<r2.closesAt&&r2.opensAt<r1.closesAt){setError(`Ngoại lệ ${dayName}: Hai khoảng thời gian mở cửa không được trùng lặp nhau.`);setBusy(false);return}
    }
   }
  }

  const intervals:Interval[]=[]
  for(let day=1;day<=7;day++){
   const exc=weekdayExceptions.find(e=>e.dayOfWeek===day)
   if(exc){
    if(!exc.closed){
     for(const r of exc.ranges){
      intervals.push({dayOfWeek:day,opensAt:r.opensAt,closesAt:r.closesAt,slotMinutes:60})
     }
    }
   }else{
    for(const r of ranges){
     intervals.push({dayOfWeek:day,opensAt:r.opensAt,closesAt:r.closesAt,slotMinutes:60})
    }
   }
  }

  try{
   await api(`/schedules/facilities/${facilityId}/hours`,{method:'PUT',body:JSON.stringify({courtId:courtId||null,intervals})})
   onSaved('Đã lưu lịch mở cửa và các ngoại lệ theo thứ thành công.')
   reload()
  }catch(err){
   setImpact(readImpact(err))
   setError(err instanceof Error?err.message:'Không thể lưu lịch.')
  }finally{
   setBusy(false)
  }
 }

 async function resetToFacility(){
  if(!courtId)return;if(!window.confirm('Kế thừa lịch mở cửa của toàn cơ sở cho sân này?'))return;setBusy(true);setError('')
  try{await api(`/schedules/facilities/${facilityId}/hours`,{method:'PUT',body:JSON.stringify({courtId,intervals:[]})});onSaved('Đã chuyển sang dùng lịch cơ sở.');reload()}
  catch(err){setError(err instanceof Error?err.message:'Không thể đặt lại lịch.')}finally{setBusy(false)}
 }

 return <form onSubmit={save} className="space-y-6 rounded-2xl border bg-white p-5 shadow-sm">
  <div className="space-y-4">
   <div className="flex flex-wrap items-center justify-between gap-2 border-b pb-3">
    <div>
     <h3 className="text-base font-bold text-slate-900">Các khoảng mở cửa chung (Toàn bộ tuần)</h3>
     <p className="mt-0.5 text-xs text-slate-500">Khung giờ mở cửa áp dụng mặc định cho các ngày trong tuần (Thứ hai – Chủ nhật). Các khung giờ ngoài khoảng này mặc định đóng cửa.</p>
    </div>
    <span className="inline-flex items-center rounded-full bg-emerald-50 px-3 py-1 text-xs font-semibold text-emerald-700 border border-emerald-200">
     Cả tuần (Thứ 2 – CN)
    </span>
   </div>

   {courtId&&initial.length===0&&<div className="rounded-lg bg-emerald-50 border border-emerald-200 p-3 text-xs text-emerald-800">Sân này hiện đang kế thừa lịch mở cửa của toàn cơ sở. Bạn có thể chỉnh sửa và nhấn <strong>"Lưu lịch"</strong> để đặt lịch riêng cho sân này.</div>}

   <div className="space-y-3">
    {ranges.map((row,i)=><div key={i} className="rounded-xl border border-slate-200 bg-slate-50/70 p-4">
     <div className="mb-3 flex items-center justify-between">
      <span className="text-xs font-bold uppercase tracking-wider text-slate-700">{i===0?'Khoảng thời gian 1 (Ca chính)':'Khoảng thời gian 2 (Tùy chọn - Ca phụ)'}</span>
      {i>0&&<button type="button" className="text-xs font-medium text-red-600 hover:text-red-700 underline" disabled={busy} onClick={()=>removeRange(i)}>Bỏ khoảng</button>}
     </div>
     <div className="grid gap-4 sm:grid-cols-2">
      <label className="grid gap-1.5 text-xs font-medium text-slate-700">Mở cửa<Input type="time" value={row.opensAt} onChange={e=>updateRange(i,'opensAt',e.target.value)} required disabled={busy}/></label>
      <label className="grid gap-1.5 text-xs font-medium text-slate-700">Đóng cửa<Input type="time" value={row.closesAt} onChange={e=>updateRange(i,'closesAt',e.target.value)} required disabled={busy}/></label>
     </div>
    </div>)}
   </div>

   {ranges.length<2&&<Button type="button" variant="outline" disabled={busy} onClick={addRange}>Thêm khoảng giờ</Button>}
  </div>

  {/* SECTION: Ngoại lệ theo thứ trong tuần */}
  <div className="rounded-2xl border border-slate-200 bg-slate-50/60 p-5 space-y-4">
   <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 pb-3">
    <div>
     <h4 className="text-sm font-bold text-slate-900">Ngoại lệ giờ mở cửa theo thứ trong tuần</h4>
     <p className="mt-0.5 text-xs text-slate-500">Cài đặt giờ mở cửa riêng hoặc đóng cửa định kỳ cho một số thứ cụ thể (ví dụ: Thứ bảy, Chủ nhật). Thứ có ngoại lệ sẽ được <strong className="text-emerald-700">ưu tiên áp dụng trước</strong> giờ mở cửa chung của tuần.</p>
    </div>
    {weekdayExceptions.length<7&&<Button type="button" variant="outline" size="sm" disabled={busy} onClick={addWeekdayException} className="text-xs font-semibold text-emerald-700 border-emerald-300 hover:bg-emerald-50">
     + Thêm ngoại lệ theo thứ
    </Button>}
   </div>

   {weekdayExceptions.length===0?(
    <p className="text-xs text-slate-500 py-1 italic">Chưa có ngoại lệ theo thứ nào. Tất cả các ngày trong tuần hiện đang áp dụng theo giờ mở cửa chung ở trên.</p>
   ):(
    <div className="space-y-3">
     {weekdayExceptions.map((exc,excIdx)=>{
      const usedByOthers=new Set(weekdayExceptions.filter((_,idx)=>idx!==excIdx).map(e=>e.dayOfWeek))
      const availableDays=weekdays.map((name,n)=>({name,value:n+1})).filter(w=>!usedByOthers.has(w.value))
      return <div key={exc.dayOfWeek} className="rounded-xl border border-emerald-200 bg-white p-4 shadow-xs space-y-3">
       <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 pb-2.5">
        <div className="flex items-center gap-3">
         <span className="text-xs font-bold uppercase text-emerald-800">Thứ áp dụng:</span>
         <select
          className="rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-xs font-semibold text-slate-800 focus:border-emerald-500 focus:outline-hidden"
          value={exc.dayOfWeek}
          onChange={e=>updateExceptionDay(excIdx,Number(e.target.value))}
          disabled={busy}
         >
          {availableDays.map(d=><option key={d.value} value={d.value}>{d.name}</option>)}
         </select>
        </div>

        <div className="flex items-center gap-4">
         <div className="flex items-center gap-3 text-xs">
          <label className="flex items-center gap-1.5 cursor-pointer font-medium text-slate-700">
           <input type="radio" name={`exc-status-${excIdx}`} checked={!exc.closed} onChange={()=>updateExceptionClosed(excIdx,false)} disabled={busy}/>
           Mở theo giờ riêng
          </label>
          <label className="flex items-center gap-1.5 cursor-pointer font-medium text-slate-700">
           <input type="radio" name={`exc-status-${excIdx}`} checked={exc.closed} onChange={()=>updateExceptionClosed(excIdx,true)} disabled={busy}/>
           Đóng cửa cả ngày
          </label>
         </div>
         <button type="button" className="text-xs font-medium text-red-600 hover:text-red-700 underline" disabled={busy} onClick={()=>removeWeekdayException(excIdx)}>
          Xóa ngoại lệ
         </button>
        </div>
       </div>

       {exc.closed?(
        <p className="text-xs text-amber-800 bg-amber-50 rounded-lg p-2.5">
         ⚠️ Sân sẽ đóng cửa cả ngày vào <strong>{weekdays[exc.dayOfWeek-1]}</strong> hàng tuần (không nhận đặt sân).
        </p>
       ):(
        <div className="space-y-3">
         {exc.ranges.map((r,rIdx)=><div key={rIdx} className="rounded-lg border border-slate-200 bg-slate-50/60 p-3">
          <div className="mb-2 flex items-center justify-between text-xs">
           <span className="font-semibold text-slate-700">{rIdx===0?`Khoảng 1 (Ca chính - ${weekdays[exc.dayOfWeek-1]})`:`Khoảng 2 (Ca phụ - ${weekdays[exc.dayOfWeek-1]})`}</span>
           {rIdx>0&&<button type="button" className="text-xs text-red-600 hover:text-red-700 underline" disabled={busy} onClick={()=>removeExceptionRange(excIdx,rIdx)}>Bỏ khoảng 2</button>}
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
           <label className="grid gap-1 text-xs font-medium text-slate-700">Mở cửa<Input type="time" value={r.opensAt} onChange={e=>updateExceptionRange(excIdx,rIdx,'opensAt',e.target.value)} required disabled={busy}/></label>
           <label className="grid gap-1 text-xs font-medium text-slate-700">Đóng cửa<Input type="time" value={r.closesAt} onChange={e=>updateExceptionRange(excIdx,rIdx,'closesAt',e.target.value)} required disabled={busy}/></label>
          </div>
         </div>)}
         {exc.ranges.length<2&&<button type="button" className="text-xs font-medium text-emerald-700 hover:underline" disabled={busy} onClick={()=>addExceptionRange(excIdx)}>
          + Thêm ca phụ cho {weekdays[exc.dayOfWeek-1]}
         </button>}
        </div>
       )}
      </div>
     })}
    </div>
   )}
  </div>

  <div className="flex flex-wrap items-center gap-3 pt-1 border-t pt-4">
   <Button type="submit" disabled={busy}>{busy?'Đang lưu…':'Lưu lịch'}</Button>
   {courtId&&initial.length>0&&<Button type="button" variant="outline" disabled={busy} onClick={resetToFacility}>Kế thừa lịch cơ sở</Button>}
  </div>

  <ImpactNotice impact={impact}/>{error&&<p role="alert" className="text-sm font-medium text-red-700">{error}</p>}
  <p className="text-xs text-slate-500">* Lưu ý: Các ngoại lệ theo thứ được ưu tiên trước giờ mở cửa chung. Thay đổi lịch không tự hủy lịch đặt đã xác nhận.</p>
 </form>
}
function Exceptions({facilityId,courtId}:{facilityId:string;courtId:string}){
 const resource=useApi<ScheduleException[]>(`/schedules/facilities/${facilityId}/exceptions`);const [type,setType]=useState('CLOSED');const [editing,setEditing]=useState<ScheduleException|null>(null);const [busy,setBusy]=useState(false);const [error,setError]=useState('')
 async function save(e:React.FormEvent<HTMLFormElement>){e.preventDefault();if(busy)return;setBusy(true);setError('');const form=e.currentTarget;const v=new FormData(form)
  try{await api(`/schedules/facilities/${facilityId}/exceptions${editing?'/'+editing.id:''}`,{method:editing?'PUT':'POST',body:JSON.stringify({courtId:courtId||null,date:v.get('date'),type,opensAt:type==='CLOSED'?null:v.get('opensAt'),closesAt:type==='CLOSED'?null:v.get('closesAt'),reason:v.get('reason')})});resource.reload();form.reset();setEditing(null)}catch(err){setError(err instanceof Error?err.message:'Không thể lưu ngoại lệ.')}finally{setBusy(false)}
 }
 async function remove(id:string){if(!window.confirm('Bỏ ngoại lệ này?'))return;setBusy(true);setError('');try{await api(`/schedules/facilities/${facilityId}/exceptions/${id}`,{method:'DELETE'});resource.reload()}catch(err){setError(err instanceof Error?err.message:'Không thể bỏ ngoại lệ.')}finally{setBusy(false)}}
 return <div className="rounded-2xl border p-5"><h3 className="font-bold">Ngày nghỉ và giờ đặc biệt</h3><p className="mt-1 text-xs text-slate-500">Thiết lập ngày nghỉ hoặc giờ mở cửa riêng cho một ngày cụ thể (nếu khác với lịch cố định cả tuần ở trên).</p><form key={editing?.id??"new"} className="mt-4 grid gap-4 sm:grid-cols-2" onSubmit={save}><label className="grid gap-2 text-sm">Ngày<Input type="date" name="date" defaultValue={editing?.date} required disabled={busy}/></label><label className="grid gap-2 text-sm">Loại ngoại lệ<select className="rounded-lg border bg-white p-3" value={type} onChange={e=>setType(e.target.value)} disabled={busy}><option value="CLOSED">Đóng cửa</option><option value="SPECIAL_HOURS">Giờ đặc biệt</option></select></label>{type==='SPECIAL_HOURS'&&<><label className="grid gap-2 text-sm">Mở cửa<Input type="time" name="opensAt" defaultValue={editing?.opensAt??undefined} required disabled={busy}/></label><label className="grid gap-2 text-sm">Đóng cửa<Input type="time" name="closesAt" defaultValue={editing?.closesAt??undefined} required disabled={busy}/></label></>}<label className="grid gap-2 text-sm sm:col-span-2">Lý do<Input name="reason" defaultValue={editing?.reason} required maxLength={500} disabled={busy}/></label><Button type="submit" disabled={busy}>{busy?'Đang lưu…':editing?'Lưu ngoại lệ':'Thêm ngoại lệ'}</Button>{editing&&<Button type="button" variant="outline" onClick={()=>setEditing(null)}>Hủy chỉnh sửa ngoại lệ</Button>}</form>
  {(resource.error||error)&&<p role="alert" className="mt-4 text-red-700">{error||resource.error}</p>}{resource.loading&&<p role="status" className="mt-4">Đang tải ngoại lệ…</p>}
  <div className="mt-5 space-y-3">{resource.data?.filter(e=>(e.courtId||'')===courtId).map(e=><article className="flex flex-wrap justify-between gap-3 rounded-lg bg-slate-50 p-4" key={e.id}><div className="text-sm"><p className="font-semibold">{e.date} · {e.type==='CLOSED'?'Đóng cửa':`${e.opensAt} – ${e.closesAt}`}</p><p className="mt-1 text-slate-500">{e.reason}</p></div><Button type="button" variant="outline" disabled={busy} onClick={()=>{setEditing(e);setType(e.type)}}>Chỉnh sửa ngoại lệ</Button><button className="text-sm text-red-700" disabled={busy} onClick={()=>remove(e.id)}>Bỏ ngoại lệ</button></article>)}{resource.data?.filter(e=>(e.courtId||'')===courtId).length===0&&<p className="text-sm text-slate-500">Chưa có ngoại lệ ở phạm vi này.</p>}</div>
 </div>
}
