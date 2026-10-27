> **HISTORY ONLY — NOT AN ACTIVE REQUIREMENT SOURCE.**
> Read `openspec/CURRENT_CHANGE.md` at the project root for the current proposal.
> Do not apply, merge, or resume requirements/tasks from this document unless
> the user explicitly requests this historical change. Task counts are historical.

# Spec Delta

## Purpose

Bổ sung giám sát chỉ đọc cho các giao dịch REAL và kết quả chi thủ công theo phân quyền hiện có. Đường dẫn capability được giữ để tương thích dự án; SportHub không hưởng tiền sân hoặc phí hoàn trả người bán do cơ sở cấu hình.

## ADDED Requirements

### Requirement: Live monitoring never attributes facility income to SportHub

Giám sát REAL SHALL quy R/F và nghĩa vụ N/H về cơ sở/Owner tương ứng. Không có KPI phí/doanh thu nền tảng từ giao dịch này; tỷ lệ SHALL theo policy Owner đã chốt, không phí cố định2% hoặc tổng P thành doanh thu.

#### Scenario: Multiple Owner fee policies coexist
- **WHEN** hai cơ sở chọn3% và7% cho P100.000đ
- **THEN** F3.000đ và7.000đ thuộc từng cơ sở, không có khoản phí SportHub hoặc hòa công nợ hai Owner

### Requirement: Live monitoring exposes collection application and manual execution separately

Người giám sát hợp lệ SHALL phân biệt tiền BANK_VERIFIED, booking/handoff áp dụng, nghĩa vụ chưa trả/hoàn và kết quả MANUALLY_RECORDED. QR/claim không có mã giao dịch SHALL chưa là chi thành công, status ngân hàng SHALL không thay quyết định bàn giao.

#### Scenario: Money was collected but handoff is unresolved
- **WHEN** B đã trả đủ mà handoff chưa rõ quyết định
- **THEN** view thể hiện tiền vào và đang bàn giao/đối soát; không cấp quyền từ view, không ghi N đã trả hoặc tự hoàn tiền như ABORTED

### Requirement: Live technical exceptions remain actionable without fabricating receipts

Giám sát SHALL hiển thị lý do cho khởi tạo chưa rõ, tiền thu thiếu/dư/trùng/đến muộn, mismatch, handoff lỗi và chi thủ công cần đối soát. Sửa lỗi SHALL không tạo chứng từ ngân hàng hoặc áp dụng tiền từ một nút duyệt kết quả tự khai.

#### Scenario: Late collection creates a technical refund obligation
- **WHEN**100.000đ đã vào nhưng không được quyền sử dụng
- **THEN** view nối khoản thu với nghĩa vụ hoàn đủ100.000đ và cơ sở chịu trách nhiệm, không áp tỷ lệ hủy hoặc ghi phí SportHub

#### Scenario: Duplicate manual bank reference is found
- **WHEN** operator dùng cùng reference cho nhiều nghĩa vụ
- **THEN** hiển thị cần đối soát và identity xung đột phù hợp scope, không tự coi mọi khoản đã chi hoặc xóa receipt cũ

### Requirement: Live monitoring authority does not grant facility money execution

Admin SHALL giữ quyền đọc giám sát, không thay Owner thực hiện chi/hoàn hoặc đổi tài khoản nhận. Owner/Staff/Customer SHALL chỉ đọc phần đúng facility/grant/participant; thông tin ngân hàng nhạy cảm SHALL được tối thiểu hóa.

#### Scenario: Admin attempts payout or payment override
- **WHEN** Admin chỉ có quyền giám sát gọi hoàn tất chi, sửa STK hoặc duyệt một payment chưa có evidence
- **THEN** server từ chối, không thay nghĩa vụ/quyền sử dụng hoặc cấp quyền chi thay cơ sở

#### Scenario: Participant changes an obligation identifier
- **WHEN** A/B yêu cầu khoản chi/hoàn của người không liên quan
- **THEN** không trả mã giao dịch, STK, QR hoặc chi tiết tài chính ngoài scope

### Requirement: Live monitoring preserves cohort history and read only semantics

REAL/DEMO/UNKNOWN SHALL có nhãn riêng và không đổi mode hồi tố. Mở/filter/retry view SHALL không tạo thu/chi/hoàn/handoff; lỗi nguồn SHALL hiển thị partial/unavailable thay tổng0 đầy đủ. Receipt thành công SHALL giữ bất biến khi đối soát.

#### Scenario: Operator reviews old simulation beside live money
- **WHEN** mở dữ liệu DEMO/legacy trong triển khai LIVE
- **THEN** lịch sử vẫn giữ đúng mode và snapshot; không suy bank-verified, backfill2% hoặc tạo chứng từ thật từ kết quả mô phỏng
