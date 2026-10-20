'use client'

import Link from 'next/link'
import { useState, useRef, useEffect } from 'react'
import { useRouter, usePathname } from 'next/navigation'
import { useAuth } from '@/lib/auth-context'
import { authApi } from '@/lib/api'
import {
  Trophy,
  Search,
  Repeat,
  Users,
  Calendar,
  Heart,
  User,
  Building2,
  Shield,
  Briefcase,
  LogOut,
  Menu,
  X,
  ChevronDown,
  Sparkles,
  CalendarCheck2,
} from 'lucide-react'

export function AppHeader() {
  const { user, ready } = useAuth()
  const router = useRouter()
  const pathname = usePathname()
  const isStaffOnly = Boolean(
    user && user.roles.includes('STAFF') && !user.roles.includes('OWNER') && !user.roles.includes('ADMIN')
  )
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [userMenuOpen, setUserMenuOpen] = useState(false)
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)
  const userMenuRef = useRef<HTMLDivElement>(null)

  // Close menus on path change or click outside
  useEffect(() => {
    setUserMenuOpen(false)
    setMobileMenuOpen(false)
  }, [pathname])

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (userMenuRef.current && !userMenuRef.current.contains(event.target as Node)) {
        setUserMenuOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  async function logout() {
    setBusy(true)
    setError('')
    try {
      await authApi.logout()
      router.push('/login')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không thể đăng xuất')
    } finally {
      setBusy(false)
    }
  }

  // Active check helper
  const isActive = (path: string) => {
    if (path === '/') return pathname === '/'
    return pathname.startsWith(path)
  }

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
          <nav aria-label="Điều hướng chính" className="hidden md:flex items-center gap-1 text-sm font-semibold text-slate-600 dark:text-slate-300">
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
                  <span>Kiểm Tra Booking</span>
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
              <span>Trở thành Owner</span>
            </Link>
          )}

          {user?.roles.includes('STAFF') && !isStaffOnly && (
            <Link
              href="/staff/bookings"
              className="hidden lg:flex items-center gap-1.5 rounded-lg border border-blue-200 bg-blue-50/70 px-3 py-1.5 text-xs font-bold text-blue-800 shadow-sm transition-colors hover:bg-blue-100 dark:border-blue-900/50 dark:bg-blue-950/40 dark:text-blue-300 whitespace-nowrap"
            >
              <Briefcase size={13} className="text-blue-600" />
              <span>Kiểm Tra Booking</span>
            </Link>
          )}

          {user?.roles.includes('ADMIN') && (
            <Link
              href="/admin/accounts"
              className="hidden lg:flex items-center gap-1.5 rounded-lg bg-slate-900 px-3 py-1.5 text-xs font-bold text-amber-400 shadow-sm transition-all hover:bg-slate-800 whitespace-nowrap"
            >
              <Shield size={13} />
              <span>Admin Portal</span>
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
                <ChevronDown size={14} className={`text-slate-400 transition-transform ${userMenuOpen ? 'rotate-180' : ''}`} />
              </button>

              {userMenuOpen && (
                <div className="absolute right-0 mt-2 w-64 rounded-2xl border border-slate-200 bg-white p-2 shadow-xl dark:border-slate-800 dark:bg-slate-900 animate-in fade-in zoom-in-95 duration-150 z-50">
                  {/* User Profile Header */}
                  <div className="border-b border-slate-100 p-3 dark:border-slate-800">
                    <p className="font-bold text-slate-900 dark:text-white truncate">{user.fullName}</p>
                    <p className="text-xs text-slate-500 truncate">{user.email || user.phone}</p>
                    <div className="mt-2 flex flex-wrap gap-1">
                      {user.roles.map((r) => (
                        <span key={r} className="rounded-md bg-emerald-50 px-2 py-0.5 text-[10px] font-bold text-emerald-700 dark:bg-emerald-950 dark:text-emerald-400">
                          {r}
                        </span>
                      ))}
                    </div>
                  </div>

                  {/* Customer Links */}
                  {!isStaffOnly && (
                    <div className="py-1 text-sm font-medium text-slate-700 dark:text-slate-300">
                      <Link
                        href="/customer/bookings"
                        className="flex items-center gap-2.5 rounded-lg px-3 py-2 transition-colors hover:bg-slate-100 dark:hover:bg-slate-800"
                      >
                        <Calendar size={16} className="text-emerald-600" />
                        <span>Lịch đặt của tôi</span>
                      </Link>
                      <Link
                        href="/customer/groups"
                        className="flex items-center gap-2.5 rounded-lg px-3 py-2 transition-colors hover:bg-slate-100 dark:hover:bg-slate-800"
                      >
                        <Users size={16} className="text-emerald-600" />
                        <span>Hội nhóm thể thao</span>
                      </Link>
                      <Link
                        href="/customer/favorites"
                        className="flex items-center gap-2.5 rounded-lg px-3 py-2 transition-colors hover:bg-slate-100 dark:hover:bg-slate-800"
                      >
                        <Heart size={16} className="text-emerald-600" />
                        <span>Sân yêu thích</span>
                      </Link>
                      <Link
                        href="/customer/profile"
                        className="flex items-center gap-2.5 rounded-lg px-3 py-2 transition-colors hover:bg-slate-100 dark:hover:bg-slate-800"
                      >
                        <User size={16} className="text-emerald-600" />
                        <span>Hồ sơ cá nhân</span>
                      </Link>
                    </div>
                  )}

                  {/* Staff Dedicated Links */}
                  {isStaffOnly && (
                    <div className="py-1 text-sm font-medium text-slate-700 dark:text-slate-300">
                      <Link
                        href="/staff/bookings"
                        className="flex items-center gap-2.5 rounded-lg px-3 py-2 transition-colors hover:bg-slate-100 dark:hover:bg-slate-800 font-semibold text-slate-900 dark:text-white"
                      >
                        <Briefcase size={16} className="text-emerald-600" />
                        <span>Kiểm Tra Booking</span>
                      </Link>
                      <Link
                        href="/staff/counter-booking"
                        className="flex items-center gap-2.5 rounded-lg px-3 py-2 transition-colors hover:bg-slate-100 dark:hover:bg-slate-800"
                      >
                        <CalendarCheck2 size={16} className="text-emerald-600" />
                        <span>Đặt tại quầy</span>
                      </Link>
                    </div>
                  )}

                  {/* Portals Divider */}
                  {(user.roles.includes('OWNER') || user.roles.includes('ADMIN') || (user.roles.includes('STAFF') && !isStaffOnly)) && (
                    <div className="border-t border-slate-100 py-1 dark:border-slate-800">
                      {user.roles.includes('OWNER') && (
                        <Link
                          href="/owner/facilities"
                          className="flex items-center gap-2.5 rounded-lg px-3 py-2 text-sm font-semibold text-slate-800 hover:bg-emerald-50 dark:text-slate-200 dark:hover:bg-emerald-950"
                        >
                          <Building2 size={16} className="text-emerald-600" />
                          <span>Cơ sở của tôi</span>
                        </Link>
                      )}
                      {user.roles.includes('STAFF') && !isStaffOnly && (
                        <Link
                          href="/staff/bookings"
                          className="flex items-center gap-2.5 rounded-lg px-3 py-2 text-sm font-semibold text-slate-800 hover:bg-blue-50 dark:text-slate-200 dark:hover:bg-blue-950"
                        >
                          <Briefcase size={16} className="text-blue-600" />
                          <span>Kiểm Tra Booking</span>
                        </Link>
                      )}
                      {user.roles.includes('ADMIN') && (
                        <Link
                          href="/admin/accounts"
                          className="flex items-center gap-2.5 rounded-lg px-3 py-2 text-sm font-semibold text-amber-700 hover:bg-amber-50 dark:text-amber-400 dark:hover:bg-amber-950"
                        >
                          <Shield size={16} />
                          <span>Bảng điều khiển Admin</span>
                        </Link>
                      )}
                    </div>
                  )}

                  {/* Logout Button */}
                  <div className="border-t border-slate-100 pt-1 dark:border-slate-800">
                    <button
                      type="button"
                      onClick={logout}
                      disabled={busy}
                      className="flex w-full items-center gap-2.5 rounded-lg px-3 py-2 text-left text-sm font-semibold text-red-600 transition-colors hover:bg-red-50 dark:text-red-400 dark:hover:bg-red-950/50 cursor-pointer"
                    >
                      <LogOut size={16} />
                      <span>{busy ? 'Đang đăng xuất…' : 'Đăng xuất'}</span>
                    </button>
                  </div>
                </div>
              )}
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
      {mobileMenuOpen && (
        <div className="border-b border-slate-200 bg-white/98 backdrop-blur-xl p-4 shadow-2xl md:hidden dark:border-slate-800 dark:bg-slate-900/98 max-h-[calc(100vh-64px)] overflow-y-auto animate-in slide-in-from-top duration-200">
          {/* Guest Auth Banner in Mobile Drawer */}
          {!user ? (
            <div className="mb-4 rounded-2xl border border-emerald-100 bg-gradient-to-br from-emerald-500/10 via-teal-500/5 to-transparent p-4 dark:border-emerald-900/50 dark:bg-emerald-950/20">
              <div className="flex items-center gap-3">
                <div className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-gradient-to-tr from-emerald-600 to-teal-500 text-white shadow-md shadow-emerald-600/20">
                  <Trophy size={20} />
                </div>
                <div>
                  <h3 className="text-sm font-bold text-slate-900 dark:text-white">Chào mừng đến SportHub</h3>
                  <p className="text-xs text-slate-500 dark:text-slate-400">Tham gia để đặt sân, ghép trận và nhận ưu đãi</p>
                </div>
              </div>
              <div className="mt-3.5 grid grid-cols-2 gap-2">
                <Link
                  href="/login"
                  onClick={() => setMobileMenuOpen(false)}
                  className="flex h-10 items-center justify-center rounded-xl border border-slate-200 bg-white text-xs font-bold text-slate-800 shadow-2xs hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200 transition-colors"
                >
                  Đăng nhập
                </Link>
                <Link
                  href="/register"
                  onClick={() => setMobileMenuOpen(false)}
                  className="flex h-10 items-center justify-center rounded-xl bg-gradient-to-r from-emerald-600 to-teal-600 text-xs font-bold text-white shadow-sm hover:from-emerald-500 hover:to-teal-500 transition-all"
                >
                  Đăng ký ngay
                </Link>
              </div>
            </div>
          ) : (
            /* Logged-in User Profile Header in Mobile Drawer */
            <div className="mb-4 rounded-2xl border border-slate-200 bg-slate-50/80 p-3.5 dark:border-slate-800 dark:bg-slate-800/60">
              <div className="flex items-center gap-3">
                <div className="flex size-10 items-center justify-center rounded-full bg-emerald-600 text-sm font-bold text-white shrink-0 shadow-sm">
                  {user.fullName ? user.fullName.charAt(0).toUpperCase() : 'U'}
                </div>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-bold text-slate-900 dark:text-white">{user.fullName}</p>
                  <p className="truncate text-xs text-slate-500 dark:text-slate-400">{user.email || user.phone}</p>
                </div>
              </div>
              <div className="mt-2.5 flex flex-wrap gap-1">
                {user.roles.map((r) => (
                  <span key={r} className="rounded-md bg-emerald-100/80 px-2 py-0.5 text-[10px] font-bold text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300">
                    {r}
                  </span>
                ))}
              </div>
            </div>
          )}

          {/* Navigation Links */}
          <nav className="space-y-1 font-semibold text-slate-700 dark:text-slate-200">
            {isStaffOnly ? (
              <>
                <Link
                  href="/staff/bookings"
                  onClick={() => setMobileMenuOpen(false)}
                  className={`flex items-center gap-3 rounded-xl px-3 py-2.5 transition-colors ${
                    isActive('/staff/bookings')
                      ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/60 dark:text-emerald-400 font-bold'
                      : 'hover:bg-slate-100 dark:hover:bg-slate-800'
                  }`}
                >
                  <Briefcase size={18} className="text-emerald-600" />
                  <span>Kiểm Tra Booking</span>
                </Link>
                <Link
                  href="/staff/counter-booking"
                  onClick={() => setMobileMenuOpen(false)}
                  className={`flex items-center gap-3 rounded-xl px-3 py-2.5 transition-colors ${
                    isActive('/staff/counter-booking')
                      ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/60 dark:text-emerald-400 font-bold'
                      : 'hover:bg-slate-100 dark:hover:bg-slate-800'
                  }`}
                >
                  <CalendarCheck2 size={18} className="text-emerald-600" />
                  <span>Đặt tại quầy</span>
                </Link>
              </>
            ) : (
              <>
                <Link
                  href="/search"
                  onClick={() => setMobileMenuOpen(false)}
                  className={`flex items-center gap-3 rounded-xl px-3 py-2.5 transition-colors ${
                    isActive('/search')
                      ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/60 dark:text-emerald-400'
                      : 'hover:bg-slate-100 dark:hover:bg-slate-800'
                  }`}
                >
                  <Search size={18} className="text-emerald-600" />
                  <span>Tìm kiếm sân</span>
                </Link>

                {user?.roles.includes('CUSTOMER') && (
                  <Link
                    href="/transfers"
                    onClick={() => setMobileMenuOpen(false)}
                    className={`flex items-center gap-3 rounded-xl px-3 py-2.5 transition-colors ${
                      isActive('/transfers')
                        ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/60 dark:text-emerald-400'
                        : 'hover:bg-slate-100 dark:hover:bg-slate-800'
                    }`}
                  >
                    <Repeat size={18} className="text-emerald-600" />
                    <span>Chợ chuyển nhượng vé</span>
                  </Link>
                )}

                {user && (
                  <>
                    <Link
                      href="/customer/bookings"
                      onClick={() => setMobileMenuOpen(false)}
                      className={`flex items-center gap-3 rounded-xl px-3 py-2.5 transition-colors ${
                        isActive('/customer/bookings')
                          ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/60 dark:text-emerald-400'
                          : 'hover:bg-slate-100 dark:hover:bg-slate-800'
                      }`}
                    >
                      <Calendar size={18} className="text-emerald-600" />
                      <span>Lịch đặt của tôi</span>
                    </Link>
                    <Link
                      href="/customer/groups"
                      onClick={() => setMobileMenuOpen(false)}
                      className={`flex items-center gap-3 rounded-xl px-3 py-2.5 transition-colors ${
                        isActive('/customer/groups')
                          ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/60 dark:text-emerald-400'
                          : 'hover:bg-slate-100 dark:hover:bg-slate-800'
                      }`}
                    >
                      <Users size={18} className="text-emerald-600" />
                      <span>Hội nhóm & Chia tiền</span>
                    </Link>
                    <Link
                      href="/customer/favorites"
                      onClick={() => setMobileMenuOpen(false)}
                      className={`flex items-center gap-3 rounded-xl px-3 py-2.5 transition-colors ${
                        isActive('/customer/favorites')
                          ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/60 dark:text-emerald-400'
                          : 'hover:bg-slate-100 dark:hover:bg-slate-800'
                      }`}
                    >
                      <Heart size={18} className="text-emerald-600" />
                      <span>Sân yêu thích</span>
                    </Link>
                    <Link
                      href="/customer/profile"
                      onClick={() => setMobileMenuOpen(false)}
                      className={`flex items-center gap-3 rounded-xl px-3 py-2.5 transition-colors ${
                        isActive('/customer/profile')
                          ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/60 dark:text-emerald-400'
                          : 'hover:bg-slate-100 dark:hover:bg-slate-800'
                      }`}
                    >
                      <User size={18} className="text-emerald-600" />
                      <span>Hồ sơ cá nhân</span>
                    </Link>
                  </>
                )}

                {/* Role Links */}
                {user?.roles.includes('OWNER') && (
                  <Link
                    href="/owner/facilities"
                    onClick={() => setMobileMenuOpen(false)}
                    className="flex items-center gap-3 rounded-xl px-3 py-2.5 text-emerald-700 hover:bg-emerald-50 dark:text-emerald-400 dark:hover:bg-emerald-950"
                  >
                    <Building2 size={18} />
                    <span>Không gian Chủ sân</span>
                  </Link>
                )}
                {user?.roles.includes('CUSTOMER') && !user.roles.includes('OWNER') && (
                  <Link
                    href="/owner/application"
                    onClick={() => setMobileMenuOpen(false)}
                    className="flex items-center gap-3 rounded-xl px-3 py-2.5 text-amber-700 hover:bg-amber-50 dark:text-amber-400 dark:hover:bg-amber-950"
                  >
                    <Sparkles size={18} className="text-amber-600" />
                    <span>Đăng ký trở thành chủ sân</span>
                  </Link>
                )}
                {user?.roles.includes('STAFF') && (
                  <Link
                    href="/staff/bookings"
                    onClick={() => setMobileMenuOpen(false)}
                    className="flex items-center gap-3 rounded-xl px-3 py-2.5 text-blue-700 hover:bg-blue-50 dark:text-blue-400 dark:hover:bg-blue-950"
                  >
                    <Briefcase size={18} />
                    <span>Kiểm Tra Booking</span>
                  </Link>
                )}
              </>
            )}
            {user?.roles.includes('ADMIN') && (
              <Link
                href="/admin/accounts"
                onClick={() => setMobileMenuOpen(false)}
                className="flex items-center gap-3 rounded-xl px-3 py-2.5 text-amber-700 hover:bg-amber-50 dark:text-amber-400 dark:hover:bg-amber-950"
              >
                <Shield size={18} />
                <span>Bảng điều khiển Admin</span>
              </Link>
            )}

            {/* Logout button in Mobile Drawer */}
            {user && (
              <div className="border-t border-slate-100 pt-2 mt-2 dark:border-slate-800">
                <button
                  type="button"
                  onClick={() => {
                    setMobileMenuOpen(false)
                    logout()
                  }}
                  disabled={busy}
                  className="flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left text-sm font-semibold text-rose-600 transition-colors hover:bg-rose-50 dark:text-rose-400 dark:hover:bg-rose-950/40 cursor-pointer"
                >
                  <LogOut size={18} />
                  <span>{busy ? 'Đang đăng xuất…' : 'Đăng xuất tài khoản'}</span>
                </button>
              </div>
            )}
          </nav>
        </div>
      )}

      {error && (
        <div role="alert" className="border-t border-red-200 bg-red-50 px-4 py-2 text-center text-xs font-semibold text-red-700">
          {error}
        </div>
      )}
    </header>
  )
}
