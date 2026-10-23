'use client'
import { type PriceRule, money } from '@/features/schedule/types'
import { useApi } from '@/lib/use-api'
import { useState } from 'react'

export function PricingHistory({ facility, rule }: { facility: string; rule: string }) {
  const [open, setOpen] = useState(false)
  const r = useApi<
    { ruleId: string; version: number; snapshot: PriceRule; actorId: string; recordedAt: string }[]
  >(open ? `/schedules/facilities/${facility}/pricing/${rule}/history` : null)
  return (
    <details onToggle={(e) => setOpen(e.currentTarget.open)} className="w-full text-sm">
      <summary className="cursor-pointer text-emerald-700">Lịch sử quy tắc giá</summary>
      {r.loading && <p role="status">Đang tải phiên bản…</p>}
      {r.error && <p role="alert">{r.error}</p>}
      <ol className="mt-3 space-y-3">
        {r.data?.map((version) => (
          <li key={version.version}>
            <strong>
              Phiên bản {version.version}: {money(version.snapshot.pricePerSlot)}
            </strong>
            <p className="text-slate-500">
              {new Date(version.recordedAt).toLocaleString('vi-VN')} ·{' '}
              {version.snapshot.active ? 'Đang áp dụng' : 'Đã ngừng'} ·{' '}
              {version.snapshot.effectiveFrom} → {version.snapshot.effectiveTo || 'Không giới hạn'}
            </p>
          </li>
        ))}
      </ol>
    </details>
  )
}
