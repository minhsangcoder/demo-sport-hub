'use client'
import { displayLabel } from '@/lib/display-labels'
import { ChevronRight, Menu, RefreshCw, X } from 'lucide-react'
import Link from 'next/link'
import { useFacility } from './facility-store'
export function FacilityBreadcrumb({
  sidebarOpen,
  setSidebarOpen,
  activeLabel,
}: {
  sidebarOpen: boolean
  setSidebarOpen: React.Dispatch<React.SetStateAction<boolean>>
  activeLabel: string
}) {
  const { facility: data, reload, applicationId } = useFacility()
  return (
    <header className="sticky top-0 z-10 flex h-16 items-center justify-between gap-4 border-b border-slate-200/80 bg-white/95 px-4 backdrop-blur-md sm:px-6 dark:border-slate-800 dark:bg-slate-900/95">
      <div className="flex items-center gap-3">
        <button
          type="button"
          onClick={() => setSidebarOpen((prev) => !prev)}
          className="flex size-9 items-center justify-center rounded-lg border border-slate-200 text-slate-600 hover:bg-slate-100 lg:hidden dark:border-slate-700 dark:text-slate-300"
          aria-label="Mở hoặc thu gọn thanh bên"
        >
          {sidebarOpen ? <X size={18} /> : <Menu size={18} />}
        </button>

        {/* Breadcrumb Navigation */}
        <div className="flex items-center gap-2 text-xs font-semibold text-slate-500 dark:text-slate-400">
          <Link
            href={applicationId ? `/owner/application/${applicationId}` : '/owner/facilities'}
            className="hover:text-emerald-600"
          >
            {applicationId ? 'Hồ sơ chủ cơ sở' : 'Cơ sở của tôi'}
          </Link>
          <ChevronRight size={12} className="text-slate-400" />
          <span className="font-bold text-slate-900 dark:text-white truncate max-w-[150px] sm:max-w-xs">
            {data.name}
          </span>
          <ChevronRight size={12} className="text-slate-400" />
          <span className="text-emerald-600 font-bold dark:text-emerald-400">{activeLabel}</span>
        </div>
      </div>

      {/* Right Status Badge & Actions */}
      <div className="flex items-center gap-3">
        <span
          className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-bold uppercase tracking-wider ${
            data.status === 'ACTIVE'
              ? 'bg-emerald-50 text-emerald-700 border border-emerald-200 dark:bg-emerald-950 dark:text-emerald-400 dark:border-emerald-800'
              : data.status === 'PENDING_APPROVAL'
                ? 'bg-amber-50 text-amber-800 border border-amber-200 dark:bg-amber-950 dark:text-amber-400 dark:border-amber-800'
                : 'bg-slate-100 text-slate-700 border border-slate-200 dark:bg-slate-800 dark:text-slate-300'
          }`}
        >
          <span
            className={`size-2 rounded-full ${data.status === 'ACTIVE' ? 'bg-emerald-500 animate-pulse' : 'bg-amber-500'}`}
          />
          <span>{displayLabel(data.status)}</span>
        </span>

        <button
          type="button"
          onClick={reload}
          title="Tải lại dữ liệu cơ sở"
          className="flex size-8 items-center justify-center rounded-lg border border-slate-200 text-slate-600 hover:bg-slate-100 dark:border-slate-700 dark:text-slate-300"
        >
          <RefreshCw size={14} />
        </button>
      </div>
    </header>
  )
}
