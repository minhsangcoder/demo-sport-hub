'use client'
import { useApi } from '@/lib/use-api'
import type { BankCatalog } from './types'

export function BankSelect({
  name = 'bankBin',
  defaultValue = '',
  disabled = false,
}: {
  name?: string
  defaultValue?: string
  disabled?: boolean
}) {
  const catalog = useApi<BankCatalog>('/payments/banks')
  return (
    <div className="space-y-2">
      <label className="grid gap-2 text-sm">
        Ngân hàng nhận
        <select
          name={name}
          required
          defaultValue={defaultValue}
          disabled={disabled || catalog.loading || !catalog.data}
          className="h-11 w-full rounded-lg border bg-white px-3 text-slate-900"
        >
          <option value="">Chọn ngân hàng</option>
          {catalog.data?.banks.map((bank) => (
            <option key={bank.bin} value={bank.bin}>
              {bank.shortName} — {bank.name} ({bank.bin})
            </option>
          ))}
        </select>
      </label>
      {catalog.loading && (
        <p role="status" className="text-sm">
          Đang tải danh mục ngân hàng…
        </p>
      )}
      {catalog.error && (
        <p role="alert" className="text-sm text-red-700">
          {catalog.error}{' '}
          <button type="button" className="underline" onClick={catalog.reload}>
            Tải lại danh mục
          </button>
        </p>
      )}
      {catalog.data?.stale && (
        <p className="text-xs text-amber-800">
          Đang dùng danh mục cập nhật ngày{' '}
          {new Date(catalog.data.updatedAt).toLocaleDateString('vi-VN')}.
        </p>
      )}
    </div>
  )
}
