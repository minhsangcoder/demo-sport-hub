'use client'
import { Button } from '@/components/ui/button'
import { PERMISSION_DESCRIPTIONS, PERMISSION_LABELS } from '@/features/staff/staff-permissions.data'
import type { useStaffProfile } from '@/features/staff/use-staff-profile'
import { businessApi } from '@/lib/business-api'
export function StaffPermissionEditor({
  model,
}: {
  model: Pick<
    ReturnType<typeof useStaffProfile>,
    'permissions' | 'catalog' | 'setPermissions' | 'a' | 'p' | 'version' | 'facility' | 'reload'
  >
}) {
  const { permissions, catalog, setPermissions, a, p, version, facility, reload } = model
  return (
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
                  setPermissions(
                    e.target.checked
                      ? [...permissions, code]
                      : permissions.filter((x) => x !== code),
                  )
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
                () =>
                  businessApi.permissions(
                    facility,
                    p.bindingId!,
                    permissions,
                    version.data!.version,
                  ),
                () => {
                  version.reload()
                  reload()
                },
              )
            }
            className="text-xs"
          >
            Lưu quyền hạn
          </Button>
        </div>
      )}
    </div>
  )
}
