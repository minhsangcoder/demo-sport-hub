'use client'
import { AdminAccount } from '@/features/admin/admin-account-types'
import { useApi } from '@/lib/use-api'
import { useState } from 'react'
export function useAccounts() {
  const [path, setPath] = useState('/admin/accounts')
  const r = useApi<AdminAccount[]>(path)

  function search(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault()
    const f = new FormData(e.currentTarget)
    const p = new URLSearchParams()
    for (const key of ['q', 'status', 'role']) {
      const v = String(f.get(key) || '').trim()
      if (v) p.set(key, v)
    }
    setPath(`/admin/accounts?${p.toString()}`)
  }

  // Calculate quick stats from loaded results
  const totalUsers = r.data?.length ?? 0
  const activeUsers = r.data?.filter((u) => u.status === 'ACTIVE').length ?? 0
  const lockedUsers =
    r.data?.filter((u) => u.status === 'LOCKED' || u.status === 'SUSPENDED').length ?? 0
  const privilegedUsers =
    r.data?.filter((u) => u.roles.includes('OWNER') || u.roles.includes('ADMIN')).length ?? 0

  return { path, setPath, r, search, totalUsers, activeUsers, lockedUsers, privilegedUsers }
}
