'use client'
import { Button } from '@/components/ui/button'
import { RequireAuth } from '@/features/auth/require-auth'
import { ContactChannels } from '@/features/profile/contact-channels'
import { ContactHero } from '@/features/profile/contact-hero'
import { useContact } from '@/features/profile/use-contact'
import { ApiError } from '@/lib/api'
import { AlertCircle, CheckCircle2, Clock, Key, ShieldCheck, User } from 'lucide-react'
import Link from 'next/link'
export default function ContactPage() {
  const model = useContact()
  const { error, busy, setLoading, load, setError, message } = model
  return (
    <RequireAuth>
      <div className="min-h-screen bg-slate-50/70 pb-20 dark:bg-slate-950/40">
        {/* Cover Hero Header */}
        <ContactHero />

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
                    .catch((err) =>
                      setError(err instanceof ApiError ? err.message : 'Không thể tải lại.'),
                    )
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
              <span className="font-bold text-slate-900 dark:text-white block text-sm">
                Xác thực hai lớp bảo vệ tài khoản
              </span>
              Thư điện tử và Số điện thoại được dùng để nhận mã OTP đăng nhập, thông báo lịch đặt
              sân và nhận tiền chuyển nhượng. Thông tin liên hệ cũ sẽ được giữ nguyên cho đến khi
              bạn xác minh thành công thông tin mới.
            </div>
          </div>

          {/* Channels Grid */}
          <ContactChannels model={model} />
        </div>
      </div>
    </RequireAuth>
  )
}
