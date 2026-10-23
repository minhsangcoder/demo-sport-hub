'use client'
import { ArrowRight, Key, Sparkles } from 'lucide-react'
import Link from 'next/link'
export function ProfileHero() {
  return (
    <div className="relative h-72 w-full overflow-hidden bg-gradient-to-r from-emerald-700 via-teal-700 to-slate-900 shadow-inner md:h-80">
      <div className="absolute inset-0 opacity-15 bg-[radial-gradient(#fff_1px,transparent_1px)] [background-size:18px_18px]" />
      <div className="absolute -left-20 -top-20 h-72 w-72 rounded-full bg-emerald-400/20 blur-3xl" />
      <div className="absolute -right-20 -bottom-20 h-80 w-80 rounded-full bg-teal-300/20 blur-3xl" />

      <div className="mx-auto flex h-full max-w-6xl items-end justify-between px-4 pb-20 sm:px-6 md:pb-24 lg:px-8">
        <div className="text-white">
          <span className="inline-flex items-center gap-1.5 rounded-full bg-white/15 px-3 py-1 text-xs font-medium text-emerald-100 backdrop-blur-md">
            <Sparkles size={13} className="text-emerald-300" />
            Hồ sơ tài khoản SportHub
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
  )
}
