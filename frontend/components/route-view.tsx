'use client'
import {RequireAuth} from '@/features/auth/require-auth'
import {FavoritesPage} from '@/features/integration/favorites'
import {StaffInvitations} from '@/features/integration/staff'
import {KeptGroupPage} from '@/features/integration/group'
import {SupportPage,RefundReviews,SportsPage} from '@/features/integration/admin'
import {CourtTypes} from '@/features/integration/facility'
import {FacilityShell} from '@/features/facility/facility-context'
import {FacilityListPage} from '@/features/facility/facility-list'
import {OperationBookings} from '@/features/operations/bookings-page'
import {OperationalDetail} from '@/features/integration/operations'
import {ReportPage} from '@/features/admin/report-page'
import {useApi} from '@/lib/use-api'
import type {ApplicationDetail} from '@/features/owner-application/types'
import {ReconciliationReview} from '@/features/integration/synchronization'
import {StaffConfiguration} from '@/features/integration/staff-configuration'
export function RouteView({parts:p}:{parts:string[]}){if(p[0]==='customer'){if(p[1]==='favorites')return <FavoritesPage/>;if(p[1]==='staff-invitations')return <StaffInvitations/>;return <RequireAuth roles={['CUSTOMER']}><KeptGroupPage id={p[3]}/></RequireAuth>}
 if(p[0]==='admin'){const child=p[1]==='bookings'&&p[2]==='reconciliation'?<ReconciliationReview id={p[3]}/>:p[1]==='disputes'?<SupportPage id={p[2]}/>:p[1]==='refunds'?<RefundReviews id={p[2]}/>:p[1]==='sports'?<SportsPage/>:<ReportPage/>;return <RequireAuth roles={['ADMIN']}>{child}</RequireAuth>}
 if(p[0]==='owner'&&p[1]==='facilities')return <FacilityShell id={p[2]}><CourtTypes/></FacilityShell>
 if(p[0]==='owner'&&p[1]==='application')return <RequireAuth roles={['CUSTOMER','OWNER']}><ApplicationCourtTypes id={p[2]}/></RequireAuth>
 if(p[0]==='staff'&&p[1]==='facilities')return <StaffConfiguration id={p[2]} tool={p[4]==='preview'?'preview':p[3] as 'schedule'|'courts'|'maintenance'}/>
 if(p[1]==='bookings'&&p[2])return <RequireAuth roles={[p[0]==='owner'?'OWNER':'STAFF']}><OperationalDetail id={p[2]}/></RequireAuth>
 return p[0]==='owner'?<FacilityListPage/>:<OperationBookings/>}
function ApplicationCourtTypes({id}:{id:string}){const r=useApi<ApplicationDetail>(`/owner-applications/${id}`);if(!r.data)return <p className="p-8" role={r.error?'alert':'status'}>{r.error||'Đang tải hồ sơ…'}</p>;return <FacilityShell id={r.data.application.facilityId} applicationId={id}><CourtTypes/></FacilityShell>}
