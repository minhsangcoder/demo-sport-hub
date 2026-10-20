'use client'

import { useState } from 'react'
import { useApi } from '@/lib/use-api'
import { businessApi, type StaffProfile, type StaffInvitation, type PermissionView } from '@/lib/business-api'
import { useFacility } from '@/features/facility/facility-context'
import { RequireAuth } from '@/features/auth/require-auth'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { Frame, Card, Status, useMutation } from './shared'
import { UserPlus, KeyRound, Shield, Check, Eye, EyeOff, Lock, AlertCircle, Sparkles } from 'lucide-react'

const PERMISSION_LABELS: Record<string, string> = {
  BOOKING_READ: 'Xem danh sách booking',
  BOOKING_CREATE_COUNTER: 'Tạo booking tại quầy',
  BOOKING_PAYMENT_COUNTER: 'Ghi nhận thanh toán tại quầy',
  BOOKING_CHECK_IN: 'Check-in khách đặt sân',
  SCHEDULE_READ: 'Xem lịch sân',
  SCHEDULE_MANAGE: 'Quản lý lịch sân',
  COURT_STATUS_MANAGE: 'Quản lý trạng thái sân',
  COURT_MAINTENANCE_MANAGE: 'Quản lý bảo trì sân',
}

const PERMISSION_DESCRIPTIONS: Record<string, string> = {
  BOOKING_READ: 'Xem danh sách và chi tiết các booking tại cơ sở',
  BOOKING_CREATE_COUNTER: 'Tạo booking cho khách đến đặt sân trực tiếp tại quầy',
  BOOKING_PAYMENT_COUNTER: 'Ghi nhận thu tiền mặt, chuyển khoản và người thu tiền tại quầy',
  BOOKING_CHECK_IN: 'Check-in khách đặt sân bằng mã QR hoặc mã booking',
  SCHEDULE_READ: 'Xem lịch sân và khung giờ hoạt động',
  SCHEDULE_MANAGE: 'Khóa / mở lịch và chỉnh sửa khung giờ',
  COURT_STATUS_MANAGE: 'Bật / tắt trạng thái hoạt động sân',
  COURT_MAINTENANCE_MANAGE: 'Lên lịch và kết thúc bảo trì sân',
}

const DEFAULT_STAFF_PERMISSIONS = [
  'BOOKING_READ',
  'BOOKING_CREATE_COUNTER',
  'BOOKING_PAYMENT_COUNTER',
  'BOOKING_CHECK_IN',
  'SCHEDULE_READ',
]

export function StaffInvitations() {
  return (
    <RequireAuth>
      <Invitations />
    </RequireAuth>
  )
}

function Invitations() {
  const r = useApi<StaffInvitation[]>('/users/me/staff-invitations')
  const a = useMutation()
  return (
    <Frame title="Lời mời làm việc tại cơ sở">
      <Status loading={r.loading} error={r.error || a.error} empty={r.data?.length === 0} />
      {r.data?.map((i) => (
        <Card key={i.id} title={i.staffName}>
          <p>
            Cơ sở {i.facilityId.slice(0, 8)} · {i.state}
          </p>
          <p className="text-sm text-slate-500">
            {i.permissions.map((p) => PERMISSION_LABELS[p] || p).join(' · ')} · Hết hạn{' '}
            {new Date(i.expiresAt).toLocaleString('vi-VN')}
          </p>
          {i.state === 'PENDING' && (
            <div className="flex gap-3">
              <Button disabled={a.busy} onClick={() => a.run(() => businessApi.invitation(i.id, 'accept'), r.reload)}>
                Đồng ý lời mời
              </Button>
              <Button
                variant="outline"
                disabled={a.busy}
                onClick={() => a.run(() => businessApi.invitation(i.id, 'decline'), r.reload)}
              >
                Từ chối
              </Button>
            </div>
          )}
          {i.state === 'ACCEPTED' && (
            <p className="text-amber-800">
              Đã đồng ý; chờ chủ cơ sở xác nhận liên kết. Đăng nhập lại sau khi được cấp quyền Staff.
            </p>
          )}
        </Card>
      ))}
    </Frame>
  )
}

export function StaffManagement() {
  const { facility } = useFacility()
  const base = `/staff-profiles/facilities/${facility.id}`
  const r = useApi<StaffProfile[]>(base)
  const catalog = useApi<string[]>(`/staff-bindings/facilities/${facility.id}/permission-catalog`)
  const a = useMutation()

  // Form state for creating direct staff account
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [phone, setPhone] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [selectedPermissions, setSelectedPermissions] = useState<string[]>(DEFAULT_STAFF_PERMISSIONS)
  const [createSuccess, setCreateSuccess] = useState('')

  const availableCatalog = catalog.data && catalog.data.length > 0
    ? catalog.data
    : Object.keys(PERMISSION_LABELS)

  const handleCreateStaffAccount = async (e: React.FormEvent) => {
    e.preventDefault()
    setCreateSuccess('')
    if (selectedPermissions.length === 0) {
      a.setMessage('Vui lòng chọn ít nhất một quyền cho nhân viên')
      return
    }

    await a.run(
      () =>
        businessApi.createStaffAccount(facility.id, {
          fullName: fullName.trim(),
          email: email.trim(),
          phone: phone.trim() || undefined,
          password,
          permissions: selectedPermissions,
        }),
      () => {
        setCreateSuccess(`Đã tạo tài khoản nhân viên ${email.trim()} thành công! Nhân viên có thể đăng nhập ngay.`)
        setFullName('')
        setEmail('')
        setPhone('')
        setPassword('')
        setSelectedPermissions(DEFAULT_STAFF_PERMISSIONS)
        r.reload()
      }
    )
  }

  const togglePermission = (code: string) => {
    setSelectedPermissions((prev) =>
      prev.includes(code) ? prev.filter((p) => p !== code) : [...prev, code]
    )
  }

  const selectAllPermissions = () => {
    setSelectedPermissions([...availableCatalog])
  }

  const clearAllPermissions = () => {
    setSelectedPermissions([])
  }

  return (
    <div className="space-y-8">
      <div>
        <h2 className="text-2xl font-black text-slate-900 dark:text-white">Nhân viên cơ sở</h2>
        <p className="mt-1 text-sm text-slate-500">
          Chủ cơ sở tạo tài khoản cho nhân viên để vận hành kiểm tra booking và check-in khách tại quầy.
        </p>
      </div>

      {/* Primary Action: Direct Staff Account Creation */}
      <section className="rounded-2xl border border-emerald-100 bg-gradient-to-br from-emerald-50/60 via-white to-slate-50/50 p-6 shadow-sm dark:border-emerald-950/40 dark:bg-slate-900/60">
        <div className="flex items-center gap-2.5 pb-4 border-b border-emerald-100 dark:border-emerald-950/40">
          <div className="flex size-9 items-center justify-center rounded-xl bg-emerald-600 text-white shadow-sm">
            <UserPlus size={18} />
          </div>
          <div>
            <h3 className="text-lg font-bold text-slate-900 dark:text-white">Tạo tài khoản nhân viên mới</h3>
            <p className="text-xs text-slate-500">
              Nhân viên được tạo sẽ có quyền STAFF, đăng nhập vào thẳng màn hình Kiểm Tra Booking.
            </p>
          </div>
        </div>

        {createSuccess && (
          <div className="mt-4 flex items-center gap-2 rounded-xl bg-emerald-50 p-3.5 text-sm font-semibold text-emerald-800 dark:bg-emerald-950/60 dark:text-emerald-300">
            <Check size={18} className="text-emerald-600 shrink-0" />
            <span>{createSuccess}</span>
          </div>
        )}

        <Status error={a.error} />

        <form onSubmit={handleCreateStaffAccount} className="mt-5 space-y-5">
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="grid gap-1.5 text-sm font-semibold text-slate-700 dark:text-slate-300">
              Họ và tên nhân viên *
              <Input
                placeholder="Ví dụ: Nguyễn Văn A"
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                required
                minLength={2}
                maxLength={255}
                disabled={a.busy}
              />
            </label>

            <label className="grid gap-1.5 text-sm font-semibold text-slate-700 dark:text-slate-300">
              Email đăng nhập *
              <Input
                type="email"
                placeholder="nhanvien@sporthub.local"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                disabled={a.busy}
              />
            </label>

            <label className="grid gap-1.5 text-sm font-semibold text-slate-700 dark:text-slate-300">
              Số điện thoại (tùy chọn)
              <Input
                placeholder="0912345678"
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                pattern="\+?[0-9]{9,15}"
                disabled={a.busy}
              />
            </label>

            <label className="grid gap-1.5 text-sm font-semibold text-slate-700 dark:text-slate-300">
              Mật khẩu đăng nhập ban đầu *
              <div className="relative">
                <Input
                  type={showPassword ? 'text' : 'password'}
                  placeholder="Tối thiểu 6 ký tự"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                  minLength={6}
                  maxLength={72}
                  disabled={a.busy}
                  className="pr-10"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
                >
                  {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
            </label>
          </div>

          <div>
            <div className="flex items-center justify-between mb-2.5">
              <span className="text-sm font-semibold text-slate-800 dark:text-slate-200">
                Phân quyền cho nhân viên tại cơ sở:
              </span>
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={selectAllPermissions}
                  className="text-xs font-semibold text-emerald-600 hover:underline"
                >
                  Chọn tất cả
                </button>
                <span className="text-xs text-slate-300">|</span>
                <button
                  type="button"
                  onClick={clearAllPermissions}
                  className="text-xs font-semibold text-slate-500 hover:underline"
                >
                  Bỏ chọn
                </button>
              </div>
            </div>

            <div className="grid gap-2.5 sm:grid-cols-2 lg:grid-cols-3 rounded-xl border border-slate-200 bg-white p-3.5 dark:border-slate-800 dark:bg-slate-900">
              {availableCatalog.map((code) => {
                const checked = selectedPermissions.includes(code)
                return (
                  <label
                    key={code}
                    className={`flex items-start gap-2.5 rounded-lg border p-2.5 text-xs font-medium cursor-pointer transition-all ${
                      checked
                        ? 'border-emerald-300 bg-emerald-50/60 text-emerald-950 dark:border-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-200'
                        : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-300'
                    }`}
                  >
                    <input
                      type="checkbox"
                      checked={checked}
                      onChange={() => togglePermission(code)}
                      className="mt-0.5 size-4 rounded border-slate-300 text-emerald-600 focus:ring-emerald-500"
                    />
                    <div>
                      <p className="font-bold">{PERMISSION_LABELS[code] || code}</p>
                      {PERMISSION_DESCRIPTIONS[code] && (
                        <p className="mt-0.5 text-[11px] text-slate-500 leading-snug">{PERMISSION_DESCRIPTIONS[code]}</p>
                      )}
                      <p className="text-[10px] text-slate-400 font-mono mt-0.5">{code}</p>
                    </div>
                  </label>
                )
              })}
            </div>
          </div>

          <div className="flex justify-end pt-2">
            <Button
              type="submit"
              disabled={a.busy || !fullName.trim() || !email.trim() || !password || selectedPermissions.length === 0}
              className="gap-2 bg-emerald-600 hover:bg-emerald-700 text-white font-bold px-6 py-2.5 rounded-xl shadow-md"
            >
              <UserPlus size={16} />
              <span>{a.busy ? 'Đang tạo tài khoản…' : 'Tạo tài khoản nhân viên'}</span>
            </Button>
          </div>
        </form>
      </section>

      {/* Staff Accounts List */}
      <section className="space-y-4">
        <div className="flex items-center justify-between">
          <h3 className="text-lg font-bold text-slate-900 dark:text-white">
            Danh sách nhân viên ({r.data?.length ?? 0})
          </h3>
          <Button variant="outline" size="sm" onClick={() => r.reload()} disabled={r.loading}>
            Làm mới
          </Button>
        </div>

        <Status loading={r.loading} error={r.error || catalog.error} empty={r.data?.length === 0} />

        <div className="space-y-4">
          {r.data?.map((p) => (
            <StaffProfileCard
              key={p.id + ':' + p.version}
              profile={p}
              facility={facility.id}
              catalog={availableCatalog}
              reload={r.reload}
            />
          ))}
        </div>
      </section>
    </div>
  )
}

function StaffProfileCard({
  profile: p,
  facility,
  catalog,
  reload,
}: {
  profile: StaffProfile
  facility: string
  catalog: string[]
  reload: () => void
}) {
  const a = useMutation()
  const [permissions, setPermissions] = useState<string[]>(p.permissions)
  const [resettingPassword, setResettingPassword] = useState(false)
  const [newPassword, setNewPassword] = useState('')
  const [showNewPassword, setShowNewPassword] = useState(false)
  const [passwordSuccess, setPasswordSuccess] = useState('')

  const version = useApi<PermissionView>(
    p.linked ? `/staff-bindings/facilities/${facility}/${p.bindingId}/permissions` : null
  )

  const handleResetPassword = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!newPassword || newPassword.length < 6) return
    setPasswordSuccess('')
    await a.run(
      () => businessApi.resetStaffPassword(facility, p.id, newPassword),
      () => {
        setPasswordSuccess('Đã đổi mật khẩu nhân viên thành công!')
        setNewPassword('')
        setResettingPassword(false)
      }
    )
  }

  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs transition-all hover:border-slate-300 dark:border-slate-800 dark:bg-slate-900">
      <div className="flex flex-wrap items-start justify-between gap-4 border-b border-slate-100 pb-4 dark:border-slate-800">
        <div>
          <div className="flex items-center gap-2.5">
            <h4 className="text-base font-bold text-slate-900 dark:text-white">{p.fullName}</h4>
            <span
              className={`rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                p.active
                  ? 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300'
                  : 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-400'
              }`}
            >
              {p.active ? 'Đang làm việc' : 'Ngừng làm việc'}
            </span>
            <span className="rounded-full bg-blue-50 px-2.5 py-0.5 text-xs font-semibold text-blue-700 dark:bg-blue-950 dark:text-blue-300">
              STAFF
            </span>
          </div>
          <p className="mt-1 text-xs text-slate-500">
            {p.email && <span>Email: <strong className="text-slate-700 dark:text-slate-300">{p.email}</strong></span>}
            {p.email && p.phone && <span className="mx-1.5">·</span>}
            {p.phone && <span>SĐT: <strong className="text-slate-700 dark:text-slate-300">{p.phone}</strong></span>}
          </p>
        </div>

        {/* Action Buttons */}
        <div className="flex flex-wrap items-center gap-2">
          {p.linked && (
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => setResettingPassword(!resettingPassword)}
              className="gap-1.5 text-xs"
            >
              <KeyRound size={14} />
              <span>{resettingPassword ? 'Hủy đổi MK' : 'Đặt lại mật khẩu'}</span>
            </Button>
          )}

          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={a.busy}
            onClick={() => {
              if (window.confirm(p.active ? 'Xác nhận ngừng làm việc cho nhân viên này?' : 'Kích hoạt lại nhân viên này?')) {
                void a.run(
                  () =>
                    businessApi.profile(
                      facility,
                      {
                        fullName: p.fullName,
                        email: p.email,
                        phone: p.phone,
                        active: !p.active,
                        expectedVersion: p.version,
                      },
                      p.id
                    ),
                  reload
                )
              }
            }}
            className="text-xs"
          >
            {p.active ? 'Ngừng làm việc' : 'Kích hoạt lại'}
          </Button>

          {p.linked && (
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={a.busy}
              onClick={() => {
                if (window.confirm('Thu hồi liên kết quyền của nhân viên này tại cơ sở?')) {
                  void a.run(
                    () =>
                      import('@/lib/api').then(({ api }) =>
                        api(`/staff-bindings/facilities/${facility}/${p.bindingId}`, { method: 'DELETE' })
                      ),
                    reload
                  )
                }
              }}
              className="text-xs text-red-600 hover:bg-red-50 hover:text-red-700 dark:text-red-400"
            >
              Thu hồi liên kết
            </Button>
          )}
        </div>
      </div>

      <Status error={a.error || version.error} />

      {passwordSuccess && (
        <div className="mt-3 flex items-center gap-2 rounded-lg bg-emerald-50 p-2.5 text-xs font-semibold text-emerald-800">
          <Check size={14} className="text-emerald-600" />
          <span>{passwordSuccess}</span>
        </div>
      )}

      {/* Inline Reset Password Form */}
      {resettingPassword && (
        <form onSubmit={handleResetPassword} className="mt-3 rounded-xl border border-amber-200 bg-amber-50/50 p-3.5 dark:border-amber-900/50 dark:bg-amber-950/20">
          <p className="text-xs font-bold text-amber-900 dark:text-amber-300 mb-2">
            Đặt lại mật khẩu mới cho nhân viên:
          </p>
          <div className="flex flex-wrap items-center gap-2">
            <div className="relative min-w-[220px]">
              <Input
                type={showNewPassword ? 'text' : 'password'}
                placeholder="Mật khẩu mới (tối thiểu 6 ký tự)"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                required
                minLength={6}
                maxLength={72}
                disabled={a.busy}
                className="h-9 text-xs pr-8 bg-white"
              />
              <button
                type="button"
                onClick={() => setShowNewPassword(!showNewPassword)}
                className="absolute right-2 top-1/2 -translate-y-1/2 text-slate-400"
              >
                {showNewPassword ? <EyeOff size={14} /> : <Eye size={14} />}
              </button>
            </div>
            <Button type="submit" size="sm" disabled={a.busy || newPassword.length < 6} className="h-9 text-xs">
              Lưu mật khẩu mới
            </Button>
          </div>
        </form>
      )}

      {/* Permissions Editor */}
      <div className="mt-4">
        <p className="text-xs font-bold text-slate-700 dark:text-slate-300 mb-2">
          Quyền hạn tại cơ sở ({permissions.length}):
        </p>
        <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
          {catalog.map((code) => {
            const checked = permissions.includes(code)
            return (
              <label
                key={code}
                title={PERMISSION_DESCRIPTIONS[code] || code}
                className={`flex items-center gap-2 rounded-lg border px-2.5 py-1.5 text-xs cursor-pointer ${
                  checked
                    ? 'border-emerald-300 bg-emerald-50/50 font-semibold text-emerald-900 dark:border-emerald-800 dark:bg-emerald-950/30 dark:text-emerald-200'
                    : 'border-slate-200 text-slate-500 hover:bg-slate-50 dark:border-slate-800'
                }`}
              >
                <input
                  type="checkbox"
                  checked={checked}
                  onChange={(e) =>
                    setPermissions(e.target.checked ? [...permissions, code] : permissions.filter((x) => x !== code))
                  }
                  disabled={a.busy || !p.active}
                  className="size-3.5 rounded text-emerald-600 focus:ring-emerald-500"
                />
                <span className="truncate">{PERMISSION_LABELS[code] || code}</span>
              </label>
            )
          })}
        </div>

        {p.linked && (
          <div className="mt-3 flex items-center justify-end">
            <Button
              type="button"
              size="sm"
              variant="outline"
              disabled={a.busy || !version.data}
              onClick={() =>
                a.run(
                  () => businessApi.permissions(facility, p.bindingId!, permissions, version.data!.version),
                  () => {
                    version.reload()
                    reload()
                  }
                )
              }
              className="text-xs"
            >
              Lưu quyền hạn
            </Button>
          </div>
        )}
      </div>

      {a.message && <p role="status" className="mt-2 text-xs font-semibold text-emerald-700">{a.message}</p>}
    </div>
  )
}
