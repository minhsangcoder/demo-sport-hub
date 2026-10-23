'use client'
import { Briefcase, Calendar, CalendarCheck2, Repeat, Search, Users } from 'lucide-react'
import Link from 'next/link'
import type { HeaderModel } from './use-header'
export function DesktopNavigation({ model }: { model: HeaderModel }) {
  const { user, isStaffOnly, isActive } = model
  return (
    <nav
      aria-label="Điều hướng chính"
      className="hidden md:flex items-center gap-1 text-sm font-semibold text-slate-600 dark:text-slate-300"
    >
      {!isStaffOnly && (
        <Link
          href="/search"
          className={`flex items-center gap-1.5 rounded-lg px-3 py-2 transition-colors whitespace-nowrap ${
            isActive('/search')
              ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/60 dark:text-emerald-400'
              : 'hover:bg-slate-100 hover:text-slate-900 dark:hover:bg-slate-800 dark:hover:text-white'
          }`}
        >
          <Search size={15} />
          <span>Tìm sân</span>
        </Link>
      )}

      {!isStaffOnly && user?.roles.includes('CUSTOMER') && (
        <Link
          href="/transfers"
          className={`flex items-center gap-1.5 rounded-lg px-3 py-2 transition-colors whitespace-nowrap ${
            isActive('/transfers')
              ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/60 dark:text-emerald-400'
              : 'hover:bg-slate-100 hover:text-slate-900 dark:hover:bg-slate-800 dark:hover:text-white'
          }`}
        >
          <Repeat size={15} />
          <span>Chuyển nhượng</span>
        </Link>
      )}

      {!isStaffOnly && user?.roles.includes('CUSTOMER') && (
        <Link
          href="/customer/groups"
          className={`flex items-center gap-1.5 rounded-lg px-3 py-2 transition-colors whitespace-nowrap ${
            isActive('/customer/groups')
              ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/60 dark:text-emerald-400'
              : 'hover:bg-slate-100 hover:text-slate-900 dark:hover:bg-slate-800 dark:hover:text-white'
          }`}
        >
          <Users size={15} />
          <span>Nhóm thể thao</span>
        </Link>
      )}

      {!isStaffOnly && user?.roles.includes('CUSTOMER') && (
        <Link
          href="/customer/bookings"
          className={`flex items-center gap-1.5 rounded-lg px-3 py-2 transition-colors whitespace-nowrap ${
            isActive('/customer/bookings')
              ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/60 dark:text-emerald-400'
              : 'hover:bg-slate-100 hover:text-slate-900 dark:hover:bg-slate-800 dark:hover:text-white'
          }`}
        >
          <Calendar size={15} />
          <span>Lịch đặt</span>
        </Link>
      )}

      {isStaffOnly && (
        <>
          <Link
            href="/staff/bookings"
            className={`flex items-center gap-1.5 rounded-lg px-3 py-2 transition-colors whitespace-nowrap ${
              isActive('/staff/bookings')
                ? 'bg-emerald-50 text-emerald-700 font-bold dark:bg-emerald-950/60 dark:text-emerald-400'
                : 'hover:bg-slate-100 hover:text-slate-900 dark:hover:bg-slate-800 dark:hover:text-white'
            }`}
          >
            <Briefcase size={15} className="text-emerald-600" />
            <span>Kiểm tra lượt đặt</span>
          </Link>
          <Link
            href="/staff/counter-booking"
            className={`flex items-center gap-1.5 rounded-lg px-3 py-2 transition-colors whitespace-nowrap ${
              isActive('/staff/counter-booking')
                ? 'bg-emerald-50 text-emerald-700 font-bold dark:bg-emerald-950/60 dark:text-emerald-400'
                : 'hover:bg-slate-100 hover:text-slate-900 dark:hover:bg-slate-800 dark:hover:text-white'
            }`}
          >
            <CalendarCheck2 size={15} />
            <span>Đặt tại quầy</span>
          </Link>
        </>
      )}
    </nav>
  )
}
