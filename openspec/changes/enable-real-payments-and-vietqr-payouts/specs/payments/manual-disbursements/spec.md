> **HISTORY ONLY — NOT AN ACTIVE REQUIREMENT SOURCE.**
> Read `openspec/CURRENT_CHANGE.md` at the project root for the current proposal.
> Do not apply, merge, or resume requirements/tasks from this document unless
> the user explicitly requests this historical change. Task counts are historical.

# Spec Delta

## Purpose

Hỗ trợ cơ sở chuyển khoản trả người sang nhượng hoặc hoàn khách qua VietQR được điền sẵn, sau đó người có quyền xác nhận mã giao dịch trên SportHub. Kết quả thủ công được phân biệt với xác thực ngân hàng của tiền thu.

## ADDED Requirements

### Requirement: Sellers provide their bank destination before publishing live transfers

A SHALL cung cấp/chọn ngân hàng có BIN và số tài khoản nhận của mình trước khi đăng tin sang nhượng REAL. Server SHALL áp dụng điều kiện này cho quote, đăng và sửa tin, kể cả khi A dự kiến không nhận tiền vì phí Owner bằng100%.

#### Scenario: Seller omits a bank account
- **WHEN** A đăng hoặc gửi HTTP trực tiếp mà không có destination hợp lệ
- **THEN** hệ thống chặn đăng, hướng dẫn nhập ngân hàng/STK và không đăng tin thiếu thông tin nhận tiền

#### Scenario: Seller uses another account principal
- **WHEN** A gửi destination revision thuộc Customer khác
- **THEN** server từ chối, không dùng destination đó hoặc tiết lộ STK người kia

### Requirement: Bank account inputs are validated and preserved as text

Hệ thống SHALL kiểm BIN trong danh mục hỗ trợ và số tài khoản số không rỗng trong giới hạn VietQR; SHALL giữ nguyên số0 đầu và không coi kiểm định dạng là xác minh chủ tài khoản ngân hàng. Khách SHALL thấy tài khoản đã khai để xác nhận.

#### Scenario: Leading zeros and an invalid bank
- **WHEN** khách nhập STK00123456789 hoặc BIN không hỗ trợ
- **THEN** STK hợp lệ giữ00123456789 trong dữ liệu/QR; BIN sai bị báo lỗi, không tự đoán sang ngân hàng khác

### Requirement: Bank destination revisions cannot silently redirect obligations

Listing/acquisition/nghĩa vụ SHALL gắn phiên bản tài khoản đã chốt. Đổi profile SHALL không tự đổi khoản đang xử lý; thay destination của nghĩa vụ chưa trả SHALL cần consent đúng payee, lý do và kiểm phiên bản. Bản ghi đã trả SHALL không bị sửa người nhận.

#### Scenario: Seller changes the default bank after acquisition
- **WHEN** A đổi ngân hàng trong profile sau khi B đã khóa mua
- **THEN** nghĩa vụ đã chốt vẫn dùng revision ban đầu; thay thế hợp lệ hiển thị rõ và không tính lại P/F/N

#### Scenario: Old destination instructions race with a replacement
- **WHEN** operator dùng phiên bản chuẩn bị cũ sau khi destination nghĩa vụ đã được thay hợp lệ
- **THEN** lệnh hoàn tất cũ bị chặn hoặc vào đối soát nếu đã chuyển, không tự yêu cầu chuyển tiếp đến STK mới

### Requirement: Positive customer refund requests include the correct bank destination

Khách chủ động yêu cầu khoản hoàn dương SHALL cung cấp destination của đúng người được hoàn. Khoản hoàn tự sinh vì lỗi kỹ thuật SHALL vẫn được ghi nhận khi thiếu tài khoản, ở trạng thái chờ bổ sung, không mất quyền hoàn.

#### Scenario: Refund request has no destination
- **WHEN** khách submit yêu cầu hoàn dương chưa có BIN/STK
- **THEN** server hướng dẫn bổ sung trước khi nhận yêu cầu chủ động; không giả STK hoặc mặc định người tạo booking là người nhận

#### Scenario: Technical refund exists before buyer bank setup
- **WHEN** B đã trả tiền nhưng handoff ABORTED và B chưa khai tài khoản
- **THEN** vẫn có nghĩa vụ hoàn đủ cho B ở AWAITING_BANK_DETAILS; B được bổ sung tài khoản để cơ sở thực hiện sau

### Requirement: Operational bank destinations stay private

STK đầy đủ và QR chi SHALL chỉ hiển thị cho người nhận của chính tài khoản hoặc operator đang thực hiện đúng quyền/cơ sở. Chợ, B, báo cáo tổng, log và người ngoài SHALL không có bank details của A; thông tin nhạy cảm SHALL không có trong URL công khai.

#### Scenario: Buyer opens the public listing and finance terms
- **WHEN** B hoặc người khác xem tin sang nhượng của A
- **THEN** thấy giá/phí/cơ sở cần thiết nhưng không STK/BIN/QR chuyển trả A hoặc credential nội bộ

### Requirement: Facility operators receive prefilled bank transfer QR instructions

Với nghĩa vụ dương hợp lệ, hệ thống SHALL tự render VietQR chứa BIN/STK đã chốt, đúng số tiền còn phải trả/hoàn và nội dung tham chiếu ổn định. Operator SHALL thấy các thông tin này trước khi quét; client SHALL không tự quyết định amount/payee.

#### Scenario: Owner prepares a seller payout
- **WHEN** P100.000đ, phí Owner5% tạo N95.000đ chưa trả và Owner mở thao tác
- **THEN** QR quét bằng app ngân hàng điền STK của A,95.000đ và nội dung của đúng khoản trả A; chưa ghi đã trả

#### Scenario: Owner prepares a policy refund
- **WHEN** khoản hoàn của B là80.000đ có tài khoản nhận hợp lệ
- **THEN** QR chứa STK của B và80.000đ, không STK A, giá sân gốc hoặc số tiền do trình duyệt sửa

### Requirement: Transfer content is stable and distinct for each disbursement

Nội dung QR chi SHALL có mã tham chiếu duy nhất cho từng operation, phân biệt trả người bán và hoàn khách, không chứa dữ liệu khách không cần thiết. Tải lại cùng operation SHALL giữ nguyên nội dung, số tiền và destination đã chốt.

#### Scenario: Reload and parallel equal amounts
- **WHEN** operator tải lại QR hoặc hai nghĩa vụ cùng95.000đ tồn tại
- **THEN** cùng operation giữ reference cũ; hai nghĩa vụ có reference khác, không đối chiếu theo số tiền/tên khách đơn thuần

### Requirement: One active operator claim limits duplicate manual work

Mỗi nghĩa vụ/allocation SHALL chỉ có một claim thực hiện đang hoạt động. Người khác SHALL thấy đang xử lý và không được mở một lệnh chi độc lập; trạng thái không rõ sau chuyển khoản SHALL không tự hết hạn để tạo lệnh chuyển mới.

#### Scenario: Owner and Staff prepare the same payout concurrently
- **WHEN** hai người đủ quyền cùng mở QR cho một khoản N
- **THEN** chỉ một claim hiện hành; người còn lại không nhận một tác vụ chuyển tiền thứ hai

#### Scenario: Operator already transferred but has not confirmed
- **WHEN** tab đóng hoặc claim cũ không hoạt động trong khi có thể đã chuyển tiền
- **THEN** giữ chờ ghi nhận/REVIEW và cho tiếp nhận đối soát có audit, không tự release để chuyển lại

### Requirement: Manual completion requires a bank transaction reference

Owner/Staff SHALL nhập mã giao dịch chuyển khoản không rỗng hợp lệ và bấm Hoàn tất trước khi server ghi PAID/REFUNDED cho nghĩa vụ REAL. QR, mở app ngân hàng, ghi chú hoặc body outcome SUCCESS SHALL không thay mã giao dịch bắt buộc.

#### Scenario: Operator completes after transferring
- **WHEN** operator có quyền gửi mã giao dịch hợp lệ cho operation/phiên bản hiện tại
- **THEN** server lưu số tiền/người nhận đã chốt, bank reference, actor và thời điểm; hiển thị cơ sở xác nhận đã chuyển

#### Scenario: Reference is missing or an old demo command is used
- **WHEN** gửi mã trống/chỉ khoảng trắng hoặc outcome SUCCESS không có reference
- **THEN** server từ chối, nghĩa vụ vẫn chưa hoàn tất và không tính là đã chi/hoàn

### Requirement: Manual completion is idempotent across all disbursement kinds

Một operation SHALL chỉ có một xác nhận thành công. Replay cùng key/body SHALL trả kết quả cũ; payload khác hoặc reference đã dùng ở khoản khác cùng nguồn chi SHALL bị từ chối/REVIEW. Bản ghi thành công SHALL bất biến và không nhân tiền khi retry.

#### Scenario: Confirmation response is lost
- **WHEN** server đã ghi PAID nhưng response mất và operator gửi lại cùng key
- **THEN** trả receipt đã có; không tạo khoản chi hoặc yêu cầu chuyển khoản ngân hàng lần hai

#### Scenario: Bank reference is reused for a different obligation
- **WHEN** cùng mã giao dịch/nguồn chi đã dùng trả A được gửi để hoàn B hoặc trả khoản khác
- **THEN** server báo trùng/cần đối soát, không dùng lại làm hai khoản chi thành công

### Requirement: Current facility grants control manual disbursement actions

Owner hoặc active Staff có TRANSFER_PAYOUT đúng cơ sở SHALL thực hiện trả A; BOOKING_REFUND SHALL thực hiện hoàn khách. Quyền SHALL kiểm lại ở prepare/complete/retry; Customer, Admin giám sát và người chỉ tạo booking SHALL không tự có quyền chi.

#### Scenario: Staff uses the wrong permission or facility
- **WHEN** Staff chỉ BOOKING_REFUND gọi trả A hoặc dùng grant cơ sở khác
- **THEN** server từ chối trước khi trả STK/QR hoặc cập nhật trạng thái tiền

#### Scenario: Permission is revoked after QR preparation
- **WHEN** Owner thu hồi quyền trước request hoàn tất của Staff
- **THEN** request mới của Staff bị chặn; Owner được tiếp nhận ghi nhận giao dịch đã chuyển với audit, không yêu cầu chuyển lần nữa

### Requirement: Manual recordings disclose their assurance level

Kết quả chi/hoàn REAL thủ công SHALL mang confirmationMethod MANUALLY_RECORDED và nhãn Cơ sở xác nhận đã chuyển. Hệ thống SHALL không gọi đó là xác thực ngân hàng, không dùng reference người nhập để chứng minh tiền khách đã trả hoặc hứa có thể thu hồi tiền đã chuyển.

#### Scenario: Participant views a completed manual payout
- **WHEN** A xem khoản N đã được Owner xác nhận
- **THEN** thấy số tiền, trạng thái và phương thức xác nhận thủ công phù hợp scope; không thấy nhãn payOS/bank-verified cho khoản chi đó

### Requirement: Zero amount decisions never generate bank transfer operations

N/H bằng0 SHALL có quyết định NOT_REQUIRED/NO_REFUND và lịch sử phù hợp, không QR chi, không yêu cầu nhập mã giao dịch cho khoản0 hoặc tạo money event thành công số0.

#### Scenario: Owner selected a full retention fee or no refund
- **WHEN** x100% tạo N0 hoặc policy hủy tạo H0
- **THEN** thể hiện không cần chuyển tiền; không bịa bank reference, dù A vẫn phải khai ngân hàng trước khi đăng sang nhượng
