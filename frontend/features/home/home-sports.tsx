import { LocalCourtImage as Image } from '@/components/local-court-image'
import { ArrowRight } from 'lucide-react'
import Link from 'next/link'
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
export function HomeSports({ today }: { today: string }) {
  return (
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
  )
}
