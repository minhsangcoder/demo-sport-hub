'use client'
import { CalendarCheck2, MapPin, Repeat, Star, User, Users } from 'lucide-react'
import { LocalCourtImage as Image } from '@/components/local-court-image'
export function AuthShowcase() {
  return (
    <div className="relative hidden w-full overflow-hidden rounded-3xl bg-slate-900 lg:col-span-6 lg:flex lg:flex-col lg:justify-between p-8 xl:p-10 text-white min-h-[580px]">
      {/* Background image & deep gradients */}
      <div className="absolute inset-0 z-0">
        <Image
          src="/images/hero-pickleball.png"
          alt="Sân thể thao SportHub"
          fill
          className="object-cover object-center opacity-40 scale-105 transition-transform duration-1000 hover:scale-100"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-slate-950 via-slate-900/70 to-emerald-950/60" />
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_top_right,_var(--tw-gradient-stops))] from-emerald-500/20 via-transparent to-transparent" />
      </div>

      {/* Top Bar */}
      <div className="relative z-10 flex items-center justify-between gap-3">
        <div className="inline-flex items-center gap-2 rounded-full border border-white/15 bg-white/10 px-3 py-1.5 text-xs font-medium text-slate-200 backdrop-blur-md whitespace-nowrap">
          <span className="size-1.5 rounded-full bg-emerald-400 animate-pulse" />
          <span>Đặt sân trực tuyến</span>
        </div>
        <div className="flex items-center gap-1.5 text-xs text-slate-300 font-medium whitespace-nowrap">
          <MapPin size={12} className="text-emerald-400 shrink-0" />
          <span>Hà Nội & TP.HCM</span>
        </div>
      </div>

      {/* Main Punchline and Features */}
      <div className="relative z-10 space-y-6 my-auto py-6">
        <h2 className="text-2xl xl:text-3xl font-extrabold tracking-tight text-white leading-tight">
          Sẵn sàng cho trận cầu đỉnh cao tiếp theo của bạn.
        </h2>
        <p className="text-slate-300 text-xs sm:text-sm leading-relaxed max-w-md">
          Đặt sân Pickleball, Cầu lông, quần vợt & Bóng đá trong 30 giây. Xác nhận vào sân bằng mã
          QR, chia tiền nhóm và chuyển nhượng linh hoạt.
        </p>

        {/* Feature chips */}
        <div className="grid gap-3 pt-1">
          <div className="flex items-center gap-3.5 rounded-2xl border border-white/10 bg-white/5 p-3.5 backdrop-blur-md transition-all hover:bg-white/10">
            <div className="flex size-10 items-center justify-center rounded-xl bg-emerald-500/20 text-emerald-400 shrink-0">
              <CalendarCheck2 size={20} />
            </div>
            <div>
              <div className="text-sm font-bold text-white">Đặt sân & Giữ chỗ tức thì</div>
              <div className="text-xs text-slate-300">
                Lịch khung giờ trống thời gian thực, tạm giữ chỗ 15 phút
              </div>
            </div>
          </div>

          <div className="flex items-center gap-3.5 rounded-2xl border border-white/10 bg-white/5 p-3.5 backdrop-blur-md transition-all hover:bg-white/10">
            <div className="flex size-10 items-center justify-center rounded-xl bg-teal-500/20 text-teal-400 shrink-0">
              <Users size={20} />
            </div>
            <div>
              <div className="text-sm font-bold text-white">Đặt sân nhóm & Chia tiền</div>
              <div className="text-xs text-slate-300">
                Mời bạn bè qua mã QR, tự động chia tiền & nhắc thanh toán
              </div>
            </div>
          </div>

          <div className="flex items-center gap-3.5 rounded-2xl border border-white/10 bg-white/5 p-3.5 backdrop-blur-md transition-all hover:bg-white/10">
            <div className="flex size-10 items-center justify-center rounded-xl bg-amber-500/20 text-amber-400 shrink-0">
              <Repeat size={20} />
            </div>
            <div>
              <div className="text-sm font-bold text-white">Sàn chuyển nhượng khung giờ sân</div>
              <div className="text-xs text-slate-300">
                Bận việc đột xuất? Nhượng lại khung giờ an toàn & sang tên vé
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Bottom Proof */}
      <div className="relative z-10 flex items-center justify-between border-t border-white/10 pt-4 text-xs text-slate-400">
        <div className="flex items-center gap-2.5">
          <div className="flex -space-x-2">
            <div className="size-7 rounded-full border-2 border-slate-900 bg-slate-700 flex items-center justify-center text-slate-300">
              <User size={13} />
            </div>
            <div className="size-7 rounded-full border-2 border-slate-900 bg-emerald-700 flex items-center justify-center text-white">
              <User size={13} />
            </div>
            <div className="size-7 rounded-full border-2 border-slate-900 bg-teal-700 flex items-center justify-center text-white">
              <User size={13} />
            </div>
          </div>
          <span className="font-semibold text-slate-300">10,000+ Vận động viên</span>
        </div>
        <div className="flex items-center gap-1.5 text-slate-300 font-semibold">
          <Star size={14} className="fill-amber-400 text-amber-400" />
          <span>4.9</span>
          <span className="text-slate-400 font-normal text-[11px]">(1.2k+ đánh giá)</span>
        </div>
      </div>
    </div>
  )
}
