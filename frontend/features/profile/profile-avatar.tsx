'use client'
import { Camera } from 'lucide-react'
import Image from 'next/image'
import type { useProfile } from './use-profile'
export function ProfileAvatar({
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
  >
}) {
  const {
    avatarUrl,
    fullName,
    setAvatarUrl,
    getInitials,
    user,
    setShowAvatarPicker,
    showAvatarPicker,
  } = model
  return (
    <div className="relative group shrink-0">
      <div className="relative size-24 sm:size-28 overflow-hidden rounded-2xl border-4 border-white bg-gradient-to-br from-emerald-500 to-teal-700 shadow-md ring-1 ring-slate-200 dark:border-slate-800 dark:ring-slate-700">
        {avatarUrl ? (
          <Image
            unoptimized
            width={112}
            height={112}
            src={avatarUrl}
            alt={fullName || 'Ảnh đại diện'}
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
  )
}
