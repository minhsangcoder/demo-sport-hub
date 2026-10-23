'use client'
import { titles } from './auth-mode'
import type { AuthModel } from './use-auth-form'

export function AuthHeading({ model }: { model: AuthModel }) {
  const { mode, switchMode, applyAsOwner } = model
  return (
    <>
      {/* Segmented Control for Login/Register with Fluid Sliding Indicator */}
      {(mode === 'login' || mode === 'register') && !applyAsOwner && (
        <div className="relative mb-5 sm:mb-6 flex rounded-xl sm:rounded-2xl bg-slate-100 p-1 sm:p-1.5 text-xs sm:text-sm font-semibold dark:bg-slate-800/80 select-none">
          {/* Sliding Background Pill Indicator */}
          <div
            className={`absolute top-1 sm:top-1.5 bottom-1 sm:bottom-1.5 w-[calc(50%-4px)] sm:w-[calc(50%-6px)] rounded-lg sm:rounded-xl bg-white shadow-md shadow-slate-900/10 transition-all duration-300 ease-[cubic-bezier(0.16,1,0.3,1)] dark:bg-slate-900 ${
              mode === 'login'
                ? 'left-1 sm:left-1.5'
                : 'left-[calc(50%+2px)] sm:left-[calc(50%+3px)]'
            }`}
          />
          <button
            type="button"
            onClick={() => switchMode('login')}
            className={`relative z-10 flex-1 flex items-center justify-center rounded-lg sm:rounded-xl py-2 sm:py-2.5 transition-colors duration-200 cursor-pointer ${
              mode === 'login'
                ? 'text-slate-900 font-bold dark:text-white'
                : 'text-slate-500 hover:text-slate-800 dark:hover:text-slate-200'
            }`}
          >
            Đăng nhập
          </button>
          <button
            type="button"
            onClick={() => switchMode('register')}
            className={`relative z-10 flex-1 flex items-center justify-center rounded-lg sm:rounded-xl py-2 sm:py-2.5 transition-colors duration-200 cursor-pointer ${
              mode === 'register'
                ? 'text-slate-900 font-bold dark:text-white'
                : 'text-slate-500 hover:text-slate-800 dark:hover:text-slate-200'
            }`}
          >
            Đăng ký
          </button>
        </div>
      )}

      {/* Form Header with animated entrance */}
      <div key={mode} className="mb-5 sm:mb-6 space-y-1.5 sm:space-y-2 auth-fade-in">
        <h1 className="text-xl sm:text-2xl lg:text-3xl font-extrabold tracking-tight text-slate-900 dark:text-white">
          {titles[mode]}
        </h1>
        <p className="text-xs sm:text-sm text-slate-500 dark:text-slate-400">
          {mode === 'login'
            ? 'Đăng nhập để đặt sân nhanh, quản lý lịch đấu và nhận ưu đãi.'
            : mode === 'register'
              ? 'Tạo tài khoản SportHub để tham gia cộng đồng thể thao sôi động.'
              : mode === 'verify'
                ? 'Nhập mã 6 chữ số gửi về thư điện tử hoặc điện thoại để kích hoạt.'
                : 'Nhập thông tin xác thực để tiếp tục.'}
        </p>
      </div>
    </>
  )
}
