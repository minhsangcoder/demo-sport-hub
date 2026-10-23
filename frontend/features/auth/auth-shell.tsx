'use client'
import type { Mode } from './auth-mode'
import { AuthShowcase } from './auth-showcase'
export function AuthShell({
  mode,
  isExpandedOwner,
  children,
}: {
  mode: Mode
  isExpandedOwner: boolean
  children: React.ReactNode
}) {
  return (
    <div
      className={`mx-auto w-full transition-all duration-500 ease-[cubic-bezier(0.16,1,0.3,1)] ${
        isExpandedOwner
          ? 'max-w-4xl'
          : 'max-w-5xl rounded-2xl sm:rounded-3xl border border-slate-200/80 bg-white/95 p-4 sm:p-6 lg:p-8 shadow-xl sm:shadow-2xl shadow-slate-900/5 backdrop-blur-xl dark:border-slate-800 dark:bg-slate-900/95'
      }`}
    >
      <div
        className={`grid w-full items-stretch gap-6 sm:gap-8 lg:gap-12 transition-all duration-300 ${
          isExpandedOwner
            ? 'rounded-2xl sm:rounded-3xl border border-slate-200/80 bg-white p-4 sm:p-8 lg:p-10 shadow-xl dark:border-slate-800 dark:bg-slate-900'
            : mode === 'login' || mode === 'register'
              ? 'lg:grid-cols-12'
              : 'max-w-md mx-auto'
        }`}
      >
        {/* Left Form Section */}
        <div
          className={`${
            isExpandedOwner
              ? 'w-full'
              : mode === 'login' || mode === 'register'
                ? 'lg:col-span-6 flex flex-col justify-center py-1 sm:py-2'
                : 'w-full'
          }`}
        >
          {children}
        </div>
        {!isExpandedOwner && (mode === 'login' || mode === 'register') && <AuthShowcase />}
      </div>
    </div>
  )
}
