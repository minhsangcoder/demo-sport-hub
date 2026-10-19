'use client'

import Link from 'next/link'
import { Trophy, MapPin, Phone, Mail, ShieldCheck, ArrowUpRight, Sparkles } from 'lucide-react'

export function Footer() {
  return (
    <footer className="border-t border-slate-200/80 bg-slate-900 text-slate-300">
      {/* Top Banner CTA for Court Owners */}
      <div className="border-b border-slate-800 bg-slate-950/60 py-10">
        <div className="mx-auto flex max-w-7xl flex-col items-center justify-between gap-6 px-4 sm:px-6 sm:flex-row">
          <div>
            <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-emerald-400">
              <Sparkles size={14} />
              <span>Dành cho chủ sân & nhà vận hành</span>
            </div>
            <h3 className="mt-1 text-2xl font-bold text-white">Bạn sở hữu cụm sân thể thao?</h3>
            <p className="mt-1 text-sm text-slate-400">
              Số hóa quy trình vận hành, bảng giá linh hoạt, chống trùng lịch và tiếp cận hàng nghìn người chơi mỗi ngày.
            </p>
          </div>
          <div className="flex shrink-0 items-center gap-3">
            <Link
              href="/owner/application"
              className="inline-flex items-center gap-2 rounded-xl bg-emerald-500 px-6 py-3 text-sm font-bold text-slate-950 transition-all hover:bg-emerald-400 hover:shadow-lg hover:shadow-emerald-500/20"
            >
              <span>Đăng ký mở cơ sở</span>
              <ArrowUpRight size={16} />
            </Link>
          </div>
        </div>
      </div>

      {/* Main Footer Links */}
      <div className="mx-auto max-w-7xl px-4 sm:px-6 py-10 sm:py-14">
        <div className="grid grid-cols-1 gap-10 sm:grid-cols-2 md:grid-cols-4 lg:grid-cols-5">
          {/* Brand Info */}
          <div className="lg:col-span-2">
            <Link href="/" className="inline-flex items-center gap-2 text-2xl font-extrabold tracking-tight text-white">
              <div className="flex size-9 items-center justify-center rounded-xl bg-emerald-500 text-slate-950 font-black shadow-md shadow-emerald-500/30">
                <Trophy size={20} />
              </div>
              <span>SportHub<span className="text-amber-400">.</span></span>
            </Link>
            <p className="mt-4 max-w-sm text-sm leading-relaxed text-slate-400">
              Hệ thống kết nối người chơi và cơ sở thể thao thông minh. Đặt sân theo thời gian thực, quản lý hội nhóm, check-in mã QR bảo mật và tối ưu doanh thu sân đấu.
            </p>
            <div className="mt-6 flex items-center gap-4 text-xs text-slate-400">
              <div className="flex items-center gap-1.5">
                <ShieldCheck className="size-4 text-emerald-400" />
                <span>Thanh toán xác thực</span>
              </div>
              <div className="flex items-center gap-1.5">
                <MapPin className="size-4 text-emerald-400" />
                <span>Toàn quốc</span>
              </div>
            </div>
          </div>

          {/* Khám phá */}
          <div>
            <h4 className="text-sm font-bold uppercase tracking-wider text-white">Khám phá</h4>
            <ul className="mt-4 space-y-2.5 text-sm">
              <li>
                <Link href="/search" className="transition-colors hover:text-emerald-400">
                  Tìm sân gần đây
                </Link>
              </li>
              <li>
                <Link href="/transfers" className="transition-colors hover:text-emerald-400">
                  Chợ chuyển nhượng vé
                </Link>
              </li>
              <li>
                <Link href="/customer/groups" className="transition-colors hover:text-emerald-400">
                  Hội nhóm thể thao
                </Link>
              </li>
              <li>
                <Link href="/search?sportCategoryId=tennis" className="transition-colors hover:text-emerald-400">
                  Sân Tennis cao cấp
                </Link>
              </li>
              <li>
                <Link href="/search?sportCategoryId=pickleball" className="transition-colors hover:text-emerald-400">
                  Sân Pickleball mới
                </Link>
              </li>
            </ul>
          </div>

          {/* Dành cho Chủ sân */}
          <div>
            <h4 className="text-sm font-bold uppercase tracking-wider text-white">Chủ sân & Quản lý</h4>
            <ul className="mt-4 space-y-2.5 text-sm">
              <li>
                <Link href="/owner/application" className="transition-colors hover:text-emerald-400">
                  Đăng ký hợp tác
                </Link>
              </li>
              <li>
                <Link href="/owner/facilities" className="transition-colors hover:text-emerald-400">
                  Quản trị cơ sở
                </Link>
              </li>
              <li>
                <Link href="/staff/bookings" className="transition-colors hover:text-emerald-400">
                  Nhân viên vận hành
                </Link>
              </li>
              <li>
                <Link href="/staff/counter-booking" className="transition-colors hover:text-emerald-400">
                  Đặt sân tại quầy
                </Link>
              </li>
            </ul>
          </div>

          {/* Hỗ trợ & Liên hệ */}
          <div>
            <h4 className="text-sm font-bold uppercase tracking-wider text-white">Hỗ trợ khách hàng</h4>
            <ul className="mt-4 space-y-2.5 text-sm text-slate-400">
              <li className="flex items-center gap-2">
                <Phone size={14} className="text-emerald-400" />
                <span>1900 6868 (8:00 - 22:00)</span>
              </li>
              <li className="flex items-center gap-2">
                <Mail size={14} className="text-emerald-400" />
                <span>support@sporthub.vn</span>
              </li>
              <li className="pt-2 text-xs text-slate-500">
                Cam kết hỗ trợ giải quyết sự cố sân bãi trong 15 phút.
              </li>
            </ul>
          </div>
        </div>

        {/* Bottom Bar */}
        <div className="mt-12 flex flex-col items-center justify-between gap-4 border-t border-slate-800 pt-8 text-xs text-slate-500 sm:flex-row">
          <p>© 2026 SportHub Việt Nam. Giữ toàn quyền sở hữu trí tuệ.</p>
          <div className="flex gap-6">
            <Link href="/" className="hover:text-slate-300">Điều khoản dịch vụ</Link>
            <Link href="/" className="hover:text-slate-300">Chính sách bảo mật</Link>
            <Link href="/" className="hover:text-slate-300">Quy chế hoạt động sàn</Link>
          </div>
        </div>
      </div>
    </footer>
  )
}
