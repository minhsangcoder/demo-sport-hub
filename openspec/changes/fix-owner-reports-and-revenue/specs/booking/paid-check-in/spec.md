> **HISTORY ONLY — NOT AN ACTIVE REQUIREMENT SOURCE.**
> Read `openspec/CURRENT_CHANGE.md` at the project root for the current proposal.
> Do not apply, merge, or resume requirements/tasks from this document unless
> the user explicitly requests this historical change. Task counts are historical.

# Spec Delta

## Purpose

Bảo đảm mọi hình thức check-in chỉ cho sử dụng sân sau khi thanh toán đủ trong mô hình DEMO. Biên nhận ứng dụng do server ghi nhận là đủ, không cần xác minh ngân hàng; quyền vận hành, sở hữu và lifecycle hiện tại được giữ nguyên.

## ADDED Requirements

### Requirement: Personal check in requires full applied application payment

Booking cá nhân SHALL chỉ check-in khi CONFIRMED và đủ tiền đã áp dụng đúng paymentId/paidAt/amount/currency/binding. DEMO SUCCESS đã áp dụng SHALL là bằng chứng hợp lệ, không cần bank proof. Status, QR hoặc giá trên UI SHALL không thay bằng chứng thanh toán.

#### Scenario: Fully paid demo booking
- **WHEN** payment DEMO SUCCESS đã áp dụng đủ và các guard quyền/thời gian/transfer đều hợp lệ
- **THEN** check-in thành công đúng một lần, booking chuyển CHECKED_IN mà không cần tài khoản ngân hàng hay duyệt tiền thật

#### Scenario: Missing or inconsistent application payment
- **WHEN** booking PENDING, chưa trả đủ, Payment SUCCESS chưa áp dụng hoặc receipt/amount/currency/binding không khớp
- **THEN** server chặn check-in, nêu cần hoàn tất/đồng bộ/kiểm tra thanh toán, không tự tạo receipt hoặc cho dùng trước trả sau

### Requirement: Group check in requires the exact full contribution total

Nhóm SHALL chỉ check-in khi contribution hợp lệ, không trùng, khớp nghĩa vụ/member và tổng đúng tiền sân. Thiếu/thừa/sai currency SHALL bị chặn; nhóm SHALL không cần một paymentId đại diện duy nhất. Payment DEMO được xác thực trong ứng dụng SHALL được tính đầy đủ.

#### Scenario: Partial then complete group payment
- **WHEN** nhóm300.000đ mới trả100.000đ, rồi trả thêm200.000đ hợp lệ
- **THEN** trước khi đủ tổng thì QR/mã đều bị chặn; sau tổng đúng300.000đ thì có thể check-in nếu đủ guard

#### Scenario: Member display and receipts disagree
- **WHEN** member hiển thị đã trả nhưng contribution thiếu/trùng/không khớp
- **THEN** server chặn, không lấy giá hiển thị hoặc trạng thái booking làm bằng chứng đủ tiền

### Requirement: Every check in entry point enforces the same server rule

QR, mã thủ công và HTTP trực tiếp SHALL dùng cùng guard đủ tiền/quyền/thời gian/transfer, áp dụng online/counter linked Customer/Guest/nhóm. Quyết định SHALL nguyên tử trước transition; quyền vận hành hoặc bỏ qua UI thanh toán SHALL không bypass.

#### Scenario: Manual bypass and competing requests
- **WHEN** operator nhập mã đúng cho booking thiếu tiền, hoặc QR/mã cùng check-in booking đủ tiền
- **THEN** thiếu tiền bị từ chối; cạnh tranh đủ tiền chỉ tạo một CHECKED_IN và không nhân lịch sử sử dụng

### Requirement: Payment payer and booking holder remain separate identities

Proof tiền sân gốc SHALL hợp lệ dù payer khác holder do counter/transfer. Guest holderNULL SHALL check-in bằng mã khi đủ guard; createdBy chỉ audit. Linked Customer SHALL giữ sở hữu, Staff SHALL không thành holder/người nhận QR Customer chỉ vì tạo/thu tiền.

#### Scenario: Counter booking for a Guest or linked Customer
- **WHEN** Staff tạo booking tại quầy và hoàn tất thanh toán DEMO hợp lệ
- **THEN** Guest giữ holderNULL và dùng mã tại cơ sở; linked Customer thấy booking của mình và giữ credential theo quyền sở hữu

### Requirement: Buyer rights are independent of seller payment execution

B trả DEMO đủ P và handoff đã commit SHALL được dùng booking theo lịch dù N UNPAID/FAILED. Handoff SHALL tự chạy sau payment hợp lệ, không chờ Owner duyệt hoặc trả A. Payment thành công nhưng chưa commit holder SHALL cần đồng bộ, chưa cấp quyền/QR B.

#### Scenario: Buyer checks in while A is unpaid
- **WHEN** B đã thanh toán/nhận quyền và đến khung check-in, Owner chưa bấm trả A hoặc trả A FAILED
- **THEN** B check-in được nếu đủ guard; không yêu cầu PAID của N, duyệt bank hoặc thanh toán P lần nữa

#### Scenario: Handoff is delayed or aborted
- **WHEN** payment SUCCESS nhưng handoff chưa có quyết định hoặc đã ABORTED
- **THEN** chưa cấp QR B; chưa rõ thì phục hồi cùng acquisition, ABORTED xử lý hoàn kỹ thuật thay vì cấp quyền

### Requirement: Facility time and transfer restrictions remain enforced

Server SHALL giữ BOOKING_CHECK_IN đúng cơ sở, credential hợp lệ, khung startsAt−30 phút đến trước endsAt và transfer lock. Trả đủ DEMO SHALL không cấp quyền cơ sở khác, dùng QR cũ sau chuyển nhượng hoặc bỏ qua khóa giao dịch.

#### Scenario: Paid booking is outside operator scope or time
- **WHEN** Staff cơ sởA check-in booking cơ sởB, chưa đến cửa sổ, đã tới endsAt hoặc acquisition còn LOCKED
- **THEN** server từ chối dù payment đủ, không lộ/cập nhật booking ngoài scope

#### Scenario: Former holder reuses a credential
- **WHEN** A dùng QR trước handoff sau khi B đã nhận quyền
- **THEN** credential cũ không còn quyền check-in; không phục hồi quyền A vì N chưa được trả

### Requirement: Check in preserves the existing lifecycle

Hệ thống SHALL giữ CONFIRMED→CHECKED_IN→COMPLETED, không thêm IN_PROGRESS. Complete SHALL cần CHECKED_IN, quyền hiện có và đã qua endsAt; đọc báo cáo SHALL không tự check-in/complete hoặc kéo thời lượng sau endsAt.

#### Scenario: Completion and reporting respect the schedule
- **WHEN** operator complete trước check-in/endsAt hoặc Owner đọc report sau giờ chơi
- **THEN** complete sai điều kiện bị chặn; GET không đổi status và không tự gọi CHECKED_IN quá giờ là đang chơi

### Requirement: Cancellation and check in are mutually exclusive

Hủy trước chơi và check-in SHALL quyết định nguyên tử với ownership/transfer hiện tại. CANCELLED SHALL không check-in dù H đang PENDING/FAILED và payment gốc đủ; CHECKED_IN/COMPLETED SHALL không vào nhánh hủy trước chơi. Refund SHALL không phục hồi quyền sử dụng.

#### Scenario: Cancellation races with check in
- **WHEN** hai lệnh cùng chạy hoặc QR được dùng sau khi hủy đã commit
- **THEN** chỉ quyết định hợp lệ thắng; hủy thắng thì không CHECKED_IN, check-in thắng thì không hủy theo nhánh trước chơi

### Requirement: Legacy application uncertainty is not silently repaired

Legacy thiếu bằng chứng ứng dụng SHALL báo rõ và chặn check-in tới khi xử lý được phép; không suy đã trả từ CONFIRMED/CHECKED_IN hoặc sửa payer/holder/history. DEMO đã có receipt hợp lệ SHALL không bị coi thiếu tiền chỉ vì không có chứng từ ngân hàng.

#### Scenario: Legacy receipt is missing versus valid demo receipt
- **WHEN** booking cũ thiếu receipt hoặc booking DEMO có receipt đầy đủ nhưng không bank proof
- **THEN** trường hợp cũ giữ nguyên dữ liệu và yêu cầu kiểm tra; DEMO hợp lệ được tiếp tục theo guard bình thường