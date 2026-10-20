'use client'

import Image from 'next/image'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useState, useEffect } from 'react'
import { useAuth } from '@/lib/auth-context'
import {
  Search,
  Calendar,
  MapPin,
  ArrowRight,
  Sparkles,
  Zap,
  QrCode,
  Users,
  Repeat,
  ShieldCheck,
  Building2,
  Clock,
  CheckCircle2,
} from 'lucide-react'
import { useApi } from '@/lib/use-api'
import { provinces } from '@/features/facility/administrative-divisions'
import type { Category } from '@/features/facility/types'
import type { DiscoveryResult } from '@/features/discovery/types'
import { money } from '@/features/schedule/types'
import { Footer } from '@/components/layout/footer'

export default function Home() {
  const router = useRouter()
  const { user, ready } = useAuth()

  useEffect(() => {
    if (
      ready &&
      user &&
      user.roles.includes('STAFF') &&
      !user.roles.includes('OWNER') &&
      !user.roles.includes('ADMIN')
    ) {
      router.replace('/staff/bookings')
    }
  }, [user, ready, router])

  const today = new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' })

  // State for search widget
  const [keyword, setKeyword] = useState('')
  const [sportId, setSportId] = useState('')
  const [provinceCode, setProvinceCode] = useState('')
  const [date, setDate] = useState(today)

  // Real data fetching
  const categories = useApi<Category[]>('/sport-categories')
  const courtsResource = useApi<{ items: DiscoveryResult[]; truncated: boolean }>(
    `/bookings/search?date=${today}&sort=NAME`
  )

  function handleSearchSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault()
    const params = new URLSearchParams()
    if (keyword.trim()) params.set('q', keyword.trim())
    if (sportId) params.set('sportCategoryId', sportId)
    if (provinceCode) params.set('provinceCode', provinceCode)
    if (date) params.set('date', date)
    router.push(`/search?${params.toString()}`)
  }

  // Pre-configured sport category cards with visual badges
  const featuredSports = [
    {
      name: 'Pickleball',
      image: '/images/hero-pickleball.png',
      tagline: 'Môn thể thao phát triển nhanh nhất',
      color: 'bg-emerald-500/10 text-emerald-700 border-emerald-200',
    },
    {
      name: 'Tennis',
      image: '/images/hero-tennis.png',
      tagline: 'Mặt sân chuẩn quốc tế, đèn đêm LED',
      color: 'bg-amber-500/10 text-amber-800 border-amber-200',
    },
    {
      name: 'Cầu lông',
      image: '/images/hero-paddle.png',
      tagline: 'Sân thảm chống trượt, mái che mát mẻ',
      color: 'bg-blue-500/10 text-blue-700 border-blue-200',
    },
    {
      name: 'Bóng đá sân cỏ nhân tạo',
      image: '/images/court-aerial.jpg',
      tagline: 'Sân 5 - 7 người, cỏ mềm thoát nước tốt',
      color: 'bg-emerald-500/10 text-emerald-800 border-emerald-200',
    },
  ]

  return (
    <div className="flex flex-col min-h-screen bg-slate-50 dark:bg-slate-950">
      {/* 1. HERO SECTION */}
      <section className="relative overflow-hidden bg-slate-950 text-white">
        {/* Background Image with layered gradient */}
        <div className="absolute inset-0 z-0">
          <Image
            src="/images/hero-pickleball.png"
            alt="Sân thể thao chuyên nghiệp SportHub"
            fill
            priority
            className="object-cover object-center opacity-20 filter saturate-150"
          />
          <div className="absolute inset-0 bg-gradient-to-b from-slate-950/70 via-slate-950/85 to-slate-950" />
        </div>

        <div className="relative z-10 mx-auto max-w-7xl px-4 py-20 sm:px-6 md:py-28 lg:py-32">
          {/* Eyebrow Pill */}
          <div className="inline-flex items-center gap-2 rounded-full border border-emerald-500/30 bg-emerald-950/60 px-4 py-1.5 text-xs font-bold uppercase tracking-wider text-emerald-400 backdrop-blur-sm">
            <Sparkles size={14} className="text-amber-400" />
            <span>Nền tảng đặt sân thể thao thông minh #1</span>
          </div>

          {/* Headline */}
          <h1 className="mt-6 max-w-3xl text-4xl font-black tracking-tight text-white sm:text-6xl md:text-7xl leading-[1.1]">
            Hẹn nhau ra sân.<br />
            <span className="bg-gradient-to-r from-emerald-400 via-teal-300 to-amber-300 bg-clip-text text-transparent">
              Chỉ trong 30 giây.
            </span>
          </h1>

          {/* Subtitle */}
          <p className="mt-6 max-w-2xl text-base sm:text-lg leading-relaxed text-slate-300">
            Khám phá cụm sân Pickleball, Tennis, Cầu lông và Bóng đá trên toàn quốc. Đặt lịch theo thời gian thực, check-in QR bảo mật và kết nối đồng đội dễ dàng.
          </p>

          {/* QUICK COURT SEARCH WIDGET */}
          <div className="mt-10 rounded-3xl border border-slate-700/80 bg-white/95 p-4 shadow-2xl backdrop-blur-xl sm:p-6 dark:bg-slate-900/95 dark:border-slate-800">
            <form onSubmit={handleSearchSubmit} className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-5 lg:items-end">
              {/* Keyword / Facility */}
              <div className="space-y-1.5">
                <label className="flex items-center gap-1.5 text-xs font-bold uppercase tracking-wider text-slate-700 dark:text-slate-300">
                  <MapPin size={14} className="text-emerald-600" />
                  <span>Cơ sở / Khu vực</span>
                </label>
                <input
                  type="text"
                  placeholder="Tên sân, đường, quận..."
                  value={keyword}
                  onChange={(e) => setKeyword(e.target.value)}
                  maxLength={180}
                  className="h-12 w-full rounded-xl border border-slate-200 bg-white px-3.5 text-sm font-medium text-slate-900 shadow-sm transition-all focus:border-emerald-500 focus:outline-none focus:ring-2 focus:ring-emerald-500/20 dark:border-slate-700 dark:bg-slate-800 dark:text-white"
                />
              </div>

              {/* Sport Category */}
              <div className="space-y-1.5">
                <label className="flex items-center gap-1.5 text-xs font-bold uppercase tracking-wider text-slate-700 dark:text-slate-300">
                  <Zap size={14} className="text-emerald-600" />
                  <span>Bộ môn</span>
                </label>
                <select
                  aria-label="Chọn bộ môn thể thao"
                  value={sportId}
                  onChange={(e) => setSportId(e.target.value)}
                  className="h-12 w-full rounded-xl border border-slate-200 bg-white px-3 text-sm font-medium text-slate-900 shadow-sm transition-all focus:border-emerald-500 focus:outline-none focus:ring-2 focus:ring-emerald-500/20 dark:border-slate-700 dark:bg-slate-800 dark:text-white"
                >
                  <option value="">Tất cả bộ môn</option>
                  {categories.data?.filter((c) => c.active).map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name}
                    </option>
                  ))}
                </select>
              </div>

              {/* Date */}
              <div className="space-y-1.5">
                <label className="flex items-center gap-1.5 text-xs font-bold uppercase tracking-wider text-slate-700 dark:text-slate-300">
                  <Calendar size={14} className="text-emerald-600" />
                  <span>Ngày chơi</span>
                </label>
                <input
                  type="date"
                  min={today}
                  value={date}
                  onChange={(e) => setDate(e.target.value)}
                  required
                  className="h-12 w-full rounded-xl border border-slate-200 bg-white px-3 text-sm font-medium text-slate-900 shadow-sm transition-all focus:border-emerald-500 focus:outline-none focus:ring-2 focus:ring-emerald-500/20 dark:border-slate-700 dark:bg-slate-800 dark:text-white"
                />
              </div>

              {/* Province / City */}
              <div className="space-y-1.5">
                <label className="flex items-center gap-1.5 text-xs font-bold uppercase tracking-wider text-slate-700 dark:text-slate-300">
                  <MapPin size={14} className="text-emerald-600" />
                  <span>Tỉnh / Thành</span>
                </label>
                <select
                  aria-label="Chọn tỉnh thành"
                  value={provinceCode}
                  onChange={(e) => setProvinceCode(e.target.value)}
                  className="h-12 w-full rounded-xl border border-slate-200 bg-white px-3 text-sm font-medium text-slate-900 shadow-sm transition-all focus:border-emerald-500 focus:outline-none focus:ring-2 focus:ring-emerald-500/20 dark:border-slate-700 dark:bg-slate-800 dark:text-white"
                >
                  <option value="">Tất cả tỉnh thành</option>
                  {provinces.map((p) => (
                    <option key={p.code} value={p.code}>
                      {p.name}
                    </option>
                  ))}
                </select>
              </div>

              {/* Submit Button */}
              <button
                type="submit"
                className="flex h-12 w-full items-center justify-center gap-2 rounded-xl bg-emerald-600 px-6 text-sm font-bold text-white shadow-lg shadow-emerald-600/30 transition-all hover:bg-emerald-500 hover:shadow-xl active:scale-[0.98]"
              >
                <Search size={16} />
                <span>Tìm sân ngay</span>
              </button>
            </form>

            {/* Quick Filter Tags */}
            <div className="mt-4 flex flex-wrap items-center gap-2 border-t border-slate-200/80 pt-3 text-xs text-slate-500 dark:border-slate-800 dark:text-slate-400">
              <span className="font-semibold text-slate-700 dark:text-slate-300">Gợi ý tìm nhanh:</span>
              <button
                type="button"
                onClick={() => { setKeyword('Pickleball'); router.push(`/search?q=Pickleball&date=${today}`) }}
                className="rounded-lg bg-slate-100 px-2.5 py-1 hover:bg-emerald-100 hover:text-emerald-800 dark:bg-slate-800"
              >
                Pickleball Hà Nội
              </button>
              <button
                type="button"
                onClick={() => { setKeyword('Tennis'); router.push(`/search?q=Tennis&date=${today}`) }}
                className="rounded-lg bg-slate-100 px-2.5 py-1 hover:bg-emerald-100 hover:text-emerald-800 dark:bg-slate-800"
              >
                Tennis Đà Nẵng
              </button>
              <button
                type="button"
                onClick={() => { setKeyword('Cầu lông'); router.push(`/search?q=Cầu lông&date=${today}`) }}
                className="rounded-lg bg-slate-100 px-2.5 py-1 hover:bg-emerald-100 hover:text-emerald-800 dark:bg-slate-800"
              >
                Sân Cầu lông máy lạnh
              </button>
            </div>
          </div>
        </div>
      </section>

      {/* 2. SPORT CATEGORIES GRID */}
      <section className="mx-auto max-w-7xl px-4 py-16 sm:px-6">
        <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
          <div>
            <p className="text-xs font-bold uppercase tracking-widest text-emerald-600 dark:text-emerald-400">
              Đa dạng bộ môn
            </p>
            <h2 className="mt-1 text-2xl font-black text-slate-900 sm:text-3xl dark:text-white">
              Chọn sân theo môn thể thao bạn yêu thích
            </h2>
          </div>
          <Link
            href="/search"
            className="group flex items-center gap-1.5 text-sm font-bold text-emerald-600 hover:text-emerald-700 dark:text-emerald-400"
          >
            <span>Xem tất cả danh mục</span>
            <ArrowRight size={16} className="transition-transform group-hover:translate-x-1" />
          </Link>
        </div>

        <div className="mt-8 grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
          {featuredSports.map((sport) => (
            <Link
              key={sport.name}
              href={`/search?q=${encodeURIComponent(sport.name)}&date=${today}`}
              className="group relative flex flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-all hover:border-emerald-500 hover:shadow-xl dark:border-slate-800 dark:bg-slate-900"
            >
              <div className="relative mb-5 h-36 w-full overflow-hidden rounded-xl bg-slate-100 dark:bg-slate-800">
                <Image
                  src={sport.image}
                  alt={sport.name}
                  fill
                  className="object-cover object-center transition-transform duration-500 group-hover:scale-105"
                />
                <div className="absolute inset-0 bg-gradient-to-t from-slate-950/60 to-transparent" />
                <span className="absolute bottom-3 left-3 rounded-lg bg-white/90 px-2.5 py-1 text-xs font-bold text-slate-900 backdrop-blur-sm">
                  {sport.name}
                </span>
              </div>
              <h3 className="text-lg font-bold text-slate-900 group-hover:text-emerald-600 transition-colors dark:text-white">
                {sport.name}
              </h3>
              <p className="mt-1.5 text-xs text-slate-500 dark:text-slate-400 leading-relaxed">
                {sport.tagline}
              </p>
              <div className="mt-4 flex items-center gap-1 text-xs font-bold text-emerald-600 dark:text-emerald-400">
                <span>Xem sân trống</span>
                <ArrowRight size={14} className="transition-transform group-hover:translate-x-1" />
              </div>
            </Link>
          ))}
        </div>
      </section>

      {/* 3. FEATURED COURTS LIST (REAL API DATA) */}
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
                <div key={n} className="animate-pulse rounded-2xl border border-slate-200 bg-white p-5 dark:border-slate-800 dark:bg-slate-900">
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
            <div role="alert" className="mt-8 rounded-2xl border border-red-200 bg-red-50 p-6 text-sm text-red-700 dark:border-red-900/50 dark:bg-red-950/30 dark:text-red-300">
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
                Chưa có sân nào có slot trống cho hôm nay
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
                          {item.availableSlots} slot trống
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
                        <span className="truncate">{f.addressLine}, {f.ward}, {f.province}</span>
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
                          <span className="text-xs text-slate-400">/h</span>
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

      {/* 4. VALUE PROPOSITION / WHY CHOOSE SPORTHUB */}
      <section className="mx-auto max-w-7xl px-4 py-20 sm:px-6">
        <div className="text-center max-w-2xl mx-auto">
          <p className="text-xs font-bold uppercase tracking-widest text-emerald-600 dark:text-emerald-400">
            Trải nghiệm vượt trội
          </p>
          <h2 className="mt-2 text-3xl font-black text-slate-900 sm:text-4xl dark:text-white">
            Vì sao cộng đồng thể thao tin chọn SportHub?
          </h2>
          <p className="mt-3 text-sm text-slate-500 dark:text-slate-400">
            Giải quyết triệt để vấn đề mất thời gian gọi điện hỏi sân, chuyển khoản thủ công hay tranh chấp giờ chơi.
          </p>
        </div>

        <div className="mt-14 grid grid-cols-1 gap-8 sm:grid-cols-2 lg:grid-cols-4">
          <div className="rounded-2xl border border-slate-200/90 bg-white p-7 shadow-sm dark:border-slate-800 dark:bg-slate-900">
            <div className="flex size-12 items-center justify-center rounded-xl bg-emerald-50 text-emerald-600 dark:bg-emerald-950 dark:text-emerald-400">
              <Clock size={24} />
            </div>
            <h3 className="mt-5 text-lg font-bold text-slate-900 dark:text-white">
              Cập nhật slot 100% thời gian thực
            </h3>
            <p className="mt-2 text-sm leading-relaxed text-slate-500 dark:text-slate-400">
              Ma trận giờ mở cửa đồng bộ trực tiếp với phần mềm của chủ sân, không bao giờ bị trùng lịch (double booking).
            </p>
          </div>

          <div className="rounded-2xl border border-slate-200/90 bg-white p-7 shadow-sm dark:border-slate-800 dark:bg-slate-900">
            <div className="flex size-12 items-center justify-center rounded-xl bg-blue-50 text-blue-600 dark:bg-blue-950 dark:text-blue-400">
              <QrCode size={24} />
            </div>
            <h3 className="mt-5 text-lg font-bold text-slate-900 dark:text-white">
              Mã Check-in QR bảo mật
            </h3>
            <p className="mt-2 text-sm leading-relaxed text-slate-500 dark:text-slate-400">
              Vé điện tử tạo mã QR tự động làm mới mỗi 2 phút, quét nhanh tại cổng để vào sân thi đấu ngay lập tức.
            </p>
          </div>

          <div className="rounded-2xl border border-slate-200/90 bg-white p-7 shadow-sm dark:border-slate-800 dark:bg-slate-900">
            <div className="flex size-12 items-center justify-center rounded-xl bg-amber-50 text-amber-600 dark:bg-amber-950 dark:text-amber-400">
              <Users size={24} />
            </div>
            <h3 className="mt-5 text-lg font-bold text-slate-900 dark:text-white">
              Chia tiền nhóm tự động
            </h3>
            <p className="mt-2 text-sm leading-relaxed text-slate-500 dark:text-slate-400">
              Tạo phòng nhóm, mời bạn bè bằng mã QR. Mỗi thành viên đóng phần của mình minh bạch, không ai phải chịu ứng trước.
            </p>
          </div>

          <div className="rounded-2xl border border-slate-200/90 bg-white p-7 shadow-sm dark:border-slate-800 dark:bg-slate-900">
            <div className="flex size-12 items-center justify-center rounded-xl bg-purple-50 text-purple-600 dark:bg-purple-950 dark:text-purple-400">
              <Repeat size={24} />
            </div>
            <h3 className="mt-5 text-lg font-bold text-slate-900 dark:text-white">
              Chợ chuyển nhượng vé
            </h3>
            <p className="mt-2 text-sm leading-relaxed text-slate-500 dark:text-slate-400">
              Bận việc đột xuất? Dễ dàng đăng nhượng lại slot cho người khác trên chợ chuyển nhượng công khai và an toàn.
            </p>
          </div>
        </div>
      </section>

      {/* 5. FOOTER */}
      <Footer />
    </div>
  )
}