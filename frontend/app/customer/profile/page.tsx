'use client'

import { useState, useEffect } from 'react'
import Link from 'next/link'
import { api, ApiError, type UserProfile, type Role } from '@/lib/api'
import { useAuth } from '@/lib/auth-context'
import { RequireAuth } from '@/features/auth/require-auth'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import {
  User,
  Calendar,
  Mail,
  Phone,
  ShieldCheck,
  Sparkles,
  Building2,
  CheckCircle2,
  AlertCircle,
  Camera,
  Copy,
  Check,
  ArrowRight,
  Save,
  ExternalLink,
  Trophy,
  Shield,
  Key,
  RefreshCw,
  Clock,
  ChevronRight,
  Info
} from 'lucide-react'

// Curated sports-themed avatars for quick selection
const PRESET_AVATARS = [
  {
    name: 'Tennis Player',
    url: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=240&auto=format&fit=crop&q=80',
    icon: '🎾'
  },
  {
    name: 'Pickleball Fan',
    url: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=240&auto=format&fit=crop&q=80',
    icon: '🏓'
  },
  {
    name: 'Badminton Pro',
    url: 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=240&auto=format&fit=crop&q=80',
    icon: '🏸'
  },
  {
    name: 'Football Striker',
    url: 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=240&auto=format&fit=crop&q=80',
    icon: '⚽'
  },
  {
    name: 'Basketball Star',
    url: 'https://images.unsplash.com/photo-1522075469751-3a6694fb2f61?w=240&auto=format&fit=crop&q=80',
    icon: '🏀'
  },
  {
    name: 'Runner',
    url: 'https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=240&auto=format&fit=crop&q=80',
    icon: '🏃'
  }
]

export default function ProfilePage() {
  const { user } = useAuth()
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [copiedId, setCopiedId] = useState(false)

  // Form states with live preview
  const [fullName, setFullName] = useState('')
  const [dateOfBirth, setDateOfBirth] = useState('')
  const [avatarUrl, setAvatarUrl] = useState('')
  const [showAvatarPicker, setShowAvatarPicker] = useState(false)

  // Sync initial user data
  useEffect(() => {
    if (user) {
      setFullName(user.fullName || '')
      setDateOfBirth(user.dateOfBirth || '')
      setAvatarUrl(user.avatarUrl || '')
    }
  }, [user])

  const copyUserId = () => {
    if (!user?.id) return
    navigator.clipboard.writeText(user.id)
    setCopiedId(true)
    setTimeout(() => setCopiedId(false), 2000)
  }

  const getInitials = (name?: string) => {
    if (!name) return 'U'
    const parts = name.trim().split(/\s+/)
    if (parts.length === 1) return parts[0].substring(0, 2).toUpperCase()
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
  }

  const getRoleBadge = (role: Role) => {
    switch (role) {
      case 'ADMIN':
        return {
          label: 'Quản trị viên',
          icon: ShieldCheck,
          className: 'bg-rose-50 text-rose-700 border-rose-200 dark:bg-rose-950/40 dark:text-rose-300 dark:border-rose-800'
        }
      case 'OWNER':
        return {
          label: 'Chủ cơ sở sân',
          icon: Building2,
          className: 'bg-amber-50 text-amber-700 border-amber-200 dark:bg-amber-950/40 dark:text-amber-300 dark:border-amber-800'
        }
      case 'STAFF':
        return {
          label: 'Nhân viên quản lý',
          icon: Shield,
          className: 'bg-blue-50 text-blue-700 border-blue-200 dark:bg-blue-950/40 dark:text-blue-300 dark:border-blue-800'
        }
      case 'CUSTOMER':
      default:
        return {
          label: 'Khách hàng',
          icon: Sparkles,
          className: 'bg-emerald-50 text-emerald-700 border-emerald-200 dark:bg-emerald-950/40 dark:text-emerald-300 dark:border-emerald-800'
        }
    }
  }

  async function handleSave(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy) return
    setBusy(true)
    setMessage('')
    setError('')

    try {
      const updated = await api<UserProfile>('/users/me', {
        method: 'PATCH',
        body: JSON.stringify({
          fullName: fullName.trim(),
          avatarUrl: avatarUrl.trim() || null,
          dateOfBirth: dateOfBirth || null
        })
      })

      window.dispatchEvent(new CustomEvent('sporthub-session', { detail: updated }))
      setMessage('Hồ sơ của bạn đã được cập nhật thành công!')
      setTimeout(() => setMessage(''), 4000)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Không thể lưu hồ sơ. Vui lòng thử lại.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <RequireAuth>
      <div className="min-h-screen bg-slate-50/70 pb-20 dark:bg-slate-950/40">
        {/* Cover Hero Banner */}
        <div className="relative h-72 w-full overflow-hidden bg-gradient-to-r from-emerald-700 via-teal-700 to-slate-900 shadow-inner md:h-80">
          <div className="absolute inset-0 opacity-15 bg-[radial-gradient(#fff_1px,transparent_1px)] [background-size:18px_18px]" />
          <div className="absolute -left-20 -top-20 h-72 w-72 rounded-full bg-emerald-400/20 blur-3xl" />
          <div className="absolute -right-20 -bottom-20 h-80 w-80 rounded-full bg-teal-300/20 blur-3xl" />
          
          <div className="mx-auto flex h-full max-w-6xl items-end justify-between px-4 pb-20 sm:px-6 md:pb-24 lg:px-8">
            <div className="text-white">
              <span className="inline-flex items-center gap-1.5 rounded-full bg-white/15 px-3 py-1 text-xs font-medium text-emerald-100 backdrop-blur-md">
                <Sparkles size={13} className="text-emerald-300" />
                SportHub Account Profile
              </span>
              <h1 className="mt-2 text-2xl font-bold tracking-tight sm:text-3xl lg:text-4xl">
                Cài đặt hồ sơ cá nhân
              </h1>
            </div>
            
            <Link
              href="/customer/profile/contact"
              className="hidden items-center gap-2 rounded-xl bg-white/10 px-4 py-2 text-sm font-semibold text-white backdrop-blur-md transition-all hover:bg-white/20 sm:inline-flex"
            >
              <Key size={15} />
              Bảo mật & Liên hệ
              <ArrowRight size={14} />
            </Link>
          </div>
        </div>

        {/* Floating Profile Overview Card */}
        <div className="mx-auto max-w-6xl px-4 sm:px-6 lg:px-8">
          <div className="-mt-16 relative z-10 rounded-2xl border border-slate-200/80 bg-white p-5 shadow-xl shadow-slate-900/5 backdrop-blur-xl dark:border-slate-800 dark:bg-slate-900 sm:p-6 lg:p-8">
            <div className="flex flex-col gap-6 md:flex-row md:items-center md:justify-between">
              {/* Left: Avatar + Identity */}
              <div className="flex flex-col gap-4 sm:flex-row sm:items-center">
                {/* Avatar with Camera Overlay */}
                <div className="relative group shrink-0">
                  <div className="relative size-24 sm:size-28 overflow-hidden rounded-2xl border-4 border-white bg-gradient-to-br from-emerald-500 to-teal-700 shadow-md ring-1 ring-slate-200 dark:border-slate-800 dark:ring-slate-700">
                    {avatarUrl ? (
                      <img
                        src={avatarUrl}
                        alt={fullName || 'Avatar'}
                        className="size-full object-cover transition-transform duration-300 group-hover:scale-105"
                        onError={() => setAvatarUrl('')}
                      />
                    ) : (
                      <div className="flex size-full items-center justify-center font-bold text-white text-2xl sm:text-3xl">
                        {getInitials(fullName || user?.fullName)}
                      </div>
                    )}
                  </div>
                  <button
                    type="button"
                    onClick={() => setShowAvatarPicker(!showAvatarPicker)}
                    title="Đổi ảnh đại diện"
                    className="absolute -bottom-1 -right-1 flex size-8 items-center justify-center rounded-xl bg-emerald-600 text-white shadow-md transition-all hover:bg-emerald-700 hover:scale-110 active:scale-95"
                  >
                    <Camera size={15} />
                  </button>
                </div>

                {/* Name, Roles, ID */}
                <div className="space-y-1.5">
                  <div className="flex flex-wrap items-center gap-2">
                    <h2 className="text-xl font-bold text-slate-900 dark:text-white sm:text-2xl">
                      {fullName || user?.fullName || 'Người dùng SportHub'}
                    </h2>
                    {user?.status === 'ACTIVE' && (
                      <span title="Tài khoản đã kích hoạt" className="flex items-center text-emerald-600">
                        <CheckCircle2 size={18} className="fill-emerald-50 text-emerald-600 dark:fill-emerald-950" />
                      </span>
                    )}
                  </div>

                  <p className="text-sm text-slate-500 dark:text-slate-400">
                    {user?.email || user?.phone || 'Chưa cung cấp thông tin liên hệ'}
                  </p>

                  {/* Role Badges */}
                  <div className="flex flex-wrap items-center gap-2 pt-1">
                    {user?.roles?.map((role) => {
                      const badge = getRoleBadge(role)
                      const IconComponent = badge.icon
                      return (
                        <span
                          key={role}
                          className={`inline-flex items-center gap-1.5 rounded-lg border px-2.5 py-1 text-xs font-semibold ${badge.className}`}
                        >
                          <IconComponent size={12} />
                          {badge.label}
                        </span>
                      )
                    })}
                  </div>
                </div>
              </div>

              {/* Right: Quick Portal Navigation Links */}
              <div className="flex flex-wrap items-center gap-2.5 border-t border-slate-100 pt-4 md:border-t-0 md:pt-0">
                <Link
                  href="/customer/bookings"
                  className="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 shadow-sm transition-all hover:bg-slate-50 hover:border-slate-300 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-200 dark:hover:bg-slate-800"
                >
                  <Trophy size={16} className="text-emerald-600" />
                  Lịch đặt của tôi
                </Link>

                {user?.roles?.includes('ADMIN') && (
                  <Link
                    href="/admin"
                    className="inline-flex items-center gap-1.5 rounded-xl bg-rose-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-all hover:bg-rose-700 hover:shadow-rose-600/20"
                  >
                    <ShieldCheck size={16} />
                    Trang Admin
                  </Link>
                )}

                {user?.roles?.includes('OWNER') && (
                  <Link
                    href="/owner"
                    className="inline-flex items-center gap-1.5 rounded-xl bg-amber-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-all hover:bg-amber-700 hover:shadow-amber-600/20"
                  >
                    <Building2 size={16} />
                    Kênh Chủ sân
                  </Link>
                )}

                {user?.roles?.includes('STAFF') && (
                  <Link
                    href="/staff"
                    className="inline-flex items-center gap-1.5 rounded-xl bg-blue-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-all hover:bg-blue-700 hover:shadow-blue-600/20"
                  >
                    <Shield size={16} />
                    Kênh Nhân viên
                  </Link>
                )}
              </div>
            </div>

            {/* Quick Avatar Preset Selector Accordion */}
            {showAvatarPicker && (
              <div className="mt-6 border-t border-slate-100 pt-5 dark:border-slate-800">
                <div className="flex items-center justify-between pb-3">
                  <div className="text-xs font-semibold uppercase tracking-wider text-slate-500">
                    Chọn nhanh ảnh đại diện phong cách thể thao
                  </div>
                  <button
                    type="button"
                    onClick={() => setShowAvatarPicker(false)}
                    className="text-xs text-slate-400 hover:text-slate-600 dark:hover:text-slate-200"
                  >
                    Đóng lại
                  </button>
                </div>
                <div className="grid grid-cols-3 gap-3 sm:grid-cols-6">
                  {PRESET_AVATARS.map((preset) => (
                    <button
                      key={preset.name}
                      type="button"
                      onClick={() => {
                        setAvatarUrl(preset.url)
                        setShowAvatarPicker(false)
                      }}
                      className={`group relative flex flex-col items-center gap-1.5 rounded-xl border p-2 text-center transition-all ${
                        avatarUrl === preset.url
                          ? 'border-emerald-600 bg-emerald-50/50 ring-2 ring-emerald-600/30 dark:bg-emerald-950/20'
                          : 'border-slate-200 hover:border-emerald-300 hover:bg-slate-50 dark:border-slate-800 dark:hover:bg-slate-800'
                      }`}
                    >
                      <img
                        src={preset.url}
                        alt={preset.name}
                        className="size-12 rounded-full object-cover shadow-sm group-hover:scale-105 transition-transform"
                      />
                      <span className="text-[11px] font-medium text-slate-600 dark:text-slate-300 truncate w-full">
                        {preset.icon} {preset.name}
                      </span>
                    </button>
                  ))}
                </div>
              </div>
            )}
          </div>

          {/* Sub Navigation Tabs */}
          <div className="mt-8 flex items-center justify-between border-b border-slate-200 pb-3 dark:border-slate-800">
            <nav className="flex space-x-6">
              <Link
                href="/customer/profile"
                className="border-b-2 border-emerald-600 pb-3 text-sm font-bold text-emerald-600 flex items-center gap-2"
              >
                <User size={16} />
                Thông tin cá nhân
              </Link>
              <Link
                href="/customer/profile/contact"
                className="border-b-2 border-transparent pb-3 text-sm font-medium text-slate-500 hover:text-slate-800 hover:border-slate-300 dark:text-slate-400 dark:hover:text-slate-200 flex items-center gap-2 transition-colors"
              >
                <Key size={16} />
                Bảo mật & Liên hệ
              </Link>
              <Link
                href="/customer/bookings"
                className="border-b-2 border-transparent pb-3 text-sm font-medium text-slate-500 hover:text-slate-800 hover:border-slate-300 dark:text-slate-400 dark:hover:text-slate-200 flex items-center gap-2 transition-colors"
              >
                <Clock size={16} />
                Lịch sử đặt sân
              </Link>
            </nav>
          </div>

          {/* Main Content Grid: Form (Left) & Sidebar Widgets (Right) */}
          <div className="mt-8 grid grid-cols-1 gap-8 lg:grid-cols-3">
            {/* Left 2 Cols: Profile Form */}
            <div className="lg:col-span-2">
              <div className="rounded-2xl border border-slate-200/80 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900 sm:p-8">
                <div className="flex items-center justify-between border-b border-slate-100 pb-5 dark:border-slate-800">
                  <div>
                    <h3 className="text-lg font-bold text-slate-900 dark:text-white">
                      Thông tin cơ bản
                    </h3>
                    <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
                      Cập nhật họ tên, ảnh đại diện và ngày sinh hiển thị trong các trận đấu và lịch đặt sân.
                    </p>
                  </div>
                  <span className="rounded-lg bg-emerald-50 px-2.5 py-1 text-xs font-semibold text-emerald-700 dark:bg-emerald-950/60 dark:text-emerald-300">
                    Cá nhân
                  </span>
                </div>

                {/* Status Banners */}
                {message && (
                  <div className="mt-5 flex items-center gap-3 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm font-medium text-emerald-800 dark:border-emerald-800/60 dark:bg-emerald-950/40 dark:text-emerald-200 animate-in fade-in">
                    <CheckCircle2 size={18} className="shrink-0 text-emerald-600" />
                    <span>{message}</span>
                  </div>
                )}

                {error && (
                  <div className="mt-5 flex items-center gap-3 rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm font-medium text-rose-800 dark:border-rose-800/60 dark:bg-rose-950/40 dark:text-rose-200 animate-in fade-in">
                    <AlertCircle size={18} className="shrink-0 text-rose-600" />
                    <span>{error}</span>
                  </div>
                )}

                <form onSubmit={handleSave} className="mt-6 space-y-6">
                  {/* Full Name Field */}
                  <div className="space-y-2">
                    <label className="text-sm font-semibold text-slate-800 dark:text-slate-200 flex items-center gap-1.5">
                      <User size={15} className="text-emerald-600" />
                      Họ và tên <span className="text-rose-500">*</span>
                    </label>
                    <Input
                      name="fullName"
                      value={fullName}
                      onChange={(e) => setFullName(e.target.value)}
                      placeholder="Nhập họ và tên đầy đủ của bạn"
                      minLength={2}
                      maxLength={120}
                      required
                      disabled={busy}
                      className="h-11 rounded-xl border-slate-200 bg-slate-50/50 focus:bg-white dark:border-slate-800 dark:bg-slate-900 text-sm"
                    />
                    <p className="text-xs text-slate-400">
                      Tên này sẽ hiển thị trên lịch đặt sân, nhóm chơi và hóa đơn.
                    </p>
                  </div>

                  {/* Date of Birth Field */}
                  <div className="space-y-2">
                    <label className="text-sm font-semibold text-slate-800 dark:text-slate-200 flex items-center gap-1.5">
                      <Calendar size={15} className="text-emerald-600" />
                      Ngày sinh
                    </label>
                    <Input
                      name="dateOfBirth"
                      type="date"
                      value={dateOfBirth}
                      onChange={(e) => setDateOfBirth(e.target.value)}
                      disabled={busy}
                      className="h-11 rounded-xl border-slate-200 bg-slate-50/50 focus:bg-white dark:border-slate-800 dark:bg-slate-900 text-sm"
                    />
                    <p className="text-xs text-slate-400">
                      Giúp cá nhân hóa trải nghiệm và nhận ưu đãi đặt sân ngày sinh nhật.
                    </p>
                  </div>

                  {/* Avatar URL Field */}
                  <div className="space-y-2">
                    <div className="flex items-center justify-between">
                      <label className="text-sm font-semibold text-slate-800 dark:text-slate-200 flex items-center gap-1.5">
                        <Camera size={15} className="text-emerald-600" />
                        Đường dẫn ảnh đại diện (Avatar URL)
                      </label>
                      <button
                        type="button"
                        onClick={() => setShowAvatarPicker(true)}
                        className="text-xs font-semibold text-emerald-600 hover:underline"
                      >
                        Chọn mẫu có sẵn
                      </button>
                    </div>
                    <div className="flex gap-2">
                      <Input
                        name="avatarUrl"
                        type="url"
                        value={avatarUrl}
                        onChange={(e) => setAvatarUrl(e.target.value)}
                        placeholder="https://example.com/my-photo.jpg"
                        maxLength={2048}
                        disabled={busy}
                        className="h-11 rounded-xl border-slate-200 bg-slate-50/50 focus:bg-white dark:border-slate-800 dark:bg-slate-900 text-sm"
                      />
                      {avatarUrl && (
                        <Button
                          type="button"
                          variant="outline"
                          onClick={() => setAvatarUrl('')}
                          className="h-11 px-3 text-xs text-slate-500 hover:text-rose-600"
                        >
                          Xóa
                        </Button>
                      )}
                    </div>
                  </div>

                  {/* Actions Bar */}
                  <div className="flex flex-col-reverse gap-3 pt-4 sm:flex-row sm:items-center sm:justify-end border-t border-slate-100 dark:border-slate-800">
                    <Button
                      type="button"
                      variant="outline"
                      disabled={busy}
                      onClick={() => {
                        if (user) {
                          setFullName(user.fullName || '')
                          setDateOfBirth(user.dateOfBirth || '')
                          setAvatarUrl(user.avatarUrl || '')
                        }
                      }}
                      className="h-11 rounded-xl font-medium"
                    >
                      Đặt lại
                    </Button>
                    <Button
                      type="submit"
                      disabled={busy}
                      className="h-11 rounded-xl bg-gradient-to-r from-emerald-600 to-teal-600 px-6 font-semibold text-white shadow-md shadow-emerald-600/20 hover:from-emerald-700 hover:to-teal-700 active:scale-98 transition-all"
                    >
                      {busy ? (
                        <>
                          <RefreshCw size={16} className="animate-spin mr-2" />
                          Đang lưu thay đổi…
                        </>
                      ) : (
                        <>
                          <Save size={16} className="mr-2" />
                          Lưu thay đổi hồ sơ
                        </>
                      )}
                    </Button>
                  </div>
                </form>
              </div>
            </div>

            {/* Right 1 Col: Quick Info & Security Sidebar */}
            <div className="space-y-6">
              {/* Account Identifier Card */}
              <div className="rounded-2xl border border-slate-200/80 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900">
                <h4 className="text-sm font-bold uppercase tracking-wider text-slate-500 dark:text-slate-400">
                  Định danh tài khoản
                </h4>

                <div className="mt-4 space-y-3">
                  <div className="rounded-xl bg-slate-50 p-3 dark:bg-slate-800/60">
                    <span className="text-[11px] font-medium text-slate-400 block">MÃ NGƯỜI DÙNG (USER ID)</span>
                    <div className="flex items-center justify-between gap-2 mt-1">
                      <code className="text-xs font-mono font-semibold text-slate-700 dark:text-slate-300 truncate">
                        {user?.id || '—'}
                      </code>
                      <button
                        type="button"
                        onClick={copyUserId}
                        title="Sao chép User ID"
                        className="rounded-lg p-1.5 text-slate-400 hover:bg-white hover:text-emerald-600 dark:hover:bg-slate-700 shadow-sm transition-all"
                      >
                        {copiedId ? <Check size={14} className="text-emerald-600" /> : <Copy size={14} />}
                      </button>
                    </div>
                  </div>

                  <div className="rounded-xl bg-slate-50 p-3 dark:bg-slate-800/60">
                    <span className="text-[11px] font-medium text-slate-400 block">TRẠNG THÁI TÀI KHOẢN</span>
                    <div className="flex items-center gap-2 mt-1">
                      <span className="size-2 rounded-full bg-emerald-500 animate-pulse" />
                      <span className="text-xs font-bold text-emerald-700 dark:text-emerald-400 uppercase">
                        {user?.status || 'ACTIVE'}
                      </span>
                    </div>
                  </div>
                </div>
              </div>

              {/* Contact & Verification Card */}
              <div className="rounded-2xl border border-slate-200/80 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900">
                <div className="flex items-center justify-between">
                  <h4 className="text-sm font-bold uppercase tracking-wider text-slate-500 dark:text-slate-400">
                    Thông tin liên hệ
                  </h4>
                  <Link
                    href="/customer/profile/contact"
                    className="text-xs font-semibold text-emerald-600 hover:underline flex items-center gap-1"
                  >
                    Thay đổi
                    <ChevronRight size={13} />
                  </Link>
                </div>

                <div className="mt-4 space-y-3">
                  <div className="flex items-start gap-3 rounded-xl border border-slate-100 p-3.5 dark:border-slate-800">
                    <div className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-emerald-50 text-emerald-600 dark:bg-emerald-950/60">
                      <Mail size={16} />
                    </div>
                    <div className="min-w-0 flex-1">
                      <span className="text-[11px] font-medium text-slate-400 block">EMAIL ĐĂNG NHẬP</span>
                      <p className="text-xs font-semibold text-slate-800 dark:text-slate-200 truncate mt-0.5">
                        {user?.email || 'Chưa cung cấp'}
                      </p>
                      <div className="flex items-center gap-1 mt-1 text-[11px]">
                        {user?.emailVerified ? (
                          <span className="inline-flex items-center gap-1 text-emerald-600 font-medium">
                            <CheckCircle2 size={12} /> Đã xác minh
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1 text-amber-600 font-medium">
                            <AlertCircle size={12} /> Chưa xác minh
                          </span>
                        )}
                      </div>
                    </div>
                  </div>

                  <div className="flex items-start gap-3 rounded-xl border border-slate-100 p-3.5 dark:border-slate-800">
                    <div className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-blue-50 text-blue-600 dark:bg-blue-950/60">
                      <Phone size={16} />
                    </div>
                    <div className="min-w-0 flex-1">
                      <span className="text-[11px] font-medium text-slate-400 block">SỐ ĐIỆN THOẠI</span>
                      <p className="text-xs font-semibold text-slate-800 dark:text-slate-200 truncate mt-0.5">
                        {user?.phone || 'Chưa liên kết số điện thoại'}
                      </p>
                      <div className="flex items-center gap-1 mt-1 text-[11px]">
                        {user?.phoneVerified ? (
                          <span className="inline-flex items-center gap-1 text-emerald-600 font-medium">
                            <CheckCircle2 size={12} /> Đã xác minh
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1 text-slate-400 font-medium">
                            Chưa xác minh SMS
                          </span>
                        )}
                      </div>
                    </div>
                  </div>
                </div>

                <Link
                  href="/customer/profile/contact"
                  className="mt-4 flex w-full items-center justify-center gap-2 rounded-xl border border-slate-200 bg-slate-50 py-2.5 text-xs font-semibold text-slate-700 hover:bg-slate-100 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200 transition-colors"
                >
                  <Key size={13} />
                  Quản lý Email & Số điện thoại OTP
                </Link>
              </div>

              {/* Owner CTA Banner (If not owner) */}
              {!user?.roles?.includes('OWNER') && (
                <div className="relative overflow-hidden rounded-2xl bg-gradient-to-br from-amber-500 to-orange-600 p-6 text-white shadow-lg shadow-orange-600/15">
                  <div className="absolute -right-6 -bottom-6 size-28 rounded-full bg-white/10 blur-xl" />
                  <Building2 size={32} className="text-amber-200" />
                  <h4 className="mt-3 text-base font-bold">Bạn sở hữu sân thể thao?</h4>
                  <p className="mt-1 text-xs text-amber-100 leading-relaxed">
                    Đăng ký trở thành Đối tác Chủ sân để tiếp cận hàng nghìn người chơi thể thao tại Hà Nội ngay hôm nay.
                  </p>
                  <Link
                    href="/register?mode=owner"
                    className="mt-4 inline-flex items-center gap-1.5 rounded-xl bg-white px-4 py-2 text-xs font-bold text-amber-800 shadow-sm transition-all hover:bg-amber-50 active:scale-95"
                  >
                    Đăng ký làm Chủ sân
                    <ArrowRight size={13} />
                  </Link>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </RequireAuth>
  )
}
