'use client'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Eye, EyeOff, Lock, Mail, User } from 'lucide-react'
import Link from 'next/link'
import type { AuthModel } from './use-auth-form'

export function AuthAccountFields({ model }: { model: AuthModel }) {
  const {
    mode,
    busy,
    fieldErrors,
    showPassword,
    setShowPassword,
    rememberMe,
    setRememberMe,
    identifierInput,
    setIdentifierInput,
    passwordInput,
    setPasswordInput,
  } = model
  return (
    <>
      {/* Full Name for Register */}
      {mode === 'register' && (
        <div className="space-y-1.5 auth-slide-down">
          <label className="text-sm font-semibold text-slate-700 dark:text-slate-200">
            Họ và tên
          </label>
          <div className="relative">
            <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3.5 text-slate-400">
              <User size={18} />
            </div>
            <Input
              name="fullName"
              required
              disabled={busy}
              minLength={2}
              maxLength={120}
              autoComplete="name"
              placeholder="Nguyễn Văn An"
              className="h-12 pl-10 transition-all duration-200"
              aria-invalid={Boolean(fieldErrors.fullName)}
            />
          </div>
          {fieldErrors.fullName && (
            <span className="text-xs font-medium text-rose-600">{fieldErrors.fullName}</span>
          )}
        </div>
      )}

      {/* Email / Phone identifier */}
      {['login', 'register', 'forgot'].includes(mode) && (
        <div className="space-y-1.5">
          <label className="text-sm font-semibold text-slate-700 dark:text-slate-200">
            Thư điện tử hoặc số điện thoại
          </label>
          <div className="relative">
            <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3.5 text-slate-400">
              <Mail size={18} />
            </div>
            <Input
              name="identifier"
              required
              disabled={busy}
              autoComplete="username"
              value={identifierInput}
              onChange={(e) => setIdentifierInput(e.target.value)}
              placeholder="name@example.com hoặc số điện thoại"
              className="h-12 pl-10"
              aria-invalid={Boolean(fieldErrors.identifier)}
            />
          </div>
          {fieldErrors.identifier && (
            <span className="text-xs font-medium text-rose-600">{fieldErrors.identifier}</span>
          )}
        </div>
      )}

      {/* Password */}
      {['login', 'register', 'reset'].includes(mode) && (
        <div className="space-y-1.5">
          <label className="text-sm font-semibold text-slate-700 dark:text-slate-200">
            {mode === 'reset' ? 'Mật khẩu mới' : 'Mật khẩu'}
          </label>
          <div className="relative">
            <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3.5 text-slate-400">
              <Lock size={18} />
            </div>
            <Input
              name="password"
              required
              type={showPassword ? 'text' : 'password'}
              disabled={busy}
              value={passwordInput}
              onChange={(e) => setPasswordInput(e.target.value)}
              minLength={mode === 'login' ? 1 : 8}
              maxLength={72}
              autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
              placeholder={mode === 'register' ? 'Tối thiểu 8 ký tự' : 'Nhập mật khẩu của bạn'}
              className="h-12 pl-10 pr-11"
              aria-invalid={Boolean(fieldErrors.password)}
            />
            <button
              type="button"
              onClick={() => setShowPassword(!showPassword)}
              tabIndex={-1}
              className="absolute inset-y-0 right-0 flex items-center pr-3.5 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 transition-colors cursor-pointer"
            >
              {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
            </button>
          </div>
          {fieldErrors.password && (
            <span className="text-xs font-medium text-rose-600">{fieldErrors.password}</span>
          )}
        </div>
      )}

      {/* Remember Me & Forgot Password Row (Login mode) */}
      {mode === 'login' && (
        <div className="flex items-center justify-between pt-0.5 auth-fade-in text-xs sm:text-sm">
          <label className="flex items-center gap-2.5 cursor-pointer text-slate-600 hover:text-slate-900 dark:text-slate-300 dark:hover:text-white font-medium select-none group transition-colors">
            <Checkbox checked={rememberMe} onCheckedChange={(checked) => setRememberMe(checked)} />
            <span>Ghi nhớ đăng nhập</span>
          </label>
          <Link
            href="/forgot-password"
            className="font-semibold text-emerald-600 hover:text-emerald-700 dark:text-emerald-400 hover:underline transition-colors"
          >
            Quên mật khẩu?
          </Link>
        </div>
      )}
    </>
  )
}
