'use client'
import { LocalCourtImage as Image } from '@/components/local-court-image'
import type { DiscoveryResult } from '@/features/discovery/types'
import { money } from '@/features/schedule/types'
import { useApi } from '@/lib/use-api'
import { ArrowRight, Building2, Clock, MapPin } from 'lucide-react'
import Link from 'next/link'
export function HomeAvailability({ today }: { today: string }) {
  const courtsResource = useApi<{ items: DiscoveryResult[]; truncated: boolean }>(
    `/bookings/search?date=${today}&sort=NAME`,
  )
  return (
    <section className="border-t border-slate-200/80 bg-white py-16 dark:border-slate-800 dark:bg-slate-900/50">
      <div className="mx-auto max-w-7xl px-4 sm:px-6">
        <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
          <div>
            <div className="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-3 py-1 text-xs font-bold text-emerald-700 dark:bg-emerald-950/80 dark:text-emerald-400">
              <Clock size={13} />
              <span>Cập nhật theo thời gian thực</span>
            </div>
            <h2 className="mt-2 text-2xl font-black text-slate-900 sm:text-3xl dark:text-white">
              Sân thể thao nổi bật & Còn lịch trống hôm nay
            </h2>
          </div>
          <Link
            href="/search"
            className="inline-flex items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-4 py-2 text-sm font-bold text-slate-700 shadow-sm hover:border-emerald-500 hover:text-emerald-600 transition-colors dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200"
          >
            <span>Mở bộ lọc tìm kiếm</span>
            <ArrowRight size={15} />
          </Link>
        </div>

        {/* Loading Skeleton */}
        {courtsResource.loading && (
          <div className="mt-8 grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
            {[1, 2, 3].map((n) => (
              <div
                key={n}
                className="animate-pulse rounded-2xl border border-slate-200 bg-white p-5 dark:border-slate-800 dark:bg-slate-900"
              >
                <div className="h-44 w-full rounded-xl bg-slate-200 dark:bg-slate-800" />
                <div className="mt-4 h-5 w-2/3 rounded bg-slate-200 dark:bg-slate-800" />
                <div className="mt-2 h-4 w-1/2 rounded bg-slate-200 dark:bg-slate-800" />
                <div className="mt-6 flex justify-between">
                  <div className="h-6 w-1/3 rounded bg-slate-200 dark:bg-slate-800" />
                  <div className="h-9 w-1/3 rounded bg-slate-200 dark:bg-slate-800" />
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Error State */}
        {courtsResource.error && (
          <div
            role="alert"
            className="mt-8 rounded-2xl border border-red-200 bg-red-50 p-6 text-sm text-red-700 dark:border-red-900/50 dark:bg-red-950/30 dark:text-red-300"
          >
            <p className="font-bold">Không thể tải danh sách sân gợi ý.</p>
            <p className="mt-1">{courtsResource.error}</p>
            <button
              type="button"
              onClick={courtsResource.reload}
              className="mt-3 inline-block font-bold text-red-800 underline dark:text-red-200"
            >
              Tải lại danh sách
            </button>
          </div>
        )}

        {/* Empty State */}
        {!courtsResource.loading && courtsResource.data?.items.length === 0 && (
          <div className="mt-8 rounded-2xl border border-dashed border-slate-300 p-12 text-center dark:border-slate-700">
            <Building2 className="mx-auto size-12 text-slate-400" />
            <h3 className="mt-3 text-lg font-bold text-slate-800 dark:text-slate-200">
              Chưa có sân nào có khung giờ trống cho hôm nay
            </h3>
            <p className="mt-1 text-sm text-slate-500">
              Hãy thử chọn ngày mai hoặc mở rộng khu vực tìm kiếm trên hệ thống.
            </p>
            <Link
              href="/search"
              className="mt-5 inline-flex items-center gap-2 rounded-xl bg-emerald-600 px-5 py-2.5 text-sm font-bold text-white shadow-sm hover:bg-emerald-500"
            >
              <span>Khám phá toàn bộ sân</span>
              <ArrowRight size={16} />
            </Link>
          </div>
        )}

        {/* Court Cards Grid */}
        {!courtsResource.loading && courtsResource.data && courtsResource.data.items.length > 0 && (
          <div className="mt-8 grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
            {courtsResource.data.items.slice(0, 6).map((item) => {
              const f = item.facility
              return (
                <article
                  key={item.courtId}
                  className="court-card-hover flex flex-col justify-between overflow-hidden rounded-2xl border border-slate-200/90 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900"
                >
                  <div>
                    {/* Top Image Preview & Sport Badge */}
                    <div className="relative mb-4 h-44 w-full overflow-hidden rounded-xl bg-slate-100 dark:bg-slate-800">
                      <Image
                        src={
                          item.sportName?.toLowerCase().includes('tennis')
                            ? '/images/hero-tennis.png'
                            : item.sportName?.toLowerCase().includes('pickleball')
                              ? '/images/hero-pickleball.png'
                              : '/images/court-aerial.jpg'
                        }
                        alt={item.courtName}
                        fill
                        className="object-cover object-center"
                      />
                      <div className="absolute top-3 left-3 rounded-md bg-emerald-700/90 px-2.5 py-1 text-[11px] font-extrabold uppercase tracking-wider text-white shadow-sm backdrop-blur-sm">
                        {item.sportName}
                      </div>
                      <div className="absolute bottom-3 right-3 rounded-md bg-slate-950/80 px-2 py-0.5 text-[11px] font-bold text-emerald-400 backdrop-blur-sm">
                        {item.availableSlots} khung giờ trống
                      </div>
                    </div>

                    {/* Court & Facility Title */}
                    <h3 className="text-xl font-black text-slate-900 dark:text-white">
                      {item.courtName}
                    </h3>
                    <Link
                      href={`/facilities/${f.id}`}
                      className="mt-1 block text-sm font-semibold text-emerald-700 hover:underline dark:text-emerald-400"
                    >
                      {f.name}
                    </Link>

                    {/* Location */}
                    <p className="mt-2 flex items-center gap-1.5 text-xs text-slate-500 dark:text-slate-400">
                      <MapPin size={14} className="shrink-0 text-slate-400" />
                      <span className="truncate">
                        {f.addressLine}, {f.ward}, {f.province}
                      </span>
                    </p>

                    {/* Amenities Pills */}
                    <div className="mt-3 flex flex-wrap gap-1.5">
                      {f.amenities.slice(0, 3).map((a) => (
                        <span
                          key={a}
                          className="rounded-md bg-slate-100 px-2 py-0.5 text-[11px] font-medium text-slate-600 dark:bg-slate-800 dark:text-slate-300"
                        >
                          {a}
                        </span>
                      ))}
                    </div>
                  </div>

                  {/* Bottom Pricing & CTA */}
                  <div className="mt-6 flex items-center justify-between border-t border-slate-100 pt-4 dark:border-slate-800">
                    <div>
                      <span className="text-[11px] font-medium text-slate-400 block">Giá từ</span>
                      <div className="flex items-baseline gap-1">
                        <span className="text-xl font-black text-emerald-700 dark:text-emerald-400">
                          {money(item.fromPrice, item.currency)}
                        </span>
                        <span className="text-xs text-slate-400">/giờ</span>
                      </div>
                    </div>
                    <Link
                      href={`/facilities/${f.id}/courts/${item.courtId}?date=${item.date || today}`}
                      className="inline-flex items-center gap-1.5 rounded-xl bg-slate-900 px-4 py-2.5 text-xs font-bold text-white shadow-sm transition-all hover:bg-emerald-600 dark:bg-emerald-600 dark:hover:bg-emerald-500"
                    >
                      <span>Đặt ngay</span>
                      <ArrowRight size={14} />
                    </Link>
                  </div>
                </article>
              )
            })}
          </div>
        )}
      </div>
    </section>
  )
}
