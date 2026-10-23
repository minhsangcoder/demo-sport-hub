'use client'
import type { useAccounts } from '@/features/admin/use-accounts'
import { Shield, UserCheck, UserX, Users } from 'lucide-react'
export function AccountStats({
  model,
}: {
  model: Pick<
    ReturnType<typeof useAccounts>,
    'r' | 'totalUsers' | 'activeUsers' | 'lockedUsers' | 'privilegedUsers'
  >
}) {
  const { r, totalUsers, activeUsers, lockedUsers, privilegedUsers } = model
  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <div className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
        <div className="flex items-center justify-between">
          <span className="text-xs font-bold uppercase tracking-wider text-slate-500">
            Tài khoản kết quả
          </span>
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
          <span className="text-xs font-bold uppercase tracking-wider text-slate-500">
            Đang hoạt động
          </span>
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
          <span className="text-xs font-bold uppercase tracking-wider text-slate-500">
            Bị khóa / Tạm dừng
          </span>
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
          <span className="text-xs font-bold uppercase tracking-wider text-slate-500">
            Chủ sân & Quản trị
          </span>
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
        <p className="mt-2 text-xs text-slate-500">
          Tài khoản có quyền chủ cơ sở hoặc quản trị viên
        </p>
      </div>
    </div>
  )
}
