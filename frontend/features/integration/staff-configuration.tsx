'use client'
import { RequireAuth } from '@/features/auth/require-auth'
import { StaffTool } from '@/features/operations/staff-tool-types'
import { Workspace } from '@/features/operations/staff-tool-workspace'
export function StaffConfiguration({ id, tool }: { id: string; tool: StaffTool }) {
  return (
    <RequireAuth roles={['STAFF']}>
      <Workspace id={id} tool={tool} />
    </RequireAuth>
  )
}
export { type StaffTool } from '@/features/operations/staff-tool-types'
