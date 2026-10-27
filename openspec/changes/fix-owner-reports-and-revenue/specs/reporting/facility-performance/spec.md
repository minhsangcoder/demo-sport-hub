> **HISTORY ONLY — NOT AN ACTIVE REQUIREMENT SOURCE.**
> Read `openspec/CURRENT_CHANGE.md` at the project root for the current proposal.
> Do not apply, merge, or resume requirements/tasks from this document unless
> the user explicitly requests this historical change. Task counts are historical.

# Spec Delta

## Purpose

Cung cấp Báo cáo & Doanh thu cơ sở cho đồ án DEMO với bộ lọc thống nhất, KPI, biểu đồ ngày, hiệu suất từng sân và giao dịch đọc từ database. Báo cáo phân biệt doanh thu cơ sở với dòng tiền chuyển nhượng và nghĩa vụ trả/hoàn, không yêu cầu đối soát ngân hàng.

## ADDED Requirements

### Requirement: One facility calendar window governs the whole report

KPI/chart/bảng sân/giao dịch SHALL dùng cùng fromDate/toDate inclusive theo IANA timezone cơ sở, chuẩn hóa UTC nửa mở. Mặc định SHALL là hôm nay và6 ngày trước theo cơ sở, có preset7/30 ngày/tháng này; browser timezone SHALL không đổi dữ liệu.

#### Scenario: Owner applies an inclusive range
- **WHEN** chọn14/10/2026 đến hết21/10/2026 cho cơ sở Asia/Ho_Chi_Minh
- **THEN** toàn trang dùng từ13/10 17:00UTC đến trước21/10 17:00UTC, không có panel tự dùng30 ngày khác

#### Scenario: Browser timezone or daylight saving differs
- **WHEN** browser khác timezone hoặc kỳ đi qua ngày chuyển DST của cơ sở
- **THEN** tính theo lịch cơ sở, không cộng cố định24 giờ hoặc lấy offset browser

### Requirement: Invalid report ranges are rejected on both client and server

UI/API SHALL từ chối ngày sai, thiếu một đầu, đảo thứ tự hoặc quá366 ngày lịch inclusive. API SHALL trả400 độc lập UI; UI SHALL giải thích lỗi, không âm thầm dùng range khác hoặc trình bày số cũ như kết quả mới.

#### Scenario: Bad or excessive range
- **WHEN** Owner gửi fromDate sau toDate, thiếu ngày hoặc367 ngày
- **THEN** báo lỗi và không chạy report với phạm vi thay thế ngầm

### Requirement: Metric date bases and obligation cutoffs are explicit

R/P SHALL theo paidAt, F handoffAt, N demo paidAt, H demo refundedAt; cohort booking theo startsAt, giờ theo overlap. N/H còn nợ SHALL là phát sinh trừ success trước cutoff=min(cuối kỳ,asOf), gồm nợ kỳ trước. Kết quả SHALL công bố range/timezone/dateBasis/asOf/cutoff.

#### Scenario: Payment play and seller payment have different dates
- **WHEN** R/P trả20/10, handoff21/10, chơi25/10 và trả A26/10
- **THEN** từng metric vào đúng ngày tương ứng; không dời F sang26/10 hoặc thu R lần nữa ngày25/10

#### Scenario: Older obligations remain outstanding at the selected cutoff
- **WHEN** N/H phát sinh kỳ trước, chưa thành công trước cutoff hoặc chỉ được trả/hoàn sau cutoff
- **THEN** vẫn có trong số còn phải thực hiện tại cutoff, có nhãn rõ; dòng trả/hoàn nằm ở kỳ có success tương ứng

### Requirement: Demo facility revenue separates court receipts fees refunds and payables

Owner SHALL thấy R tiền sân, F phí cơ sở, tổng R+F, H policy refund thành công và sau hoàn R+F−H. P mua lại và N trả A SHALL ở dòng tiền/nghĩa vụ riêng; UNPAID/PENDING/FAILED SHALL không thành đã trả/hoàn. Không chỉ số nào SHALL gọi là doanh thu SportHub hoặc lợi nhuận tiền thật.

#### Scenario: Transfer and refund in the same period
- **WHEN** R120.000/P100.000/x5% tạo F5.000/N95.000 và H80.000 đã hoàn DEMO trong kỳ
- **THEN** tổng125.000, sau hoàn45.000, N95.000 riêng; nếu H còn PENDING/FAILED thì chưa trừ80.000

#### Scenario: Buyer paid while seller payout failed
- **WHEN** P đủ và handoff thành công nhưng N FAILED
- **THEN** B vẫn đã trả/nhận quyền, N còn nợ; F không tăng thêm và P không bị cộng toàn bộ thành doanh thu

### Requirement: Booking counts and payment status follow separate evidence

Cohort SHALL đếm booking theo startsAt với trạng thái hiện tại và asOf rõ. Đủ tiền SHALL cần receipt/contribution hợp lệ, không chỉ CONFIRMED. Contribution nhóm SHALL là tiền thực áp dụng, không thêm booking.amount hoặc đếm payment thành booking; pending/cancelled SHALL không suy thành đã sử dụng.

#### Scenario: Two statuses belong to the same court
- **WHEN** một sân có một CONFIRMED và một CHECKED_IN trong cohort, mỗi booking đã trả120.000đ trong kỳ
- **THEN** cohort có hai booking và R240.000đ, đủ tiền hai; chỉ booking có check-in hợp lệ tạo giờ sử dụng

#### Scenario: Partial group or inconsistent legacy booking
- **WHEN** nhóm300.000 mới trả100.000, hoặc legacy CONFIRMED thiếu receipt hợp lệ
- **THEN** nhóm chỉ tăng R100.000 và chưa đủ tiền; legacy được đánh dấu cần kiểm tra thay vì tự suy đã thu đủ

### Requirement: Recorded usage is bounded by check in schedule and report time

Giờ sử dụng SHALL chỉ tính booking CHECKED_IN/COMPLETED có checkedInAt và đủ tiền, từ max(startsAt,checkedInAt,đầu kỳ) tới min(endsAt,cuối kỳ,asOf), không âm. Giờ đặt SHALL tách riêng theo overlap lịch đủ điều kiện. Status quá giờ SHALL không tự được gọi đang chơi.

#### Scenario: Early late and missing check in
- **WHEN** lịch10:00–11:00 check-in09:45/10:15 hoặc chưa check-in, report sau11:00
- **THEN** giờ sử dụng lần lượt60/45/0 phút; CONFIRMED không suy thành sử dụng và CHECKED_IN lúc13:00 không kéo dài quá11:00

### Requirement: Daily charts and one row per court reconcile to the same facts

Chart SHALL có R/F/H theo ngày, bảng SHALL có một dòng mỗi sân với booking/giờ/tiền/trạng thái và tên dễ hiểu. Tổng theo component SHALL khớp KPI cùng kỳ/currency khi dữ liệu không đổi; sân ngừng hoạt động SHALL giữ lịch sử. Không đủ mẫu số lịch sử SHALL không có occupancy% giả.

#### Scenario: One court has several statuses and zero activity days
- **WHEN** cùng sân có nhiều trạng thái và có ngày không phát sinh trong nguồn đầy đủ
- **THEN** một dòng/sân, chart ngày trống bằng0, nhãn/legend/đơn vị rõ và tổng khớp KPI

#### Scenario: Historical availability is missing
- **WHEN** chỉ có lịch mở cửa hiện tại, không đủ lịch sử khả dụng
- **THEN** không dùng lịch hiện tại hoặc dữ liệu viết cứng để tính tỷ lệ lấp đầy

### Requirement: Demo transactions use simple scoped pagination

Bảng SHALL phân trang page/size, mặc định25/max100, sắp effectiveAt/id xác định và reset page khi đổi range/cơ sở/filter. Mỗi fact SHALL có ID/kind/status/amount/time/booking link an toàn. F SHALL là ghi nhận phí, không giả thêm tiền vào; P/N/technical refund SHALL tách khỏi R/F/H.

#### Scenario: Equal timestamps cross page boundaries
- **WHEN** nhiều facts cùng effectiveAt vượt một trang và dữ liệu không thay đổi
- **THEN** các trang không lặp/bỏ dòng, tổng từng loại khớp KPI tương ứng, không cần HMAC cursor hoặc snapshot phân tán

#### Scenario: Data or filter changes during browsing
- **WHEN** Owner đổi range/cơ sở hoặc hoàn tất thao tác tiền DEMO
- **THEN** đổi filter về trang đầu; dữ liệu mới được tải lại cho report, không trộn các response cũ/mới hoặc hứa snapshot xuyên nhiều nguồn

### Requirement: Financial reports require facility ownership

Dashboard/transactions/drill-down SHALL kiểm OWNER và ownership cơ sở tại server. Staff có BOOKING_REFUND/TRANSFER_PAYOUT hoặc người tạo booking SHALL không tự có quyền doanh thu Owner; kết quả SHALL không lộ QR, booking code bí mật, guest phone hoặc PII không cần thiết.

#### Scenario: Cross facility or operational role access
- **WHEN** Owner đổi facilityId sang cơ sở khác, Staff hoặc Customer gọi HTTP report trực tiếp
- **THEN** server từ chối trước khi trả tổng tiền/dòng giao dịch, dù người gọi có quyền thực hiện hoàn/trả A ở cơ sở

### Requirement: Presentation uses actual demo data and clear request states

UI SHALL dùng dữ liệu API/database, ghi Dữ liệu DEMO — không phát sinh giao dịch tiền thật và nhãn tiếng Việt. Loading/empty/error/partial/unavailable SHALL riêng; nguồn lỗi SHALL không thành tổng0 đầy đủ. Chart/table SHALL đọc được trên màn nhỏ và có nội dung hỗ trợ tiếp cận.

#### Scenario: Complete empty versus failed data
- **WHEN** nguồn tải thành công nhưng không phát sinh, hoặc source timeout
- **THEN** empty hiển thị không phát sinh; lỗi có retry/coverage rõ, không dùng số mẫu hoặc vẽ đường0 giả

#### Scenario: Requests finish out of order after a filter change
- **WHEN** response kỳ trước đến sau response kỳ mới
- **THEN** UI chỉ áp dữ liệu đúng filter đang chọn và không để số cũ mang nhãn kỳ mới

### Requirement: Existing APIs remain compatible without production reporting prerequisites

API Owner/Admin cũ SHALL giữ endpoint/shape/quyền; bổ sung report DEMO khi cần. Change SHALL không bắt xây dashboard tiền thật, bank reconciliation, signed pagination hoặc benchmark production; P2 xuất Excel/PDF/so sánh kỳ/giờ cao điểm SHALL ngoài phạm vi hiện tại.

#### Scenario: Old and new clients coexist
- **WHEN** client cũ gọi API hiện có và Owner mở report DEMO mới
- **THEN** client cũ vẫn hoạt động đúng scope, report DEMO đủ chức năng không cần ngân hàng; Admin không có doanh thu SportHub từ F