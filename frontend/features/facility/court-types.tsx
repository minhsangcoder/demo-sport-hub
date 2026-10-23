'use client'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { useFacility } from '@/features/facility/facility-store'
import { Card, Status, useMutation } from '@/features/integration/shared'
import { businessApi, type CourtType } from '@/lib/business-api'
import { useApi } from '@/lib/use-api'
export function CourtTypes() {
  const { facility } = useFacility(),
    r = useApi<CourtType[]>(`/owner/facilities/${facility.id}/court-types`),
    a = useMutation()
  return (
    <section className="space-y-5">
      <h2 className="text-2xl font-bold">Loại sân tại cơ sở</h2>
      <form
        className="flex gap-3"
        onSubmit={(e) => {
          e.preventDefault()
          const f = new FormData(e.currentTarget)
          void a.run(
            () => businessApi.courtType(facility.id, { name: String(f.get('name')), active: true }),
            r.reload,
          )
        }}
      >
        <label className="grid flex-1 gap-2 text-sm">
          Tên loại sân
          <Input name="name" required maxLength={100} />
        </label>
        <Button type="submit" className="self-end" disabled={a.busy}>
          Thêm loại sân
        </Button>
      </form>
      <Status loading={r.loading} error={r.error || a.error} empty={r.data?.length === 0} />
      {r.data?.map((t) => (
        <Card key={t.id + ':' + t.version} title={t.name}>
          <form
            className="flex flex-wrap items-end gap-3"
            onSubmit={(e) => {
              e.preventDefault()
              const f = new FormData(e.currentTarget)
              void a.run(
                () =>
                  businessApi.courtType(
                    facility.id,
                    { name: String(f.get('name')), active: f.get('active') === 'on' },
                    t.id,
                  ),
                r.reload,
              )
            }}
          >
            <label className="grid flex-1 gap-2 text-sm">
              Tên loại sân
              <Input name="name" defaultValue={t.name} required maxLength={100} />
            </label>
            <label className="flex gap-2 text-sm">
              <input name="active" type="checkbox" defaultChecked={t.active} />
              Đang dùng
            </label>
            <Button type="submit" disabled={a.busy}>
              Lưu loại sân
            </Button>
          </form>
          <p className="text-sm text-slate-500">
            Ngừng loại sân không sửa giá hoặc lượt đặt đã chốt.
          </p>
        </Card>
      ))}
    </section>
  )
}
