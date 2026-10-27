> **HISTORY ONLY — NOT AN ACTIVE REQUIREMENT SOURCE.**
> Read `openspec/CURRENT_CHANGE.md` at the project root for the current proposal.
> Do not apply, merge, or resume requirements/tasks from this document unless
> the user explicitly requests this historical change. Task counts are historical.

# Spec Delta

## Purpose

Bổ sung hoàn tiền cho booking REAL theo policy của cơ sở và tiền thực đã áp dụng, với tài khoản nhận đúng người và xác nhận chuyển khoản thủ công bằng mã giao dịch. Hoàn kỹ thuật được tách khỏi hủy theo chính sách.

## ADDED Requirements

### Requirement: Live cancellations use the accepted Owner refund policy

Booking REAL SHALL chốt policy hoàn của Owner trước khi thu tiền và chặn đặt mới nếu chưa cấu hình. H=HALF_UP(S×rateBps/10000) SHALL lấy từ policy/số đã thu hợp lệ; client SHALL không tự chọn H/r/payee, không có phí2% cố định hoặc phí nền tảng thêm vào.

#### Scenario: Policy changes after payment
- **WHEN** khách chấp nhận r80%, Owner đổi r50% rồi khách hủy hợp lệ
- **THEN** quyền hoàn theo snapshot80%, không áp tỷ lệ mới hoặc trừ thêm2%

#### Scenario: Policy is missing
- **WHEN** tạo booking online/quầy/nhóm REAL tại cơ sở chưa cấu hình hoàn
- **THEN** server chặn đặt mới trước khi thu tiền và hướng dẫn Owner cấu hình, không tự coi0%/100%

### Requirement: Live refund requests bind the rightful bank recipient

Yêu cầu hoàn dương do khách submit SHALL có destination revision thuộc người được hoàn và gắn quote/ownership/payment hiện hành. STK của creator/operator SHALL không tự được dùng thay người có quyền nhận tiền.

#### Scenario: Customer requests a policy refund
- **WHEN** khách chấp nhận quote H80.000đ và chọn tài khoản của mình
- **THEN** nghĩa vụ hoàn gắn tài khoản/phiên bản đó; Owner/Staff thấy QR80.000đ của đúng người, khách không có quyền tự hoàn tất

#### Scenario: Refund recipient or ownership changes before submit
- **WHEN** gửi quote cũ sau chuyển nhượng hoặc destination thuộc tài khoản người khác
- **THEN** server từ chối và yêu cầu dữ liệu/quote hợp lệ, không chuyển sai người từ request cũ

### Requirement: Live original and transferred refunds use different payment bases

Booking chưa resale SHALL dùng khoản court/contribution REAL đã áp dụng và đúng payer/người hưởng. Sau resale, S SHALL là giá mua của buyer cuối đã nhận quyền, trả buyer đó. H SHALL không trừ thêm x hoặc tự đảo nghĩa vụ trả người bán.

#### Scenario: Original court price differs from resale price
- **WHEN** R120.000đ, B mua100.000đ rồi hủy hợp lệ r80%
- **THEN** H80.000đ cho B, không96.000đ theo R, không trả A hoặc giảm tiếp H bởi phí chuyển nhượng

### Requirement: Live group refund allocations complete independently

Nhóm SHALL phân H theo contribution/payer thật đã áp dụng, tổng allocations đúng H và không vượt capacity từng payment. Mỗi allocation dương SHALL có tài khoản/QR/mã giao dịch riêng; hoàn một người SHALL không đánh dấu mọi người đã được hoàn.

#### Scenario: Partially paid group is cancelled
- **WHEN** nhóm giá300.000đ mới áp dụng100.000đ và được hủy hợp lệ với r50%
- **THEN** H50.000đ trên khoản thực thu, không150.000đ trên giá sân chưa trả

#### Scenario: One payer has received money while another lacks bank details
- **WHEN** allocation đầu đã có mã giao dịch, allocation sau còn thiếu tài khoản
- **THEN** chỉ phần đầu ghi đã hoàn; phần sau còn chờ và nghĩa vụ nhóm chưa hoàn tất toàn bộ

#### Scenario: Group cancellation creates obligations for other payers
- **WHEN** organizer hủy hợp lệ nhưng chưa có tài khoản nhận của những payer khác
- **THEN** vẫn ghi quyết định hủy và từng quyền hoàn; payer thiếu tài khoản tự bổ sung cho allocation của mình, organizer không phải nhập STK của mọi người

### Requirement: Walk in guest refunds do not require a SportHub customer account

Guest SHALL được cung cấp BIN/STK tại cơ sở cho khoản hoàn của mình. Owner/Staff BOOKING_REFUND SHALL ghi thông tin theo Guest booking với audit, không tạo Customer giả hoặc tự hoàn cho Staff payer kỹ thuật.

#### Scenario: Guest receives a live counter booking refund
- **WHEN** Guest holder NULL hủy hợp lệ, cung cấp thông tin ngân hàng và operator thực hiện
- **THEN** QR hoàn gắn tài khoản Guest và đúng H; receipt ghi actor riêng với người nhận, Staff không thành chủ sở hữu hay người hưởng

### Requirement: Technical returns preserve the full unapplied real collection

Tiền thu REAL không áp dụng được do handoff ABORTED/booking mất giữ sân, tiền dư hoặc thu trùng SHALL có khoản phải trả lại đầy đủ phần không áp dụng. Policy hủy r/phí x SHALL không làm giảm quyền này; thiếu bank details SHALL chỉ làm chờ bổ sung.

#### Scenario: Late payment cannot reserve the slot
- **WHEN** nhận100.000đ hợp lệ nhưng booking đã EXPIRED và r chỉ50%
- **THEN** khoản cần hoàn kỹ thuật là100.000đ, không50.000đ; không tạo R/F từ khoản chưa áp dụng

#### Scenario: Extra collection is separate from a later policy refund
- **WHEN** nghĩa vụ100.000đ nhận110.000đ, áp dụng100.000đ rồi hủy r80%
- **THEN**10.000đ dư cần hoàn kỹ thuật riêng, H80.000đ theo policy; tổng khả năng hoàn kiểm chung để không vượt khoản thực thu

### Requirement: Live refund execution requires current facility authority and a receipt

Chỉ Owner hoặc active Staff có BOOKING_REFUND đúng cơ sở SHALL prepare/complete hoàn REAL. REFUNDED SHALL cần mã giao dịch và xác nhận thủ công đã commit; note, QR, request của Customer hoặc quyền TRANSFER_PAYOUT SHALL không đủ.

#### Scenario: Operator records a completed bank transfer
- **WHEN** người đủ quyền nhập reference cho allocation hiện hành đã chuyển
- **THEN** hệ thống lưu H/destination/actor/confirmedAt/reference và nhãn cơ sở xác nhận, không biến nó thành chứng từ payOS

#### Scenario: Wrong role confirms a refund
- **WHEN** Customer, Admin chỉ giám sát, Staff sai grant hoặc ngoài cơ sở gọi hoàn tất
- **THEN** bị từ chối, không trả QR/STK hoặc giảm nghĩa vụ tiền

### Requirement: Live refund capacity includes completed and unresolved operations

Tất cả hoàn cùng payment SHALL kiểm chung khoản thực thu, đã áp dụng, đã hoàn và đang xử lý/chưa rõ. Request/retry SHALL không vượt capacity, không tạo lại quyền H hoặc dùng cùng reference làm hai lần hoàn.

#### Scenario: Two operators refund concurrently
- **WHEN** hai lệnh chuẩn bị/hoàn tất cùng allocation cạnh tranh
- **THEN** chỉ một operation/receipt hiện hành thắng; lệnh còn lại nhận trạng thái đã xử lý/đang xử lý, không chuyển gấp đôi

#### Scenario: Transfer may have occurred before response loss
- **WHEN** operator đã chuyển nhưng confirmation timeout
- **THEN** giữ trạng thái chưa rõ và phục hồi bằng key/reference cũ; không tự cấp capacity để chuyển khoản lần nữa

### Requirement: Live cancellation decisions and cash execution remain independent

Hủy trước chơi SHALL quyết định nguyên tử với check-in/handoff, khóa quyền sử dụng ngay khi CANCELLED dù hoàn tiền còn chờ. Hoàn sau đó SHALL không phục hồi booking hoặc đổi CHECKED_IN/COMPLETED thành trạng thái khác; H0 SHALL không tạo QR chi.

#### Scenario: Cancelled customer has not yet received a refund
- **WHEN** hủy đã commit trước giờ chơi nhưng cơ sở chưa xác nhận hoàn
- **THEN** khách không check-in; nghĩa vụ hoàn vẫn tồn tại và có thể được thực hiện sau khung giờ sân

#### Scenario: No money is due back
- **WHEN** hủy hợp lệ cho H0
- **THEN** lưu NO_REFUND và quyết định hủy, không yêu cầu mã giao dịch hoặc dựng lệnh chuyển0 đồng
