'use client'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, Status, useMutation } from '@/features/integration/shared'
import { businessApi, type StatusImpact } from '@/lib/business-api'
import { useApi } from '@/lib/use-api'
export function FacilityStatus({ id, status }: { id: string; status: string }) {
  const r = useApi<StatusImpact>(
      ['ACTIVE', 'SUSPENDED'].includes(status) ? `/admin/facilities/${id}/status-impact` : null,
    ),
    a = useMutation()
  if (!['ACTIVE', 'SUSPENDED'].includes(status)) return null
  return (
    <Card title="Tạm ngừng / khôi phục cơ sở">
      <Status loading={r.loading} error={r.error || a.error} />
      {r.data && (
        <>
          <p className="text-sm text-slate-500">
            {r.data.reservations.length} lượt giữ chỗ có liên quan. Không tự hủy lượt đặt đã xác
            nhận hoặc thực hiện hoàn tiền.
          </p>
          <form
            className="flex flex-wrap gap-3"
            onSubmit={(e) => {
              e.preventDefault()
              const f = new FormData(e.currentTarget)
              if (!window.confirm('Xác nhận thay đổi trạng thái theo bản xem trước hiện tại?'))
                return
              void a.run(
                () =>
                  businessApi.statusDecision(
                    id,
                    r.data!,
                    status === 'ACTIVE' ? 'SUSPEND' : 'RESTORE',
                    String(f.get('reason')),
                  ),
                () => window.location.reload(),
              )
            }}
          >
            <label className="grid flex-1 gap-2 text-sm">
              Lý do quyết định
              <Input name="reason" required minLength={20} maxLength={2000} />
            </label>
            <Button type="submit" className="self-end" disabled={a.busy}>
              {status === 'ACTIVE' ? 'Tạm ngừng cơ sở' : 'Khôi phục cơ sở'}
            </Button>
            <Button type="button" variant="outline" onClick={r.reload}>
              Cập nhật bản xem trước
            </Button>
          </form>
        </>
      )}
    </Card>
  )
}
