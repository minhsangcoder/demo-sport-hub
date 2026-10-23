'use client'
import { useFacility } from '@/features/facility/facility-store'
import type { Court } from '@/features/facility/types'
import { Exceptions } from '@/features/schedule/date-exceptions'
import { HoursEditor } from '@/features/schedule/hours-editor'
import { type Hours } from '@/features/schedule/types'
import { timezoneLabel } from '@/lib/display-labels'
import { useApi } from '@/lib/use-api'
import Link from 'next/link'
import { useState } from 'react'
export function SchedulePage() {
  const { facility, basePath } = useFacility()
  const hours = useApi<Hours[]>(`/schedules/facilities/${facility.id}/hours`)
  const courts = useApi<Court[]>(`/owner/facilities/${facility.id}/courts`)
  const [scope, setScope] = useState('')
  const [feedback, setFeedback] = useState('')
  return (
    <section className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold">Lịch mở cửa</h2>
          <p className="mt-2 text-sm text-slate-500">
            Giờ theo {timezoneLabel(facility.timezone)}. Sân dùng lịch cơ sở khi chưa có lịch riêng.
          </p>
        </div>
        <Link
          className="rounded-full bg-emerald-600 px-5 py-2 text-sm text-white"
          href={`${basePath}/schedule/preview`}
        >
          Xem trước lịch và giá
        </Link>
      </div>
      <label className="grid max-w-sm gap-2 text-sm">
        Phạm vi lịch
        <select
          className="rounded-lg border bg-white p-3"
          value={scope}
          onChange={(e) => {
            setScope(e.target.value)
            setFeedback('')
          }}
        >
          <option value="">Toàn cơ sở</option>
          {courts.data?.map((c) => (
            <option value={c.id} key={c.id}>
              {c.name}
            </option>
          ))}
        </select>
      </label>
      {(hours.error || courts.error) && (
        <p role="alert" className="text-red-700">
          {hours.error || courts.error}
          <button
            className="ml-3 underline"
            onClick={() => {
              hours.reload()
              courts.reload()
            }}
          >
            Thử lại
          </button>
        </p>
      )}
      {hours.loading ? (
        <p role="status">Đang tải lịch…</p>
      ) : (
        hours.data && (
          <HoursEditor
            onSaved={setFeedback}
            key={scope + JSON.stringify(hours.data)}
            facilityId={facility.id}
            courtId={scope}
            initial={hours.data.filter((h) => (h.courtId || '') === scope)}
            reload={hours.reload}
          />
        )
      )}
      {feedback && (
        <p role="status" className="text-sm text-emerald-700">
          {feedback}
        </p>
      )}
      <Exceptions key={scope} facilityId={facility.id} courtId={scope} />
    </section>
  )
}
