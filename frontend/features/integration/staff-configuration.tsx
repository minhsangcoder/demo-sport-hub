'use client'
import Link from 'next/link'
import {useState} from 'react'
import {RequireAuth} from '@/features/auth/require-auth'
import {useApi} from '@/lib/use-api'
import {businessApi} from '@/lib/business-api'
import {FacilityWorkspace} from '@/features/facility/facility-context'
import type {FacilityProfile,Court} from '@/features/facility/types'
import {SchedulePage} from '@/features/schedule/schedule-page'
import {PreviewPage} from '@/features/schedule/preview-page'
import {MaintenancePage} from '@/features/facility/maintenance'
import {Button} from '@/components/ui/button'
import {Frame,Card,Status,useMutation} from './shared'
import {ImpactNotice,readImpact,type ConfigurationImpact} from './configuration-impact'
export type StaffTool='schedule'|'maintenance'|'courts'|'preview'
const permissions={schedule:'SCHEDULE_MANAGE',preview:'SCHEDULE_MANAGE',maintenance:'COURT_MAINTENANCE_MANAGE',courts:'COURT_STATUS_MANAGE'}
export function StaffConfiguration({id,tool}:{id:string;tool:StaffTool}){return <RequireAuth roles={['STAFF']}><Workspace id={id} tool={tool}/></RequireAuth>}
function Workspace({id,tool}:{id:string;tool:StaffTool}){
 const access=useApi<{facilityBindings:{facilityId:string;permissions:string[]}[]}>('/users/me/access')
 const codes=access.data?.facilityBindings.find(b=>b.facilityId===id)?.permissions??[]
 const allowed=codes.includes(permissions[tool])
 const facility=useApi<FacilityProfile>(allowed?`/facilities/${id}/operations/access?permission=${permissions[tool]}`:null)
 const base=`/staff/facilities/${id}`
 return <Frame title="Cấu hình vận hành được phân quyền"><Link className="text-emerald-700 underline" href="/staff/schedule">Về lịch cơ sở được phân công</Link><Status loading={access.loading||facility.loading} error={access.error||facility.error}/>{!access.loading&&!allowed&&<p role="alert">Bạn chưa có quyền thực hiện thao tác này tại cơ sở.</p>}{facility.data&&<FacilityWorkspace facility={facility.data} reload={facility.reload} basePath={base}><h2 className="text-xl font-bold">{facility.data.name}</h2><nav className="flex flex-wrap gap-3" aria-label="Quyền vận hành">{([['schedule','Lịch mở cửa'],['maintenance','Bảo trì'],['courts','Trạng thái sân']] as const).filter(([key])=>codes.includes(permissions[key])).map(([key,label])=><Link className="rounded-full border px-4 py-2 text-sm text-emerald-700" key={key} href={base+'/'+key}>{label}</Link>)}</nav>{tool==='schedule'?<SchedulePage/>:tool==='preview'?<PreviewPage/>:tool==='maintenance'?<MaintenancePage/>:<CourtStatuses id={id}/>}</FacilityWorkspace>}</Frame>
}
function CourtStatuses({id}:{id:string}){const r=useApi<Court[]>(`/owner/facilities/${id}/courts`),a=useMutation(),[impact,setImpact]=useState<ConfigurationImpact|null>(null);return <section className="space-y-4"><h2 className="text-2xl font-bold">Trạng thái sân</h2><Status loading={r.loading} error={r.error||a.error}/><ImpactNotice impact={impact}/>{r.data?.map(c=><Card key={c.id} title={c.name}><p>{c.enabled?'Đang nhận đặt':'Tạm ngừng'}</p><Button type="button" variant="outline" disabled={a.busy} onClick={()=>{if(!window.confirm(c.enabled?'Tạm ngừng nhận lượt đặt mới trên sân này?':'Kích hoạt lại sân này?'))return;setImpact(null);void a.run(()=>businessApi.courtStatus(c.id,!c.enabled).catch(error=>{setImpact(readImpact(error));throw error}),r.reload)}}>{c.enabled?'Tạm ngừng sân':'Kích hoạt sân'}</Button></Card>)}</section>}
