'use client'
import { AccountFilters } from '@/features/admin/account-filters'
import { AccountStats } from '@/features/admin/account-stats'
import { AccountTable } from '@/features/admin/account-table'
import { useAccounts } from '@/features/admin/use-accounts'
export function AccountsContent() {
  const model = useAccounts()

  return (
    <div className="space-y-6">
      {/* 1. TOP STATS / KPI CARDS */}
      <AccountStats model={model} />

      {/* 2. SEARCH & FILTER BAR */}
      <AccountFilters model={model} />

      {/* 3. PROFESSIONAL DATA TABLE */}
      <AccountTable model={model} />
    </div>
  )
}
