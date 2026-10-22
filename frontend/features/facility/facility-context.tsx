'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { createContext, useContext, useState, useRef, useEffect } from 'react'
import { useApi } from '@/lib/use-api'
import { RequireAuth } from '@/features/auth/require-auth'
import type { Facility, FacilityProfile } from './types'
import {
  LayoutDashboard,
  Layers,
  Grid,
  Clock,
  Tag,
  Wrench,
  Users,
  FileText,
  Repeat,
  BarChart3,
  Settings,
  CheckCircle2,
  Building2,
  ChevronDown,
  ExternalLink,
  Menu,
  X,
  ChevronRight,
  PlusCircle,
  RefreshCw,
} from 'lucide-react'

const FacilityContext = createContext<{
  facility: FacilityProfile
  reload: () => void
  basePath: string
  editable: boolean
  applicationId?: string
} | null>(null)

export function FacilityWorkspace({
  facility,
  reload,
  basePath,
  children,
}: {
  facility: FacilityProfile
  reload: () => void
  basePath: string
  children: React.ReactNode
}) {
  return (
    <FacilityContext.Provider value={{ facility, reload, basePath, editable: true }}>
      {children}
    </FacilityContext.Provider>
  )
}

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
  const [switcherOpen, setSwitcherOpen] = useState(false)
  const switcherRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (switcherRef.current && !switcherRef.current.contains(event.target as Node)) {
        setSwitcherOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  if (loading) {
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
        <div role="alert" className="rounded-2xl border border-red-200 bg-red-50 p-6 text-sm text-red-700">
          <p className="font-bold">Không thể tải thông tin cơ sở</p>
          <p className="mt-1">{error || 'Cơ sở không tồn tại hoặc bạn không có quyền truy cập.'}</p>
          <div className="mt-4 flex gap-3">
            <button onClick={reload} className="rounded-lg bg-red-600 px-4 py-2 font-bold text-white hover:bg-red-500">
              Thử lại
            </button>
            <Link href="/owner/facilities" className="rounded-lg border border-red-300 bg-white px-4 py-2 font-bold text-red-700">
              Về danh sách cơ sở
            </Link>
          </div>
        </div>
      </div>
    )
  }

  const base = applicationId ? `/owner/application/${applicationId}/facility` : `/owner/facilities/${id}`
  const editable = !['PENDING_APPROVAL', 'REJECTED'].includes(data.status)

  // Navigation Items with Icons and Groups
  const navGroups = applicationId
    ? [
        {
          group: 'HỒ SƠ THẨM ĐỊNH',
          items: [
            { suffix: '', label: 'Thông tin và ảnh', icon: LayoutDashboard },
            { suffix: '/courts', label: 'Danh sách sân', icon: Layers },
            { suffix: '/court-types', label: 'Loại sân', icon: Grid },
            { suffix: '/schedule', label: 'Lịch mở cửa', icon: Clock },
            { suffix: '/pricing', label: 'Bảng giá', icon: Tag },
            { suffix: '/documents', label: 'Tài liệu pháp lý', icon: FileText },
            { suffix: '/edit', label: 'Chỉnh sửa hồ sơ', icon: Settings },
          ],
        },
      ]
    : [
        {
          group: 'TỔNG QUAN',
          items: [
            { suffix: '', label: 'Tổng quan cơ sở', icon: LayoutDashboard },
            { suffix: '/review', label: 'Kiểm duyệt & Trạng thái', icon: CheckCircle2 },
          ],
        },
        {
          group: 'SÂN & LỊCH',
          items: [
            { suffix: '/courts', label: 'Quản lý sân', icon: Layers },
            { suffix: '/court-types', label: 'Loại sân', icon: Grid },
            { suffix: '/schedule', label: 'Lịch hoạt động', icon: Clock },
            { suffix: '/pricing', label: 'Bảng giá sân', icon: Tag },
            { suffix: '/maintenance', label: 'Bảo trì định kỳ', icon: Wrench },
          ],
        },
        {
          group: 'VẬN HÀNH & ĐỘI NGŨ',
          items: [
            { suffix: '/staff', label: 'Nhân viên cơ sở', icon: Users },
            { suffix: '/documents', label: 'Tài liệu & Giấy phép', icon: FileText },
            { suffix: '/transfer-policy', label: 'Chính sách chuyển nhượng', icon: Repeat },
             { suffix: '/refund-policy', label: 'Chính sách hoàn tiền', icon: FileText },
            { suffix: '/reports', label: 'Báo cáo & Doanh thu', icon: BarChart3 },
          ],
        },
        {
          group: 'CÀI ĐẶT',
          items: [{ suffix: '/edit', label: 'Cập nhật thông tin', icon: Settings }],
        },
      ]

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
    <FacilityContext.Provider value={{ facility: data, reload, basePath: base, editable, applicationId }}>
      <div className="flex min-h-[calc(100vh-65px)] bg-slate-50 dark:bg-slate-950">
        {/* DESKTOP & MOBILE SIDEBAR */}
        <aside
          className={`fixed inset-y-0 left-0 z-30 flex w-72 flex-col border-r border-slate-200/80 bg-white transition-transform duration-200 lg:static lg:translate-x-0 dark:border-slate-800 dark:bg-slate-900 ${
            sidebarOpen ? 'translate-x-0' : '-translate-x-full'
          }`}
        >
          {/* Sidebar Top: Facility Switcher */}
          <div className="border-b border-slate-200/80 p-4 dark:border-slate-800">
            <div className="relative" ref={switcherRef}>
              <button
                type="button"
                onClick={() => setSwitcherOpen((prev) => !prev)}
                className="flex w-full items-center justify-between rounded-xl border border-slate-200 bg-slate-50 p-2.5 text-left transition hover:border-emerald-500 hover:bg-emerald-50/50 dark:border-slate-700 dark:bg-slate-800"
              >
                <div className="flex items-center gap-2.5 min-w-0">
                  <div className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-emerald-600 text-white font-black shadow-sm">
                    <Building2 size={18} />
                  </div>
                  <div className="min-w-0">
                    <p className="truncate text-xs font-bold text-slate-900 dark:text-white">
                      {data.name}
                    </p>
                    <p className="text-[11px] text-slate-500 truncate">{data.province || 'Cơ sở thể thao'}</p>
                  </div>
                </div>
                <ChevronDown size={16} className={`text-slate-400 shrink-0 transition-transform ${switcherOpen ? 'rotate-180' : ''}`} />
              </button>

              {/* Switcher Dropdown */}
              {switcherOpen && (
                <div className="absolute top-full left-0 mt-2 w-full rounded-2xl border border-slate-200 bg-white p-2 shadow-xl z-50 dark:border-slate-800 dark:bg-slate-900 animate-in fade-in zoom-in-95 duration-150">
                  <p className="px-3 py-1.5 text-[11px] font-bold uppercase tracking-wider text-slate-400">
                    Cơ sở của bạn
                  </p>
                  <div className="max-h-52 overflow-y-auto space-y-1">
                    {ownerFacilities.data?.map((f) => (
                      <Link
                        key={f.id}
                        href={`/owner/facilities/${f.id}`}
                        onClick={() => setSwitcherOpen(false)}
                        className={`flex items-center justify-between rounded-lg px-3 py-2 text-xs font-semibold transition ${
                          f.id === data.id
                            ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-400'
                            : 'hover:bg-slate-100 text-slate-700 dark:hover:bg-slate-800 dark:text-slate-300'
                        }`}
                      >
                        <span className="truncate">{f.name}</span>
                        <span className="text-[10px] uppercase font-bold text-slate-400 ml-1">{f.status}</span>
                      </Link>
                    ))}
                  </div>
                  <div className="mt-2 border-t border-slate-100 pt-2 dark:border-slate-800">
                    <Link
                      href="/owner/facilities/new"
                      onClick={() => setSwitcherOpen(false)}
                      className="flex items-center gap-2 rounded-lg px-3 py-2 text-xs font-bold text-emerald-600 hover:bg-emerald-50 dark:text-emerald-400 dark:hover:bg-emerald-950"
                    >
                      <PlusCircle size={14} />
                      <span>Thêm cơ sở mới</span>
                    </Link>
                    <Link
                      href="/owner/facilities"
                      onClick={() => setSwitcherOpen(false)}
                      className="flex items-center gap-2 rounded-lg px-3 py-1.5 text-xs font-medium text-slate-500 hover:bg-slate-100 dark:hover:bg-slate-800"
                    >
                      <span>Tất cả cơ sở</span>
                    </Link>
                  </div>
                </div>
              )}
            </div>
          </div>

          {/* Sidebar Nav Links */}
          <nav className="flex-1 overflow-y-auto p-3 space-y-6">
            {navGroups.map((group) => (
              <div key={group.group}>
                <p className="px-3 text-[10px] font-bold uppercase tracking-widest text-slate-400 dark:text-slate-500 mb-1.5">
                  {group.group}
                </p>
                <div className="space-y-1">
                  {group.items.map((item) => {
                    const itemHref = base + item.suffix
                    const active = path === itemHref
                    const Icon = item.icon
                    return (
                      <Link
                        key={item.suffix}
                        href={itemHref}
                        onClick={() => setSidebarOpen(false)}
                        className={`flex items-center gap-3 rounded-xl px-3 py-2 text-xs font-bold transition ${
                          active
                            ? 'bg-emerald-600 text-white shadow-sm shadow-emerald-600/30'
                            : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900 dark:text-slate-300 dark:hover:bg-slate-800 dark:hover:text-white'
                        }`}
                      >
                        <Icon size={16} className={active ? 'text-white' : 'text-slate-400'} />
                        <span>{item.label}</span>
                      </Link>
                    )
                  })}
                </div>
              </div>
            ))}
          </nav>

          {/* Sidebar Footer Link to Public View */}
          <div className="border-t border-slate-200/80 p-3 dark:border-slate-800">
            <Link
              href={`/facilities/${data.id}`}
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center justify-between rounded-xl bg-slate-100 px-3 py-2.5 text-xs font-bold text-slate-700 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-200"
            >
              <div className="flex items-center gap-2">
                <ExternalLink size={14} className="text-emerald-600" />
                <span>Xem trang công khai</span>
              </div>
              <ChevronRight size={14} className="text-slate-400" />
            </Link>
          </div>
        </aside>

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
          <header className="sticky top-0 z-10 flex h-16 items-center justify-between gap-4 border-b border-slate-200/80 bg-white/95 px-4 backdrop-blur-md sm:px-6 dark:border-slate-800 dark:bg-slate-900/95">
            <div className="flex items-center gap-3">
              <button
                type="button"
                onClick={() => setSidebarOpen((prev) => !prev)}
                className="flex size-9 items-center justify-center rounded-lg border border-slate-200 text-slate-600 hover:bg-slate-100 lg:hidden dark:border-slate-700 dark:text-slate-300"
                aria-label="Toggle sidebar"
              >
                {sidebarOpen ? <X size={18} /> : <Menu size={18} />}
              </button>

              {/* Breadcrumb Navigation */}
              <div className="flex items-center gap-2 text-xs font-semibold text-slate-500 dark:text-slate-400">
                <Link href={applicationId ? `/owner/application/${applicationId}` : '/owner/facilities'} className="hover:text-emerald-600">
                  {applicationId ? 'Hồ sơ Owner' : 'Cơ sở của tôi'}
                </Link>
                <ChevronRight size={12} className="text-slate-400" />
                <span className="font-bold text-slate-900 dark:text-white truncate max-w-[150px] sm:max-w-xs">{data.name}</span>
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
                <span className={`size-2 rounded-full ${data.status === 'ACTIVE' ? 'bg-emerald-500 animate-pulse' : 'bg-amber-500'}`} />
                <span>{data.status}</span>
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

          {/* Notice for Readonly Mode when Pending Approval */}
          {applicationId && !editable && (
            <div className="border-b border-amber-200 bg-amber-50 px-6 py-3 text-xs font-medium text-amber-900 dark:border-amber-900/50 dark:bg-amber-950/40 dark:text-amber-300">
              Hồ sơ đã gửi thẩm định được giữ nguyên để Admin kiểm tra. Bạn sẽ nhận được thông báo khi được duyệt hoặc cần bổ sung.
            </div>
          )}

          {/* Children View Container */}
          <main className="flex-1 p-4 sm:p-6 lg:p-8">
            {applicationId ? <fieldset disabled={!editable} className="min-w-0">{children}</fieldset> : children}
          </main>
        </div>
      </div>
    </FacilityContext.Provider>
  )
}

export function useFacility() {
  const value = useContext(FacilityContext)
  if (!value) throw new Error('Facility context is required')
  return value
}
