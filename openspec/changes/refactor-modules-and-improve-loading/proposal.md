> **HISTORY ONLY — NOT AN ACTIVE REQUIREMENT SOURCE.**
> Read `openspec/CURRENT_CHANGE.md` at the project root for the current proposal.
> Do not apply, merge, or resume requirements/tasks from this document unless
> the user explicitly requests this historical change. Task counts are historical.

# Proposal

## Why

SportHub hiện có các màn hình gom nhiều trách nhiệm trong một file, điển hình `frontend/features/auth/auth-form.tsx` có 831 dòng; đồng thời có file ít dòng nhưng nén nhiều màn hình vào những dòng dài hàng nghìn ký tự. Việc bảo trì khó hơn, còn các chuỗi import, lần tải dữ liệu riêng lẻ và cách đọc dữ liệu tìm kiếm/báo cáo tạo ra những điểm cần đo và tối ưu trước buổi demo.

## What Changes

- **Đo trước, tối ưu có bằng chứng:** ghi nhận dung lượng JavaScript/tài nguyên, số request, thời điểm nội dung chính sẵn sàng và thời gian API trên cùng dữ liệu demo; so sánh trước/sau bằng bản dựng production. Số dòng code không được dùng làm bằng chứng chương trình chạy nhanh hơn.
- **Tách module theo trách nhiệm:** ưu tiên xác thực, hồ sơ, điều hướng, nhân viên, quét QR, cấu hình cơ sở và báo cáo. Các trang giữ vai trò ghép thành phần; biểu mẫu, xử lý dữ liệu, hook và hằng số có nơi sở hữu rõ ràng. Định dạng code dễ đọc trước khi đánh giá kích thước; không nén JSX hoặc chia file thành những mảnh tùy ý để vượt kiểm tra số dòng.
- **Giảm phần phải tải ban đầu:** tách đăng nhập khỏi mã đăng ký chủ cơ sở; tách tiện ích tạo payload khỏi thành phần giao diện; tải công cụ QR/camera và công cụ vị trí khi cần. Giữ cơ chế bản đồ tìm kiếm đã tải theo nhu cầu, tách dữ liệu tỉnh/thành nhẹ khỏi toàn bộ danh sách xã/phường, tối ưu ảnh và phông chữ cục bộ.
- **Thống nhất cách đọc dữ liệu:** mở rộng `useApi` theo hướng tương thích, chia sẻ request GET đang chạy, hủy request không còn người dùng và bỏ kết quả cũ. Chỉ danh mục công khai được giữ ngắn hạn; dữ liệu booking, quyền, tiền và điều khoản phải được làm mới đúng thời điểm. Đăng xuất/đổi tài khoản không được để lộ dữ liệu của phiên trước.
- **Tối ưu đường đọc backend có giới hạn:** tách phần truy vấn lịch trống khỏi điều phối booking, tách truy vấn/tổng hợp báo cáo, tách các trách nhiệm lịch/giá trong `ScheduleService`. Thu hẹp truy vấn theo ngày/kỳ cần xem, giữ đầy đủ công nợ kỳ trước; xử lý tìm kiếm theo nhóm cơ sở với mức song song hữu hạn khi phép đo xác nhận hiệu quả. Giữ các hàng rào đồng bộ cấu hình, transaction và kiểm tra quyền hiện có.
- **Kiểm soát chất lượng lâu dài:** áp dụng định dạng và ngân sách kích thước cho module logic đã refactor, kiểm tra đường import nặng, bổ sung kiểm thử hành vi bị ảnh hưởng và hướng dẫn đo hiệu năng có thể chạy lại. Triển khai theo từng phần nhỏ, có thể hoàn tác độc lập.
- **Giữ nguyên sản phẩm đồ án:** giao diện tiếng Việt, route/API công khai, chức năng hiện có và mô hình thanh toán mô phỏng; không xây thêm hệ thống ngân hàng, cache phân tán, hàng đợi hoặc microservice.

## Capabilities

### New Capabilities

Không bổ sung chức năng nghiệp vụ mới. Change dùng `skip_specs: true`: đây là refactor và tối ưu cách thực thi, với tiêu chí kỹ thuật trong `design.md` và `tasks.md`; không tạo delta spec nghiệp vụ chỉ để mô tả việc chia file.

### Modified Capabilities

Không thay đổi yêu cầu nghiệp vụ. Giữ các quyết định về sở hữu booking, Guest, thanh toán đủ mới được vào sân, trạng thái `CHECKED_IN`, phí chuyển nhượng/hoàn tiền do Owner cấu hình và doanh thu thuộc cơ sở trong các change hiện có.

## Impact

- **Frontend:** cây đang chạy là `frontend/app`, `frontend/features`, `frontend/components`, `frontend/lib`; ưu tiên các file được liệt kê kèm bằng chứng trong design. Giữ các điểm export đang được route/test sử dụng trong giai đoạn chuyển tiếp. Không sửa cây `frontend/src` cũ để làm kiểm thử chạy qua.
- **Backend:** tập trung `booking-service` và `schedule-service`, gồm `DiscoveryService`, đường `BookingService.availability`, `FacilityDemoReportingService`, truy vấn liên quan và `ScheduleService`; không viết lại vòng đời Payment/Transfer.
- **API/database:** giữ hợp đồng công khai, body, quyền, tiền chính xác, thứ tự kết quả và dữ liệu hiện có. Chỉ cân nhắc index bổ sung khi có kế hoạch truy vấn chứng minh cần thiết; không đổi bảng nghiệp vụ hoặc chạy chuyển đổi dữ liệu thật.
- **Công cụ/kiểm thử:** tận dụng Node test, Playwright, Maven và fixture hiện có; thêm cấu hình định dạng và công cụ đo nhỏ. Không bắt buộc thêm framework quản lý dữ liệu frontend hoặc hệ thống giám sát mới.
- **Phối hợp:** bảo toàn các thay đổi Việt hóa đang có trong working tree; không gộp việc hoàn tất các task chưa xong của `fix-counter-booking-ownership` vào change này. Chỉ triển khai sau khi người dùng yêu cầu apply riêng.
