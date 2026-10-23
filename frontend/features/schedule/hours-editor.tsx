'use client'
import { Button } from '@/components/ui/button'
import { ImpactNotice } from '@/features/integration/configuration-impact'
import { CommonHoursForm } from '@/features/schedule/common-hours-form'
import { Interval } from '@/features/schedule/hours-model'
import { useHoursEditor } from '@/features/schedule/use-hours-editor'
import { WeekdayExceptionsForm } from '@/features/schedule/weekday-exceptions-form'
export function HoursEditor({
  facilityId,
  courtId,
  initial,
  reload,
  onSaved,
}: {
  facilityId: string
  courtId: string
  initial: Interval[]
  reload: () => void
  onSaved: (message: string) => void
}) {
  const model = useHoursEditor({ facilityId, courtId, initial, reload, onSaved })
  const { save, busy, resetToFacility, impact, error } = model
  return (
    <form onSubmit={save} className="space-y-6 rounded-2xl border bg-white p-5 shadow-sm">
      <CommonHoursForm model={model} />

      {/* SECTION: Ngoại lệ theo thứ trong tuần */}
      <WeekdayExceptionsForm model={model} />

      <div className="flex flex-wrap items-center gap-3 pt-1 border-t pt-4">
        <Button type="submit" disabled={busy}>
          {busy ? 'Đang lưu…' : 'Lưu lịch'}
        </Button>
        {courtId && initial.length > 0 && (
          <Button type="button" variant="outline" disabled={busy} onClick={resetToFacility}>
            Kế thừa lịch cơ sở
          </Button>
        )}
      </div>

      <ImpactNotice impact={impact} />
      {error && (
        <p role="alert" className="text-sm font-medium text-red-700">
          {error}
        </p>
      )}
      <p className="text-xs text-slate-500">
        * Lưu ý: Các ngoại lệ theo thứ được ưu tiên trước giờ mở cửa chung. Thay đổi lịch không tự
        hủy lịch đặt đã xác nhận.
      </p>
    </form>
  )
}
