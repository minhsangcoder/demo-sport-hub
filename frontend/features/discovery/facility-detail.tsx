'use client'
import Image from 'next/image'
import Link from 'next/link'
import { MapPin, Navigation, Ticket } from 'lucide-react'
import { useApi } from '@/lib/use-api'
import { useAuth } from '@/lib/auth-context'
import { FavoriteButton } from '@/features/integration/favorites'
import type { Facility, Court, Media, Category } from '@/features/facility/types'
import type { Booking } from '@/features/booking/types'
import { DirectionsCard } from './directions-card'

export function PublicFacilityDetail({ id }: { id: string }) {
  const resource = useApi<Facility>(`/facilities/${id}`)
  const courts = useApi<Court[]>(`/facilities/${id}/courts?includeDisabled=true`)
  const images = useApi<Media[]>(`/facilities/${id}/images`)
  const categories = useApi<Category[]>('/sport-categories')

  const { user } = useAuth()
  const bookingsRes = useApi<Booking[]>(user ? '/bookings' : null)
  const confirmedBooking = bookingsRes.data?.find(
    (b) =>
      b.facilityId === id &&
      (b.status === 'CONFIRMED' || b.status === 'CHECKED_IN')
  ) || bookingsRes.data?.find((b) => b.facilityId === id)

  if (resource.loading) return <p className="p-8" role="status">Đang tải cơ sở…</p>
  if (resource.error || !resource.data)
    return (
      <div className="p-8" role="alert">
        {resource.error}
        <Link className="ml-4 text-emerald-700 underline" href="/search">
          Quay lại tìm sân
        </Link>
      </div>
    )

  const f = resource.data
  return (
    <div className="mx-auto max-w-6xl px-5 py-8">
      <div className="flex flex-wrap items-center justify-between gap-3 text-sm text-slate-500">
        <Link href="/search" className="hover:underline">
          Tìm sân / {f.name}
        </Link>
        <Link
          href={`/facilities/${id}/directions${confirmedBooking ? `?bookingId=${confirmedBooking.id}` : ''}`}
          className="inline-flex items-center gap-1.5 font-semibold text-rose-700 hover:text-rose-800 hover:underline"
        >
          <Navigation className="h-4 w-4" />
          <span>Chỉ đường & Vị trí trực tiếp</span>
        </Link>
      </div>

      {confirmedBooking && (
        <div className="mt-4 flex flex-wrap items-center justify-between gap-4 rounded-2xl border border-emerald-300 bg-emerald-50/90 p-4 sm:p-5 shadow-xs">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-emerald-600 text-white font-bold">
              ✓
            </div>
            <div>
              <p className="font-bold text-emerald-950">
                Bạn đã thanh toán và có lịch đặt tại cơ sở này
              </p>
              <p className="text-xs sm:text-sm text-emerald-800">
                Mã booking: <strong className="font-mono">{confirmedBooking.id.slice(0, 8).toUpperCase()}</strong> · Trạng thái: Đã xác nhận
              </p>
            </div>
          </div>
          <Link
            href={`/customer/bookings/${confirmedBooking.id}`}
            className="inline-flex items-center gap-2 rounded-full bg-emerald-600 px-5 py-2.5 text-sm font-semibold text-white shadow-xs transition hover:bg-emerald-700"
          >
            <Ticket className="h-4 w-4" />
            <span>Quay lại phần xác nhận</span>
          </Link>
        </div>
      )}

      <div className="mt-4 flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-3xl sm:text-4xl font-black text-slate-900">{f.name}</h1>
          <div className="mt-2 flex items-center gap-2 text-slate-600">
            <MapPin className="h-4 w-4 shrink-0 text-rose-600" />
            <p className="text-sm sm:text-base">
              {f.addressLine}, {f.ward}, {f.province}
            </p>
          </div>
        </div>
        <div>
          <FavoriteButton id={id} />
        </div>
      </div>

      {images.data && images.data.length > 0 && (
        <div className="mt-6 grid gap-3 sm:grid-cols-3">
          {images.data.map((i) => (
            <Image
              unoptimized
              src={i.url}
              alt={`Ảnh ${f.name}`}
              key={i.id}
              width={480}
              height={320}
              className="h-52 w-full rounded-xl object-cover"
            />
          ))}
        </div>
      )}
      {images.error && (
        <p role="alert" className="mt-4 text-sm text-red-700">
          Không tải được ảnh: {images.error}
        </p>
      )}

      <div className="my-8 grid gap-8 md:grid-cols-[1fr_280px]">
        <div>
          <h2 className="text-xl font-bold">Về cơ sở</h2>
          <p className="mt-3 leading-relaxed text-slate-600">
            {f.description || 'Cơ sở chưa bổ sung mô tả.'}
          </p>
          <div className="mt-5 flex flex-wrap gap-2">
            {f.amenities.map((a) => (
              <span
                key={a}
                className="rounded-full bg-emerald-50 px-4 py-2 text-sm text-emerald-800"
              >
                {a}
              </span>
            ))}
          </div>
        </div>
        <aside className="rounded-2xl bg-slate-50 p-5">
          <h3 className="font-bold">Thông tin liên hệ</h3>
          <p className="mt-3 text-sm">{f.phone}</p>
          <p className="mt-2 text-sm text-slate-500">
            Lịch và giờ chơi theo {f.timezone}.
          </p>
        </aside>
      </div>

      {/* Chỉ đường & Bản đồ điều hướng trực tiếp */}
      <div className="my-10">
        <DirectionsCard
          venueName={f.name}
          addressLine={f.addressLine}
          ward={f.ward}
          province={f.province}
          latitude={f.latitude}
          longitude={f.longitude}
        />
      </div>

      <h2 className="text-2xl font-bold">Chọn sân</h2>
      {courts.loading && <p role="status" className="mt-5">Đang tải sân…</p>}
      {courts.error && <p role="alert" className="mt-5 text-red-700">{courts.error}</p>}
      {courts.data?.length === 0 && (
        <p className="mt-5 text-slate-500">Hiện chưa có sân đang nhận đặt chỗ.</p>
      )}
      <div className="mt-5 grid gap-4 sm:grid-cols-2">
        {courts.data?.map((c) => (
          <article className="rounded-xl border p-5" key={c.id}>
            <p className="text-xs font-semibold uppercase tracking-wide text-emerald-700">
              {categories.data?.find((v) => v.id === c.sportCategoryId)?.name || 'Sân thể thao'}
            </p>
            <h3 className="mt-2 text-xl font-bold">{c.name}</h3>
            <p className="mt-2 text-sm text-slate-500">{c.description || c.code}</p>
            {!c.enabled && <p className="mt-4 text-sm text-amber-800">Sân tạm ngừng nhận đặt.</p>}
            {c.enabled && (
              <Link
                href={`/facilities/${id}/courts/${c.id}`}
                className="mt-5 inline-flex rounded-full bg-emerald-600 px-5 py-2 text-sm text-white"
              >
                Kiểm tra lịch trống
              </Link>
            )}
          </article>
        ))}
      </div>
    </div>
  )
}
