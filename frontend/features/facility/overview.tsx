'use client'
import { FacilityGallery } from '@/features/facility/facility-gallery'
import { FacilityInformation } from '@/features/facility/facility-information'
import { FacilityStats } from '@/features/facility/facility-stats'
import { OverviewCourts } from '@/features/facility/overview-courts'
import { useFacilityOverview } from '@/features/facility/use-facility-overview'
import { Clock, PlusCircle, Tag, Wrench } from 'lucide-react'
import Link from 'next/link'
export function FacilityOverview() {
  const model = useFacilityOverview()
  const { basePath } = model
  return (
    <div className="space-y-8">
      {/* 1. TOP KPI CARDS */}
      <FacilityStats model={model} />

      {/* 2. QUICK ACTION BAR */}
      <div className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
        <h3 className="text-xs font-bold uppercase tracking-wider text-slate-400 mb-3">
          Thao tác nhanh
        </h3>
        <div className="flex flex-wrap gap-3">
          <Link
            href={`${basePath}/courts/new`}
            className="inline-flex items-center gap-2 rounded-xl bg-emerald-600 px-4 py-2.5 text-xs font-bold text-white shadow-sm hover:bg-emerald-500 transition"
          >
            <PlusCircle size={15} />
            <span>Thêm sân mới</span>
          </Link>
          <Link
            href={`${basePath}/pricing`}
            className="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-xs font-bold text-slate-700 shadow-sm hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200"
          >
            <Tag size={15} className="text-emerald-600" />
            <span>Cấu hình bảng giá</span>
          </Link>
          <Link
            href={`${basePath}/schedule`}
            className="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-xs font-bold text-slate-700 shadow-sm hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200"
          >
            <Clock size={15} className="text-blue-600" />
            <span>Lịch mở cửa</span>
          </Link>
          <Link
            href={`${basePath}/maintenance`}
            className="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-xs font-bold text-slate-700 shadow-sm hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200"
          >
            <Wrench size={15} className="text-amber-600" />
            <span>Khóa bảo trì sân</span>
          </Link>
        </div>
      </div>

      {/* 3. COURTS TABLE SUMMARY */}
      <OverviewCourts model={model} />

      {/* 4. FACILITY PROFILE DETAILS & AMENITIES */}
      <FacilityInformation model={model} />

      {/* 5. PHOTOS & MEDIA MANAGEMENT */}
      <FacilityGallery model={model} />
    </div>
  )
}
