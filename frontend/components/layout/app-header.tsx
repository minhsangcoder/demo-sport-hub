'use client'
import { Briefcase, Building2, ChevronDown, Menu, Shield, Sparkles, Trophy, X } from 'lucide-react'
import dynamic from 'next/dynamic'
import Link from 'next/link'
import { DesktopNavigation } from './header-navigation'
import { useHeader } from './use-header'
const HeaderAccountMenu = dynamic(() =>
  import('./header-account-menu').then((m) => m.HeaderAccountMenu),
)
const HeaderMobileMenu = dynamic(() =>
  import('./header-mobile-menu').then((m) => m.HeaderMobileMenu),
)
export function AppHeader() {
  const model = useHeader()
  const {
    isStaffOnly,
    user,
    ready,
    pathname,
    userMenuRef,
    setUserMenuOpen,
    userMenuOpen,
    setMobileMenuOpen,
    mobileMenuOpen,
    error,
  } = model
  return (
    <header className="sticky top-0 z-40 border-b border-slate-200/80 bg-white/95 backdrop-blur-md dark:border-slate-800 dark:bg-slate-900/95">
      <div className="mx-auto flex h-16 max-w-7xl items-center justify-between gap-2 sm:gap-4 px-3 sm:px-6">
        {/* Brand Logo & Desktop Nav */}
        <div className="flex items-center gap-4 lg:gap-8 min-w-0">
          <Link
            href={isStaffOnly ? '/staff/bookings' : '/'}
            className="group flex items-center gap-2 sm:gap-2.5 text-xl font-black tracking-tight text-slate-900 transition-opacity hover:opacity-95 dark:text-white shrink-0"
          >
            <div className="flex size-8 sm:size-9 items-center justify-center rounded-xl bg-gradient-to-tr from-emerald-600 to-teal-500 text-white shadow-md shadow-emerald-600/20 group-hover:scale-105 transition-all shrink-0">
              <Trophy size={16} className="sm:size-[18px]" />
            </div>
            <span className="flex items-baseline text-xl sm:text-2xl font-black tracking-tight whitespace-nowrap">
              SportHub<span className="text-emerald-500">.</span>
            </span>
          </Link>

          {/* Desktop Navigation */}
          <DesktopNavigation model={model} />
        </div>

        {/* Right Section: Role Portals & User Profile */}
        <div className="flex items-center gap-1.5 sm:gap-3 shrink-0">
          {/* Quick Role Portal Buttons (desktop only) */}
          {user?.roles.includes('OWNER') && (
            <Link
              href="/owner/facilities"
              className="hidden lg:flex items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs font-bold text-slate-700 shadow-sm transition-all hover:border-emerald-500 hover:text-emerald-600 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200 whitespace-nowrap"
            >
              <Building2 size={13} className="text-emerald-600" />
              <span>Cơ sở của tôi</span>
            </Link>
          )}

          {user?.roles.includes('CUSTOMER') && !user.roles.includes('OWNER') && !isStaffOnly && (
            <Link
              href="/owner/application"
              className="hidden lg:flex items-center gap-1.5 rounded-lg border border-amber-200 bg-amber-50/70 px-3 py-1.5 text-xs font-bold text-amber-800 shadow-sm transition-colors hover:bg-amber-100 dark:border-amber-900/50 dark:bg-amber-950/40 dark:text-amber-300 whitespace-nowrap"
            >
              <Sparkles size={13} className="text-amber-600" />
              <span>Trở thành chủ cơ sở</span>
            </Link>
          )}

          {user?.roles.includes('STAFF') && !isStaffOnly && (
            <Link
              href="/staff/bookings"
              className="hidden lg:flex items-center gap-1.5 rounded-lg border border-blue-200 bg-blue-50/70 px-3 py-1.5 text-xs font-bold text-blue-800 shadow-sm transition-colors hover:bg-blue-100 dark:border-blue-900/50 dark:bg-blue-950/40 dark:text-blue-300 whitespace-nowrap"
            >
              <Briefcase size={13} className="text-blue-600" />
              <span>Kiểm tra lượt đặt</span>
            </Link>
          )}

          {user?.roles.includes('ADMIN') && (
            <Link
              href="/admin/accounts"
              className="hidden lg:flex items-center gap-1.5 rounded-lg bg-slate-900 px-3 py-1.5 text-xs font-bold text-amber-400 shadow-sm transition-all hover:bg-slate-800 whitespace-nowrap"
            >
              <Shield size={13} />
              <span>Trang quản trị</span>
            </Link>
          )}

          {/* User Auth State */}
          {!ready ? (
            <div className="flex h-9 items-center px-2 sm:px-3 text-xs text-slate-400 animate-pulse whitespace-nowrap">
              Đang xác thực…
            </div>
          ) : !user ? (
            <div className="flex items-center gap-1.5 sm:gap-2">
              <Link
                href="/login"
                className={`rounded-xl px-2.5 py-1.5 sm:px-3.5 sm:py-2 text-xs sm:text-sm font-semibold transition-all whitespace-nowrap shrink-0 ${
                  pathname === '/login'
                    ? 'bg-emerald-50 text-emerald-700 font-bold dark:bg-emerald-950/60 dark:text-emerald-400'
                    : 'text-slate-700 hover:bg-slate-100 hover:text-slate-900 dark:text-slate-200 dark:hover:bg-slate-800'
                }`}
              >
                Đăng nhập
              </Link>
              <Link
                href="/register"
                className={`hidden sm:inline-flex items-center justify-center rounded-xl px-3.5 sm:px-4 py-1.5 sm:py-2 text-xs sm:text-sm font-bold shadow-sm transition-all active:scale-95 whitespace-nowrap shrink-0 ${
                  pathname === '/register'
                    ? 'bg-emerald-700 text-white shadow-emerald-700/30'
                    : 'bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-500 hover:to-teal-500 text-white shadow-emerald-600/20 hover:shadow-md'
                }`}
              >
                Đăng ký
              </Link>
            </div>
          ) : (
            /* User Dropdown */
            <div className="relative" ref={userMenuRef}>
              <button
                type="button"
                onClick={() => setUserMenuOpen((prev) => !prev)}
                aria-expanded={userMenuOpen}
                className="flex items-center gap-1.5 sm:gap-2 rounded-full border border-slate-200 bg-white p-1 pr-2 sm:pr-3 text-left shadow-sm transition-all hover:border-slate-300 dark:border-slate-700 dark:bg-slate-800 shrink-0 cursor-pointer"
              >
                <div className="flex size-7 items-center justify-center rounded-full bg-emerald-600 text-xs font-bold text-white shrink-0">
                  {user.fullName ? user.fullName.charAt(0).toUpperCase() : 'U'}
                </div>
                <span className="hidden sm:inline-block max-w-[110px] truncate text-xs font-bold text-slate-800 dark:text-slate-200">
                  {user.fullName || 'Tài khoản'}
                </span>
                <ChevronDown
                  size={14}
                  className={`text-slate-400 transition-transform ${userMenuOpen ? 'rotate-180' : ''}`}
                />
              </button>

              {userMenuOpen && <HeaderAccountMenu model={model} />}
            </div>
          )}

          {/* Mobile Menu Toggle Button */}
          <button
            type="button"
            onClick={() => setMobileMenuOpen((prev) => !prev)}
            aria-label={mobileMenuOpen ? 'Đóng menu' : 'Mở menu di động'}
            className="flex size-9 items-center justify-center rounded-xl border border-slate-200 text-slate-700 hover:bg-slate-100 md:hidden dark:border-slate-700 dark:text-slate-300 shrink-0 transition-colors cursor-pointer"
          >
            {mobileMenuOpen ? <X size={20} /> : <Menu size={20} />}
          </button>
        </div>
      </div>

      {/* Mobile Drawer Navigation */}
      {mobileMenuOpen && <HeaderMobileMenu model={model} />}

      {error && (
        <div
          role="alert"
          className="border-t border-red-200 bg-red-50 px-4 py-2 text-center text-xs font-semibold text-red-700"
        >
          {error}
        </div>
      )}
    </header>
  )
}
