'use client'
import { AccountsContent } from '@/features/admin/accounts-content'
import { AdminShell } from '@/features/admin/admin-shell'
import { RequireAuth } from '@/features/auth/require-auth'
export function AdminAccounts() {
  return (
    <RequireAuth roles={['ADMIN']}>
      <AdminShell active="accounts">
        <AccountsContent />
      </AdminShell>
    </RequireAuth>
  )
}
export { type AdminAccount } from '@/features/admin/admin-account-types'
export { AdminNav } from '@/features/admin/admin-nav'
export { AdminShell } from '@/features/admin/admin-shell'
