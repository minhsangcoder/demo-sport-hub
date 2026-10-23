'use client'

import { LocalCourtImage } from '@/components/local-court-image'
import type { Media } from '@/features/facility/types'
import { money } from '@/features/schedule/types'
import { useApi } from '@/lib/use-api'
import { Clock, Heart, MapPin, Navigation, Star } from 'lucide-react'
import Link from 'next/link'
import { useState } from 'react'
import type { DiscoveryResult } from './types'

function getSportFallbackImage(sportName: string): string {
  const lower = (sportName || '').toLowerCase()
  if (lower.includes('pickleball')) return '/images/hero-pickleball.png'
  if (lower.includes('tennis')) return '/images/hero-tennis.png'
  if (lower.includes('cầu lông') || lower.includes('badminton') || lower.includes('paddle')) {
    return '/images/hero-paddle.png'
  }
  return '/images/court-aerial.jpg'
}

interface CourtCardProps {
  result: DiscoveryResult
  time: (value: string, zone: string) => string
}

export function CourtCard({ result, time }: CourtCardProps) {
  const f = result.facility
  const [isFavorite, setIsFavorite] = useState(false)
  const [imgError, setImgError] = useState(false)

  // Fetch images uploaded by the facility owner
  const imagesRes = useApi<Media[]>(`/facilities/${f.id}/images`)

  // Find image assigned specifically to this court, or the facility's first image
  const uploadedImage =
    imagesRes.data?.find((m) => m.courtId === result.courtId) || imagesRes.data?.[0]

  const fallbackImage = getSportFallbackImage(result.sportName)
  const displayImageUrl = !imgError && uploadedImage?.url ? uploadedImage.url : fallbackImage

  return (
    <article className="group relative flex flex-col overflow-hidden rounded-2xl sm:rounded-3xl border border-slate-200/90 bg-white shadow-xs transition-all duration-300 hover:-translate-y-1 hover:border-emerald-300 hover:shadow-xl">
      {/* 1. Top Image Banner (Cover Photo from Owner / Fallback) */}
      <div className="relative h-44 sm:h-48 w-full overflow-hidden bg-slate-900">
        <LocalCourtImage
          fill
          sizes="(max-width: 640px) 100vw, (max-width: 1024px) 50vw, 33vw"
          src={displayImageUrl}
          alt={`${result.courtName} - ${f.name}`}
          onError={() => setImgError(true)}
          className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
          loading="lazy"
        />

        {/* Soft gradient overlay */}
        <div className="absolute inset-0 bg-gradient-to-t from-slate-950/60 via-transparent to-black/30 pointer-events-none" />

        {/* Top-Left Badges: Rating (Photo 2 style), Đơn ngày, Sport */}
        <div className="absolute top-3 left-3 flex flex-wrap items-center gap-1.5 z-10">
          <span className="inline-flex items-center gap-1 rounded-full bg-white/95 px-2.5 py-1 text-xs font-black text-amber-500 shadow-sm backdrop-blur-xs">
            <Star className="h-3.5 w-3.5 fill-amber-400 text-amber-500" />
            <span>5.0</span>
          </span>

          <span className="rounded-full bg-emerald-600/95 px-2.5 py-1 text-xs font-bold text-white shadow-sm backdrop-blur-xs">
            Đơn ngày
          </span>

          <span className="rounded-full bg-slate-900/80 px-2.5 py-1 text-[11px] font-semibold uppercase tracking-wider text-white shadow-sm backdrop-blur-xs">
            {result.sportName}
          </span>
        </div>

        {/* Top-Right Quick Actions: Directions & Favorite */}
        <div className="absolute top-3 right-3 flex items-center gap-1.5 z-10">
          <Link
            href={`/facilities/${f.id}/directions`}
            className="flex h-9 w-9 items-center justify-center rounded-full bg-white/90 text-slate-700 shadow-sm backdrop-blur-xs transition hover:bg-emerald-600 hover:text-white"
            title="Xem chỉ đường Google Maps"
            onClick={(e) => e.stopPropagation()}
          >
            <Navigation className="h-4 w-4" />
          </Link>

          <button
            type="button"
            onClick={(e) => {
              e.preventDefault()
              e.stopPropagation()
              setIsFavorite(!isFavorite)
            }}
            className="flex h-9 w-9 items-center justify-center rounded-full bg-white/90 text-slate-700 shadow-sm backdrop-blur-xs transition hover:bg-white hover:text-rose-600"
            title={isFavorite ? 'Bỏ thích' : 'Lưu vào yêu thích'}
            aria-label="Yêu thích"
          >
            <Heart
              className={`h-4 w-4 transition ${
                isFavorite ? 'fill-rose-500 text-rose-500 scale-110' : 'text-slate-600'
              }`}
            />
          </button>
        </div>
      </div>

      {/* 2. Card Content Body */}
      <div className="flex flex-1 flex-col justify-between p-4 sm:p-5">
        <div>
          {/* Header row with Circular Facility/Court Avatar (Photo 2 style) */}
          <div className="flex items-start gap-3">
            <div className="relative flex h-12 w-12 shrink-0 items-center justify-center overflow-hidden rounded-full border-2 border-emerald-500/20 bg-slate-900 text-white shadow-xs">
              <LocalCourtImage
                width={48}
                height={48}
                src={displayImageUrl}
                alt=""
                className="h-full w-full object-cover"
                aria-hidden="true"
              />
              <div className="absolute inset-0 bg-emerald-950/20" />
            </div>

            <div className="min-w-0 flex-1">
              <h3 className="text-base sm:text-lg font-black text-slate-900 leading-tight group-hover:text-emerald-700 transition-colors truncate">
                {result.courtName}
              </h3>
              <Link
                href={`/facilities/${f.id}`}
                className="mt-0.5 block text-xs font-semibold text-emerald-700 hover:underline truncate"
              >
                {f.name}
              </Link>
            </div>
          </div>

          {/* Address & Distance */}
          <div className="mt-3 flex items-start gap-2 text-xs text-slate-600">
            <MapPin className="h-4 w-4 shrink-0 text-emerald-600 mt-0.5" />
            <p className="line-clamp-2 leading-relaxed">
              {result.distanceKm != null && (
                <strong className="text-amber-600 font-bold mr-1">
                  ({result.distanceKm.toFixed(1)}km)
                </strong>
              )}
              <span>
                {f.addressLine}, {f.ward}, {f.province}
              </span>
            </p>
          </div>

          {/* Operating hours & Available slots */}
          <div className="mt-2.5 flex items-center gap-2 text-xs text-slate-500">
            <Clock className="h-3.5 w-3.5 shrink-0 text-slate-400" />
            <span className="truncate">
              Sớm nhất {time(result.firstStartsAt, f.timezone)} –{' '}
              {time(result.firstEndsAt, f.timezone)} ·{' '}
              <strong className="text-emerald-700 font-semibold">
                {result.availableSlots} khung giờ trống
              </strong>
            </span>
          </div>

          {/* Amenities tags */}
          <div className="mt-3 flex flex-wrap gap-1.5">
            {f.amenities.map((a) => (
              <span
                key={a}
                className="rounded-lg bg-slate-100 px-2.5 py-1 text-[11px] font-medium text-slate-600 transition group-hover:bg-emerald-50 group-hover:text-emerald-800"
              >
                {a}
              </span>
            ))}
          </div>
        </div>

        {/* 3. Card Footer: Pricing (/h) & Golden Amber CTA Button */}
        <div className="mt-4 flex items-center justify-between gap-3 border-t border-slate-100 pt-3">
          <div className="min-w-0">
            <span className="text-[11px] font-medium text-slate-400 block">Giá thuê sân</span>
            <div className="flex items-baseline gap-1 whitespace-nowrap">
              <span className="text-base sm:text-lg font-black text-emerald-800 tracking-tight">
                Từ {money(result.fromPrice, result.currency)}
              </span>
              <span className="text-xs font-semibold text-slate-500">/giờ</span>
            </div>
          </div>

          {/* Yellow/Amber CTA Button without arrow */}
          <Link
            href={`/facilities/${f.id}/courts/${result.courtId}?date=${result.date}`}
            aria-label="Đặt lịch"
            className="shrink-0 inline-flex items-center justify-center rounded-xl bg-amber-500 hover:bg-amber-600 px-4 sm:px-5 py-2 text-xs sm:text-sm font-bold text-slate-950 shadow-xs transition-all active:scale-[0.98] whitespace-nowrap"
          >
            <span>Đặt lịch</span>
          </Link>
        </div>
      </div>
    </article>
  )
}
