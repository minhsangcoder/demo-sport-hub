# Spec Delta

## Purpose

Đưa lời mời và thao tác chốt chia tiền vào ngay phần thanh toán nhóm.

## ADDED Requirements

### Requirement: Group checkout displays an actionable invitation

Trang nhóm SHALL hiển thị mã mời, nút sao chép và liên kết xem QR tại phần thanh toán cho người tổ chức khi nhóm còn nhận thành viên. Nhóm đã khóa danh sách, đủ người hoặc kết thúc SHALL không phát hành lời mời mới. Thành viên khác SHALL nhập mã qua luồng xác minh/tham gia hiện có.

#### Scenario: Another customer joins using the checkout invitation
- **WHEN** người khác nhập mã hợp lệ và xác nhận tham gia
- **THEN** người đó mở trang nhóm; trang của người tổ chức tự tải lại dữ liệu thành viên/tiến độ qua polling mỗi 5 giây mà không mất thao tác đang nhập

### Requirement: Organizer explicitly finalizes the payment split

Thành viên tham gia SHALL không tự chốt danh sách hoặc tạo giao dịch. Người tổ chức SHALL bấm “Chia đều và bắt đầu thanh toán” hoặc chọn chia tùy chỉnh. Server SHALL tính phần tiền từ danh sách hiện tại dưới group lock; bấm lặp không tạo thanh toán trùng. Khi đã bắt đầu thanh toán SHALL không thay đổi thành viên/phân bổ.

#### Scenario: Two members split a court price of 120000 VND
- **WHEN** người tổ chức chốt chia đều sau khi người thứ hai đã tham gia
- **THEN** mỗi người thấy phần60000đ và nút thanh toán của mình; mã mời được ẩn và không nhận thêm người

### Requirement: Group checkout excludes the removed resale refund panel

Trang nhóm SHALL không render “Hủy theo chính sách cơ sở (Người mua chuyển nhượng)” hoặc nút xem khoản hoàn cho người mua lại. Các nghĩa vụ lịch sử vẫn hiển thị trong phần thu chi của người dùng.

#### Scenario: An existing confirmed group is viewed
- **WHEN** người tổ chức mở nhóm đã thanh toán đủ
- **THEN** không thấy panel hoàn cho người mua lại và không thể mở lại mời/chia tiền
