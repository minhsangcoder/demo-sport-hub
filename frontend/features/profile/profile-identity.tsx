'use client'
import type { useProfile } from '@/features/profile/use-profile'
import { PRESET_AVATARS } from '@/features/profile/use-profile-shared'
import { Building2, CheckCircle2, Shield, ShieldCheck, Trophy } from 'lucide-react'
import Image from 'next/image'
import Link from 'next/link'
export function ProfileIdentity({
  model,
}: {
  model: Pick<
    ReturnType<typeof useProfile>,
    | 'avatarUrl'
    | 'fullName'
    | 'setAvatarUrl'
    | 'getInitials'
    | 'user'
    | 'setShowAvatarPicker'
    | 'showAvatarPicker'
    | 'getRoleBadge'
  >
}) {
  const {
    avatarUrl,
    fullName,
    setAvatarUrl,
    user,
    setShowAvatarPicker,
    showAvatarPicker,
    getRoleBadge,
  } = model
  return (
    <div className="-mt-16 relative z-10 rounded-2xl border border-slate-200/80 bg-white p-5 shadow-xl shadow-slate-900/5 backdrop-blur-xl dark:border-slate-800 dark:bg-slate-900 sm:p-6 lg:p-8">
      <div className="flex flex-col gap-6 md:flex-row md:items-center md:justify-between">
        {/* Left: Avatar + Identity */}
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center">
          {/* Avatar with Camera Overlay */}
          <ProfileAvatar model={model} />

          {/* Name, Roles, ID */}
          <div className="space-y-1.5">
            <div className="flex flex-wrap items-center gap-2">
              <h2 className="text-xl font-bold text-slate-900 dark:text-white sm:text-2xl">
                {fullName || user?.fullName || 'Người dùng SportHub'}
              </h2>
              {user?.status === 'ACTIVE' && (
                <span title="Tài khoản đã kích hoạt" className="flex items-center text-emerald-600">
                  <CheckCircle2
                    size={18}
                    className="fill-emerald-50 text-emerald-600 dark:fill-emerald-950"
                  />
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
              Trang quản trị viên
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
                <Image
                  unoptimized
                  width={48}
                  height={48}
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
  )
}

import { ProfileAvatar } from './profile-avatar'
