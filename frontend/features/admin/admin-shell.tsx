'use client'
import {
  AlertTriangle,
  BarChart3,
  Building2,
  CalendarCheck,
  ChevronRight,
  CreditCard,
  ExternalLink,
  FileCheck,
  RefreshCw,
  RotateCcw,
  Shield,
  Trophy,
  Users,
} from 'lucide-react'
import Link from 'next/link'
import { usePathname } from 'next/navigation'

export function AdminShell({ active, children }: { active: string; children: React.ReactNode }) {
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
        {
          key: 'owner-applications',
          label: 'Đăng ký Chủ sân',
          href: '/admin/owner-applications',
          icon: FileCheck,
        },
        {
          key: 'facilities',
          label: 'Kiểm duyệt Cơ sở',
          href: '/admin/facilities',
          icon: Building2,
        },
      ],
    },
    {
      title: 'GIÁM SÁT DÒNG TIỀN & VẬN HÀNH',
      items: [
        {
          key: 'bookings',
          label: 'Giám sát lượt đặt',
          href: '/admin/bookings',
          icon: CalendarCheck,
        },
        {
          key: 'payments',
          label: 'Giao dịch thanh toán',
          href: '/admin/payments',
          icon: CreditCard,
        },
        {
          key: 'reconciliation',
          label: 'Đối soát thất lạc',
          href: '/admin/bookings/reconciliation',
          icon: RefreshCw,
        },
        {
          key: 'disputes',
          label: 'Khiếu nại & Tranh chấp',
          href: '/admin/disputes',
          icon: AlertTriangle,
        },
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
            <span className="font-extrabold text-sm text-white tracking-tight">
              Quản trị SportHub
            </span>
            <span className="block text-[10px] font-bold uppercase tracking-wider text-amber-400">
              Bảng điều khiển
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
          {navSections
            .flatMap((s) => s.items)
            .map((item) => (
              <Link
                key={item.key}
                href={item.href}
                className={`whitespace-nowrap rounded-lg px-3 py-1.5 text-xs font-bold ${
                  active === item.key
                    ? 'bg-amber-500 text-slate-950'
                    : 'text-slate-600 hover:bg-slate-100'
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
