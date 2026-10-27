# Spec Delta

## Purpose

Không cấp quyền hoàn tiền riêng cho người mua lại khi tự hủy; bảo toàn nghĩa vụ đã ghi nhận và hoàn vận hành ngoài phạm vi chuyển nhượng.

## ADDED Requirements

### Requirement: Resold bookings do not grant new cancellation refund benefits

Booking đã bàn giao qua chuyển nhượng SHALL không tạo báo giá hoặc nghĩa vụ hoàn mới khi tự hủy, cả REAL/P2P và DEMO. Guard SHALL kiểm handoff trong cùng court lock, áp dụng cả Customer, Owner và Staff có BOOKING_REFUND. Giá mua lại P và tỷ lệ policy cũ SHALL không tạo quyền hoàn.

#### Scenario: Buyer or operator requests a refund after handoff
- **WHEN** B, Owner hoặc Staff gửi quote/cancel-with-policy cho booking đã bàn giao A→B, kể cả dùng quote cũ
- **THEN** server từ chối, không đổi holder/trạng thái booking và không tạo nghĩa vụ hoàn

#### Scenario: Cancellation requests race after handoff
- **WHEN** nhiều request hoàn mới đến đồng thời sau bàn giao
- **THEN** tất cả bị từ chối và không ghi refund/receipt giả

### Requirement: Customer screens do not advertise resale cancellation refunds

Customer booking/payment SHALL bỏ khối phụ gồm mô tả 100% chuyển nhượng, chi tiết hoàn riêng cho người mua lại và ghi chú check-in/đối soát phía dưới. Chi tiết booking và các trang chuyển nhượng/cơ sở SHALL không hiển thị form hoặc quảng bá tỷ lệ hoàn riêng cho người mua lại. Luồng chuyển nhượng và kiểm tra đủ tiền check-in SHALL vẫn hoạt động.

#### Scenario: Customer opens the payment summary
- **WHEN** snapshot cũ chứa rateBps=9500
- **THEN** vẫn hiển thị quy định không hoàn khi tự hủy, không có chi tiết hoàn95% hoặc khối phụ đã bỏ

### Requirement: Recorded obligations and unrelated refunds remain intact

Khoản hoàn đã ghi nhận, funding, beneficiary, snapshots, F/N/H, lịch sử thu chi SHALL được giữ và thực hiện theo quyền hiện hành. Replay yêu cầu đã hoàn tất SHALL không tạo bản sao. Group/counter và hoàn thu lỗi ngoài phạm vi này SHALL không bị xóa. Nghĩa vụ chưa thực hiện vẫn khóa ngân hàng theo nguồn trách nhiệm tương ứng.

#### Scenario: A historical buyer refund is still unpaid
- **WHEN** Owner hoặc Staff có quyền đúng cơ sở thực hiện nghĩa vụ đã ghi nhận trước thay đổi
- **THEN** dùng đúng người nhận/nguồn thu cũ và ghi MANUALLY_RECORDED, không tạo quyền hủy mới

### Requirement: Peer disputes resolve usage only

Owner SHALL chỉ phân xử quyền sử dụng; tranh chấp A–B không tạo hoàn P hoặc H từ cơ sở. Tiền A–B do hai bên khai/xác nhận riêng.

#### Scenario: Owner awards usage to A
- **WHEN** quyết định tranh chấp giữ quyền sân cho A
- **THEN** không phát sinh khoản cơ sở hoàn B hoặc tự xác nhận B đã được trả tiền
