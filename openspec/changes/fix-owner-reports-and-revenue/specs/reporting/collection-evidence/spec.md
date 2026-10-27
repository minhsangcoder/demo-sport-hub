> **HISTORY ONLY — NOT AN ACTIVE REQUIREMENT SOURCE.**
> Read `openspec/CURRENT_CHANGE.md` at the project root for the current proposal.
> Do not apply, merge, or resume requirements/tasks from this document unless
> the user explicitly requests this historical change. Task counts are historical.

# Spec Delta

## Purpose

Xác định dữ liệu ứng dụng làm nguồn cho báo cáo DEMO: tiền sân, phí chuyển nhượng cơ sở hưởng, khoản phải trả A và hoàn tiền. Báo cáo phản ánh thao tác server đã lưu, tránh cộng trùng và không đòi bằng chứng ngân hàng của giao dịch giả lập.

## ADDED Requirements

### Requirement: Applied demo court payments are reportable collection facts

R SHALL chỉ gồm payment DEMO SUCCESS đã được Booking áp dụng, khớp purpose/binding/amount/currency/paidAt và receipt ứng dụng. Receipt này SHALL đủ để tính tổng DEMO; không yêu cầu merchant/bank proof. SUCCESS chưa áp dụng hoặc FAILED SHALL không được gọi là tiền sân đã áp dụng.

#### Scenario: Applied payment without a bank account
- **WHEN** payment DEMO120.000đ đã được Booking áp dụng hợp lệ và paidAt trong kỳ
- **THEN** R tăng120.000đ dù chưa có tài khoản ngân hàng; báo cáo ghi rõ số tiền mô phỏng

#### Scenario: Payment succeeds before Booking applies it
- **WHEN** Payment SUCCESS nhưng chưa có application receipt hoặc receipt sai amount/binding/currency
- **THEN** chưa cộng vào R đã áp dụng hoặc coi booking đã đủ tiền, nêu phần đang đồng bộ/cần kiểm tra thay vì sửa dữ liệu

### Requirement: Stable business identity prevents duplicate totals

Mỗi payment/effect SHALL chỉ được tính một lần theo identity nghiệp vụ, không theo số tiền/tên sân. Callback/retry/history/review/join member SHALL không nhân tổng; các payment khác nhau cùng số tiền SHALL vẫn là các khoản riêng.

#### Scenario: Duplicate and equal valued payments coexist
- **WHEN** một payment120.000đ bị replay nhiều lần và một payment khác cũng120.000đ
- **THEN** tổng240.000đ và hai khoản riêng, không120.000đ hoặc tổng theo số callback/history

### Requirement: Group collections use actual applied contributions

R nhóm SHALL là tổng contribution DEMO hợp lệ đã áp dụng, không cộng thêm booking.amount. Booking count SHALL đếm booking, không số payment/member; chỉ tổng đúng nghĩa vụ mới được coi đủ tiền/check-in.

#### Scenario: Partial then full group collection
- **WHEN** nhóm300.000đ trả100.000đ rồi200.000đ hợp lệ
- **THEN** R lần lượt100.000đ rồi300.000đ; booking count vẫn một và chỉ sau đủ tổng mới đủ điều kiện tiền để check-in

### Requirement: Original court collections survive ownership and payer changes

Chuyển nhượng SHALL giữ R tiền sân gốc dù holder khác payer. P mua lại SHALL không thuộc R. Nguồn tại quầy SHALL không tự được gắn tiền mặt; Staff/Guest/linked Customer SHALL giữ identity theo booking/payment thực tế.

#### Scenario: Counter booking is resold
- **WHEN** booking gốc R120.000đ do operator thu DEMO rồi B mua lại P100.000đ
- **THEN** R vẫn120.000đ, không220.000đ hoặc bị mất vì payer khác holder; nguồn Tại quầy không suy thành phương thức tiền mặt

### Requirement: Server facility bindings and accepted terms determine transfer facts

P/F/N SHALL gắn receiving facility/Owner, seller/buyer, acquisition và snapshot do server chốt. P SUCCESS SHALL là cashflow mua lại; F/N chỉ phát sinh khi handoff commit. Payment/payout/refund client tự khai hoặc sai binding SHALL không làm dữ liệu hợp lệ.

#### Scenario: Buyer paid and handoff is pending
- **WHEN** P100.000đ SUCCESS nhưng chưa commit handoff
- **THEN** có payment mua lại đang đồng bộ, chưa có F/N hoặc quyền B giả; không cần bank reconciliation để tiếp tục xử lý ứng dụng

#### Scenario: Handoff succeeds before seller payment
- **WHEN** P100.000/x5% đã handoff và N chưa trả
- **THEN** có P cashflow100.000, F cơ sở5.000, nghĩa vụ N95.000 và B đã trả đủ, không chờ PAID của N

### Requirement: Facility revenue excludes resale principal and seller payment

Tổng kinh doanh DEMO SHALL là R+F; sau hoàn SHALL là R+F−H policy refund đã thành công. P và N SHALL hiển thị riêng dưới dòng tiền/nghĩa vụ, không cộng thêm vào revenue; SportHub SHALL không có phí/doanh thu giao dịch. Các tổng SHALL không được gọi là lợi nhuận tiền thật.

#### Scenario: Court revenue transfer and cancellation share a period
- **WHEN** R120.000/P100.000/x5% tạo F5.000/N95.000 và H80.000 đã hoàn DEMO trong kỳ
- **THEN** tổng125.000, sau hoàn45.000; N95.000 là khoản trả A riêng, không revenue220.000 hoặc phí nền tảng

### Requirement: Successful demo execution is enough for payout and refund facts

PAID/REFUNDED do thao tác DEMO được phép đã commit SHALL là kết quả thực hiện trong ứng dụng. N SHALL ghi ở paidAt, H ở refundedAt; PENDING/UNPAID/FAILED/note SHALL chưa là đã trả/hoàn. GET SHALL không tạo kết quả tiền.

#### Scenario: Operator completes a demo refund
- **WHEN** người có BOOKING_REFUND bấm SUCCESS và server ghi REFUNDED80.000đ
- **THEN** H tăng80.000đ đúng một lần mà không cần bank proof hoặc funding

#### Scenario: A refund or seller payment fails
- **WHEN** mô phỏng FAILED hoặc chỉ có yêu cầu/ghi chú
- **THEN** giữ nghĩa vụ còn phải thực hiện, chưa cộng executed totals và chưa trừ H khỏi số sau hoàn

### Requirement: Refund reasons remain distinct from seller settlements

H SHALL chỉ gồm POLICY_CANCELLATION thuộc cơ sở, kể cả payment purpose TRANSFER của buyer cuối. Hoàn đủ P do TECHNICAL_TRANSFER_FAILURE SHALL ở dòng tiền chuyển nhượng riêng, không tự trừ R. N trả A SHALL không bị gọi là hoàn hủy cho B; hủy SHALL không tự đảo F/N.

#### Scenario: Handoff aborts after collection
- **WHEN** P100.000đ được hoàn DEMO vì ABORTED trong khi R sân gốc120.000đ vẫn hợp lệ
- **THEN** technical refund100.000đ riêng với H, F/N0 và R vẫn120.000đ

#### Scenario: Current buyer receives a policy refund
- **WHEN** A→B→C rồi C được hoàn theo policy trên giá mua của C
- **THEN** H quy đúng cơ sở và C, không hoàn lại người đã bán hoặc thay đổi F/N các handoff hợp lệ

### Requirement: Demo totals are complete application totals with an explicit label

Dữ liệu DEMO đầy đủ SHALL được tính trọn vào KPI/chart/table DEMO và ghi Dữ liệu DEMO — không phát sinh giao dịch tiền thật. Thiếu bank metadata SHALL không đẩy DEMO hợp lệ sang UNKNOWN. Record legacy không rõ SHALL không tự đổi mode hoặc hòa vào tổng DEMO hợp lệ.

#### Scenario: All application evidence is present
- **WHEN** kỳ có đủ payment/handoff/payout/refund DEMO và không có bank proof
- **THEN** hiển thị tổng DEMO đầy đủ, không báo thiếu tiền chỉ vì chưa xác minh ngân hàng hoặc bắt xây dashboard REAL/UNKNOWN

#### Scenario: Legacy payment evidence is incomplete
- **WHEN** gặp record cũ không rõ mode/amount/binding
- **THEN** nêu phần cần kiểm tra hoặc ngoài phạm vi DEMO, không giả0, tự gán DEMO hay khẳng định đã thu tiền thật

### Requirement: Date attribution preserves money history

R/P SHALL theo paidAt, F theo handoffAt, N theo demo paidAt và H theo demo refundedAt. Khoản thu SHALL không bị xóa vì hủy; refund khác kỳ SHALL không kéo khoản thu cũ vào kỳ mới. Cùng tiền tệ/kỳ SHALL có tổng khớp từ facts tới mọi panel.

#### Scenario: Refund occurs after the original collection period
- **WHEN** R nằm ngoài kỳ nhưng H có refundedAt trong kỳ
- **THEN** kỳ này có H, không lặp R; số R+F−H có thể âm và không được ép về0

### Requirement: Reporting facts are read only and minimally scoped

Facts nội bộ SHALL dùng service authentication và binding nghiệp vụ, không lộ secret hoặc credential. Khi dùng endpoint batch hiện có SHALL giữ giới hạn500 cặp payment/booking; browser SHALL không dùng endpoint đó để đọc payment tùy ý. Không yêu cầu pipeline chứng từ ngân hàng mới.

#### Scenario: Valid and invalid fact requests
- **WHEN** service gửi batch hợp lệ hoặc caller thiếu xác thực/vượt giới hạn/sai booking binding
- **THEN** request hợp lệ chỉ nhận facts cần thiết; request sai bị từ chối hoặc không trả record sai binding, không ghi tiền/quyền

### Requirement: Application data failures do not become complete zero reports

Nguồn ứng dụng lỗi/timeout/binding xung đột SHALL cho trạng thái partial/unavailable và lý do rõ. Báo cáo SHALL không biến lỗi thành tổng0 đầy đủ, sửa receipt hoặc xóa lịch sử; retry SHALL cùng scope và chỉ đọc.

#### Scenario: A source service cannot be loaded
- **WHEN** report không lấy được facts Payment/Booking cần thiết
- **THEN** metric phụ thuộc báo lỗi/một phần và có retry; metric vận hành đủ dữ liệu có thể hiển thị riêng, không vẽ chuỗi0 giả