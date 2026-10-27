'use client'
import { Button } from '@/components/ui/button'
import { PricingHistory } from '@/features/schedule/pricing-history'
import { money, weekdays } from '@/features/schedule/types'
import type { usePricing } from '@/features/schedule/use-pricing'
export function PricingRules({
  model,
}: {
  model: Pick<
    ReturnType<typeof usePricing>,
    'rules' | 'courts' | 'facility' | 'busy' | 'setEditing' | 'setDateMode' | 'setDraft' | 'disable'
  >
}) {
  const { rules, courts, facility, busy, setEditing, setDateMode, setDraft, disable } = model
  return (
    <div className="space-y-3">
      {rules.data?.map((r) => (
        <article
          key={r.id}
          className={`flex flex-wrap items-center justify-between gap-4 rounded-xl border p-5 ${r.active ? '' : 'opacity-60'}`}
        >
          <div>
            <h3 className="font-bold">
              {r.label} ·{' '}
              {r.pricingUnit === 'HOURLY'
                ? r.pricePerHour == null
                  ? 'Chưa có giá'
                  : money(r.pricePerHour)
                : money(r.pricePerSlot)}
              {r.pricingUnit === 'HOURLY' ? '/giờ' : '/khung lịch cũ'}
            </h3>
            <p className="mt-2 text-sm text-slate-500">
              {r.courtId ? courts.data?.find((c) => c.id === r.courtId)?.name : 'Toàn cơ sở'} ·{' '}
              {r.specificDate || weekdays[(r.dayOfWeek || 1) - 1]} · {r.startsAt}–{r.endsAt}
            </p>
            <p className="mt-1 text-xs text-slate-500">
              {r.effectiveFrom} → {r.effectiveTo || 'không giới hạn'} · Ưu tiên {r.priority} ·{' '}
              {r.active ? 'Đang áp dụng' : 'Đã ngừng'}
            </p>
          </div>
          <PricingHistory facility={facility.id} rule={r.id} />
          {r.active && (
            <Button
              type="button"
              variant="outline"
              disabled={busy}
              onClick={() => {
                setEditing(r)
                setDateMode(Boolean(r.specificDate))
                setDraft(null)
              }}
            >
              Chỉnh sửa giá
            </Button>
          )}
          {r.active && (
            <button disabled={busy} onClick={() => disable(r.id)} className="text-sm text-red-700">
              Ngừng áp dụng
            </button>
          )}
        </article>
      ))}
    </div>
  )
}
