import { Clock, QrCode, Repeat, Users } from 'lucide-react'
export function HomeBenefits() {
  return (
    <section className="mx-auto max-w-7xl px-4 py-20 sm:px-6">
      <div className="text-center max-w-2xl mx-auto">
        <p className="text-xs font-bold uppercase tracking-widest text-emerald-600 dark:text-emerald-400">
          Trải nghiệm vượt trội
        </p>
        <h2 className="mt-2 text-3xl font-black text-slate-900 sm:text-4xl dark:text-white">
          Vì sao cộng đồng thể thao tin chọn SportHub?
        </h2>
        <p className="mt-3 text-sm text-slate-500 dark:text-slate-400">
          Giải quyết triệt để vấn đề mất thời gian gọi điện hỏi sân, chuyển khoản thủ công hay tranh
          chấp giờ chơi.
        </p>
      </div>

      <div className="mt-14 grid grid-cols-1 gap-8 sm:grid-cols-2 lg:grid-cols-4">
        <div className="rounded-2xl border border-slate-200/90 bg-white p-7 shadow-sm dark:border-slate-800 dark:bg-slate-900">
          <div className="flex size-12 items-center justify-center rounded-xl bg-emerald-50 text-emerald-600 dark:bg-emerald-950 dark:text-emerald-400">
            <Clock size={24} />
          </div>
          <h3 className="mt-5 text-lg font-bold text-slate-900 dark:text-white">
            Cập nhật khung giờ 100% thời gian thực
          </h3>
          <p className="mt-2 text-sm leading-relaxed text-slate-500 dark:text-slate-400">
            Ma trận giờ mở cửa đồng bộ trực tiếp với phần mềm của chủ sân, không bao giờ bị trùng
            lịch.
          </p>
        </div>

        <div className="rounded-2xl border border-slate-200/90 bg-white p-7 shadow-sm dark:border-slate-800 dark:bg-slate-900">
          <div className="flex size-12 items-center justify-center rounded-xl bg-blue-50 text-blue-600 dark:bg-blue-950 dark:text-blue-400">
            <QrCode size={24} />
          </div>
          <h3 className="mt-5 text-lg font-bold text-slate-900 dark:text-white">
            Mã xác nhận vào sân QR bảo mật
          </h3>
          <p className="mt-2 text-sm leading-relaxed text-slate-500 dark:text-slate-400">
            Vé điện tử tạo mã QR tự động làm mới mỗi 2 phút, quét nhanh tại cổng để vào sân thi đấu
            ngay lập tức.
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
            Tạo phòng nhóm, mời bạn bè bằng mã QR. Mỗi thành viên đóng phần của mình minh bạch,
            không ai phải chịu ứng trước.
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
            Bận việc đột xuất? Dễ dàng đăng nhượng lại khung giờ cho người khác trên chợ chuyển
            nhượng công khai và an toàn.
          </p>
        </div>
      </div>
    </section>
  )
}
