'use client'
import { provinces } from '@/features/facility/provinces.data'
import type { Category } from '@/features/facility/types'
import { useApi } from '@/lib/use-api'
import { Calendar, MapPin, Search, Zap } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { useState } from 'react'
export function HomeSearch({ today }: { today: string }) {
  const router = useRouter()
  const [keyword, setKeyword] = useState(''),
    [sportId, setSportId] = useState(''),
    [provinceCode, setProvinceCode] = useState(''),
    [date, setDate] = useState(today)
  const categories = useApi<Category[]>('/sport-categories')
  function handleSearchSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault()
    const params = new URLSearchParams()
    if (keyword.trim()) params.set('q', keyword.trim())
    if (sportId) params.set('sportCategoryId', sportId)
    if (provinceCode) params.set('provinceCode', provinceCode)
    if (date) params.set('date', date)
    router.push(`/search?${params.toString()}`)
  }
  return (
    <div className="mt-10 rounded-3xl border border-slate-700/80 bg-white/95 p-4 shadow-2xl backdrop-blur-xl sm:p-6 dark:bg-slate-900/95 dark:border-slate-800">
      <form
        onSubmit={handleSearchSubmit}
        className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-5 lg:items-end"
      >
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
            {categories.data
              ?.filter((c) => c.active)
              .map((c) => (
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
          onClick={() => {
            setKeyword('Pickleball')
            router.push(`/search?q=Pickleball&date=${today}`)
          }}
          className="rounded-lg bg-slate-100 px-2.5 py-1 hover:bg-emerald-100 hover:text-emerald-800 dark:bg-slate-800"
        >
          Pickleball Hà Nội
        </button>
        <button
          type="button"
          onClick={() => {
            setKeyword('Tennis')
            router.push(`/search?q=Tennis&date=${today}`)
          }}
          className="rounded-lg bg-slate-100 px-2.5 py-1 hover:bg-emerald-100 hover:text-emerald-800 dark:bg-slate-800"
        >
          Quần vợt Đà Nẵng
        </button>
        <button
          type="button"
          onClick={() => {
            setKeyword('Cầu lông')
            router.push(`/search?q=Cầu lông&date=${today}`)
          }}
          className="rounded-lg bg-slate-100 px-2.5 py-1 hover:bg-emerald-100 hover:text-emerald-800 dark:bg-slate-800"
        >
          Sân Cầu lông máy lạnh
        </button>
      </div>
    </div>
  )
}
