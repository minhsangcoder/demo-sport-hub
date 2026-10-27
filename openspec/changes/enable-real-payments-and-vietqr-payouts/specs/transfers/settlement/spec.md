> **HISTORY ONLY — NOT AN ACTIVE REQUIREMENT SOURCE.**
> Read `openspec/CURRENT_CHANGE.md` at the project root for the current proposal.
> Do not apply, merge, or resume requirements/tasks from this document unless
> the user explicitly requests this historical change. Task counts are historical.

# Spec Delta

## Purpose

Quy định chuyển nhượng REAL theo mô hình B thanh toán cơ sở, B nhận quyền sau xác thực đủ tiền và bàn giao, cơ sở trả A bằng chuyển khoản thủ công hỗ trợ VietQR. Phí thuộc Owner theo cấu hình riêng, không là doanh thu SportHub.

## ADDED Requirements

### Requirement: Live resale collections belong to the booking facility

B SHALL trả giá mua lại P vào kênh payOS của cơ sở chứa booking. Cơ sở/Owner nhận và amount/currency SHALL do server chốt; SportHub SHALL không là người hưởng phí hoặc chủ khoản phải trả A.

#### Scenario: Buyer pays for a resale
- **WHEN** B mở QR của acquisition hợp lệ
- **THEN** thông tin nhận là cơ sở/Owner tương ứng, số tiền là P đã chấp nhận và không có phí nền tảng thêm vào

### Requirement: Live resale fees use only the Owner configured policy

Phí cơ sở F SHALL tính từ feeBps Owner đã cấu hình, F=HALF_UP(P×feeBps/10000), N=P−F nguyên VND. Không có mức cố định/mặc định2% hoặc yêu cầu feeBps=200. Thiếu policy SHALL chặn giao dịch mới, không tự điền tỷ lệ.

#### Scenario: Two facilities choose different fees
- **WHEN** cơ sởA chọn3%, cơ sởB chọn7% cho P100.000đ
- **THEN** A có F3.000/N97.000, B có F7.000/N93.000; không khoản nào bị ép2% hoặc ghi phí SportHub

#### Scenario: Boundaries and rounding
- **WHEN** x0%, x100% hoặc P25đ/x50% được chấp nhận rõ
- **THEN** lần lượt F0/N=P, F=P/N0 và F13/N12; không tạo lệnh chi0 hoặc tính phí hai lần

### Requirement: Live seller and buyer accept immutable financial terms

Đăng tin REAL SHALL yêu cầu destination của A và chấp nhận P/x/F/N/policy/receiver đã chốt. B SHALL chấp nhận terms acquisition trước trả tiền. Sau khóa mua, đổi policy/profile SHALL không đổi số tiền hoặc destination đã chốt âm thầm.

#### Scenario: Seller bypasses the bank form
- **WHEN** A gửi đăng tin trực tiếp thiếu destination hoặc dùng hash trước khi đổi ngân hàng/giá/phí
- **THEN** server từ chối và yêu cầu nhập tài khoản/xem lại điều khoản, không đăng tin nửa đầy đủ

#### Scenario: Fee policy changes after acquisition lock
- **WHEN** Owner đổi phí sau khi B đã khóa mua theo terms hợp lệ
- **THEN** acquisition/payment giữ P/F/N và policy đã chấp nhận; listing chưa khóa phải cập nhật/chấp nhận điều khoản mới trước giao dịch mới

### Requirement: Verified live collection triggers handoff without operator approval

Collection REAL canonical đủ P đúng acquisition SHALL tự khởi động handoff. B SHALL nhận holder/credential sau commit thành công, không cần Owner duyệt thủ công tiền vào hoặc chờ cơ sở trả N cho A.

#### Scenario: Buyer receives usage before seller payout
- **WHEN** B đã trả đủ BANK_VERIFIED và handoff thành công nhưng Owner chưa chuyển A
- **THEN** B thấy booking/credential của mình và dùng theo lịch; N tiếp tục nghĩa vụ cơ sở riêng

#### Scenario: Provider receipt is valid but handoff is still pending
- **WHEN** payment đã nhận nhưng event hoặc response handoff chậm
- **THEN** UI hiển thị đã nhận tiền, đang bàn giao; chưa cấp QR sử dụng và không thu P lần nữa

### Requirement: Successful live handoff creates exactly one facility seller payable

Mỗi acquisition COMPLETED SHALL phát sinh một F của cơ sở và N phải trả A cùng quyết định đổi quyền. Replay SHALL không nhân nghĩa vụ; N dương SHALL chờ thao tác chuyển khoản, N0 SHALL là NOT_REQUIRED.

#### Scenario: Process crashes after ownership changed
- **WHEN** B đã trở thành holder nhưng response hoặc event bị mất rồi replay
- **THEN** phục hồi một quyết định COMPLETED, một F/N và quyền B; không tạo thêm khoản phải trả A

### Requirement: Live seller payout uses bank instructions and a manual receipt

Owner hoặc Staff có TRANSFER_PAYOUT đúng cơ sở SHALL được mở VietQR trả N vào tài khoản A đã chốt. Hệ thống SHALL chỉ ghi đã trả sau mã giao dịch hợp lệ và lệnh hoàn tất của operator, với nhãn xác nhận thủ công; A SHALL không tự hoàn tất.

#### Scenario: Owner scans and records a seller payout
- **WHEN** Owner quét QR N97.000đ, chuyển khoản qua ngân hàng rồi nhập reference và hoàn tất
- **THEN** khoản trả A ghi97.000đ với actor/thời điểm/reference đúng một lần, không đổi quyền B hoặc tạo thêm F

#### Scenario: Owner generated the QR but did not transfer
- **WHEN** QR đã hiển thị nhưng chưa có mã giao dịch/lệnh hoàn tất
- **THEN** N vẫn chưa trả, A thấy còn chờ cơ sở và B vẫn giữ quyền đã nhận

### Requirement: Live complete and abort are mutually exclusive final decisions

Mỗi acquisition SHALL chỉ có một quyết định COMPLETED hoặc ABORTED. Timeout/chưa thấy event SHALL không là ABORTED; ABORTED SHALL chặn complete muộn, COMPLETED SHALL không bị xử lý như lỗi chưa bàn giao.

#### Scenario: Complete races with abort
- **WHEN** hai lệnh cạnh tranh hoặc response mất sau commit
- **THEN** chỉ quyết định cuối hợp lệ thắng và được phục hồi; không vừa đổi holder vừa tạo full refund do chưa bàn giao

### Requirement: Live aborted acquisitions return all collected principal without a fee

Nếu đã thu REAL nhưng acquisition được xác nhận ABORTED, cơ sở SHALL nợ hoàn đủ khoản thu cho B, F/N=0, không áp x/r. Thiếu tài khoản B SHALL chỉ trì hoãn chuẩn bị QR hoàn, không xóa nghĩa vụ; Owner/Staff BOOKING_REFUND SHALL thực hiện.

#### Scenario: Owner policies are partial but handoff aborts
- **WHEN** B đã trả100.000đ, x7% và policy hoàn hủy80%, handoff ABORTED
- **THEN** B được hoàn100.000đ, không93.000đ hoặc80.000đ; tiền chưa chuyển hoàn vẫn hiển thị còn phải hoàn

#### Scenario: Late money follows a final abort
- **WHEN** evidence thu tiền đến sau ABORTED
- **THEN** tạo khoản phải hoàn đầy đủ một lần và không cấp lại quyền B hoặc tính phí cơ sở

### Requirement: Live cancellation after handoff follows the latest buyer policy

Hủy hợp lệ sau handoff SHALL dùng policy r đã chốt và giá mua của holder cuối làm base, trả người mua đó. Khoản H SHALL độc lập với N trả người bán; không tự đảo F/N, hoàn thêm R cho A hoặc trừ thêm phí chuyển nhượng trên H.

#### Scenario: Transferred booking is cancelled before A is paid
- **WHEN** P100.000/x7% tạo F7.000/N93.000, B hủy với r80% khi N chưa trả
- **THEN** H80.000 cho B, N93.000 cho A vẫn riêng; không chờ trả A hoặc thu thêm phí SportHub

#### Scenario: Booking is sold twice
- **WHEN** A→B→C đã bàn giao và C hủy hợp lệ
- **THEN** khoản hoàn tính trên giá mua của C và tài khoản nhận của C; nghĩa vụ của các handoff trước vẫn giữ lịch sử riêng

### Requirement: Live transfer rollout preserves historical cohorts

Terms DEMO/legacy SHALL giữ protocol/fee/money class đã lưu, không được tự nâng thành REAL, backfill ngân hàng hoặc ép phí lịch sử thành tỷ lệ hiện tại. Giao dịch REAL mới SHALL không đi vào nhánh tạo mới legacy cố định2%.

#### Scenario: Historical two percent terms are displayed
- **WHEN** xem giao dịch legacy đã chốt2% hoặc tạo giao dịch REAL tại cơ sở chọn7%
- **THEN** lịch sử giữ snapshot cũ với nhãn thích hợp; giao dịch mới dùng7% từ Owner, không dùng overload/constraint2% làm mặc định
