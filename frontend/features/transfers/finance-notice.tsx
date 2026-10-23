import { demoVnd } from '@/features/facility/demo-report'
import type { TransferFinanceTerms } from './types'
export function TransferFinanceNotice({ terms }: { terms: TransferFinanceTerms }) {
  if (terms.mode !== 'DEMO')
    return (
      <p className="my-5 rounded-xl bg-amber-50 p-4 text-sm text-amber-900">
        Giao dịch dùng điều khoản cũ. Cần kiểm tra và chấp nhận điều khoản mô phỏng của cơ sở trước
        giao dịch mới; không tự áp phí hiện tại vào lịch sử.
      </p>
    )
  return (
    <section className="my-5 space-y-3 rounded-xl border border-emerald-200 bg-emerald-50 p-5 text-sm">
      <h2 className="font-bold">Điều khoản chuyển nhượng mô phỏng</h2>
      <p>
        Bên nhận thanh toán:{' '}
        <strong>{terms.receiverName || 'Cơ sở của sân được chuyển nhượng'}</strong>.
      </p>
      <dl className="grid grid-cols-2 gap-3">
        <dt>Người mua thanh toán</dt>
        <dd className="text-right font-semibold">{demoVnd(terms.amount)}</dd>
        <dt>Phí cơ sở ({(terms.feeBps / 100).toLocaleString('vi-VN')}%)</dt>
        <dd className="text-right">{demoVnd(terms.quotedFeeAmount)}</dd>
        <dt>Cơ sở phải trả người bán</dt>
        <dd className="text-right font-semibold">{demoVnd(terms.quotedSellerNetAmount)}</dd>
        <dt>Hoàn khi hủy hợp lệ</dt>
        <dd className="text-right font-semibold text-emerald-700">
          {(terms.refundPolicy.rateBps / 100).toLocaleString('vi-VN')}% giá mua lại
        </dd>
      </dl>
      <p>
        Người mua nhận quyền sau thanh toán mô phỏng thành công và bàn giao, sử dụng theo lịch;
        không phải chờ cơ sở trả người bán.
      </p>
      <p className="text-xs text-slate-600">
        Nếu người mua lại hủy hợp lệ trước giờ chơi, chủ cơ sở hoặc nhân viên được cấp quyền sẽ hoàn
        tiền cho người mua lại theo tỷ lệ{' '}
        {(terms.refundPolicy.rateBps / 100).toLocaleString('vi-VN')}% trên giá đã mua lại. Phí thuộc
        cơ sở; SportHub không hưởng phí. Không phát sinh giao dịch tiền thật.
      </p>
    </section>
  )
}
