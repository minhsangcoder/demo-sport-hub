'use client'
import type { useFacilityOverview } from '@/features/facility/use-facility-overview'
import { displayLabel, timezoneLabel } from '@/lib/display-labels'
import { CheckCircle2, ImageIcon, Layers, MapPin } from 'lucide-react'
export function FacilityStats({
  model,
}: {
  model: Pick<
    ReturnType<typeof useFacilityOverview>,
    'courts' | 'totalCourtsCount' | 'activeCourtsCount' | 'facility' | 'images'
  >
}) {
  const { courts, totalCourtsCount, activeCourtsCount, facility, images } = model
  return (
    <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-4">
      {/* KPI 1: Courts */}
      <div className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
        <div className="flex items-center justify-between">
          <span className="text-xs font-bold uppercase tracking-wider text-slate-500">
            Sân vận hành
          </span>
          <div className="flex size-9 items-center justify-center rounded-xl bg-emerald-50 text-emerald-600 dark:bg-emerald-950 dark:text-emerald-400">
            <Layers size={18} />
          </div>
        </div>
        <div className="mt-4 flex items-baseline gap-2">
          <span className="text-3xl font-black text-slate-900 dark:text-white">
            {courts.loading ? '…' : totalCourtsCount}
          </span>
          <span className="text-xs font-semibold text-emerald-600 dark:text-emerald-400">
            ({activeCourtsCount} sân đang bật)
          </span>
        </div>
        <p className="mt-2 text-xs text-slate-500">
          {totalCourtsCount > 0
            ? 'Sẵn sàng phục vụ khách đặt trực tuyến'
            : 'Chưa có sân nào được thêm'}
        </p>
      </div>

      {/* KPI 2: Status */}
      <div className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
        <div className="flex items-center justify-between">
          <span className="text-xs font-bold uppercase tracking-wider text-slate-500">
            Trạng thái cơ sở
          </span>
          <div className="flex size-9 items-center justify-center rounded-xl bg-amber-50 text-amber-600 dark:bg-amber-950 dark:text-amber-400">
            <CheckCircle2 size={18} />
          </div>
        </div>
        <div className="mt-4">
          <span className="text-xl font-black text-slate-900 dark:text-white">
            {facility.status === 'ACTIVE'
              ? 'Đang hoạt động'
              : facility.status === 'PENDING_APPROVAL'
                ? 'Đang chờ duyệt'
                : facility.status === 'DRAFT'
                  ? 'Bản nháp'
                  : displayLabel(facility.status)}
          </span>
        </div>
        <p className="mt-2 text-xs text-slate-500">
          {facility.status === 'ACTIVE'
            ? 'Đã hiển thị trên tìm kiếm công khai'
            : 'Hồ sơ cần được phê duyệt để mở bán'}
        </p>
      </div>

      {/* KPI 3: Location & Timezone */}
      <div className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
        <div className="flex items-center justify-between">
          <span className="text-xs font-bold uppercase tracking-wider text-slate-500">
            Khu vực & Múi giờ
          </span>
          <div className="flex size-9 items-center justify-center rounded-xl bg-blue-50 text-blue-600 dark:bg-blue-950 dark:text-blue-400">
            <MapPin size={18} />
          </div>
        </div>
        <div className="mt-4">
          <span className="text-xl font-black text-slate-900 dark:text-white truncate block">
            {facility.province || 'Toàn quốc'}
          </span>
        </div>
        <p className="mt-2 text-xs text-slate-500 truncate">
          Múi giờ vận hành: {timezoneLabel(facility.timezone)}
        </p>
      </div>

      {/* KPI 4: Photos */}
      <div className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
        <div className="flex items-center justify-between">
          <span className="text-xs font-bold uppercase tracking-wider text-slate-500">
            Hình ảnh cơ sở
          </span>
          <div className="flex size-9 items-center justify-center rounded-xl bg-purple-50 text-purple-600 dark:bg-purple-950 dark:text-purple-400">
            <ImageIcon size={18} />
          </div>
        </div>
        <div className="mt-4 flex items-baseline gap-2">
          <span className="text-3xl font-black text-slate-900 dark:text-white">
            {images.loading ? '…' : (images.data?.length ?? 0)}
          </span>
          <span className="text-xs text-slate-400">ảnh</span>
        </div>
        <p className="mt-2 text-xs text-slate-500">Ảnh thực tế giúp tăng 40% tỷ lệ đặt sân</p>
      </div>
    </div>
  )
}
