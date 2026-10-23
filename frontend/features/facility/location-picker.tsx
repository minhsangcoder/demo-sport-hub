'use client'
import styles from '@/features/discovery/search-map.module.css'
import type { FacilityLocation } from './location-types'
import { LocationUrlInput } from './location-url-input'
import { useLocationMap } from './use-location-map'
export type { FacilityLocation } from './location-types'
export function LocationPicker({
  value,
  focusLocation,
  onChange,
  disabled = false,
}: {
  value: FacilityLocation
  focusLocation: FacilityLocation
  onChange: (value: FacilityLocation) => void
  disabled?: boolean
}) {
  const { container, ready, provider, error, retry, applyPosition } = useLocationMap(
    value,
    focusLocation,
    disabled,
    onChange,
  )
  return (
    <section className="min-w-0 space-y-3 md:col-span-2" aria-label="Vị trí cơ sở">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold">
          Vị trí cơ sở <span className="ml-1 font-bold text-red-500">*</span>
        </h2>
        <span className="rounded-full bg-emerald-50 px-3 py-1 text-xs font-semibold text-emerald-800">
          {provider === 'google' ? 'Google Maps' : 'Bản đồ OpenStreetMap (Miễn phí)'}
        </span>
      </div>

      <p id="location-instructions" className="text-sm text-slate-600">
        Bạn có thể <strong>dán đường dẫn Google Maps</strong> vào ô dưới đây để hệ thống tự động
        ghim vị trí, hoặc bấm trực tiếp lên bản đồ / kéo ghim để điều chỉnh.
      </p>

      {/* Hộp dán link Google Maps tiện lợi */}
      <LocationUrlInput value={value} disabled={disabled} onApply={applyPosition} />

      <div
        ref={container}
        className={styles.canvas}
        role="region"
        aria-label="Chọn vị trí cơ sở trên bản đồ"
        aria-describedby="location-instructions"
      />

      {error && (
        <p role="alert" className="text-sm text-red-700">
          {error}
          <button type="button" className="ml-3 underline" onClick={retry}>
            Thử lại
          </button>
        </p>
      )}
      {disabled && <p className="text-sm text-slate-500">Chọn vị trí đang tạm khóa.</p>}
      {!ready && !error && (
        <p role="status" className="text-sm text-slate-500">
          Đang khởi tạo bản đồ…
        </p>
      )}

      <div className="flex flex-wrap items-center justify-between gap-2 text-xs">
        <p role="status" className="text-slate-700 font-medium">
          {value
            ? `Đã ghim: Vĩ độ ${value.latitude.toFixed(7)} · Kinh độ ${value.longitude.toFixed(7)}`
            : 'Chưa chọn vị trí. Vui lòng dán đường dẫn Google Maps hoặc bấm lên bản đồ.'}
        </p>
        <p className="text-slate-400">
          Bản đồ tích hợp OpenStreetMap không phát sinh chi phí dịch vụ.
        </p>
      </div>
    </section>
  )
}
