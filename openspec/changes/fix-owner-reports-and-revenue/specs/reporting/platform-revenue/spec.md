> **HISTORY ONLY — NOT AN ACTIVE REQUIREMENT SOURCE.**
> Read `openspec/CURRENT_CHANGE.md` at the project root for the current proposal.
> Do not apply, merge, or resume requirements/tasks from this document unless
> the user explicitly requests this historical change. Task counts are historical.

# Spec Delta

## Purpose

Giữ khả năng giám sát giao dịch DEMO và lỗi nghiệp vụ theo phân quyền hiện có; tiền sân, phí và nghĩa vụ đều thuộc cơ sở. Đường dẫn artifact platform-revenue được giữ để tương thích change, không yêu cầu xây báo cáo doanh thu SportHub hoặc hệ thống đối soát ngân hàng.

## ADDED Requirements

### Requirement: Monitoring never treats facility fees as SportHub revenue

View/API SHALL quy R/F về cơ sở nhận tiền và N là nghĩa vụ cơ sở trả A. P SHALL là dòng tiền mua lại, không phí toàn bộ; SportHub SHALL không có KPI phí/doanh thu/lợi nhuận từ giao dịch này. Tỷ lệ SHALL lấy snapshot Owner của từng cơ sở, không dùng2% chung.

#### Scenario: Different facility fees coexist
- **WHEN** hai cơ sở đặt x5% và10% cho P100.000đ
- **THEN** F/N lần lượt5.000/95.000 và10.000/90.000 thuộc đúng cơ sở; không có doanh thu SportHub hoặc trộn công nợ hai cơ sở

### Requirement: Existing Admin monitoring shows application demo outcomes

Admin view hiện có SHALL có thể theo dõi payment/handoff/payout/refund/status lỗi DEMO với facility/Owner/amount/time cần thiết. Database ứng dụng SHALL là nguồn kết quả; thiếu bank metadata SHALL không khiến DEMO hợp lệ bị coi chưa thực hiện hoặc buộc xây console kế toán mới.

#### Scenario: Demo success exists without bank evidence
- **WHEN** payment/handoff/trả A/hoàn B đã thành công trong ứng dụng
- **THEN** view giám sát hiển thị kết quả DEMO tương ứng, không yêu cầu merchant verified, sao kê, nguồn khả dụng hay proof chuyển khoản

### Requirement: Unfinished obligations remain distinct from successful execution

Monitoring SHALL tách P đã thanh toán, handoff, N UNPAID/FAILED/PAID và H PENDING/FAILED/REFUNDED/NO_REFUND. PAID/REFUNDED SHALL cần thao tác DEMO server đã commit; note/request/GET SHALL không thay kết quả. N chưa trả SHALL không làm B mất quyền hoặc tăng F.

#### Scenario: Seller payment fails after successful handoff
- **WHEN** B đã nhận quyền và N95.000đ FAILED
- **THEN** có nghĩa vụ cơ sở còn phải trả95.000đ, F giữ nguyên và B đủ tiền; không gọi N là tiền SportHub giữ/hưởng

#### Scenario: Refund is pending
- **WHEN** H80.000đ mới được yêu cầu hoặc mô phỏng FAILED
- **THEN** chưa ghi đã hoàn80.000đ hoặc trừ H như thành công, booking đã hủy vẫn CANCELLED

### Requirement: Cancellation refunds and technical refunds remain separate

Monitoring SHALL phân biệt H theo r của buyer hiện tại, full P khi handoff ABORTED và N trả seller. ABORTED SHALL có F0/N0; hủy sau handoff SHALL không tự đảo F/N hoặc hoàn thêm A. Các trạng thái này SHALL dùng quyết định ứng dụng, không xác minh tiền thật.

#### Scenario: Aborted delivery versus later cancellation
- **WHEN** P100.000đ bị ABORTED hoặc handoff thành công rồi hủy r80% với x5%
- **THEN** nhánh đầu có quyền hoàn100.000/F0/N0; nhánh sau H80.000/F5.000/N95.000, không gộp cùng loại hoàn

### Requirement: Money monitoring respects dates and facility attribution

Các view có tổng SHALL giữ R/P paidAt, F handoffAt, N demo paidAt, H demo refundedAt theo range/timezone/asOf đã công bố. Nghĩa vụ còn nợ SHALL tính phát sinh trừ success trước cutoff, gồm nợ kỳ trước; không đổi ngày phí sang ngày trả A hoặc nhân effect do replay.

#### Scenario: Prior period seller payable remains unpaid
- **WHEN** N95.000đ phát sinh trước kỳ và chưa trả trước cutoff dù không có payment mới
- **THEN** view có số nghĩa vụ95.000đ tại cutoff nếu hiển thị công nợ, không gọi đó là doanh thu hoặc nợ SportHub

### Requirement: Roles grant distinct reading and execution capabilities

Owner SHALL xem cơ sở mình; Staff SHALL chỉ xem/thực hiện tối thiểu đúng BOOKING_REFUND/TRANSFER_PAYOUT; A/B SHALL chỉ xem giao dịch mình. Admin SHALL giữ quyền đọc giám sát hiện có, không tự authorize/thực hiện tiền Owner. Server SHALL kiểm scope mọi request.

#### Scenario: Admin or Staff exceeds execution authority
- **WHEN** Admin không là operator hợp lệ gọi trả A/hoàn B, hoặc Staff dùng grant sai loại/cơ sở
- **THEN** từ chối; quyền xem giám sát hoặc thực hiện tiền không tự cấp dashboard doanh thu Owner

#### Scenario: Participants inspect the same transfer
- **WHEN** A/B mở acquisition của mình lúc N chưa trả
- **THEN** A thấy cơ sở phải trả N và trạng thái DEMO, B thấy đã thanh toán/nhận quyền; không thấy giao dịch khách khác hoặc quyền tự đánh dấu trả/hoàn

### Requirement: Monitoring remains read only private and compatible

Mở/filter/retry view SHALL không tạo payment, payout, refund, handoff hoặc sửa booking. Existing Admin/Owner API SHALL giữ shape/quyền; dữ liệu SHALL tối thiểu, có nhãn DEMO và loading/error/partial rõ. Không lộ QR/guest phone/secrets hoặc giả source lỗi thành tổng0 đầy đủ.

#### Scenario: A source fails during monitoring
- **WHEN** service ứng dụng timeout khi Admin/Owner tải view
- **THEN** view báo lỗi/một phần và retry chỉ đọc, không post tiền hoặc chỉnh dữ liệu để ép tổng khớp

### Requirement: Production financial operations are outside demo acceptance

Nghiệm thu change SHALL không yêu cầu bank reconciliation, funding/top-up/reserve, bank return/chargeback, KYC, beneficiary verification, duyệt nhiều cấp, live provider hoặc signed snapshot pagination. Code/migrations production đã có SHALL được giữ, không bị xóa hoặc trở thành gate cho DEMO.

#### Scenario: Complete school project demonstration
- **WHEN** chưa tích hợp ngân hàng thật nhưng server DEMO đã có đủ policy, quyền, số tiền, lỗi/retry và báo cáo
- **THEN** có thể demo trọn luồng nghiệp vụ; phần tích hợp tiền thật cần change riêng thay vì chặn nghiệm thu DEMO