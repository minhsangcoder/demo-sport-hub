'use client'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { AdminNav } from '@/features/admin/admin-nav'
import { Card, Frame, Status, useMutation } from '@/features/integration/shared'
import { businessApi, type Sport } from '@/lib/business-api'
import { useApi } from '@/lib/use-api'
export function SportsPage() {
  const r = useApi<Sport[]>('/admin/sport-categories'),
    a = useMutation()
  return (
    <Frame title="Danh mục môn thể thao">
      <AdminNav active="sports" />
      <form
        className="flex flex-wrap gap-3"
        onSubmit={(e) => {
          e.preventDefault()
          const f = new FormData(e.currentTarget)
          void a.run(
            () => businessApi.sport({ name: String(f.get('name')), active: true }),
            r.reload,
          )
        }}
      >
        <label className="grid flex-1 gap-2 text-sm">
          Tên môn thể thao
          <Input name="name" required maxLength={100} />
        </label>
        <Button type="submit" className="self-end" disabled={a.busy}>
          Thêm môn thể thao
        </Button>
      </form>
      <Status loading={r.loading} error={r.error || a.error} />
      {r.data?.map((s) => (
        <SportEditor key={s.id + ':' + s.version} sport={s} reload={r.reload} />
      ))}
    </Frame>
  )
}
function SportEditor({ sport: s, reload }: { sport: Sport; reload: () => void }) {
  const a = useMutation()
  return (
    <Card title={s.name}>
      <p className="text-sm">
        {s.active ? 'Đang hoạt động' : 'Ngừng tạo sân mới'} · {s.court_count ?? 0} sân tham chiếu
      </p>
      <form
        className="grid gap-3 sm:grid-cols-2"
        onSubmit={(e) => {
          e.preventDefault()
          const f = new FormData(e.currentTarget)
          void a.run(
            () =>
              businessApi.sport(
                {
                  name: String(f.get('name')),
                  active: f.get('active') === 'on',
                  description: String(f.get('description')) || undefined,
                  icon: String(f.get('icon')) || undefined,
                  expectedVersion: s.version,
                },
                s.id,
              ),
            reload,
          )
        }}
      >
        <label className="grid gap-2 text-sm">
          Tên môn
          <Input name="name" defaultValue={s.name} required maxLength={100} />
        </label>
        <label className="grid gap-2 text-sm">
          Mô tả
          <Input name="description" defaultValue={s.description} maxLength={2000} />
        </label>
        <label className="grid gap-2 text-sm">
          Mã biểu tượng (tùy chọn)
          <Input name="icon" defaultValue={s.icon} pattern="[A-Za-z][A-Za-z0-9_-]{0,79}" />
        </label>
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" name="active" defaultChecked={s.active} />
          Đang hoạt động
        </label>
        <Button type="submit" disabled={a.busy}>
          Lưu danh mục
        </Button>
      </form>
      <Status error={a.error} />
    </Card>
  )
}
