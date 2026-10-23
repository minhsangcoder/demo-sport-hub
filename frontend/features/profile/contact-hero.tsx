'use client'
import { ArrowLeft } from 'lucide-react'
import Link from 'next/link'
export function ContactHero() {
  return (
    <div className="relative h-48 w-full overflow-hidden bg-gradient-to-r from-emerald-700 via-teal-700 to-slate-900 shadow-inner">
      <div className="absolute inset-0 opacity-15 bg-[radial-gradient(#fff_1px,transparent_1px)] [background-size:18px_18px]" />
      <div className="mx-auto flex h-full max-w-6xl items-end justify-between px-4 pb-6 sm:px-6 lg:px-8">
        <div className="text-white">
          <Link
            href="/customer/profile"
            className="inline-flex items-center gap-1.5 text-xs font-semibold text-emerald-200 hover:text-white transition-colors mb-2"
          >
            <ArrowLeft size={14} />
            Quay lại Hồ sơ cá nhân
          </Link>
          <h1 className="text-2xl font-bold tracking-tight sm:text-3xl">
            Bảo mật & Thông tin liên hệ
          </h1>
        </div>
      </div>
    </div>
  )
}
