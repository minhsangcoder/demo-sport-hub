> **HISTORY ONLY — NOT AN ACTIVE REQUIREMENT SOURCE.**
> Read `openspec/CURRENT_CHANGE.md` at the project root for the current proposal.
> Do not apply, merge, or resume requirements/tasks from this document unless
> the user explicitly requests this historical change. Task counts are historical.

# Spec Delta

## Purpose

Bổ sung điều kiện cấp quyền và sử dụng sân cho cohort REAL: tiền thật phải được xác thực và áp dụng đúng nghĩa vụ. Các yêu cầu DEMO hiện hữu tiếp tục chỉ áp dụng dữ liệu mô phỏng, không miễn xác thực cho LIVE.

## ADDED Requirements

### Requirement: Live bookings are confirmed only from fully verified applied payments

Booking REAL SHALL chỉ CONFIRMED sau khi đủ tiền BANK_VERIFIED đúng binding và giữ sân còn hợp lệ. Status tự khai, receipt thiếu/sai, payment chỉ đang khởi tạo hoặc evidence DEMO SHALL không xác nhận booking REAL.

#### Scenario: Payment is pending versus verified
- **WHEN** khách chỉ mở QR hoặc đã có khoản thu đầy đủ được xác thực và áp dụng trước hạn giữ sân
- **THEN** trường hợp đầu vẫn chờ thanh toán; trường hợp sau mới CONFIRMED và có thể nhận credential theo quyền

#### Scenario: Verified payment could not be applied
- **WHEN** tiền đã vào nhưng giữ sân đã hết hạn hoặc sai cơ sở/booking
- **THEN** không cấp quyền sân; hệ thống hiển thị khoản thu cần đối soát/hoàn và không lấy lại sân đã cấp cho người khác

### Requirement: All live check in routes enforce the same payment and usage rules

QR, mã thủ công và HTTP trực tiếp SHALL cùng yêu cầu CONFIRMED, đủ collection REAL hợp lệ, quyền đúng cơ sở, credential hiện hành, khung giờ cho phép và không có transfer lock chưa xử lý. Quyền vận hành SHALL không bypass kiểm tiền.

#### Scenario: Manual code is used before payment
- **WHEN** Staff có mã booking đúng nhưng khoản thu chưa đủ hoặc chỉ DEMO SUCCESS
- **THEN** server chặn CHECKED_IN, không cho dùng sân trước trả tiền thật

#### Scenario: Two check in routes compete
- **WHEN** QR và nhập mã cùng check-in một booking REAL đã đủ điều kiện
- **THEN** chỉ một CHECKED_IN thành công và một lịch sử sử dụng hợp lệ

### Requirement: Live group usage requires the full distinct real contribution total

Nhóm REAL SHALL chỉ được cấp quyền sử dụng/check-in khi tổng contribution REAL áp dụng không trùng bằng tiền sân. Tiền DEMO, phần thu dư, payment chưa áp dụng hoặc nhãn member đã trả SHALL không bù phần thiếu.

#### Scenario: Contributions are partial and then complete
- **WHEN** nhóm300.000đ nhận100.000đ REAL rồi200.000đ REAL hợp lệ
- **THEN** trước khi đủ300.000đ không sử dụng sân; sau đủ và các guard khác hợp lệ mới được check-in

#### Scenario: A mixed cohort attempts to complete payment
- **WHEN** nhóm REAL chỉ có100.000đ REAL và200.000đ DEMO
- **THEN** tổng hợp lệ vẫn100.000đ, nhóm chưa đủ tiền và không phát credential cho phần chưa trả

### Requirement: Live counter payment does not transfer ownership to Staff

Booking tại quầy REAL SHALL cho khách quét QR thu tiền đúng cơ sở và chỉ xác nhận sau evidence ngân hàng. Guest SHALL giữ holder NULL và check-in tại cơ sở bằng mã; linked Customer SHALL giữ sở hữu. Staff payer/creator ứng dụng SHALL không tự thành holder hoặc payee hoàn.

#### Scenario: Guest pays the counter bank QR
- **WHEN** Staff tạo booking cho Guest và khách quét chuyển đủ vào Owner
- **THEN** booking chỉ được xác nhận từ server; Guest không cần tài khoản SportHub, Staff không nhận QR vào sân như Customer

#### Scenario: Staff creates for a linked Customer
- **WHEN** booking tại quầy đã thu đủ REAL cho Customer có tài khoản
- **THEN** Customer thấy booking của mình; Staff tiếp tục quyền vận hành đúng cơ sở mà không sở hữu booking

### Requirement: Live transferred buyer rights depend on handoff rather than seller disbursement

B đã trả đủ REAL và handoff COMPLETED SHALL có quyền sử dụng theo lịch dù khoản trả A chưa hoàn tất hoặc cần đối soát. Trước handoff commit SHALL chưa cấp credential B; credential A SHALL mất quyền sau đổi holder.

#### Scenario: Owner has not recorded payment to A
- **WHEN** B đã thanh toán và nhận quyền, N còn UNPAID/PREPARED/REVIEW
- **THEN** B được check-in trong thời gian/quyền hợp lệ, không cần mã giao dịch trả A hoặc trả P thêm lần nữa

#### Scenario: Handoff is delayed or old QR is reused
- **WHEN** B chỉ có payment SUCCESS chưa handoff hoặc A dùng QR cũ sau handoff
- **THEN** B chưa có credential sử dụng trước commit; QR cũ A bị từ chối sau commit

### Requirement: Live usage preserves the booking lifecycle and cancellation exclusion

Hệ thống SHALL giữ CONFIRMED→CHECKED_IN→COMPLETED và các cửa sổ thời gian hiện tại. CANCELLED SHALL không check-in dù chưa hoàn tiền; hủy trước chơi và check-in SHALL quyết định nguyên tử, không thêm IN_PROGRESS hoặc tự complete khi đọc báo cáo.

#### Scenario: Check in is outside the facility time window
- **WHEN** operator check-in trước startsAt−30 phút, tại/sau endsAt hoặc thuộc cơ sở khác
- **THEN** bị từ chối dù đã thu đủ REAL

#### Scenario: Cancellation races with usage
- **WHEN** hủy hợp lệ và check-in cạnh tranh
- **THEN** chỉ quyết định hợp lệ thắng; hủy thắng thì không dùng sân, check-in thắng thì không hủy theo luồng trước chơi

### Requirement: Live credentials never arise from historical demo or unknown money

Booking/contribution/acquisition SHALL giữ cohort tiền đã chốt. Dữ liệu DEMO/UNKNOWN cũ SHALL không tự đổi thành REAL hoặc được đăng bán trên chợ LIVE như sân đã trả tiền thật; người đọc SHALL thấy lý do giới hạn rõ.

#### Scenario: An old demo booking is opened after live rollout
- **WHEN** Customer xem booking DEMO trước đây hoặc cố dùng nó để tạo acquisition REAL
- **THEN** lịch sử giữ nguyên nhãn DEMO; hệ thống không tạo chứng từ ngân hàng hoặc nghĩa vụ tiền thật từ record đó
