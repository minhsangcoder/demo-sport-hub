import type { RefundPolicy } from '@/features/facility/refund-policy'

export function RefundPolicyNotice({ policy }: { policy?: RefundPolicy }) {
  if (!policy?.configured || policy.rateBps == null)
    return (
      <p className="rounded-xl bg-amber-50 p-4 text-sm text-amber-900">
        Lượt đặt hoặc giữ chỗ cũ chưa có chính sách hoàn được chốt. Liên hệ cơ sở để đối soát; với
        giữ chỗ cũ, hãy chọn sân lại sau khi chủ cơ sở cấu hình chính sách.
      </p>
    )
  return (
    <aside
      aria-label="Chính sách hoàn đã chốt"
      className="space-y-2.5 rounded-xl bg-slate-50 p-4 text-sm text-slate-600"
    >
      <p className="text-amber-900">
        <strong className="font-semibold text-rose-700">Quy định hoàn tiền:</strong> Không áp dụng
        hoàn tiền cho người đặt ban đầu khi tự hủy lượt đặt. Nếu không thể đến sân, quý khách có thể{' '}
        <strong>Đăng chuyển nhượng lượt đặt</strong> để sang nhượng cho người khác.
      </p>
    </aside>
  )
}
