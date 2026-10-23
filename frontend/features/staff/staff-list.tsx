'use client'
import { Button } from '@/components/ui/button'
import { Status } from '@/features/integration/shared'
import { StaffProfileCard } from '@/features/staff/staff-profile-card'
import type { useStaffDirectory } from '@/features/staff/use-staff-directory'
export function StaffList({
  model,
}: {
  model: Pick<
    ReturnType<typeof useStaffDirectory>,
    'r' | 'catalog' | 'facility' | 'availableCatalog'
  >
}) {
  const { r, catalog, facility, availableCatalog } = model
  return (
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
  )
}
