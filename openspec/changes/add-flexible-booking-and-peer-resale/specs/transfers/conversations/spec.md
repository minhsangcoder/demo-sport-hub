# Spec Delta

## Purpose

Cho người bán và người mua trao đổi trong giao dịch chuyển nhượng và cung cấp chứng cứ riêng tư theo đúng phiên, không dùng tin nhắn làm lệnh giữ sân hoặc đổi tiền.

## ADDED Requirements

### Requirement: Buying opens a private seller buyer conversation

Phiên mua được chấp nhận SHALL liên kết conversation chỉ có A và B của listing. Retry không tạo nhiều phòng; các lần mua lại giữa cùng hai người có thể dùng cùng conversation nhưng trạng thái/terms/evidence phải phân biệt từng attempt.

#### Scenario: Two buyers contact the same seller
- **WHEN** B và C có các conversation hợp lệ với A
- **THEN** mỗi buyer chỉ đọc phòng/chứng cứ thuộc mình, không thấy phòng của buyer kia

### Requirement: Conversation history survives acquisition expiry

Hết hạn giữ/mở lại listing SHALL giữ lịch sử và quyền nhắn của hai bên trong conversation, theo kiểm soát an toàn hiện hành. Tin nhắn sau hạn SHALL không tạo claim, gia hạn hoặc thay holder.

#### Scenario: The five minute acquisition expires during a discussion
- **WHEN** B chưa gửi khai thanh toán hợp lệ sau5 phút
- **THEN** listing có thể mở lại và A–B vẫn nhắn được; phòng chat không giữ sân

### Requirement: Chat messages do not change accepted terms

Message SHALL chỉ trao đổi, không sửa giá, ngân hàng, hạn hoặc quyền sử dụng đã chốt. Đổi giá trước khai tiền SHALL cần hủy/rút phiên hợp lệ và chấp nhận terms mới. Đổi ngân hàng SHALL còn qua guard trách nhiệm cá nhân; hủy phiên không tự mở khóa booking/case đang tồn tại.

#### Scenario: Seller promises a different price in chat
- **WHEN** A gửi tin nhắn giảm giá sau QR đã chốt P
- **THEN** QR/amount không tự đổi và B được hướng dẫn cập nhật phiên chính thức trước thanh toán

### Requirement: Declaring payment requires ready private evidence and a reference

B SHALL đính kèm ít nhất1 ảnh JPEG/PNG hợp lệ đã lưu thành công cùng mã giao dịch1–120 ký tự khi khai đã chuyển. Ảnh tối đa5 MiB/20 triệu pixel, tối đa3 ảnh/lần nộp; phải thuộc uploader và attempt. Text trống hoặc URL ảnh tự chỉ định SHALL không đủ.

#### Scenario: Receipt upload is incomplete or belongs to someone else
- **WHEN** B khai đã chuyển với ảnh đang upload, sai attempt hoặc file giả dạng ảnh
- **THEN** không chuyển sang trạng thái đã khai và không kéo dài hạn giữ

### Requirement: Evidence and case records have scoped immutable history

Ảnh, reference và các lần nộp SHALL có lịch sử không bị sửa/xóa để thay chứng cứ đã dùng. Chỉ A/B đúng attempt và Owner đúng cơ sở xử case SHALL đọc được; public listing, Staff không có quyền case và Owner cơ sở khác SHALL không đọc được.

#### Scenario: Owner inspects disputed evidence
- **WHEN** Owner hiện hành của đúng cơ sở mở case
- **THEN** đọc được ảnh/timeline có audit; không có quyền sửa chứng cứ do một bên nộp

### Requirement: Chat and uploads are bounded and safely replayed

Text SHALL tối đa2000 ký tự, lịch sử có cursor/max100 tin/lần và không chạy HTML người dùng. Gửi lặp cùng key/body SHALL trả cùng message. Chat giới hạn10 tin/phút/user/phòng, upload10 ảnh/giờ/user; đổi session không giữ private data của tài khoản trước.

#### Scenario: Message succeeds but its response is lost
- **WHEN** người dùng gửi lại cùng message/key
- **THEN** chỉ có một message, không gửi kép hoặc nhân chứng cứ

#### Scenario: A user spams a closed or expired attempt conversation
- **WHEN** số tin/ảnh vượt hạn dù không còn active claim
- **THEN** bị giới hạn có retryAt, không được tạo thêm phiên mua hoặc làm treo chat của người khác

### Requirement: Chat updates avoid unrelated reloads and unbounded history

Gửi/đọc tin SHALL chỉ cập nhật tài nguyên liên quan thay vì làm tải lại toàn bộ lịch, báo cáo hoặc danh mục ngân hàng. Tin mới SHALL được đọc tăng dần có giới hạn; tab ẩn/unmount/session đổi không tiếp tục polling hoặc giữ dữ liệu tài khoản trước. Thay đổi tiền/quyền/case SHALL vẫn làm mới mọi trạng thái liên quan và không tự replay mutation.

#### Scenario: A buyer sends one ordinary chat message
- **WHEN** tin nhắn thành công nhưng không đổi tiền, quyền hoặc trạng thái case
- **THEN** conversation được cập nhật, không phát lại GET lịch sân/dashboard/bank catalog chỉ vì gửi tin

#### Scenario: Seller confirmation changes usage and bank restrictions
- **WHEN** A xác nhận rồi handoff commit
- **THEN** holder, credential, listing/attempt và bank-lock views liên quan đều được làm mới; tối ưu chat không giữ quyền cũ

#### Scenario: Opening a long conversation does not replay its entire history
- **WHEN** người dùng mở phòng đã có 1000 tin
- **THEN** request không có cursor trả tối đa 50 tin mới nhất theo thứ tự tăng dần; polling dùng `after` để chỉ lấy tin mới
- **AND** tin cũ chỉ được tải khi người dùng yêu cầu qua `before`; không chấp nhận cả `before` và `after` trong cùng request, mỗi trang tối đa 100 tin và kiểm quyền như nhau
