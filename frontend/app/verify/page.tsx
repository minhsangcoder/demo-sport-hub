import { Suspense } from 'react'
import { AuthForm } from '@/features/auth/auth-form'

export default function VerifyPage() {
  return (
    <div className="relative min-h-[calc(100vh-64px)] flex items-center justify-center px-3 py-4 sm:px-6 sm:py-8 lg:px-8 bg-gradient-to-b from-slate-50 via-white to-slate-100/60 dark:from-slate-950 dark:via-slate-900 dark:to-slate-950 overflow-hidden">
      <div className="pointer-events-none absolute -top-40 -right-40 size-96 rounded-full bg-emerald-500/10 blur-3xl" />
      <div className="pointer-events-none absolute -bottom-40 -left-40 size-96 rounded-full bg-teal-500/10 blur-3xl" />
      <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(#e2e8f0_1px,transparent_1px)] dark:bg-[radial-gradient(#1e293b_1px,transparent_1px)] [background-size:24px_24px] opacity-60" />

      <div className="relative z-10 w-full max-w-md py-2 sm:py-6">
        <Suspense fallback={<div className="text-center py-20 text-slate-400 animate-pulse font-medium">Đang tải biểu mẫu…</div>}>
          <AuthForm mode="verify" />
        </Suspense>
      </div>
    </div>
  )
}
