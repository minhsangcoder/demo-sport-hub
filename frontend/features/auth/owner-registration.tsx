'use client'
import { Checkbox } from '@/components/ui/checkbox'
import { Building2 } from 'lucide-react'
import dynamic from 'next/dynamic'
import type { AuthModel } from './use-auth-form'
const OwnerSignupFields = dynamic(
  () => import('./owner-signup-fields').then((module) => module.OwnerSignupFields),
  { loading: () => <p role="status">Đang mở biểu mẫu cơ sở…</p> },
)
export function OwnerRegistration({ model }: { model: AuthModel }) {
  const {
    mode,
    busy,
    fieldErrors,
    applyAsOwner,
    setApplyAsOwner,
    ownerVisited,
    setOwnerVisited,
    location,
    setLocation,
  } = model
  return (
    <>
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
                Đăng ký trở thành chủ sân
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
    </>
  )
}
