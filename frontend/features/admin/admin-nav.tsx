'use client'
import Link from 'next/link'

export function AdminNav({ active }: { active: string }) {
  return (
    <nav
      aria-label="Quản trị nền tảng"
      className="my-6 flex gap-2 overflow-x-auto border-b border-slate-200 pb-4 dark:border-slate-800"
    >
      {[
        ['accounts', 'Tài khoản'],
        ['owner-applications', 'Đăng ký chủ cơ sở'],
        ['facilities', 'Cơ sở'],
        ['bookings', 'Lượt đặt'],
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
