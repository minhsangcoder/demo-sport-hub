'use client'
import { useMutation } from '@/features/integration/shared'
import { businessApi, type PermissionView, type StaffProfile } from '@/lib/business-api'
import { useApi } from '@/lib/use-api'
import { useState } from 'react'
export function useStaffProfile({
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
    p.linked ? `/staff-bindings/facilities/${facility}/${p.bindingId}/permissions` : null,
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
      },
    )
  }

  return {
    p,
    facility,
    catalog,
    reload,
    a,
    permissions,
    setPermissions,
    resettingPassword,
    setResettingPassword,
    newPassword,
    setNewPassword,
    showNewPassword,
    setShowNewPassword,
    passwordSuccess,
    setPasswordSuccess,
    version,
    handleResetPassword,
  }
}
