> **HISTORY ONLY — NOT AN ACTIVE REQUIREMENT SOURCE.**
> Read `openspec/CURRENT_CHANGE.md` at the project root for the current proposal.
> Do not apply, merge, or resume requirements/tasks from this document unless
> the user explicitly requests this historical change. Task counts are historical.

# Spec Delta

## Purpose

Quy định chính sách hoàn hủy r% của từng cơ sở và luồng hoàn DEMO do Owner hoặc Staff được cấp quyền thực hiện. Luồng giữ đúng người được hoàn, phân biệt hủy theo chính sách với lỗi bàn giao và không yêu cầu xác minh ngân hàng hay cấp nguồn tiền thật.

## ADDED Requirements

### Requirement: Only the facility Owner configures refund policies

Owner SHALL cấu hình r riêng theo cơ sở, từ 0 đến 100%, chính xác 0,01%, có version/audit và độc lập phí chuyển nhượng x. Staff kể cả có BOOKING_REFUND SHALL không sửa r/x hoặc tự cấp quyền. Thay đổi đồng thời SHALL được phát hiện thay vì ghi đè âm thầm.

#### Scenario: Owner updates a valid rate
- **WHEN** Owner gửi r80% với version hiện tại
- **THEN** hệ thống tạo phiên bản mới có actor/thời điểm, giữ nguyên phiên bản đã chốt; explicit0% vẫn hợp lệ

#### Scenario: Invalid or unauthorized configuration
- **WHEN** tỷ lệ âm/vượt100%/sai độ chính xác, version cũ, Staff hoặc Owner khác cơ sở gửi cập nhật
- **THEN** server từ chối, không làm tròn ngầm hoặc đổi policy đã chốt

### Requirement: New bookings require an accepted refund policy

Online, counter và nhóm SHALL bị chặn đặt mới nếu cơ sở chưa cấu hình r. Booking SHALL chốt và hiển thị policy trước thanh toán; buyer chuyển nhượng SHALL chấp nhận policy booking đó. Đổi cấu hình SHALL không đổi booking/giao dịch đã chốt hoặc tự áp r0/100 cho dữ liệu cũ.

#### Scenario: Facility has no refund policy
- **WHEN** Customer/operator gọi tạo hold/booking/counter/nhóm trực tiếp hoặc qua UI
- **THEN** server báo cần Owner cấu hình, không tạo đặt mới/thu tiền; hold cũ thiếu snapshot phải tạo lại theo policy hợp lệ

#### Scenario: Policy changes after purchase
- **WHEN** booking đã chốt r80%, Owner đổi sang50% rồi B mua lại booking đó
- **THEN** booking giữ80%; B thấy và chấp nhận hoàn trên giá mua lại của mình theo80% trước thanh toán

### Requirement: The server computes the refund entitlement

Server SHALL tính H=HALF_UP(S×r/10000) nguyên VND, 0≤H≤S, từ cơ sở tính và policy đã chốt. Client SHALL không quyết định S/r/H/payee. H0 SHALL lưu quyết định hủy và NO_REFUND, không tạo giao dịch tiền0; retry SHALL không tính lại H.

#### Scenario: Rates and rounding
- **WHEN** S120.000đ với r0/50/100%, hoặc S25đ với r50%
- **THEN** H lần lượt0/60.000/120.000đ và13đ; không có sai số float hoặc nhiều lần làm tròn

#### Scenario: Operator changes the submitted amount
- **WHEN** client gửi H/r/payee khác quote server đã chốt
- **THEN** server từ chối hoặc bỏ qua trường không được phép và không tạo quyền hoàn tùy ý

### Requirement: Original court payments determine untransferred booking refunds

Booking chưa resale SHALL dùng tiền sân DEMO SUCCESS đã áp dụng hợp lệ làm S, hoàn đúng payer/payment gốc. Giá chưa thu SHALL không được hoàn. Guest SHALL không cần tài khoản Customer; createdBy/holder/operator SHALL không tự trở thành người nhận.

#### Scenario: A walk in guest cancels
- **WHEN** Guest holderNULL có payment ứng dụng hợp lệ và hủy đúng điều kiện
- **THEN** refund gắn payment/payer hoặc thông tin khách của giao dịch gốc, không tạo Customer giả hay trả Staff chỉ vì Staff tạo booking

### Requirement: The latest transferred buyer receives policy refunds

Sau handoff, S SHALL là P của giao dịch mua lại cuối đã bàn giao cho holder hiện tại và H hoàn buyer đó. H SHALL là nghĩa vụ cơ sở riêng với F/N; hủy SHALL không tự đảo phí x, thu hồi N, hoàn thêm tiền sân gốc cho A hoặc chờ trả A xong.

#### Scenario: A to B is cancelled
- **WHEN** R120.000/P100.000/x5%/F5.000/N95.000 được hủy hợp lệ với r80%
- **THEN** H80.000 trả B, F/N giữ nguyên dù N UNPAID/FAILED/PAID; không hoàn thêm R cho A

#### Scenario: A to B to C is cancelled
- **WHEN** C hủy sau giao dịch B→C đã bàn giao
- **THEN** S/payment/payee lấy từ giao dịch mua của C, không hoàn B đã bán tiếp hoặc lấy giá gốc của A

### Requirement: Group refunds allocate the actual paid contributions

S nhóm SHALL là tổng contribution đã thanh toán và áp dụng hợp lệ, không phải giá sân chưa thu. H SHALL phân bổ theo tỷ trọng về payment/payer gốc, largest remainder với tie paymentId, tổng đúng H và không vượt số có thể hoàn từng payment.

#### Scenario: Group has only partial payment
- **WHEN** nhóm giá300.000đ mới trả100.000đ và hủy hợp lệ theo r50%
- **THEN** H50.000đ phân về contribution gốc, không150.000đ và không coi nhóm đã trả đủ

#### Scenario: Allocation has rounding remainders
- **WHEN** nhiều contribution phải chia H nguyên đồng
- **THEN** phần dư theo phần thập phân giảm dần rồi paymentId, tổng allocations=H; không cộng sai do làm tròn từng khoản độc lập

### Requirement: Aborted handoffs refund the full collected demo payment

Payment DEMO đã SUCCESS nhưng handoff ABORTED SHALL tạo quyền hoàn đủ P cho B, F0/N0, không áp x/r. Timeout/chưa rõ quyết định SHALL chưa được coi là ABORTED; payment chưa thu SHALL không tạo refund.

#### Scenario: Policy is partial but delivery aborts
- **WHEN** B trả100.000đ, r80%/x5% và Booking xác nhận ABORTED
- **THEN** B được hoàn100.000đ, không80.000đ hay95.000đ; Owner/Staff có BOOKING_REFUND thực hiện DEMO

#### Scenario: Response was lost after a successful handoff
- **WHEN** caller timeout nhưng Booking đã COMPLETED quyết định chuyển quyền
- **THEN** phục hồi quyền B, không tạo full technical refund hoặc dùng policy để hoàn một phần thay quyết định handoff

### Requirement: Refund execution belongs to scoped facility operators

Owner hoặc Staff active có BOOKING_REFUND đúng cơ sở SHALL thực hiện hoàn DEMO. Customer SHALL chỉ yêu cầu hủy/hoàn; Admin, Staff chỉ TRANSFER_PAYOUT hoặc người tạo booking SHALL không tự có quyền thực hiện. Grant SHALL mặc định không cấp và kiểm lại tại mỗi request.

#### Scenario: Owner grants and later revokes permission
- **WHEN** Owner cấp BOOKING_REFUND ở cơ sởA rồi thu hồi trước thao tác mới
- **THEN** Staff được thực hiện khi grant còn hiệu lực và bị từ chối sau revoke; quyền cơ sởA không áp choB

#### Scenario: Customer requests a cancellation
- **WHEN** Customer chấp nhận quote hợp lệ cho booking của mình
- **THEN** server ghi quyết định hủy/quyền được hoàn, chờ cơ sở thực hiện; Customer không tự gọi lệnh hoàn tiền thành công

### Requirement: Demo refund success is recorded directly by the server

Thao tác Hoàn tiền DEMO hợp lệ SHALL ghi REFUNDED, refundedAt, actor và event DEMO trong database. Kết quả ứng dụng này SHALL đủ để hiển thị đã hoàn và tính báo cáo; không yêu cầu tài khoản ngân hàng, bank proof, funding, reserve hoặc duyệt nhiều cấp.

#### Scenario: Facility executes a demo refund without bank setup
- **WHEN** người đủ quyền chọn SUCCESS cho H80.000đ đang chờ và chưa có cấu hình ngân hàng/nguồn tiền thật
- **THEN** server ghi đã hoàn DEMO80.000đ ngay, booking vẫn CANCELLED và báo cáo ghi H ở refundedAt

### Requirement: Failed demo refunds retain the obligation and support safe retry

Refund SHALL cho mô phỏng FAILED, giữ H còn phải hoàn và booking CANCELLED. Retry SHALL chỉ thực hiện phần chưa thành công, cùng key trả kết quả cũ; khóa/unique nghiệp vụ SHALL ngăn hai kết quả thành công cho cùng allocation hoặc tổng vượt payment.

#### Scenario: Failure then success
- **WHEN** H80.000đ FAILED rồi người có quyền retry SUCCESS
- **THEN** trước success H chưa giảm tiền sau hoàn; sau success chỉ ghi một lần80.000đ, không đổi policy hoặc tạo H mới

#### Scenario: Concurrent commands and lost responses
- **WHEN** hai operator cùng hoàn một allocation hoặc response mất sau commit
- **THEN** chỉ một kết quả thành công, replay/read trả trạng thái đã commit; key trùng payload khác bị từ chối

### Requirement: Cancellation commits once before play and excludes check in

Hủy theo policy SHALL chỉ áp dụng trước startsAt cho PENDING/CONFIRMED, quyết định nguyên tử với check-in/handoff. Transfer active SHALL cần resolve trước. CHECKED_IN/COMPLETED SHALL không dùng nhánh hủy trước chơi; CANCELLED SHALL không dùng sân dù H đang chờ/lỗi.

#### Scenario: Check in races with cancellation
- **WHEN** hủy và check-in cùng chạy cho một booking
- **THEN** chỉ quyết định hợp lệ thắng; hủy thắng thì không CHECKED_IN, check-in thắng thì không hủy theo nhánh này

#### Scenario: Refund is executed after the play time
- **WHEN** hủy đã commit hợp lệ trước startsAt nhưng hoàn DEMO diễn ra sau
- **THEN** H theo quyết định gốc vẫn được thực hiện, không phục hồi booking hoặc xóa nghĩa vụ vì đã quá giờ

#### Scenario: A paid group still has member modification guards
- **WHEN** bật luồng hủy/hoàn nhóm có tiền
- **THEN** guard sửa member/allocation đã thu vẫn giữ và timeout nhóm không tự bị đổi thành hủy policy

### Requirement: Quotes and all refunds share one authoritative payment capacity

Quote SHALL bind policy/payment/ownership version và expiry; dữ liệu đổi SHALL cần requote. Mọi refund cùng payment SHALL dùng tổng đã hoàn/đang thực hiện của ứng dụng, không vượt tiền đã thu hoặc tạo lại H; xung đột SHALL báo rõ thay vì âm thầm sửa quyền lợi.

#### Scenario: Holder changes or another refund consumes the payment
- **WHEN** quote cũ sau resale hoặc cùng payment đã có khoản hoàn thành công
- **THEN** server từ chối quote cũ/lệnh vượt capacity và yêu cầu tính lại hoặc xử lý chênh lệch, không gửi tiền trùng

### Requirement: Refund history and views remain auditable and private

Server SHALL giữ policy/S/r/H/allocations/payment/payee/reason/actor/time/outcome và nhãn DEMO. Người dùng SHALL chỉ thấy dữ liệu của mình hoặc đúng cơ sở/grant; BOOKING_REFUND SHALL không cấp dashboard Owner, tài chính toàn hệ thống hoặc credential check-in.

#### Scenario: Buyer and facility inspect the refund
- **WHEN** B hoặc operator đủ quyền mở yêu cầu hoàn
- **THEN** thấy trạng thái chờ/FAILED/REFUNDED cùng số tiền/actor phù hợp scope, không thấy dữ liệu riêng của A/khách khác hoặc nút thực hiện trái quyền

### Requirement: Legacy refund records are preserved explicitly

Booking thiếu snapshot/payment ứng dụng SHALL cần preflight và quyết định áp dụng policy rõ có audit, hoặc giữ REVIEW với lý do. Hệ thống SHALL không tự đặt r0/100, tạo receipt, sửa refund review cũ thành đã thực hiện hoặc yêu cầu bank proof cho DEMO đã ghi nhận hợp lệ.

#### Scenario: Legacy data is opened or upgraded
- **WHEN** gặp booking thiếu snapshot hoặc chứng từ tiền không hỗ trợ
- **THEN** giữ nguyên payment/history/holder, báo phần cần xử lý rõ; migration/startup/GET không tự hoàn hoặc sửa dữ liệu