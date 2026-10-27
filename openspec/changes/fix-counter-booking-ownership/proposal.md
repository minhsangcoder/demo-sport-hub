> **HISTORY ONLY — NOT AN ACTIVE REQUIREMENT SOURCE.**
> Read `openspec/CURRENT_CHANGE.md` at the project root for the current proposal.
> Do not apply, merge, or resume requirements/tasks from this document unless
> the user explicitly requests this historical change. Task counts are historical.

# Proposal

## Why

Khi không tìm thấy Customer đủ điều kiện qua số điện thoại, luồng tại quầy ghi ID người vận hành vào `customer_id`, `current_holder_id` và reservation holder. Dữ liệu này làm Staff xuất hiện như chủ booking, đưa booking khách vào danh sách cá nhân và có thể cấp QR theo nhánh chủ sở hữu; cần sửa đồng bộ mô hình sở hữu và các dependency đang giả định owner luôn tồn tại.

## What Changes

- Chọn phương án A của Explore: booking vãng lai không có chủ sở hữu tài khoản; hai owner của booking và holder của reservation đã chuyển thành booking là `NULL`. Giữ Staff trong `created_by` và `counter_booking_guests.operator_id`, giữ snapshot tên/điện thoại khách.
- Giữ nguyên hai trường hợp Customer đặt online và Staff đặt cho Customer được Identity liên kết hợp lệ, kể cả tài khoản đồng thời có role Staff và Customer.
- Phân biệt quyền sở hữu với quyền vận hành theo cơ sở. Đồng nhất danh sách cá nhân, ngăn QR/chuyển nhượng dựa trên việc Staff chỉ là người tạo; giữ quyền xem, thanh toán DEMO theo chính sách hiện tại và check-in bằng mã tại cơ sở.
- **BREAKING**: response Booking có `customerId` và `currentHolderId` nullable; response Hold của reservation đã chuyển đổi có thể có `holderId` nullable. API danh sách cá nhân không còn trả booking chỉ do người gọi tạo tại quầy.
- Cập nhật Frontend cùng Backend: type nullable, hiển thị Customer/Guest và operator đúng vai trò, điều hướng counter về trang vận hành sau thanh toán, nối check-in bằng mã tại cơ sở và giữ QR/chuyển nhượng cho Customer thật. Hoàn thành change bao gồm kiểm thử hành trình UI cho ba trường hợp sở hữu.
- Chuẩn bị schema mới, audit/lifecycle/callback/transfer hỗ trợ nullable, cùng quy trình phân loại và sửa dữ liệu legacy có kiểm soát. Không tự sửa dữ liệu vận hành khi khởi động hoặc chạy schema migration.
- Giữ `CHECKED_IN`, giá chốt, danh tính payer, payment evidence, lịch sử chuyển nhượng và snapshot bất biến.

## Capabilities

### New Capabilities

- `booking/counter-booking`: Quyền sở hữu, khách vãng lai và quyền vận hành booking tại quầy xuyên suốt Backend/Frontend, bao gồm thanh toán, check-in và quy tắc chuyển đổi dữ liệu sở hữu legacy.

### Modified Capabilities

Không có main spec hiện hữu; `openspec list --specs --json` trả về danh sách rỗng. Các hành vi đang có cần giữ được mô tả thành yêu cầu tương thích trong capability mới.

## Impact

- Booking Service: `BookingService`, `BookingRepository`, `BookingLifecycle`, `OperationalBookingService`, `PaymentOutcomeConsumer`, `TransferLeaseService`, DTO và các controller liên quan. Phải bao phủ cả `/bookings/counter` và chuỗi hold/create với `counter=true`.
- Booking DB: migration mới sau V10 cho owner nullable có ràng buộc, `booking_code_registry.holder_id`, các trigger liên quan và kiểm thử nâng cấp; bổ sung công cụ preflight/correction với chế độ mặc định chỉ đọc và yêu cầu danh sách booking được duyệt trước khi ghi.
- Contract: `contracts/openapi/v1/booking.yaml`; giữ URL và cấu trúc response hiện có, mô tả nullable và phạm vi quyền chính xác.
- Identity/Payment/Transfer: giữ giao thức lookup, payer và event envelope hiện tại; kiểm thử tích hợp và chỉnh consumer Booking để Guest không gây lỗi hoặc được cấp quyền sở hữu sai.
- Frontend thuộc phạm vi implementation của change: `features/booking/types.ts`, các màn hình hold/payment/detail/my-bookings, `features/operations`, `features/integration/operations.tsx`, API helper và các route/component đang được sử dụng có liên quan. Hỗ trợ nullable, điều hướng theo quan hệ owner/operator, hiển thị mã vận hành và form check-in bằng mã; các nút thanh toán phản ánh chính sách payer DEMO hiện tại.
- Kiểm thử: bổ sung test logic FE cho nullable/điều hướng/quyền hiển thị, kiểm thử trình duyệt cho online, linked counter và Guest counter, cùng typecheck/lint/build. Chỉ xác nhận hoàn tất khi cả Backend và FE đạt acceptance; không thiết kế lại toàn bộ UI hoặc sửa cây `frontend/src` legacy ngoài nhu cầu của các route đang dùng.
- Ngoài phạm vi: tài khoản Customer giả, guest account tự sinh, guest party model mới, liên kết booking cũ bằng số điện thoại hiện tại, thanh toán tiền mặt/POS thật, đổi quyền thu ngân sang mọi Staff cùng cơ sở, hoàn tiền/settlement mới và đổi trạng thái sang `IN_PROGRESS`.

Việc tạo bộ artifact này chỉ cho phép lập kế hoạch. Apply, triển khai và sửa dữ liệu thực tế cần yêu cầu tiếp theo; không được tự chạy trong workflow propose.
