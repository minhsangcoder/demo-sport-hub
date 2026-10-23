'use client'
import { useFacility } from '@/features/facility/facility-store'
import { useMutation } from '@/features/integration/shared'
import {
  DEFAULT_STAFF_PERMISSIONS,
  PERMISSION_LABELS,
} from '@/features/staff/staff-permissions.data'
import { businessApi, type StaffProfile } from '@/lib/business-api'
import { useApi } from '@/lib/use-api'
import { useState } from 'react'
export function useStaffDirectory() {
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
  const [selectedPermissions, setSelectedPermissions] =
    useState<string[]>(DEFAULT_STAFF_PERMISSIONS)
  const [createSuccess, setCreateSuccess] = useState('')

  const availableCatalog =
    catalog.data && catalog.data.length > 0 ? catalog.data : Object.keys(PERMISSION_LABELS)

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
        setCreateSuccess(
          `Đã tạo tài khoản nhân viên ${email.trim()} thành công! Nhân viên có thể đăng nhập ngay.`,
        )
        setFullName('')
        setEmail('')
        setPhone('')
        setPassword('')
        setSelectedPermissions(DEFAULT_STAFF_PERMISSIONS)
        r.reload()
      },
    )
  }

  const togglePermission = (code: string) => {
    setSelectedPermissions((prev) =>
      prev.includes(code) ? prev.filter((p) => p !== code) : [...prev, code],
    )
  }

  const selectAllPermissions = () => {
    setSelectedPermissions([...availableCatalog])
  }

  const clearAllPermissions = () => {
    setSelectedPermissions([])
  }

  return {
    facility,
    base,
    r,
    catalog,
    a,
    fullName,
    setFullName,
    email,
    setEmail,
    phone,
    setPhone,
    password,
    setPassword,
    showPassword,
    setShowPassword,
    selectedPermissions,
    setSelectedPermissions,
    createSuccess,
    setCreateSuccess,
    availableCatalog,
    handleCreateStaffAccount,
    togglePermission,
    selectAllPermissions,
    clearAllPermissions,
  }
}
