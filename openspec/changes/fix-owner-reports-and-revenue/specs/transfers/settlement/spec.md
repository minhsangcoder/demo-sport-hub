> **HISTORY ONLY — NOT AN ACTIVE REQUIREMENT SOURCE.**
> Read `openspec/CURRENT_CHANGE.md` at the project root for the current proposal.
> Do not apply, merge, or resume requirements/tasks from this document unless
> the user explicitly requests this historical change. Task counts are historical.

# Spec Delta

## Purpose

Quy định chuyển nhượng trong đồ án DEMO: B thanh toán giả lập cho cơ sở và nhận quyền; Owner hưởng phí x% do mình cấu hình và trả phần còn lại cho A bằng thao tác giả lập có phân quyền. SportHub cung cấp xử lý kỹ thuật, không hưởng doanh thu này.

## ADDED Requirements

### Requirement: Demo transfer payments belong to the receiving facility

B SHALL thanh toán DEMO giá P cho cơ sở của booking. Server SHALL xác định receiving facility/Owner từ dữ liệu nghiệp vụ, ghi kết quả trong database và hiển thị bên nhận; không yêu cầu merchant, tài khoản ngân hàng hoặc xác minh tiền thật.

#### Scenario: Buyer completes a demo payment
- **WHEN** B bấm Thanh toán DEMO cho acquisition hợp lệ
- **THEN** server ghi payment SUCCESS thuộc cơ sở, với amount/currency/receiver do server xác định và nhãn không phát sinh tiền thật

#### Scenario: Client tries to redirect the payment
- **WHEN** client thay facility/Owner/payee hoặc amount của giao dịch
- **THEN** server từ chối hoặc bỏ qua trường không được phép, không tạo payment sai người nhận hoặc doanh thu SportHub

### Requirement: Facility Owners configure an independent transfer fee

Chỉ Owner của cơ sở SHALL cấu hình x từ 0 đến 100%, chính xác 0,01%, có version/audit. Phí x SHALL độc lập tỷ lệ hoàn hủy r; thiếu x SHALL chặn chuyển nhượng mới, không tự áp 2% hoặc phí cơ sở khác. Explicit 0% SHALL hợp lệ.

#### Scenario: Different facilities choose different fees
- **WHEN** hai cơ sở đặt x5% và x10% cho P100.000đ
- **THEN** phí lần lượt 5.000đ và 10.000đ thuộc đúng cơ sở, không có phần phí nền tảng

#### Scenario: Configuration is missing or unauthorized
- **WHEN** cơ sở chưa đặt x hoặc Staff/Owner khác cơ sở gửi sửa x
- **THEN** thiếu x chỉ chặn chuyển nhượng mới với lý do rõ; sửa trái quyền bị từ chối và không ảnh hưởng booking thông thường

### Requirement: Accepted terms fix the facility fee and seller net

Server SHALL tính F=HALF_UP(P×x/10000) nguyên VND, N=P−F và chốt facility/Owner/A/B/P/x/version/F/N cùng điều khoản hoàn r trước thanh toán. A/B SHALL thấy và chấp nhận điều khoản tương ứng; retry SHALL dùng snapshot, không cộng phí lên P hoặc tính lại theo policy mới.

#### Scenario: Normal fee and rounding
- **WHEN** P100.000đ/x5%, hoặc P25đ/x50%
- **THEN** giao dịch đầu có F5.000/N95.000; giao dịch sau có F13/N12 và luôn P=F+N

#### Scenario: Boundary rates and later policy changes
- **WHEN** Owner đặt x0%/100% hoặc đổi x sau khi acquisition đã khóa
- **THEN** x0 cho F0/N=P, x100 cho F=P/N0 với consent rõ; acquisition giữ snapshot, listing chưa khóa phải requote/reconsent khi điều khoản đổi

### Requirement: Server application payment success automatically grants buyer rights

Payment DEMO canonical SUCCESS đủ P và đúng acquisition SHALL tự kích hoạt bàn giao cho B, không chờ Owner/Staff duyệt thanh toán hoặc trả A. B SHALL có quyền sử dụng theo lịch sau khi holder/credential đã commit; chưa có commit SHALL chưa cấp QR.

#### Scenario: Owner has not paid A
- **WHEN** B thanh toán DEMO đủ P, handoff thành công và khoản N đang UNPAID hoặc FAILED
- **THEN** B nhận quyền/credential và được check-in trong khung giờ hợp lệ với các guard hiện có, không phải chờ trả A

#### Scenario: Payment succeeded but handoff propagation is delayed
- **WHEN** payment đã commit SUCCESS nhưng response/event bàn giao bị chậm
- **THEN** UI báo đã thanh toán, đang đồng bộ quyền và phục hồi cùng acquisition; không thu lại P, chờ Owner duyệt hoặc cấp QR trước commit

#### Scenario: Payment failed or belongs to another acquisition
- **WHEN** mô phỏng FAILED hoặc SUCCESS sai payer/amount/currency/binding
- **THEN** không cấp quyền B, không ghi phí F hoặc nợ N từ payment đó

### Requirement: Successful handoff creates one facility seller payable

Handoff đã commit SHALL tạo đúng một phí F của cơ sở và nghĩa vụ N trả A, độc lập thời điểm chơi. N dương SHALL ở UNPAID tới khi Owner/Staff thực hiện DEMO; N0 SHALL là NOT_REQUIRED, không phát sinh lệnh tiền 0 hoặc yêu cầu tài khoản nhận.

#### Scenario: Handoff is replayed
- **WHEN** handoff P100.000/x5% được xử lý lại sau crash
- **THEN** chỉ có một F5.000/N95.000; không tự trả A hoặc tạo phí/nghĩa vụ trùng

#### Scenario: Cancellation precedes a delayed settlement event
- **WHEN** handoff đã commit, booking hủy rồi event tài chính đến muộn
- **THEN** vẫn ghi F/N đúng một lần; refund hủy là nghĩa vụ riêng, không biến handoff thành ABORTED

### Requirement: Owner or explicitly authorized Staff can execute a demo seller payment

Owner hoặc Staff active có TRANSFER_PAYOUT đúng cơ sở SHALL được bấm Trả tiền DEMO cho A. Server SHALL lấy N/payee từ settlement và ghi PAID, paidAt, actor cùng kết quả DEMO khi thành công; không cần bank proof, xác minh A, số dư hoặc một bước duyệt khác.

#### Scenario: An authorized operator simulates success
- **WHEN** Owner hoặc Staff có grant hợp lệ thực hiện N95.000đ chưa trả
- **THEN** server ghi PAID95.000đ trong database, UI của A/cơ sở cập nhật đã trả DEMO và B giữ quyền

#### Scenario: Wrong role or facility executes the command
- **WHEN** A tự xác nhận đã được trả, Admin thay Owner, Staff chỉ BOOKING_REFUND, Staff inactive hoặc Owner/Staff khác cơ sở gửi lệnh
- **THEN** server từ chối và không thay đổi nghĩa vụ hoặc dữ liệu tiền

### Requirement: Seller payment authority is opt in and checked on each request

TRANSFER_PAYOUT SHALL do Owner cấp/thu hồi theo cơ sở, mặc định không cấp và độc lập BOOKING_REFUND. Mỗi lệnh SHALL kiểm quyền hiện tại phía server; grant SHALL không cho Staff sửa x/N/payee, tự cấp quyền hoặc xem doanh thu Owner.

#### Scenario: Permission is revoked before a new attempt
- **WHEN** Owner thu hồi TRANSFER_PAYOUT trước request trả A hoặc retry mới
- **THEN** Staff bị từ chối; Owner hoặc người được cấp quyền hợp lệ vẫn có thể thực hiện

#### Scenario: Operator tampers with the settlement
- **WHEN** Staff gửi N lớn hơn, đổi A hoặc dùng quyền ở cơ sở khác
- **THEN** server không sử dụng các giá trị client để thực hiện và không tiết lộ settlement ngoài scope

### Requirement: Demo failures remain retryable without duplicating money

Thao tác DEMO trả A SHALL cho mô phỏng FAILED, giữ N còn nợ và quyền B. Retry hợp lệ SHALL chỉ hoàn tất phần chưa thành công; cùng key trả cùng kết quả, một nghĩa vụ SHALL chỉ có một thành công. GET/báo cáo SHALL không thực hiện tiền.

#### Scenario: Failure is followed by a successful retry
- **WHEN** trả A95.000 FAILED rồi người đủ quyền thử lại SUCCESS
- **THEN** nghĩa vụ chuyển từ FAILED sang PAID đúng95.000, không thêm F hoặc payment P và không ảnh hưởng B

#### Scenario: Double click or response loss
- **WHEN** hai lệnh cùng nghĩa vụ cạnh tranh hoặc response mất sau khi server đã ghi PAID
- **THEN** đọc/replay khôi phục kết quả đã commit, không trả A lần hai; key trùng với payload khác bị từ chối

### Requirement: Complete and abort are mutually exclusive application decisions

Complete/abort SHALL quyết định nguyên tử tại Booking: một acquisition chỉ COMPLETED hoặc ABORTED. Timeout hoặc chưa thấy event SHALL không được coi là ABORTED; ABORTED SHALL chặn complete/relock muộn, COMPLETED SHALL không bị hoàn như chưa bàn giao.

#### Scenario: Handoff races with abort
- **WHEN** complete và abort chạy đồng thời hoặc response mất sau commit
- **THEN** chỉ một quyết định cuối thắng; COMPLETED phục hồi quyền, ABORTED không cấp quyền muộn

### Requirement: Collected demo payments with an aborted handoff are fully refundable

Nếu payment DEMO đã SUCCESS nhưng handoff được xác nhận ABORTED, cơ sở SHALL nợ hoàn đủ P cho B, F0/N0, không áp x/r. Owner hoặc Staff BOOKING_REFUND SHALL thực hiện hoàn DEMO; không thu tiền SHALL không tạo khoản hoàn.

#### Scenario: Technical failure after collection
- **WHEN** P100.000đ đã SUCCESS, acquisition ABORTED, x5% và r80%
- **THEN** quyền hoàn của B là100.000đ, không95.000đ hay80.000đ; chưa thực hiện thì vẫn hiển thị còn phải hoàn

#### Scenario: Callback arrives after abort or payment was never collected
- **WHEN** payment SUCCESS đến sau ABORTED, hoặc payment chỉ FAILED/chưa thu
- **THEN** SUCCESS tạo quyền hoàn đủ P đúng một lần và không handoff; FAILED/chưa thu không tạo F/N/refund

### Requirement: Policy cancellation uses the latest buyer and a separate refund rate

Sau handoff, hủy hợp lệ SHALL dùng r đã chốt và giá mua của holder hiện tại làm S; H=HALF_UP(S×r/10000) hoàn cho buyer đó. Refund SHALL không tự đảo F/N, trả lại R cho A hoặc yêu cầu trả A trước; BOOKING_REFUND SHALL là quyền thực hiện riêng.

#### Scenario: A transferred booking is cancelled
- **WHEN** P100.000/x5%/F5.000/N95.000 được hủy hợp lệ với r80%
- **THEN** H80.000 trả B bằng hoàn DEMO, F/N giữ nguyên kể cả N chưa trả; SportHub không hưởng doanh thu

#### Scenario: Booking is sold again
- **WHEN** A chuyển B rồi B chuyển C và C hủy
- **THEN** S/payee/payment là giao dịch mua của C, không hoàn cho B đã bán tiếp hoặc tự dùng giá gốc của A

### Requirement: Demo transfers retain facility isolation and participant privacy

A/B SHALL chỉ xem giao dịch của mình; Owner/Staff SHALL chỉ xem projection đúng cơ sở/quyền. Màn hình SHALL ghi rõ DEMO, không lộ QR, booking code bí mật hoặc thông tin khách không cần thiết; createdBy SHALL không tạo quyền nhận tiền hay sở hữu.

#### Scenario: A participant changes identifiers
- **WHEN** client truy vấn acquisition/settlement của người hoặc cơ sở khác
- **THEN** server từ chối trước khi tiết lộ chi tiết hoặc thực hiện tiền

### Requirement: Existing records are preserved without production finance prerequisites

Dữ liệu/migrations cũ SHALL giữ nguyên; DEMO mới SHALL không phụ thuộc merchant, beneficiary, funding, reserve, bank-final proof hoặc worker ngân hàng. Legacy thiếu điều khoản/payment ứng dụng SHALL cần xử lý rõ, không tự gán x2%, receipt hoặc kết quả đã trả.

#### Scenario: Demo runs without bank setup
- **WHEN** cơ sở đã cấu hình x/r nhưng chưa có tài khoản ngân hàng, merchant hoặc nguồn tiền thật
- **THEN** payment DEMO, handoff, trả A và hoàn B vẫn hoàn tất qua thao tác server được phép; dữ liệu cũ không bị sửa ngầm