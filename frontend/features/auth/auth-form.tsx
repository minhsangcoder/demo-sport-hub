'use client'

import Link from 'next/link'
import Image from 'next/image'
import { useRouter, useSearchParams } from 'next/navigation'
import { useEffect, useRef, useState } from 'react'
import { OwnerSignupFields } from './owner-signup-fields'
import { postLoginPath, rememberGoogleNext } from './google-auth-state'
import { legalFrom } from '@/features/owner-application/legal-fields'
import { facilityFrom } from '@/features/facility/facility-fields'
import { validateSignupFiles } from '@/features/facility/document-file-field'
import type { FacilityLocation } from '@/features/facility/location-picker'
import { authApi, ApiError } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Mail,
  Lock,
  Eye,
  EyeOff,
  User,
  ArrowRight,
  CheckCircle2,
  AlertCircle,
  Building2,
  Users,
  Repeat,
  CalendarCheck2,
  KeyRound,
  ShieldCheck,
  Zap,
  Check,
  Star,
  MapPin,
} from 'lucide-react'

type Mode = 'login' | 'register' | 'verify' | 'forgot' | 'reset'

const titles = {
  login: 'Chào mừng trở lại',
  register: 'Đăng ký tài khoản SportHub',
  verify: 'Xác minh tài khoản',
  forgot: 'Khôi phục mật khẩu',
  reset: 'Đặt lại mật khẩu mới',
}

function GoogleLogo({ className = 'size-5' }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" aria-hidden="true">
      <path
        fill="#4285F4"
        d="M23.745 12.27c0-.7-.06-1.4-.19-2.07H12v4.51h6.6c-.29 1.52-1.14 2.8-2.4 3.65v3.05h3.88c2.27-2.09 3.66-5.17 3.66-9.14z"
      />
      <path
        fill="#34A853"
        d="M12 24c3.24 0 5.95-1.08 7.93-2.91l-3.88-3.05c-1.08.72-2.45 1.16-4.05 1.16-3.12 0-5.77-2.1-6.72-4.93H1.24v3.15C3.26 21.36 7.33 24 12 24z"
      />
      <path
        fill="#FBBC05"
        d="M5.28 14.27c-.25-.72-.38-1.49-.38-2.27s.13-1.55.38-2.27V6.58H1.24C.45 8.15 0 9.92 0 12s.45 3.85 1.24 5.42l4.04-3.15z"
      />
      <path
        fill="#EA4335"
        d="M12 4.75c1.77 0 3.35.61 4.6 1.8l3.42-3.42C17.95 1.19 15.24 0 12 0 7.33 0 3.26 2.64 1.24 6.58l4.04 3.15c.95-2.83 3.6-4.98 6.72-4.98z"
      />
    </svg>
  )
}

export function AuthForm({ mode: initialMode = 'login' }: { mode?: Mode }) {
  const router = useRouter()
  const params = useSearchParams()
  const [mode, setMode] = useState<Mode>(initialMode)

  useEffect(() => {
    setMode(initialMode)
  }, [initialMode])

  useEffect(() => {
    const handlePopState = () => {
      const path = window.location.pathname
      if (path === '/login') setMode('login')
      else if (path === '/register') setMode('register')
    }
    window.addEventListener('popstate', handlePopState)
    return () => window.removeEventListener('popstate', handlePopState)
  }, [])

  function switchMode(newMode: 'login' | 'register') {
    if (newMode === mode) return
    setMode(newMode)
    setError('')
    setSuccess('')
    setFieldErrors({})
    window.history.pushState(null, '', `/${newMode}${window.location.search}`)
  }

  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [showPassword, setShowPassword] = useState(false)
  const [rememberMe, setRememberMe] = useState(true)

  // Form controlled inputs for demo quick fill
  const [identifierInput, setIdentifierInput] = useState('')
  const [passwordInput, setPasswordInput] = useState('')
  const [demoNotice, setDemoNotice] = useState('')

  const [applyAsOwner, setApplyAsOwner] = useState(false)
  const [ownerVisited, setOwnerVisited] = useState(false)
  const [location, setLocation] = useState<FacilityLocation>(null)
  const signupRetry = useRef<{ fingerprint: string; key: string } | null>(null)

  const googleError =
    mode === 'login'
      ? {
          google_not_configured:
            'Đăng nhập Google chưa được cấu hình. Bạn có thể dùng email hoặc số điện thoại để tiếp tục.',
          google_email_unverified:
            'Xác minh email bằng phương thức đăng nhập hiện tại trước khi liên kết Google.',
          google_account_unavailable:
            'Không thể liên kết tài khoản Google với tài khoản SportHub này.',
        }[params.get('authError') ?? ''] ??
        (params.has('authError')
          ? 'Không thể đăng nhập bằng Google. Vui lòng thử lại.'
          : '')
      : ''

  function fillDemoAccount(email: string) {
    setIdentifierInput(email)
    setPasswordInput('')
    setDemoNotice(`Đã điền tài khoản: ${email}`)
    setError('')
    setTimeout(() => setDemoNotice(''), 4000)
  }

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy) return
    setBusy(true)
    setError('')
    setSuccess('')
    setFieldErrors({})
    const values = new FormData(event.currentTarget)
    const value = (name: string) => String(values.get(name) ?? '').trim()

    try {
      if (mode === 'login') {
        const session = await authApi.login(value('identifier'), String(values.get('password') ?? ''))
        const next = params.get('next')
        if (
          session.user.roles.includes('STAFF') &&
          !session.user.roles.includes('OWNER') &&
          !session.user.roles.includes('ADMIN')
        ) {
          router.push(next && next !== '/' ? postLoginPath(next) : '/staff/bookings')
        } else {
          router.push(postLoginPath(next))
        }
      } else if (mode === 'register') {
        const contact = value('identifier')
        const account = {
          fullName: value('fullName'),
          password: String(values.get('password') ?? ''),
          ...(contact.includes('@') ? { email: contact } : { phone: contact }),
        }
        let result
        if (applyAsOwner) {
          if (!location) throw new Error('Chọn vị trí cơ sở trên bản đồ trước khi gửi.')
          try {
            validateSignupFiles(values)
          } catch (fileError) {
            const message = fileError instanceof Error ? fileError.message : 'File không hợp lệ'
            const name = message.startsWith('Giấy tờ định danh')
              ? 'identityDocument'
              : message.startsWith('Giấy tờ địa điểm')
              ? 'locationDocument'
              : 'facilityImage'
            setFieldErrors({ [name]: message })
            throw fileError
          }
          const setup = {
            courtCode: value('courtCode'),
            courtName: value('courtName'),
            sportCategoryId: value('sportCategoryId'),
            days: values.getAll('days').map(Number),
            opensAt: value('opensAt'),
            closesAt: value('closesAt'),
            slotMinutes: Number(value('slotMinutes')),
            pricePerSlot: Number(value('pricePerSlot')),
          }
          if (!setup.days.length) throw new Error('Chọn ít nhất một ngày mở cửa.')
          if (setup.opensAt >= setup.closesAt) throw new Error('Giờ mở cửa phải trước giờ đóng cửa.')
          const body = {
            ...account,
            applyAsOwner: true,
            ownerApplication: {
              legal: legalFrom(values),
              facility: facilityFrom(values, location, 'facilityName'),
              setup,
            },
          }
          const fingerprint =
            JSON.stringify(body) +
            ['identityDocument', 'locationDocument', 'facilityImage']
              .map((name) => {
                const f = values.get(name) as File
                return `${f.name}:${f.size}:${f.lastModified}`
              })
              .join('|')
          if (signupRetry.current?.fingerprint !== fingerprint)
            signupRetry.current = { fingerprint, key: crypto.randomUUID() }
          const multipart = new FormData()
          multipart.set('request', new Blob([JSON.stringify(body)], { type: 'application/json' }))
          for (const name of ['identityDocument', 'locationDocument', 'facilityImage'])
            multipart.set(name, values.get(name) as File)
          result = await authApi.registerOwner(multipart, signupRetry.current!.key)
        } else {
          result = await authApi.register(account)
        }
        const query = new URLSearchParams({ challengeId: result.verificationChallengeId })
        if (result.ownerApplication) {
          query.set('ownerApplicationId', result.ownerApplication.id)
          query.set('ownerState', result.ownerApplication.state)
        }
        if (result.accountStatus === 'ACTIVE') {
          router.push(
            result.ownerApplication
              ? `/login?next=${encodeURIComponent(`/owner/application/${result.ownerApplication.id}`)}`
              : '/login'
          )
        } else {
          router.push(`/verify?${query}`)
        }
      } else if (mode === 'verify') {
        await authApi.verify(
          params.get('token')
            ? { verificationToken: params.get('token')! }
            : { challengeId: value('challengeId'), code: value('code') }
        )
        setSuccess('Xác minh thành công. Bạn có thể đăng nhập ngay bây giờ.')
      } else if (mode === 'forgot') {
        await authApi.forgot(value('identifier'))
        setSuccess('Nếu tài khoản hợp lệ, hướng dẫn đã được gửi qua email hoặc SMS đã cấu hình.')
      } else {
        await authApi.reset({
          challengeId: value('challengeId'),
          code: value('code'),
          newPassword: String(values.get('password') ?? ''),
        })
        setSuccess('Mật khẩu đã được cập nhật thành công. Vui lòng đăng nhập lại.')
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Có lỗi xảy ra.')
      if (err instanceof ApiError) {
        setFieldErrors(
          Object.fromEntries(
            err.fieldErrors.map((item) => {
              const field = item.field.replace(/^ownerApplication\.(legal|facility|setup)\./, '')
              return [['email', 'phone'].includes(item.field) ? 'identifier' : field, item.message]
            })
          )
        )
      }
    } finally {
      setBusy(false)
    }
  }

  const isExpandedOwner = mode === 'register' && applyAsOwner

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
          {/* Segmented Control for Login/Register with Fluid Sliding Indicator */}
          {(mode === 'login' || mode === 'register') && !applyAsOwner && (
            <div className="relative mb-5 sm:mb-6 flex rounded-xl sm:rounded-2xl bg-slate-100 p-1 sm:p-1.5 text-xs sm:text-sm font-semibold dark:bg-slate-800/80 select-none">
              {/* Sliding Background Pill Indicator */}
              <div
                className={`absolute top-1 sm:top-1.5 bottom-1 sm:bottom-1.5 w-[calc(50%-4px)] sm:w-[calc(50%-6px)] rounded-lg sm:rounded-xl bg-white shadow-md shadow-slate-900/10 transition-all duration-300 ease-[cubic-bezier(0.16,1,0.3,1)] dark:bg-slate-900 ${
                  mode === 'login' ? 'left-1 sm:left-1.5' : 'left-[calc(50%+2px)] sm:left-[calc(50%+3px)]'
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
                ? 'Nhập mã 6 chữ số gửi về email hoặc điện thoại để kích hoạt.'
                : 'Nhập thông tin xác thực để tiếp tục.'}
            </p>
          </div>

          <form onSubmit={submit} className="space-y-4">
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
                  Email hoặc số điện thoại
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
                  <Checkbox
                    checked={rememberMe}
                    onCheckedChange={(checked) => setRememberMe(checked)}
                  />
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

            {/* Owner Signup Switcher in Register */}
            {mode === 'register' && (
              <div className="space-y-4 pt-1 auth-slide-down">
                <label className="flex items-start gap-3.5 rounded-2xl border border-emerald-200 bg-emerald-50/70 p-4 text-sm font-medium text-emerald-950 dark:border-emerald-900/60 dark:bg-emerald-950/30 dark:text-emerald-100 cursor-pointer transition-all duration-200 hover:bg-emerald-50 hover:border-emerald-300 group">
                  <div className="mt-0.5">
                    <Checkbox
                      checked={applyAsOwner}
                      disabled={busy}
                      onCheckedChange={(checked) => {
                        setApplyAsOwner(checked)
                        if (checked) setOwnerVisited(true)
                      }}
                    />
                  </div>
                  <div>
                    <div className="font-bold flex items-center gap-1.5">
                      <Building2 size={16} className="text-emerald-600" />
                      Đăng ký trở thành chủ sân (Facility Owner)
                    </div>
                    <p className="text-xs text-emerald-800/80 dark:text-emerald-300/80 font-normal mt-0.5">
                      Khai báo thông tin cơ sở thể thao và cấu hình sân ban đầu để gửi hồ sơ thẩm định.
                    </p>
                  </div>
                </label>

                {ownerVisited && (
                  <div
                    className={`transition-all duration-500 ease-in-out overflow-hidden ${
                      applyAsOwner ? 'max-h-[3500px] opacity-100 pt-2' : 'max-h-0 opacity-0'
                    }`}
                  >
                    <fieldset disabled={!applyAsOwner || busy} className="min-w-0">
                      <OwnerSignupFields
                        location={location}
                        onLocationChange={setLocation}
                        busy={busy || !applyAsOwner}
                        fields={fieldErrors}
                      />
                    </fieldset>
                  </div>
                )}
              </div>
            )}

            {/* Verify State Info */}
            {mode === 'verify' && params.get('ownerApplicationId') && (
              <div
                role="status"
                className="rounded-2xl border border-emerald-200 bg-emerald-50/80 p-4 text-xs sm:text-sm text-emerald-900 dark:border-emerald-900/60 dark:bg-emerald-950/40 dark:text-emerald-200"
              >
                Tài khoản đã được tạo.{' '}
                {params.get('ownerState') === 'DRAFT'
                  ? 'Hồ sơ cơ sở đang là nháp; sau xác minh, bạn có thể hoàn thiện hồ sơ.'
                  : params.get('ownerState') === 'SUBMITTING'
                  ? 'Hồ sơ đang được gửi lại an toàn.'
                  : 'Hồ sơ cơ sở đầu tiên đã gửi để Admin thẩm định.'}{' '}
                Xác minh liên hệ để đăng nhập và theo dõi tiến độ.
              </div>
            )}

            {/* Verify & Reset Code Input */}
            {['verify', 'reset'].includes(mode) && !params.get('token') && (
              <div className="space-y-4">
                <div className="space-y-1.5">
                  <label className="text-sm font-semibold text-slate-700 dark:text-slate-200">
                    Mã yêu cầu xác minh (Challenge ID)
                  </label>
                  <Input
                    name="challengeId"
                    defaultValue={params.get('challengeId') ?? ''}
                    readOnly={Boolean(params.get('challengeId'))}
                    className="h-11 font-mono text-xs"
                    required
                  />
                </div>
                <div className="space-y-1.5">
                  <label className="text-sm font-semibold text-slate-700 dark:text-slate-200">
                    Mã xác minh 6 chữ số
                  </label>
                  <div className="relative">
                    <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3.5 text-slate-400">
                      <KeyRound size={18} />
                    </div>
                    <Input
                      name="code"
                      pattern="[0-9]{6}"
                      inputMode="numeric"
                      maxLength={6}
                      autoComplete="one-time-code"
                      placeholder="123456"
                      className="h-12 pl-10 text-center font-mono text-lg tracking-widest"
                      required
                    />
                  </div>
                  <p className="text-xs text-slate-400">
                    Mã xác minh đã được gửi qua email hoặc SMS đã đăng ký.
                  </p>
                </div>
              </div>
            )}

            {/* Error Message Alert */}
            {(error || googleError) && (
              <div
                role="alert"
                className="flex items-start gap-2.5 rounded-2xl border border-rose-200 bg-rose-50 p-3.5 text-xs sm:text-sm text-rose-800 dark:border-rose-900/60 dark:bg-rose-950/40 dark:text-rose-300"
              >
                <AlertCircle size={18} className="shrink-0 text-rose-600 mt-0.5" />
                <span>{error || googleError}</span>
              </div>
            )}

            {/* Success Message Alert */}
            {success && (
              <div
                role="status"
                className="flex items-start gap-2.5 rounded-2xl border border-emerald-200 bg-emerald-50 p-3.5 text-xs sm:text-sm text-emerald-800 dark:border-emerald-900/60 dark:bg-emerald-950/40 dark:text-emerald-300"
              >
                <CheckCircle2 size={18} className="shrink-0 text-emerald-600 mt-0.5" />
                <div>
                  <span>{success} </span>
                  <Link
                    className="font-bold underline text-emerald-900 dark:text-emerald-200 ml-1"
                    href={
                      mode === 'verify' && /^[0-9a-f-]{36}$/.test(params.get('ownerApplicationId') ?? '')
                        ? `/login?next=${encodeURIComponent(`/owner/application/${params.get('ownerApplicationId')}`)}`
                        : '/login'
                    }
                  >
                    Đăng nhập ngay
                  </Link>
                </div>
              </div>
            )}

            {/* Demo Notice Banner */}
            {demoNotice && (
              <div className="flex items-center gap-2 rounded-xl bg-slate-100 border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-800 dark:bg-slate-800 dark:border-slate-700 dark:text-slate-200 animate-in fade-in">
                <CheckCircle2 size={14} className="text-emerald-600" />
                <span>{demoNotice}</span>
              </div>
            )}

            {/* Primary Submit Button */}
            <Button
              className="h-12 w-full rounded-2xl bg-gradient-to-r from-emerald-600 via-emerald-500 to-teal-600 hover:from-emerald-500 hover:to-teal-500 text-white font-bold text-sm sm:text-base shadow-lg shadow-emerald-600/25 active:scale-[0.98] hover:shadow-emerald-600/35 transition-all duration-200 flex items-center justify-center gap-2 cursor-pointer group"
              type="submit"
              disabled={busy}
            >
              {busy ? (
                <>
                  <div className="size-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
                  <span>Đang xử lý…</span>
                </>
              ) : (
                <span key={mode} className="inline-flex items-center gap-2 auth-fade-in">
                  <span>
                    {mode === 'login'
                      ? 'Đăng nhập ngay'
                      : mode === 'register'
                      ? 'Tạo tài khoản'
                      : mode === 'verify'
                      ? 'Xác minh tài khoản'
                      : 'Tiếp tục'}
                  </span>
                  <ArrowRight size={17} className="transition-transform duration-200 group-hover:translate-x-1" />
                </span>
              )}
            </Button>

            {/* Google OAuth & Divider */}
            {(mode === 'login' || (mode === 'register' && !applyAsOwner)) && (
              <div className="space-y-4 pt-1 auth-fade-in">
                <div className="relative flex items-center justify-center">
                  <div className="absolute inset-0 flex items-center">
                    <div className="w-full border-t border-slate-200 dark:border-slate-800" />
                  </div>
                  <span className="relative bg-white px-3 text-xs font-medium uppercase tracking-wider text-slate-400 dark:bg-slate-900">
                    hoặc tiếp tục với
                  </span>
                </div>

                <a
                  href="/api/v1/auth/oauth2/authorization/google"
                  onClick={(event) => {
                    if (busy) {
                      event.preventDefault()
                      return
                    }
                    rememberGoogleNext(params.get('next'))
                  }}
                  aria-disabled={busy}
                  tabIndex={busy ? -1 : undefined}
                  className={`flex h-12 w-full items-center justify-center gap-3 rounded-2xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 text-sm font-semibold text-slate-700 dark:text-slate-200 shadow-xs transition-all hover:bg-slate-50 dark:hover:bg-slate-700/80 active:scale-[0.99] ${
                    busy ? 'pointer-events-none opacity-50' : ''
                  }`}
                >
                  <GoogleLogo className="size-5" />
                  <span>{mode === 'register' ? 'Đăng ký với Google' : 'Đăng nhập với Google'}</span>
                </a>
              </div>
            )}

            {/* Quick Demo Credentials Helpers */}
            {mode === 'login' && (
              <div className="rounded-2xl border border-dashed border-slate-200 bg-slate-50/70 p-3.5 space-y-2 dark:border-slate-800 dark:bg-slate-900/50 auth-fade-in">
                <div className="flex items-center justify-between text-slate-500 dark:text-slate-400">
                  <span className="flex items-center gap-1.5 text-xs font-bold text-slate-700 dark:text-slate-300">
                    <Zap size={14} className="text-emerald-600 dark:text-emerald-400" />
                    Tài khoản thử nghiệm nhanh (1-chạm):
                  </span>
                  <span className="rounded-md bg-slate-200/80 dark:bg-slate-800 px-1.5 py-0.5 text-[10px] font-bold text-slate-700 dark:text-slate-300">
                    Demo Mode
                  </span>
                </div>
                <div className="grid grid-cols-3 gap-1.5 sm:gap-2">
                  <button
                    type="button"
                    onClick={() => fillDemoAccount('demo.anh@sporthub.local')}
                    className="flex items-center justify-center gap-1 rounded-xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 p-2 sm:p-2.5 text-center text-[11px] sm:text-xs font-semibold text-slate-700 dark:text-slate-200 shadow-2xs hover:border-emerald-500 hover:text-emerald-600 transition-all active:scale-95 cursor-pointer truncate"
                  >
                    <User size={13} className="text-emerald-600 shrink-0" />
                    <span className="truncate">Khách</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => fillDemoAccount('demo.owner.hn-pickleball@sporthub.local')}
                    className="flex items-center justify-center gap-1 rounded-xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 p-2 sm:p-2.5 text-center text-[11px] sm:text-xs font-semibold text-slate-700 dark:text-slate-200 shadow-2xs hover:border-emerald-500 hover:text-emerald-600 transition-all active:scale-95 cursor-pointer truncate"
                  >
                    <Building2 size={13} className="text-emerald-600 shrink-0" />
                    <span className="truncate">Chủ sân</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => fillDemoAccount('admin@sporthub.local')}
                    className="flex items-center justify-center gap-1 rounded-xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 p-2 sm:p-2.5 text-center text-[11px] sm:text-xs font-semibold text-slate-700 dark:text-slate-200 shadow-2xs hover:border-emerald-500 hover:text-emerald-600 transition-all active:scale-95 cursor-pointer truncate"
                  >
                    <ShieldCheck size={13} className="text-emerald-600 shrink-0" />
                    <span className="truncate">Admin</span>
                  </button>
                </div>
              </div>
            )}

            {/* Bottom Nav Links */}
            <div className="flex items-center justify-center pt-2 text-xs sm:text-sm font-semibold">
              {mode === 'login' || mode === 'register' ? (
                <button
                  type="button"
                  onClick={() => switchMode(mode === 'login' ? 'register' : 'login')}
                  className="text-emerald-600 hover:text-emerald-700 dark:text-emerald-400 hover:underline cursor-pointer inline-flex items-center gap-1.5 transition-colors group"
                >
                  <span>{mode === 'login' ? 'Chưa có tài khoản? Đăng ký ngay' : 'Đã có tài khoản? Quay lại đăng nhập'}</span>
                  <ArrowRight size={14} className="transition-transform duration-200 group-hover:translate-x-1" />
                </button>
              ) : (
                <Link
                  href="/login"
                  className="text-emerald-600 hover:text-emerald-700 dark:text-emerald-400 hover:underline inline-flex items-center gap-1"
                >
                  Quay lại đăng nhập
                </Link>
              )}
            </div>
          </form>
        </div>

        {/* Right Visual Showcase Panel (Desktop Only, for Login & Regular Register) */}
        {!isExpandedOwner && (mode === 'login' || mode === 'register') && (
          <div className="relative hidden w-full overflow-hidden rounded-3xl bg-slate-900 lg:col-span-6 lg:flex lg:flex-col lg:justify-between p-8 xl:p-10 text-white min-h-[580px]">
            {/* Background image & deep gradients */}
            <div className="absolute inset-0 z-0">
              <Image
                src="/images/hero-pickleball.png"
                alt="SportHub Court"
                fill
                className="object-cover object-center opacity-40 scale-105 transition-transform duration-1000 hover:scale-100"
                priority
              />
              <div className="absolute inset-0 bg-gradient-to-t from-slate-950 via-slate-900/70 to-emerald-950/60" />
              <div className="absolute inset-0 bg-[radial-gradient(circle_at_top_right,_var(--tw-gradient-stops))] from-emerald-500/20 via-transparent to-transparent" />
            </div>

            {/* Top Bar */}
            <div className="relative z-10 flex items-center justify-between gap-3">
              <div className="inline-flex items-center gap-2 rounded-full border border-white/15 bg-white/10 px-3 py-1.5 text-xs font-medium text-slate-200 backdrop-blur-md whitespace-nowrap">
                <span className="size-1.5 rounded-full bg-emerald-400 animate-pulse" />
                <span>Đặt sân trực tuyến</span>
              </div>
              <div className="flex items-center gap-1.5 text-xs text-slate-300 font-medium whitespace-nowrap">
                <MapPin size={12} className="text-emerald-400 shrink-0" />
                <span>Hà Nội & TP.HCM</span>
              </div>
            </div>

            {/* Main Punchline and Features */}
            <div className="relative z-10 space-y-6 my-auto py-6">
              <h2 className="text-2xl xl:text-3xl font-extrabold tracking-tight text-white leading-tight">
                Sẵn sàng cho trận cầu đỉnh cao tiếp theo của bạn.
              </h2>
              <p className="text-slate-300 text-xs sm:text-sm leading-relaxed max-w-md">
                Đặt sân Pickleball, Cầu lông, Tennis & Bóng đá trong 30 giây. Check-in mã QR tự động, chia tiền nhóm và chuyển nhượng linh hoạt.
              </p>

              {/* Feature chips */}
              <div className="grid gap-3 pt-1">
                <div className="flex items-center gap-3.5 rounded-2xl border border-white/10 bg-white/5 p-3.5 backdrop-blur-md transition-all hover:bg-white/10">
                  <div className="flex size-10 items-center justify-center rounded-xl bg-emerald-500/20 text-emerald-400 shrink-0">
                    <CalendarCheck2 size={20} />
                  </div>
                  <div>
                    <div className="text-sm font-bold text-white">Đặt sân & Giữ chỗ tức thì</div>
                    <div className="text-xs text-slate-300">Lịch slot trống thời gian thực, tạm giữ chỗ 15 phút</div>
                  </div>
                </div>

                <div className="flex items-center gap-3.5 rounded-2xl border border-white/10 bg-white/5 p-3.5 backdrop-blur-md transition-all hover:bg-white/10">
                  <div className="flex size-10 items-center justify-center rounded-xl bg-teal-500/20 text-teal-400 shrink-0">
                    <Users size={20} />
                  </div>
                  <div>
                    <div className="text-sm font-bold text-white">Đặt sân nhóm & Chia tiền</div>
                    <div className="text-xs text-slate-300">Mời bạn bè qua mã QR, tự động chia tiền & nhắc thanh toán</div>
                  </div>
                </div>

                <div className="flex items-center gap-3.5 rounded-2xl border border-white/10 bg-white/5 p-3.5 backdrop-blur-md transition-all hover:bg-white/10">
                  <div className="flex size-10 items-center justify-center rounded-xl bg-amber-500/20 text-amber-400 shrink-0">
                    <Repeat size={20} />
                  </div>
                  <div>
                    <div className="text-sm font-bold text-white">Sàn chuyển nhượng slot sân</div>
                    <div className="text-xs text-slate-300">Bận việc đột xuất? Nhượng lại slot an toàn & sang tên vé</div>
                  </div>
                </div>
              </div>
            </div>

            {/* Bottom Proof */}
            <div className="relative z-10 flex items-center justify-between border-t border-white/10 pt-4 text-xs text-slate-400">
              <div className="flex items-center gap-2.5">
                <div className="flex -space-x-2">
                  <div className="size-7 rounded-full border-2 border-slate-900 bg-slate-700 flex items-center justify-center text-slate-300">
                    <User size={13} />
                  </div>
                  <div className="size-7 rounded-full border-2 border-slate-900 bg-emerald-700 flex items-center justify-center text-white">
                    <User size={13} />
                  </div>
                  <div className="size-7 rounded-full border-2 border-slate-900 bg-teal-700 flex items-center justify-center text-white">
                    <User size={13} />
                  </div>
                </div>
                <span className="font-semibold text-slate-300">10,000+ Vận động viên</span>
              </div>
              <div className="flex items-center gap-1.5 text-slate-300 font-semibold">
                <Star size={14} className="fill-amber-400 text-amber-400" />
                <span>4.9</span>
                <span className="text-slate-400 font-normal text-[11px]">(1.2k+ đánh giá)</span>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
