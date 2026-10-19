'use client'

import Link from 'next/link'
import { useState } from 'react'
import { usePathname } from 'next/navigation'
import { useApi } from '@/lib/use-api'
import { RequireAuth } from '@/features/auth/require-auth'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import {
  Users,
  Building2,
  FileCheck,
  CalendarCheck,
  CreditCard,
  RefreshCw,
  AlertTriangle,
  RotateCcw,
  BarChart3,
  Trophy,
  Search,
  Filter,
  Shield,
  ShieldAlert,
  ShieldCheck,
  UserCheck,
  UserX,
  Clock,
  ArrowRight,
  ExternalLink,
  ChevronRight,
  CheckCircle,
  XCircle,
} from 'lucide-react'

export type AdminAccount = {
  id: string
  fullName: string
  email: string | null
  phone: string | null
  emailVerified: boolean
  phoneVerified: boolean
  roles: string[]
  status: string
  createdAt: string
  lastLoginAt: string | null
  lockedAt: string | null
  lockedUntil: string | null
  lockReason: string | null
}

export function AdminAccounts() {
  return (
    <RequireAuth roles={['ADMIN']}>
      <AdminShell active="accounts">
        <AccountsContent />
      </AdminShell>
    </RequireAuth>
  )
}

function AccountsContent() {
  const [path, setPath] = useState('/admin/accounts')
  const r = useApi<AdminAccount[]>(path)

  function search(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault()
    const f = new FormData(e.currentTarget)
    const p = new URLSearchParams()
    for (const key of ['q', 'status', 'role']) {
      const v = String(f.get(key) || '').trim()
      if (v) p.set(key, v)
    }
    setPath(`/admin/accounts?${p.toString()}`)
  }

  // Calculate quick stats from loaded results
  const totalUsers = r.data?.length ?? 0
  const activeUsers = r.data?.filter((u) => u.status === 'ACTIVE').length ?? 0
  const lockedUsers = r.data?.filter((u) => u.status === 'LOCKED' || u.status === 'SUSPENDED').length ?? 0
  const privilegedUsers = r.data?.filter((u) => u.roles.includes('OWNER') || u.roles.includes('ADMIN')).length ?? 0

  return (
    <div className="space-y-6">
      {/* 1. TOP STATS / KPI CARDS */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <div className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-500">Tài khoản kết quả</span>
            <div className="flex size-9 items-center justify-center rounded-xl bg-blue-50 text-blue-600 dark:bg-blue-950 dark:text-blue-400">
              <Users size={18} />
            </div>
          </div>
          <div className="mt-3 flex items-baseline gap-2">
            <span className="text-3xl font-black text-slate-900 dark:text-white">
              {r.loading ? '…' : totalUsers}
            </span>
            <span className="text-xs text-slate-400">tài khoản</span>
          </div>
          <p className="mt-2 text-xs text-slate-500">Hiển thị trong phiên tra cứu hiện tại</p>
        </div>

        <div className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-500">Đang hoạt động</span>
            <div className="flex size-9 items-center justify-center rounded-xl bg-emerald-50 text-emerald-600 dark:bg-emerald-950 dark:text-emerald-400">
              <UserCheck size={18} />
            </div>
          </div>
          <div className="mt-3 flex items-baseline gap-2">
            <span className="text-3xl font-black text-emerald-600 dark:text-emerald-400">
              {r.loading ? '…' : activeUsers}
            </span>
            <span className="text-xs font-semibold text-emerald-600">bình thường</span>
          </div>
          <p className="mt-2 text-xs text-slate-500">Có quyền đặt sân và thao tác dịch vụ</p>
        </div>

        <div className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-500">Bị khóa / Tạm dừng</span>
            <div className="flex size-9 items-center justify-center rounded-xl bg-red-50 text-red-600 dark:bg-red-950 dark:text-red-400">
              <UserX size={18} />
            </div>
          </div>
          <div className="mt-3 flex items-baseline gap-2">
            <span className="text-3xl font-black text-red-600 dark:text-red-400">
              {r.loading ? '…' : lockedUsers}
            </span>
            <span className="text-xs text-red-500">hạn chế</span>
          </div>
          <p className="mt-2 text-xs text-slate-500">Tài khoản vi phạm hoặc chờ xác minh</p>
        </div>

        <div className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-500">Chủ sân & Quản trị</span>
            <div className="flex size-9 items-center justify-center rounded-xl bg-purple-50 text-purple-600 dark:bg-purple-950 dark:text-purple-400">
              <Shield size={18} />
            </div>
          </div>
          <div className="mt-3 flex items-baseline gap-2">
            <span className="text-3xl font-black text-purple-600 dark:text-purple-400">
              {r.loading ? '…' : privilegedUsers}
            </span>
            <span className="text-xs text-slate-400">nhân sự</span>
          </div>
          <p className="mt-2 text-xs text-slate-500">Tài khoản có quyền Owner hoặc Admin</p>
        </div>
      </div>

      {/* 2. SEARCH & FILTER BAR */}
      <div className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
        <form onSubmit={search} className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4 lg:items-end">
          <div className="space-y-1.5">
            <label className="text-xs font-bold uppercase tracking-wider text-slate-500">
              Từ khóa tìm kiếm
            </label>
            <div className="relative">
              <Search size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" />
              <Input
                name="q"
                placeholder="Họ tên, email, SĐT..."
                className="pl-9 h-11 text-xs rounded-xl"
              />
            </div>
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-bold uppercase tracking-wider text-slate-500">
              Trạng thái tài khoản
            </label>
            <select
              name="status"
              className="h-11 w-full rounded-xl border border-slate-200 bg-white px-3 text-xs font-medium text-slate-800 shadow-sm focus:border-emerald-500 focus:outline-none dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200"
            >
              <option value="">Tất cả trạng thái</option>
              {['ACTIVE', 'LOCKED', 'PENDING_VERIFICATION', 'SUSPENDED', 'DEACTIVATED'].map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-bold uppercase tracking-wider text-slate-500">
              Vai trò người dùng
            </label>
            <select
              name="role"
              className="h-11 w-full rounded-xl border border-slate-200 bg-white px-3 text-xs font-medium text-slate-800 shadow-sm focus:border-emerald-500 focus:outline-none dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200"
            >
              <option value="">Tất cả vai trò</option>
              {['CUSTOMER', 'OWNER', 'STAFF', 'ADMIN'].map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
          </div>

          <div className="flex gap-2">
            <Button type="submit" className="h-11 flex-1 bg-emerald-600 hover:bg-emerald-500 text-xs font-bold rounded-xl">
              <Filter size={14} className="mr-1.5" />
              <span>Tra cứu ngay</span>
            </Button>
            <Button
              type="button"
              variant="outline"
              onClick={() => {
                setPath('/admin/accounts')
              }}
              className="h-11 text-xs rounded-xl"
            >
              Đặt lại
            </Button>
          </div>
        </form>
      </div>

      {/* 3. PROFESSIONAL DATA TABLE */}
      <div className="overflow-hidden rounded-2xl border border-slate-200/80 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-900">
        <div className="flex items-center justify-between border-b border-slate-100 p-4 dark:border-slate-800">
          <div className="flex items-center gap-2">
            <h3 className="text-sm font-bold text-slate-900 dark:text-white">Danh sách tài khoản</h3>
            {r.data && (
              <span className="rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-bold text-slate-600 dark:bg-slate-800 dark:text-slate-300">
                {r.data.length}
              </span>
            )}
          </div>
          <p className="text-xs text-slate-400">Tối đa 100 kết quả gần nhất</p>
        </div>

        {/* Loading State */}
        {r.loading && (
          <div className="p-12 text-center text-xs text-slate-400">
            <RefreshCw className="mx-auto size-6 animate-spin text-emerald-600" />
            <p className="mt-2 font-semibold">Đang truy vấn dữ liệu tài khoản…</p>
          </div>
        )}

        {/* Error State */}
        {r.error && (
          <div role="alert" className="p-6 text-center text-xs text-red-600">
            <p className="font-bold">Không thể tải danh sách tài khoản.</p>
            <p className="mt-1">{r.error}</p>
            <button onClick={r.reload} className="mt-3 font-bold underline">
              Thử tải lại
            </button>
          </div>
        )}

        {/* Empty State */}
        {!r.loading && r.data?.length === 0 && (
          <div className="p-12 text-center">
            <Users className="mx-auto size-10 text-slate-300" />
            <h4 className="mt-3 text-sm font-bold text-slate-800 dark:text-slate-200">Không tìm thấy tài khoản phù hợp</h4>
            <p className="mt-1 text-xs text-slate-400">Hãy thử nới lỏng từ khóa hoặc bộ lọc trạng thái.</p>
          </div>
        )}

        {/* Data Table */}
        {!r.loading && r.data && r.data.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead>
                <tr className="border-b border-slate-100 bg-slate-50/70 text-slate-400 dark:border-slate-800 dark:bg-slate-800/40">
                  <th className="py-3 px-4 font-bold uppercase tracking-wider">Người dùng</th>
                  <th className="py-3 px-4 font-bold uppercase tracking-wider">Liên hệ & Xác minh</th>
                  <th className="py-3 px-4 font-bold uppercase tracking-wider">Vai trò</th>
                  <th className="py-3 px-4 font-bold uppercase tracking-wider">Trạng thái</th>
                  <th className="py-3 px-4 font-bold uppercase tracking-wider">Ngày đăng ký</th>
                  <th className="py-3 px-4 font-bold uppercase tracking-wider text-right">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                {r.data.map((u) => {
                  const initial = u.fullName ? u.fullName.charAt(0).toUpperCase() : 'U'
                  return (
                    <tr key={u.id} className="hover:bg-slate-50/80 transition dark:hover:bg-slate-800/50">
                      {/* Name & Avatar */}
                      <td className="py-3.5 px-4">
                        <div className="flex items-center gap-3">
                          <div className="flex size-8 shrink-0 items-center justify-center rounded-full bg-slate-100 text-xs font-bold text-slate-700 dark:bg-slate-800 dark:text-slate-300">
                            {initial}
                          </div>
                          <div>
                            <p className="font-bold text-slate-900 dark:text-white">{u.fullName}</p>
                            <p className="text-[10px] font-mono text-slate-400 truncate max-w-[120px]">{u.id}</p>
                          </div>
                        </div>
                      </td>

                      {/* Contact & Verified badges */}
                      <td className="py-3.5 px-4">
                        <p className="font-medium text-slate-700 dark:text-slate-300">{u.email || u.phone || 'Chưa cập nhật'}</p>
                        <div className="mt-1 flex items-center gap-2 text-[10px]">
                          {u.emailVerified && (
                            <span className="flex items-center gap-1 text-emerald-600 dark:text-emerald-400 font-semibold">
                              <CheckCircle size={10} />
                              <span>Email verified</span>
                            </span>
                          )}
                          {u.phoneVerified && (
                            <span className="flex items-center gap-1 text-emerald-600 dark:text-emerald-400 font-semibold">
                              <CheckCircle size={10} />
                              <span>SĐT verified</span>
                            </span>
                          )}
                        </div>
                      </td>

                      {/* Roles */}
                      <td className="py-3.5 px-4">
                        <div className="flex flex-wrap gap-1">
                          {u.roles.map((role) => (
                            <span
                              key={role}
                              className={`rounded-md px-2 py-0.5 text-[10px] font-bold ${
                                role === 'ADMIN'
                                  ? 'bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300'
                                  : role === 'OWNER'
                                  ? 'bg-purple-100 text-purple-800 dark:bg-purple-950 dark:text-purple-300'
                                  : role === 'STAFF'
                                  ? 'bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-300'
                                  : 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300'
                              }`}
                            >
                              {role}
                            </span>
                          ))}
                        </div>
                      </td>

                      {/* Status */}
                      <td className="py-3.5 px-4">
                        <span
                          className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-[10px] font-bold ${
                            u.status === 'ACTIVE'
                              ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-400'
                              : u.status === 'LOCKED'
                              ? 'bg-red-50 text-red-700 dark:bg-red-950 dark:text-red-400'
                              : 'bg-amber-50 text-amber-700 dark:bg-amber-950 dark:text-amber-400'
                          }`}
                        >
                          <span
                            className={`size-1.5 rounded-full ${
                              u.status === 'ACTIVE'
                                ? 'bg-emerald-500'
                                : u.status === 'LOCKED'
                                ? 'bg-red-500'
                                : 'bg-amber-500'
                            }`}
                          />
                          <span>{u.status}</span>
                        </span>
                      </td>

                      {/* Created At */}
                      <td className="py-3.5 px-4 text-slate-500">
                        {new Date(u.createdAt).toLocaleDateString('vi-VN')}
                      </td>

                      {/* Actions */}
                      <td className="py-3.5 px-4 text-right">
                        <Link
                          href={`/admin/accounts/${u.id}`}
                          className="inline-flex items-center gap-1 rounded-lg border border-slate-200 bg-white px-2.5 py-1 text-[11px] font-bold text-slate-700 shadow-sm hover:border-emerald-500 hover:text-emerald-600 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200"
                        >
                          <span>Quản trị</span>
                          <ArrowRight size={12} />
                        </Link>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}

/* ADMIN SHELL: Enterprise Layout with Grouped Sidebar & Top Header */
export function AdminShell({
  active,
  children,
}: {
  active: string
  children: React.ReactNode
}) {
  const pathname = usePathname()

  const navSections = [
    {
      title: 'TỔNG QUAN',
      items: [
        { key: 'reports', label: 'Báo cáo & Thống kê', href: '/admin/reports', icon: BarChart3 },
      ],
    },
    {
      title: 'THẨM ĐỊNH & KIỂM DUYỆT',
      items: [
        { key: 'owner-applications', label: 'Đăng ký Chủ sân (Owner)', href: '/admin/owner-applications', icon: FileCheck },
        { key: 'facilities', label: 'Kiểm duyệt Cơ sở', href: '/admin/facilities', icon: Building2 },
      ],
    },
    {
      title: 'GIÁM SÁT DÒNG TIỀN & VẬN HÀNH',
      items: [
        { key: 'bookings', label: 'Giám sát Booking', href: '/admin/bookings', icon: CalendarCheck },
        { key: 'payments', label: 'Giao dịch thanh toán', href: '/admin/payments', icon: CreditCard },
        { key: 'reconciliation', label: 'Đối soát thất lạc', href: '/admin/bookings/reconciliation', icon: RefreshCw },
        { key: 'disputes', label: 'Khiếu nại & Tranh chấp', href: '/admin/disputes', icon: AlertTriangle },
        { key: 'refunds', label: 'Rà soát hoàn tiền', href: '/admin/refunds', icon: RotateCcw },
      ],
    },
    {
      title: 'HỆ THỐNG & TÀI KHOẢN',
      items: [
        { key: 'accounts', label: 'Tài khoản người dùng', href: '/admin/accounts', icon: Users },
        { key: 'sports', label: 'Danh mục môn thể thao', href: '/admin/sports', icon: Trophy },
      ],
    },
  ]

  return (
    <div className="flex min-h-[calc(100vh-65px)] bg-slate-900 text-slate-100">
      {/* ADMIN ENTERPRISE SIDEBAR */}
      <aside className="hidden w-64 flex-col border-r border-slate-800 bg-slate-950 p-4 lg:flex">
        {/* Admin Brand */}
        <div className="flex items-center gap-2.5 px-2 py-3 border-b border-slate-800/80">
          <div className="flex size-8 items-center justify-center rounded-lg bg-amber-500 text-slate-950 font-black shadow-md shadow-amber-500/20">
            <Shield size={16} />
          </div>
          <div>
            <span className="font-extrabold text-sm text-white tracking-tight">SportHub Admin</span>
            <span className="block text-[10px] font-bold uppercase tracking-wider text-amber-400">
              Control Panel
            </span>
          </div>
        </div>

        {/* Sidebar Nav Sections */}
        <nav className="flex-1 overflow-y-auto py-4 space-y-5">
          {navSections.map((sec) => (
            <div key={sec.title}>
              <p className="px-2 text-[10px] font-bold uppercase tracking-wider text-slate-500 mb-1.5">
                {sec.title}
              </p>
              <div className="space-y-1">
                {sec.items.map((item) => {
                  const isCurrent = active === item.key || pathname.startsWith(item.href)
                  const Icon = item.icon
                  return (
                    <Link
                      key={item.key}
                      href={item.href}
                      className={`flex items-center gap-2.5 rounded-xl px-2.5 py-2 text-xs font-bold transition ${
                        isCurrent
                          ? 'bg-amber-500 text-slate-950 shadow-sm'
                          : 'text-slate-400 hover:bg-slate-800 hover:text-white'
                      }`}
                    >
                      <Icon size={15} />
                      <span>{item.label}</span>
                    </Link>
                  )
                })}
              </div>
            </div>
          ))}
        </nav>

        {/* Exit to Customer Portal */}
        <div className="border-t border-slate-800 pt-3">
          <Link
            href="/"
            className="flex items-center justify-between rounded-xl bg-slate-900 px-3 py-2 text-xs font-semibold text-slate-300 hover:bg-slate-800 transition"
          >
            <span>Về cổng khách hàng</span>
            <ExternalLink size={13} className="text-slate-400" />
          </Link>
        </div>
      </aside>

      {/* ADMIN MAIN CONTENT */}
      <div className="flex flex-1 flex-col min-w-0 bg-slate-50 text-slate-900 dark:bg-slate-950 dark:text-slate-100">
        {/* Admin Topbar */}
        <header className="sticky top-0 z-10 flex h-16 items-center justify-between border-b border-slate-200/80 bg-white/95 px-6 backdrop-blur-md dark:border-slate-800 dark:bg-slate-900/95">
          <div className="flex items-center gap-2 text-xs font-semibold text-slate-500">
            <span>Bảng quản trị</span>
            <ChevronRight size={12} className="text-slate-400" />
            <span className="font-bold text-slate-900 dark:text-white uppercase tracking-wider">
              {active}
            </span>
          </div>

          <div className="flex items-center gap-3">
            <span className="rounded-full bg-amber-50 border border-amber-200 px-3 py-1 text-[11px] font-bold text-amber-800 dark:bg-amber-950 dark:border-amber-900 dark:text-amber-400">
              Quyền Quản trị viên
            </span>
          </div>
        </header>

        {/* Mobile Horizontal Nav Fallback */}
        <div className="border-b border-slate-200 bg-white p-2 lg:hidden dark:border-slate-800 dark:bg-slate-900 overflow-x-auto flex gap-1">
          {navSections.flatMap((s) => s.items).map((item) => (
            <Link
              key={item.key}
              href={item.href}
              className={`whitespace-nowrap rounded-lg px-3 py-1.5 text-xs font-bold ${
                active === item.key ? 'bg-amber-500 text-slate-950' : 'text-slate-600 hover:bg-slate-100'
              }`}
            >
              {item.label}
            </Link>
          ))}
        </div>

        <main className="flex-1 p-4 sm:p-6 lg:p-8">{children}</main>
      </div>
    </div>
  )
}

/* BACKWARD COMPATIBLE EXPORT: AdminNav for other admin pages */
export function AdminNav({ active }: { active: string }) {
  return (
    <nav
      aria-label="Quản trị nền tảng"
      className="my-6 flex gap-2 overflow-x-auto border-b border-slate-200 pb-4 dark:border-slate-800"
    >
      {[
        ['accounts', 'Tài khoản'],
        ['owner-applications', 'Đăng ký Owner'],
        ['facilities', 'Cơ sở'],
        ['bookings', 'Booking'],
        ['payments', 'Giao dịch'],
        ['sports', 'Môn thể thao'],
        ['disputes', 'Hỗ trợ'],
        ['refunds', 'Rà soát hoàn tiền'],
        ['reports', 'Báo cáo'],
      ].map(([key, label]) => (
        <Link
          key={key}
          href={`/admin/${key}`}
          className={`whitespace-nowrap rounded-full px-4 py-2 text-xs font-bold transition ${
            active === key
              ? 'bg-amber-500 text-slate-950 shadow-sm'
              : 'bg-slate-100 text-slate-600 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-300'
          }`}
        >
          {label}
        </Link>
      ))}
    </nav>
  )
}
