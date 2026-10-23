'use client'
import { Input } from '@/components/ui/input'
import { KeyRound } from 'lucide-react'
import type { AuthModel } from './use-auth-form'

export function AuthRecoveryFields({ model }: { model: AuthModel }) {
  const { mode, params } = model
  return (
    <>
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
              : 'Hồ sơ cơ sở đầu tiên đã gửi để quản trị viên thẩm định.'}{' '}
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
              Mã xác minh đã được gửi qua thư điện tử hoặc SMS đã đăng ký.
            </p>
          </div>
        </div>
      )}
    </>
  )
}
