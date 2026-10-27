> **HISTORY ONLY — NOT AN ACTIVE REQUIREMENT SOURCE.**
> Read `openspec/CURRENT_CHANGE.md` at the project root for the current proposal.
> Do not apply, merge, or resume requirements/tasks from this document unless
> the user explicitly requests this historical change. Task counts are historical.

# Spec Delta

## Purpose

Thu tiền thật vào đúng cơ sở bằng QR payOS và chỉ cung cấp kết quả thanh toán có bằng chứng server xác thực. Phạm vi REAL này tách biệt với thanh toán DEMO và thao tác ghi nhận chi thủ công.

## ADDED Requirements

### Requirement: Live payment channels belong to the receiving facility

Hệ thống SHALL chỉ tạo khoản thu REAL khi cơ sở có kênh payOS READY gắn đúng Owner và tài khoản nhận. Chỉ Owner của cơ sở được cấu hình kênh; quyền vận hành/chi/hoàn của Staff không cấp quyền đổi người nhận tiền.

#### Scenario: Ready and missing receiving channels
- **WHEN** khách tạo thanh toán cho cơ sở đã cấu hình hợp lệ hoặc cơ sở chưa có kênh READY
- **THEN** trường hợp hợp lệ nhận QR đúng tài khoản Owner của cơ sở; trường hợp thiếu bị chặn với lý do rõ, không dùng tài khoản SportHub hoặc fallback DEMO

#### Scenario: Unauthorized channel update
- **WHEN** Staff có quyền hoàn tiền hoặc Owner khác cơ sở gửi thay tài khoản nhận
- **THEN** server từ chối, không đổi kênh hoặc tiết lộ credential

### Requirement: Receiving channel changes preserve existing payment identity

Mỗi payment REAL SHALL giữ phiên bản kênh/người nhận đã chốt. Đổi hoặc tắt kênh SHALL chỉ ảnh hưởng tạo khoản thu mới; hệ thống SHALL tiếp tục nhận thông báo và đối soát khoản đã phát sinh theo context gốc.

#### Scenario: Owner replaces the receiving account
- **WHEN** Owner đổi ngân hàng sau khi khách đã mở một QR
- **THEN** order cũ không âm thầm đổi người nhận; lịch sử và tiền đến kênh cũ vẫn được theo dõi, order mới dùng revision mới hợp lệ

### Requirement: Live payment instructions are determined by the server

QR REAL SHALL chốt từ nghĩa vụ server: booking/member/acquisition, payer, cơ sở/Owner, amount/currency và hạn thanh toán. Browser SHALL không tự chọn provider, người nhận, số tiền hoặc kéo dài thời hạn.

#### Scenario: Client tampers with a QR request
- **WHEN** client gửi STK nhận khác, amount thấp hơn hoặc expiry xa hơn nghĩa vụ
- **THEN** server từ chối hoặc bỏ qua trường trái hợp đồng, không trả QR theo dữ liệu tự khai

#### Scenario: Deadline ends during QR creation
- **WHEN** provider trả kết quả sau hạn giữ sân/acquisition
- **THEN** UI không cho dùng QR như yêu cầu còn hiệu lực; hệ thống không gia hạn sân hoặc tạo quyền từ kết quả khởi tạo

### Requirement: Uncertain QR initialization reuses the original payment

Request trùng, mất response và timeout tạo QR SHALL phục hồi cùng nghĩa vụ/order đã cấp. Hệ thống SHALL xác định kết quả order đang không rõ trước khi tạo một yêu cầu thu khác có thể làm khách trả hai lần.

#### Scenario: Provider created a link but the response was lost
- **WHEN** khách retry cùng hoặc khác key cho cùng nghĩa vụ đang chờ
- **THEN** nhận lại order/QR hiện có hoặc trạng thái đang khôi phục, không tạo order mới song song

### Requirement: Native provider evidence is authenticated and matched

Kết quả REAL SHALL được server xác thực chữ ký native và đối chiếu order/kênh/tài khoản nhận, tiền tệ, khoản thu và trạng thái từ payOS. Webhook mẫu hoặc dữ liệu không khớp SHALL không trở thành khoản thu áp dụng.

#### Scenario: Forged or mismatched receipt
- **WHEN** webhook sai chữ ký, đúng chữ ký nhưng sai tài khoản/kênh/đơn, hoặc là mẫu đăng ký
- **THEN** không xác nhận đã trả đủ và không cấp quyền; dữ liệu xung đột hợp lệ được lưu để kiểm tra, mẫu không tạo tiền

#### Scenario: Browser claims payment success
- **WHEN** khách mở returnUrl có trạng thái thành công, tải ảnh chuyển khoản hoặc bấm tự xác nhận
- **THEN** server vẫn chờ evidence hợp lệ; những thao tác này không tạo SUCCESS hoặc quyền sử dụng

### Requirement: Received bank transactions survive webhook and query retries

Thông báo hợp lệ SHALL được lưu bền vững trước khi ACK. Cùng giao dịch trong cùng kênh SHALL chỉ được ghi tiền một lần dù qua webhook, query hoặc replay; giao dịch thật khác nhau SHALL vẫn được giữ riêng.

#### Scenario: Duplicate delivery and a worker crash
- **WHEN** provider gửi lại thông báo sau crash hoặc query tìm thấy giao dịch đã nhận
- **THEN** khôi phục xử lý khoản đã lưu, không nhân số thu, sự kiện áp dụng hoặc quyền booking

#### Scenario: Valid conflicting bank reference
- **WHEN** một reference đã có lại được gửi với order/số tiền khác
- **THEN** server giữ lịch sử và báo cần đối soát, không ghi đè hoặc ghi nhận thêm tiền theo payload mới

### Requirement: Full live payment requires sufficient distinct verified credits

Thu thiếu SHALL chưa xác nhận đủ tiền. Khi tổng khoản thu hợp lệ không trùng đạt nghĩa vụ, hệ thống SHALL áp dụng tối đa số phải thu một lần; phần thừa SHALL là khoản cần hoàn kỹ thuật riêng, không doanh thu/phí.

#### Scenario: Partial payment reaches the required amount
- **WHEN** nghĩa vụ100.000đ nhận40.000đ rồi60.000đ hợp lệ trước hạn
- **THEN** khoản đầu chưa cấp quyền; tổng100.000đ tạo một kết quả trả đủ nếu các điều kiện nghiệp vụ còn hợp lệ

#### Scenario: Payment exceeds the amount due
- **WHEN** nghĩa vụ100.000đ nhận110.000đ có đủ bằng chứng phân biệt
- **THEN** chỉ100.000đ được áp dụng;10.000đ được lưu cần hoàn không áp phí, không làm mất quyền do phần thừa nếu handoff/hold vẫn hợp lệ

#### Scenario: Aggregate cannot be reconciled to bank credits
- **WHEN** dữ liệu provider không đủ chứng minh các khoản thu tạo nên tổng
- **THEN** giữ trạng thái cần đối soát, không tự cộng một số tổng để cấp quyền

### Requirement: Financial receipt and business application are distinct outcomes

Tiền đã vào SHALL vẫn được ghi nhận dù order/link hết hạn hoặc booking/handoff không áp dụng được. Kết quả ngân hàng SHALL không tự phục hồi giữ sân đã mất; khoản không áp dụng SHALL có nghĩa vụ xử lý/hoàn theo số thực nhận.

#### Scenario: Valid payment arrives after the slot was released
- **WHEN** tiền đã xác thực đến sau hạn và sân được cấp cho khách khác
- **THEN** không xác nhận booking cũ hoặc lấy lại sân; hiển thị tiền đã nhận chưa áp dụng và khoản phải hoàn kỹ thuật đầy đủ

#### Scenario: Link cancellation is followed by a bank transfer
- **WHEN** link đã hủy nhưng tiền vẫn vào tài khoản Owner
- **THEN** hệ thống lưu khoản thu và xử lý theo quyền lợi thực tế, không coi thao tác hủy link là chứng cứ đã hoàn tiền

### Requirement: Lost notifications are recoverable without client authority

Server SHALL có cơ chế truy vấn phục hồi order pending/không rõ/đến muộn, có giới hạn gọi provider. Nút kiểm tra lại SHALL chỉ yêu cầu server đối chiếu cùng order; dừng polling hoặc mất mạng SHALL không tự kết luận tiền thất bại.

#### Scenario: Bank notification is missing while the customer is offline
- **WHEN** khách đã chuyển đủ, webhook không đến và tab đã đóng
- **THEN** server vẫn có thể đối soát/áp dụng hoặc tạo nghĩa vụ hoàn theo deadline; mở lại thấy kết quả server, không phải chuyển lại

#### Scenario: Provider is temporarily unavailable
- **WHEN** payOS timeout hoặc giới hạn request
- **THEN** hiển thị đang chờ/đối soát với retry có kiểm soát, không giả SUCCESS, số0 đầy đủ hoặc tạo payment khác để thử mù

### Requirement: Live and demo collections cannot substitute for each other

Triển khai LIVE SHALL không cho lệnh/callback DEMO hoàn tất khoản REAL hoặc cấp quyền từ dữ liệu DEMO. Dữ liệu cũ SHALL giữ money class gốc; thiếu cấu hình LIVE SHALL chặn luồng mới thay vì mô phỏng thành công.

#### Scenario: Payer calls a demo completion endpoint for a live order
- **WHEN** payer hoặc operator gửi demo SUCCESS cho payment/booking REAL
- **THEN** server từ chối và không tạo evidence/quyền hoặc thay đổi báo cáo REAL

### Requirement: Payment credentials and private QR instructions are scoped

Credential SHALL không được trả về browser hoặc log. QR/detail của khách SHALL chỉ đọc được bởi payer/operator hợp lệ của booking và scope hiện hành; ID của order SHALL không tự cấp quyền xem tài khoản/QR của người khác.

#### Scenario: A user changes payment identifiers
- **WHEN** Customer khác hoặc Staff ngoài cơ sở truy vấn payment/QR của khách
- **THEN** server từ chối trước khi trả thông tin nhận tiền hoặc trạng thái riêng của giao dịch
## ADDED Requirements

### Requirement: Short immutable payOS transfer description
The server SHALL persist a unique orderCode and derive the incoming description as `SH` plus its seven-character uppercase base36 encoding, at most nine ASCII letters/digits. It SHALL reuse that exact value for retries and signatures and reject sequence exhaustion rather than truncate or reuse a reference.

#### Scenario: VA channel accepts the same short reference
- **WHEN** an order is created or retried on a channel limited to nine characters
- **THEN** its description contains at most nine ASCII letters/digits and remains identical across create/query/replay

### Requirement: Credentials never appear in read projections
Channel API keys and checksum keys SHALL be write-only, encrypted at rest, and absent in full and in part from Payment, Facility and Admin read responses, exceptions and logs. Read projections SHALL return only credential presence and masked receiving account metadata.

#### Scenario: Reading facility or channel after configuration
- **WHEN** an authorized Owner or Admin reads a configured channel or facility
- **THEN** neither plaintext nor partial apiKey/checksumKey nor ciphertext is returned
