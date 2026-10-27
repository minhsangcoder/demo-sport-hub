'use client'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { type Preview, weekdays } from '@/features/schedule/types'
import type { usePricing } from '@/features/schedule/use-pricing'
import { api } from '@/lib/api'
import { useState } from 'react'
import { pricingInput } from './pricing-input'
export function PricingForm({
  model,
}: {
  model: Pick<
    ReturnType<typeof usePricing>,
    | 'editing'
    | 'save'
    | 'busy'
    | 'selectClass'
    | 'courts'
    | 'categories'
    | 'types'
    | 'dateMode'
    | 'setDateMode'
    | 'setError'
    | 'setBusy'
    | 'facility'
    | 'setDraft'
    | 'setEditing'
  >
}) {
  const {
    editing,
    save,
    busy,
    selectClass,
    courts,
    categories,
    types,
    dateMode,
    setDateMode,
    setError,
    setBusy,
    facility,
    setDraft,
    setEditing,
  } = model
  const initialUnit = editing?.pricingUnit ?? (editing ? 'LEGACY_SLOT' : 'HOURLY')
  const [unit, setUnit] = useState(initialUnit)
  return (
    <form
      key={editing?.id ?? 'new'}
      onSubmit={save}
      className="grid gap-4 rounded-2xl border p-5 sm:grid-cols-2"
    >
      <label className="grid gap-2 text-sm">
        Tên quy tắc
        <Input
          name="label"
          defaultValue={editing?.label ?? undefined}
          required
          maxLength={200}
          disabled={busy}
        />
      </label>
      <label className="grid gap-2 text-sm">
        Sân
        <select
          className={selectClass}
          name="courtId"
          defaultValue={editing?.courtId ?? undefined}
          disabled={busy}
        >
          <option value="">Toàn cơ sở</option>
          {courts.data?.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
      </label>
      <label className="grid gap-2 text-sm">
        Môn thể thao
        <select
          className={selectClass}
          name="sportCategoryId"
          defaultValue={editing?.sportCategoryId ?? undefined}
          disabled={busy}
        >
          <option value="">Tất cả môn</option>
          {categories.data
            ?.filter((c) => c.active)
            .map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
        </select>
      </label>
      <label className="grid gap-2 text-sm">
        Loại sân
        <select
          className={selectClass}
          name="courtTypeId"
          defaultValue={editing?.courtTypeId ?? ''}
        >
          <option value="">Tất cả loại sân</option>
          {types.data
            ?.filter((t) => t.active || t.id === editing?.courtTypeId)
            .map((t) => (
              <option key={t.id} value={t.id}>
                {t.name}
              </option>
            ))}
        </select>
      </label>
      <label className="flex items-center gap-3 text-sm">
        <input
          type="checkbox"
          checked={dateMode}
          onChange={(e) => setDateMode(e.target.checked)}
          disabled={busy}
        />
        Áp dụng một ngày cụ thể
      </label>
      {dateMode ? (
        <label className="grid gap-2 text-sm">
          Ngày cụ thể
          <Input
            name="specificDate"
            defaultValue={editing?.specificDate ?? undefined}
            type="date"
            required
            disabled={busy}
          />
        </label>
      ) : (
        <label className="grid gap-2 text-sm">
          Ngày trong tuần
          <select
            className={selectClass}
            name="dayOfWeek"
            defaultValue={editing?.dayOfWeek ?? undefined}
            disabled={busy}
          >
            {weekdays.map((day, i) => (
              <option key={day} value={i + 1}>
                {day}
              </option>
            ))}
          </select>
        </label>
      )}
      <label className="grid gap-2 text-sm">
        Đơn vị tính giá
        <select
          name="pricingUnit"
          className={selectClass}
          value={unit}
          disabled={busy}
          onChange={(event) => setUnit(event.target.value as 'HOURLY' | 'LEGACY_SLOT')}
        >
          <option value="HOURLY">Theo giờ, tính theo số phút sử dụng</option>
          <option value="LEGACY_SLOT">Theo khung lịch cũ</option>
        </select>
      </label>
      <label className="grid gap-2 text-sm">
        {unit === 'HOURLY' ? 'Giá theo giờ (đồng/giờ)' : 'Giá theo khung lịch cũ (đồng/khung)'}
        <Input
          key={unit}
          name="price"
          defaultValue={
            unit === initialUnit
              ? ((unit === 'HOURLY' ? editing?.pricePerHour : editing?.pricePerSlot) ?? undefined)
              : undefined
          }
          type="number"
          min={unit === 'HOURLY' ? 1 : 0.01}
          max={unit === 'HOURLY' ? 9999999999 : 9999999999.99}
          step={unit === 'HOURLY' ? 1 : 0.01}
          required
          disabled={busy}
        />
      </label>
      <label className="grid gap-2 text-sm">
        Từ giờ
        <Input
          name="startsAt"
          defaultValue={editing?.startsAt ?? undefined}
          type="time"
          required
          disabled={busy}
        />
      </label>
      <label className="grid gap-2 text-sm">
        Đến giờ
        <Input
          name="endsAt"
          defaultValue={editing?.endsAt ?? undefined}
          type="time"
          required
          disabled={busy}
        />
      </label>
      <label className="grid gap-2 text-sm">
        Hiệu lực từ
        <Input
          name="effectiveFrom"
          defaultValue={editing?.effectiveFrom ?? undefined}
          type="date"
          required
          disabled={busy}
        />
      </label>
      <label className="grid gap-2 text-sm">
        Hiệu lực đến (tùy chọn)
        <Input
          name="effectiveTo"
          defaultValue={editing?.effectiveTo ?? undefined}
          type="date"
          disabled={busy}
        />
      </label>
      <label className="grid gap-2 text-sm">
        Ưu tiên trong cùng cấp
        <Input
          name="priority"
          type="number"
          min={0}
          max={10000}
          defaultValue={editing?.priority ?? 0}
          required
          disabled={busy}
        />
      </label>
      <Button className="self-end" disabled={busy} type="submit">
        {busy ? 'Đang lưu…' : editing ? 'Lưu phiên bản giá mới' : 'Thêm quy tắc giá'}
      </Button>
      <label className="grid gap-2 text-sm">
        Sân xem giá dự thảo
        <select className={selectClass} name="previewCourt">
          {courts.data
            ?.filter((c) => c.enabled)
            .map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
        </select>
      </label>
      <label className="grid gap-2 text-sm">
        Ngày xem dự thảo
        <Input name="previewDate" type="date" />
      </label>
      <Button
        variant="outline"
        type="button"
        disabled={busy}
        onClick={(e) => {
          const form = e.currentTarget.closest('form')!
          if (!form.reportValidity()) return
          const f = new FormData(form),
            value = (name: string) => String(f.get(name) || '')
          if (!value('previewDate')) {
            setError('Chọn ngày xem dự thảo.')
            return
          }
          let rule
          try {
            rule = pricingInput(f, dateMode)
          } catch (failure) {
            setError(failure instanceof Error ? failure.message : 'Kiểm tra lại bảng giá.')
            return
          }
          setBusy(true)
          setError('')
          void api<Preview>(`/schedules/facilities/${facility.id}/preview/pricing`, {
            method: 'POST',
            body: JSON.stringify({
              courtId: value('previewCourt'),
              date: value('previewDate'),
              replacesRuleId: editing?.id,
              rule,
            }),
          })
            .then(setDraft)
            .catch((e) => setError(e instanceof Error ? e.message : 'Không xem được giá dự thảo'))
            .finally(() => setBusy(false))
        }}
      >
        Xem giá dự thảo (không lưu)
      </Button>
      {editing && (
        <Button
          type="button"
          variant="outline"
          onClick={() => {
            setEditing(null)
            setDraft(null)
          }}
        >
          Hủy chỉnh sửa
        </Button>
      )}
    </form>
  )
}
