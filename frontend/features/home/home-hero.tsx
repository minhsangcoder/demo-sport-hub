import { LocalCourtImage as Image } from '@/components/local-court-image'
import { Sparkles } from 'lucide-react'
import { HomeSearch } from './home-search'
export function HomeHero({ today }: { today: string }) {
  return (
    <section className="relative overflow-hidden bg-slate-950 text-white">
      {/* Background Image with layered gradient */}
      <div className="absolute inset-0 z-0">
        <Image
          src="/images/hero-pickleball.png"
          alt="Sân thể thao chuyên nghiệp SportHub"
          fill
          preload
          sizes="100vw"
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
          Hẹn nhau ra sân.
          <br />
          <span className="bg-gradient-to-r from-emerald-400 via-teal-300 to-amber-300 bg-clip-text text-transparent">
            Chỉ trong 30 giây.
          </span>
        </h1>

        {/* Subtitle */}
        <p className="mt-6 max-w-2xl text-base sm:text-lg leading-relaxed text-slate-300">
          Khám phá cụm sân Pickleball, quần vợt, Cầu lông và Bóng đá trên toàn quốc. Đặt lịch theo
          thời gian thực, xác nhận vào sân QR bảo mật và kết nối đồng đội dễ dàng.
        </p>

        {/* QUICK COURT SEARCH WIDGET */}
        <HomeSearch today={today} />
      </div>
    </section>
  )
}
