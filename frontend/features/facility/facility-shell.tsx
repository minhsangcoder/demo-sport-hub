'use client'
import { RequireAuth } from '@/features/auth/require-auth'
import { useApi } from '@/lib/use-api'
import { RefreshCw } from 'lucide-react'
import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { useState } from 'react'
import { FacilityBreadcrumb } from './facility-breadcrumb'
import { facilityNavigation } from './facility-navigation'
import { FacilitySidebar } from './facility-sidebar'
import { FacilityContext } from './facility-store'
import type { Facility, FacilityProfile } from './types'
export function FacilityShell({
  id,
  children,
  applicationId,
}: {
  id: string
  children: React.ReactNode
  applicationId?: string
}) {
  return (
    <RequireAuth roles={applicationId ? ['CUSTOMER', 'OWNER'] : ['OWNER']}>
      <LoadedShell id={id} applicationId={applicationId}>
        {children}
      </LoadedShell>
    </RequireAuth>
  )
}
function LoadedShell({
  id,
  children,
  applicationId,
}: {
  id: string
  children: React.ReactNode
  applicationId?: string
}) {
  const { data, error, loading, reload } = useApi<FacilityProfile>(`/owner/facilities/${id}`)
  const ownerFacilities = useApi<Facility[]>('/owner/facilities')
  const path = usePathname()

  const [sidebarOpen, setSidebarOpen] = useState(false)
  if (loading && !data) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <div className="flex flex-col items-center gap-3 text-slate-500">
          <RefreshCw className="size-8 animate-spin text-emerald-600" />
          <p className="text-sm font-semibold">Đang tải không gian cơ sở…</p>
        </div>
      </div>
    )
  }

  if (error || !data) {
    return (
      <div className="mx-auto max-w-xl p-8">
        <div
          role="alert"
          className="rounded-2xl border border-red-200 bg-red-50 p-6 text-sm text-red-700"
        >
          <p className="font-bold">Không thể tải thông tin cơ sở</p>
          <p className="mt-1">{error || 'Cơ sở không tồn tại hoặc bạn không có quyền truy cập.'}</p>
          <div className="mt-4 flex gap-3">
            <button
              onClick={reload}
              className="rounded-lg bg-red-600 px-4 py-2 font-bold text-white hover:bg-red-500"
            >
              Thử lại
            </button>
            <Link
              href="/owner/facilities"
              className="rounded-lg border border-red-300 bg-white px-4 py-2 font-bold text-red-700"
            >
              Về danh sách cơ sở
            </Link>
          </div>
        </div>
      </div>
    )
  }

  const base = applicationId
    ? `/owner/application/${applicationId}/facility`
    : `/owner/facilities/${id}`
  const editable = !['PENDING_APPROVAL', 'REJECTED'].includes(data.status)

  const navGroups = facilityNavigation(applicationId)
  // Find active label for breadcrumb
  let activeLabel = 'Tổng quan cơ sở'
  for (const g of navGroups) {
    for (const item of g.items) {
      if (path === base + item.suffix) {
        activeLabel = item.label
        break
      }
    }
  }
  if (path.endsWith('/maintenance')) activeLabel = 'Bảo trì sân'

  return (
    <FacilityContext.Provider
      value={{ facility: data, reload, basePath: base, editable, applicationId }}
    >
      <div className="flex min-h-[calc(100vh-65px)] bg-slate-50 dark:bg-slate-950">
        {/* DESKTOP & MOBILE SIDEBAR */}
        <FacilitySidebar
          sidebarOpen={sidebarOpen}
          setSidebarOpen={setSidebarOpen}
          facilities={ownerFacilities.data}
        />

        {/* Backdrop for Mobile Sidebar */}
        {sidebarOpen && (
          <div
            onClick={() => setSidebarOpen(false)}
            className="fixed inset-0 z-20 bg-slate-950/50 backdrop-blur-sm lg:hidden"
          />
        )}

        {/* MAIN WORKSPACE CONTENT */}
        <div className="flex flex-1 flex-col min-w-0">
          {/* Header Bar with Breadcrumbs & Status */}
          <FacilityBreadcrumb
            sidebarOpen={sidebarOpen}
            setSidebarOpen={setSidebarOpen}
            activeLabel={activeLabel}
          />

          {/* Notice for Readonly Mode when Pending Approval */}
          {applicationId && !editable && (
            <div className="border-b border-amber-200 bg-amber-50 px-6 py-3 text-xs font-medium text-amber-900 dark:border-amber-900/50 dark:bg-amber-950/40 dark:text-amber-300">
              Hồ sơ đã gửi thẩm định được giữ nguyên để quản trị viên kiểm tra. Bạn sẽ nhận được
              thông báo khi được duyệt hoặc cần bổ sung.
            </div>
          )}

          {/* Children View Container */}
          <main className="flex-1 p-4 sm:p-6 lg:p-8">
            {applicationId ? (
              <fieldset disabled={!editable} className="min-w-0">
                {children}
              </fieldset>
            ) : (
              children
            )}
          </main>
        </div>
      </div>
    </FacilityContext.Provider>
  )
}
