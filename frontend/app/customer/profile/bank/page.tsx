'use client'
import { useState } from 'react'
import Link from 'next/link'
import { RequireAuth } from '@/features/auth/require-auth'
import { BankDestinationForm } from '@/features/payments/bank-destination-form'
import { useAuth } from '@/lib/auth-context'

function Settings() {
  const [revision, setRevision] = useState('')
  return (
    <main className="mx-auto max-w-2xl space-y-5 p-6">
      <Link href="/customer/profile" className="text-emerald-700 underline">
        Hồ sơ cá nhân
      </Link>
      <h1 className="text-2xl font-bold">Tài khoản nhận tiền</h1>
      <p>
        Tài khoản của bạn dùng nhận tiền chuyển nhượng trực tiếp và khoản hoàn hợp lệ. Thông tin đã
        chốt trong giao dịch cũ được giữ nguyên.
      </p>
      <BankDestinationForm value={revision} onChange={setRevision} />
    </main>
  )
}
export default function BankSettingsPage() {
  const { user } = useAuth()
  return (
    <RequireAuth roles={['CUSTOMER']}>
      <Settings key={user?.id} />
    </RequireAuth>
  )
}
