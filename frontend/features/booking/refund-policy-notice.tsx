import { refundBpsToPercent, type RefundPolicy } from '@/features/facility/refund-policy'

export function RefundPolicyNotice({ policy }: { policy?: RefundPolicy }) {
  if (!policy?.configured || policy.rateBps == null) return <p className="rounded-xl bg-amber-50 p-4 text-sm text-amber-900">Booking hoặc giữ chỗ cũ chưa có chính sách hoàn được chốt. Liên hệ cơ sở để đối soát; với giữ chỗ cũ, hãy chọn sân lại sau khi Owner cấu hình chính sách.</p>
  return <aside aria-label="Chính sách hoàn đã chốt" className="space-y-2 rounded-xl bg-slate-50 p-4 text-sm text-slate-600">
    <p>Hủy hợp lệ trước giờ chơi: hoàn <strong>{refundBpsToPercent(policy.rateBps)}%</strong> theo chính sách cơ sở, phiên bản {policy.version}.</p>
    <p>Owner hoặc nhân viên được cấp quyền thực hiện hoàn về nguồn thanh toán ban đầu. Với booking chuyển nhượng, tỷ lệ tính trên giá người giữ hiện tại đã mua lại.</p>
    <p>Chỉ được check-in khi đã thanh toán đủ. Lỗi thu tiền hoặc chuyển nhượng chưa bàn giao thành công được xử lý hoàn đủ khoản đã thu.</p>
  </aside>
}
