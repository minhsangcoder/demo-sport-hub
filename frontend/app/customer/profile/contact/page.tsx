'use client'

import Link from 'next/link'
import { useCallback, useEffect, useState } from 'react'
import { api, authApi, ApiError, type UserProfile } from '@/lib/api'
import { useAuth } from '@/lib/auth-context'
import { RequireAuth } from '@/features/auth/require-auth'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import {
  Mail,
  Phone,
  ShieldCheck,
  CheckCircle2,
  AlertCircle,
  ArrowLeft,
  Key,
  User,
  Clock,
  Send,
  RefreshCw,
  Sparkles,
  Info
} from 'lucide-react'

type Channel = 'EMAIL' | 'PHONE'
type Challenge = {
  id: string
  channel: Channel
  recipient: string
  expiresAt: string
  resendAfter: string
  usable: boolean
}
const channels: Channel[] = ['EMAIL', 'PHONE']
const labels = { EMAIL: 'Địa chỉ Email', PHONE: 'Số điện thoại' }

export default function ContactPage() {
  const { user, ready } = useAuth()
  const [challenges, setChallenges] = useState<Challenge[]>([])
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState<Channel | null>(null)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [now, setNow] = useState(0)

  const load = useCallback(async () => {
    setChallenges(await api<Challenge[]>('/users/me/contact-challenges'))
  }, [])

  useEffect(() => {
    if (!ready || !user) return
    let active = true
    void api<Challenge[]>('/users/me/contact-challenges')
      .then((value) => {
        if (active) setChallenges(value)
      })
      .catch((err) => {
        if (active) setError(err instanceof ApiError ? err.message : 'Không thể tải yêu cầu xác minh.')
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [ready, user?.id, user])

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(timer)
  }, [])

  async function perform(channel: Channel, operation: () => Promise<void>) {
    if (busy) return
    setBusy(channel)
    setError('')
    setMessage('')
    try {
      await operation()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Không thể cập nhật thông tin liên hệ.')
      await load().catch(() => {})
    } finally {
      setBusy(null)
    }
  }

  function requestChange(event: React.FormEvent<HTMLFormElement>, channel: Channel) {
    event.preventDefault()
    const recipient = String(new FormData(event.currentTarget).get('recipient') ?? '').trim()
    if (recipient === (channel === 'EMAIL' ? user?.email : user?.phone)) {
      setError('Thông tin mới phải khác thông tin đang sử dụng.')
      return
    }
    void perform(channel, async () => {
      await api('/users/me', {
        method: 'PATCH',
        body: JSON.stringify({ [channel === 'EMAIL' ? 'email' : 'phone']: recipient })
      })
      await load()
      setMessage(`Đã gửi mã xác minh đến ${recipient}. Thông tin hiện tại vẫn hoạt động bình thường đến khi bạn hoàn tất xác thực OTP.`)
    })
  }

  function verify(event: React.FormEvent<HTMLFormElement>, challenge: Challenge) {
    event.preventDefault()
    const code = String(new FormData(event.currentTarget).get('code') ?? '')
    void perform(challenge.channel, async () => {
      await authApi.verify({ challengeId: challenge.id, code })
      const updated = await api<UserProfile>('/users/me')
      window.dispatchEvent(new CustomEvent('sporthub-session', { detail: updated }))
      await load()
      setMessage(`${labels[challenge.channel]} của bạn đã được xác minh và cập nhật thành công!`)
    })
  }

  function resend(challenge: Challenge) {
    void perform(challenge.channel, async () => {
      await api(`/users/me/contact-challenges/${challenge.channel}/resend`, { method: 'POST' })
      await load()
      setMessage('Đã gửi mã OTP mới. Vui lòng kiểm tra hộp thư hoặc tin nhắn.')
    })
  }

  return (
    <RequireAuth>
      <div className="min-h-screen bg-slate-50/70 pb-20 dark:bg-slate-950/40">
        {/* Cover Hero Header */}
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

        <div className="mx-auto max-w-6xl px-4 sm:px-6 lg:px-8">
          {/* Sub Navigation Tabs */}
          <div className="mt-6 flex items-center justify-between border-b border-slate-200 pb-3 dark:border-slate-800">
            <nav className="flex space-x-6">
              <Link
                href="/customer/profile"
                className="border-b-2 border-transparent pb-3 text-sm font-medium text-slate-500 hover:text-slate-800 hover:border-slate-300 dark:text-slate-400 dark:hover:text-slate-200 flex items-center gap-2 transition-colors"
              >
                <User size={16} />
                Thông tin cá nhân
              </Link>
              <Link
                href="/customer/profile/contact"
                className="border-b-2 border-emerald-600 pb-3 text-sm font-bold text-emerald-600 flex items-center gap-2"
              >
                <Key size={16} />
                Bảo mật & Liên hệ
              </Link>
              <Link
                href="/customer/bookings"
                className="border-b-2 border-transparent pb-3 text-sm font-medium text-slate-500 hover:text-slate-800 hover:border-slate-300 dark:text-slate-400 dark:hover:text-slate-200 flex items-center gap-2 transition-colors"
              >
                <Clock size={16} />
                Lịch sử đặt sân
              </Link>
            </nav>
          </div>

          {/* Feedback Alerts */}
          {error && (
            <div className="mt-6 flex items-center justify-between rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm font-medium text-rose-800 dark:border-rose-800/60 dark:bg-rose-950/40 dark:text-rose-200">
              <div className="flex items-center gap-2">
                <AlertCircle size={18} className="text-rose-600 shrink-0" />
                <span>{error}</span>
              </div>
              <Button
                variant="ghost"
                size="sm"
                disabled={!!busy}
                onClick={() => {
                  setLoading(true)
                  void load()
                    .then(() => setError(''))
                    .catch((err) => setError(err instanceof ApiError ? err.message : 'Không thể tải lại.'))
                    .finally(() => setLoading(false))
                }}
                className="text-xs"
              >
                Tải lại
              </Button>
            </div>
          )}

          {message && (
            <div className="mt-6 flex items-center gap-2 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm font-medium text-emerald-800 dark:border-emerald-800/60 dark:bg-emerald-950/40 dark:text-emerald-200">
              <CheckCircle2 size={18} className="text-emerald-600 shrink-0" />
              <span>{message}</span>
            </div>
          )}

          {/* Notice info banner */}
          <div className="mt-6 flex items-start gap-3 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
            <div className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-emerald-50 text-emerald-600 dark:bg-emerald-950/60">
              <ShieldCheck size={20} />
            </div>
            <div className="text-xs text-slate-600 dark:text-slate-300 leading-relaxed">
              <span className="font-bold text-slate-900 dark:text-white block text-sm">Xác thực hai lớp bảo vệ tài khoản</span>
              Email và Số điện thoại được dùng để nhận mã OTP đăng nhập, thông báo lịch đặt sân và nhận tiền chuyển nhượng. Thông tin liên hệ cũ sẽ được giữ nguyên cho đến khi bạn xác minh thành công thông tin mới.
            </div>
          </div>

          {/* Channels Grid */}
          <div className="mt-6 grid grid-cols-1 gap-6 md:grid-cols-2">
            {!loading &&
              channels.map((channel) => {
                const challenge = challenges.find((item) => item.channel === channel)
                const current = channel === 'EMAIL' ? user?.email : user?.phone
                const verified = channel === 'EMAIL' ? user?.emailVerified : user?.phoneVerified
                const wait = challenge
                  ? now === 0
                    ? 60
                    : Math.max(0, Math.ceil((Date.parse(challenge.resendAfter) - now) / 1000))
                  : 0
                const expired = challenge && (!challenge.usable || (now > 0 && Date.parse(challenge.expiresAt) <= now))

                return (
                  <section
                    key={channel}
                    className="flex flex-col justify-between rounded-2xl border border-slate-200/80 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900"
                  >
                    <div>
                      {/* Section Header */}
                      <div className="flex items-center justify-between border-b border-slate-100 pb-4 dark:border-slate-800">
                        <div className="flex items-center gap-2.5">
                          <div className={`flex size-9 items-center justify-center rounded-xl ${
                            channel === 'EMAIL' ? 'bg-emerald-50 text-emerald-600' : 'bg-blue-50 text-blue-600'
                          }`}>
                            {channel === 'EMAIL' ? <Mail size={18} /> : <Phone size={18} />}
                          </div>
                          <div>
                            <h2 className="text-base font-bold text-slate-900 dark:text-white">
                              {labels[channel]}
                            </h2>
                            <p className="text-xs text-slate-400">
                              {channel === 'EMAIL' ? 'Dùng để đăng nhập & nhận email' : 'Dùng để nhận SMS mã bảo mật OTP'}
                            </p>
                          </div>
                        </div>

                        {verified ? (
                          <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2.5 py-0.5 text-xs font-semibold text-emerald-700 dark:bg-emerald-950/60 dark:text-emerald-300">
                            <CheckCircle2 size={12} /> Đã xác thực
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1 rounded-full bg-amber-50 px-2.5 py-0.5 text-xs font-semibold text-amber-700 dark:bg-amber-950/60 dark:text-amber-300">
                            <AlertCircle size={12} /> Chưa xác thực
                          </span>
                        )}
                      </div>

                      {/* Current Value Display */}
                      <div className="mt-4 rounded-xl bg-slate-50 p-3.5 dark:bg-slate-800/60">
                        <span className="text-[11px] font-semibold uppercase tracking-wider text-slate-400 block">
                          ĐANG SỬ DỤNG
                        </span>
                        <p className="text-sm font-bold text-slate-800 dark:text-slate-100 mt-0.5 break-all">
                          {current || 'Chưa thiết lập'}
                        </p>
                      </div>

                      {/* Change Request Form */}
                      <form onSubmit={(event) => requestChange(event, channel)} className="mt-5 space-y-4">
                        <div className="space-y-1.5">
                          <label className="text-xs font-semibold text-slate-700 dark:text-slate-300">
                            Cập nhật {labels[channel].toLowerCase()} mới
                          </label>
                          <Input
                            name="recipient"
                            type={channel === 'EMAIL' ? 'email' : 'tel'}
                            placeholder={channel === 'EMAIL' ? 'tenban@example.com' : '+84901234567'}
                            maxLength={channel === 'EMAIL' ? 254 : 16}
                            pattern={channel === 'PHONE' ? '\\+[1-9][0-9]{7,14}' : undefined}
                            required
                            disabled={!!busy}
                            className="h-10 rounded-xl text-sm"
                          />
                          <p className="text-[11px] text-slate-400">
                            {channel === 'PHONE' && 'Lưu ý: Nhập kèm mã quốc gia (ví dụ: +84901234567).'}
                          </p>
                        </div>

                        <Button
                          type="submit"
                          variant="outline"
                          disabled={!!busy}
                          className="w-full h-10 rounded-xl font-semibold text-xs border-slate-200 hover:bg-slate-50 hover:text-emerald-600 transition-colors"
                        >
                          {busy === channel ? (
                            <>
                              <RefreshCw size={14} className="animate-spin mr-1.5" />
                              Đang gửi yêu cầu…
                            </>
                          ) : (
                            <>
                              <Send size={13} className="mr-1.5" />
                              Gửi mã xác minh {labels[channel]} mới
                            </>
                          )}
                        </Button>
                      </form>
                    </div>

                    {/* Pending Challenge Box */}
                    {challenge && (
                      <div className="mt-6 rounded-xl border border-emerald-200 bg-emerald-50/70 p-4 dark:border-emerald-800/50 dark:bg-emerald-950/30">
                        <div className="flex items-center justify-between text-xs font-bold text-emerald-900 dark:text-emerald-200">
                          <span>MÃ XÁC MINH OTP ĐÃ GỬI</span>
                          <span className="text-[11px] font-normal text-slate-500">
                            Hết hạn: {new Date(challenge.expiresAt).toLocaleTimeString('vi-VN')}
                          </span>
                        </div>
                        <p className="mt-1 text-xs text-slate-600 dark:text-slate-300 break-all">
                          Đã gửi mã 6 chữ số đến: <strong className="font-semibold text-emerald-800 dark:text-emerald-300">{challenge.recipient}</strong>
                        </p>

                        {expired ? (
                          <div className="mt-3">
                            <p className="text-xs text-rose-600 font-medium">Mã OTP đã hết hiệu lực. Vui lòng bấm gửi lại mã mới bên dưới.</p>
                          </div>
                        ) : (
                          <form
                            key={challenge.id}
                            onSubmit={(event) => verify(event, challenge)}
                            className="mt-3 flex gap-2"
                          >
                            <Input
                              name="code"
                              inputMode="numeric"
                              autoComplete="one-time-code"
                              pattern="[0-9]{6}"
                              minLength={6}
                              maxLength={6}
                              placeholder="000000"
                              required
                              disabled={!!busy}
                              className="h-10 text-center font-mono tracking-widest text-base font-bold rounded-xl bg-white dark:bg-slate-900"
                            />
                            <Button
                              type="submit"
                              disabled={!!busy}
                              className="h-10 rounded-xl bg-emerald-600 text-white font-semibold text-xs px-4 hover:bg-emerald-700 shrink-0"
                            >
                              {busy === challenge.channel ? <RefreshCw size={14} className="animate-spin" /> : 'Xác minh'}
                            </Button>
                          </form>
                        )}

                        <div className="mt-3 flex items-center justify-between pt-2 border-t border-emerald-200/60 dark:border-emerald-800/40">
                          <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            disabled={!!busy || wait > 0}
                            onClick={() => resend(challenge)}
                            className="text-xs text-emerald-700 hover:text-emerald-800 dark:text-emerald-300 p-0 h-auto font-medium"
                          >
                            {wait > 0 ? `Gửi lại mã sau (${wait}s)` : 'Chưa nhận được? Gửi lại mã'}
                          </Button>
                        </div>
                      </div>
                    )}
                  </section>
                )
              })}
          </div>
        </div>
      </div>
    </RequireAuth>
  )
}
