# Kiểm chứng frontend mới

Ngày: 12/10/2026. Kiểm tra với source ở nhánh `codex/frontend-rebuild`. Chỉ sửa `frontend/`; backend, migration, tài liệu gốc và tệp môi trường được bảo toàn.

## Kiểm tra mã nguồn

- `pnpm install --frozen-lockfile`: thành công sau khi gỡ dependency UI cũ không còn dùng.
- `pnpm typecheck`: thành công với `next typegen` và TypeScript.
- `pnpm lint`: thành công, không warning/error.
- `pnpm test`: 13 test thành công. Bao gồm slot liên tiếp/gap/đang giữ/thiếu giá/stale ID; chống external next URL; grammar route; refresh đồng thời chỉ một request; token và credentials; FormData boundary/idempotency; lỗi validation/network/response; proxy origin/giới hạn upload/status/cookie.
- `pnpm build`: thành công với Next.js production compilation, type checking và output standalone.
- CSV ánh xạ: đúng 66 UC, mỗi dòng đủ 10 cột.

## Trình duyệt và API đang chạy

Playwright trên Microsoft Edge headless, viewport desktop 1440×1000 và mobile 390×844, gateway container demo hiện có ở `localhost:18080`, frontend mới ở `localhost:3000`.

51 màn hình/URL được mở qua đăng nhập thật của bốn tài khoản demo Customer, Owner, Staff và Admin. Kiểm tra trang chủ, tìm kiếm, cơ sở, lịch slot, hồ sơ, booking/group/transfer lists, toàn bộ workspace của một cơ sở Owner, Staff schedule/counter và Admin lists/detail/reports. Các màn hình có bản ghi sẵn được đọc theo ID lấy từ response, không hardcode thông tin giả vào frontend.

Kết quả lần kiểm tra sau sửa: không có pageerror JavaScript hoặc hydration; không tràn ngang trang ở desktop/mobile. Không có API lỗi ngoài trường hợp cố ý nhập mã mời nhóm không hợp lệ: backend trả 403, giao diện trình bày lỗi thật. Các lần đọc ảnh trùng đã được gộp, giải quyết rate-limit 429 phát hiện ở lần kiểm tra đầu.

Kiểm tra bổ sung ở 360, 390, 768 và 1280 px: lịch chọn slot không tràn ngang; số tiền từng slot và tổng tiền không xuống dòng. Quote thật cho hai slot liên tiếp của sân demo trả 700.000 VND. Trang chọn lịch và xác nhận dùng chung breakdown theo khoảng giờ, không đưa tên cấu hình nội bộ vào thông tin giá dành cho khách. URL không tồn tại trả HTTP 404.

QA với backend thật chỉ đăng nhập/refresh và đọc dữ liệu/quote. Không tạo booking/nhóm/listing/payment, upload, cấp quyền, duyệt hồ sơ hay sửa dữ liệu nghiệp vụ. Backend có thể ghi session/audit cho thao tác đăng nhập và xem hồ sơ Admin theo hành vi sẵn có của controller.

## Luồng ghi trong trình duyệt được cô lập

`tests/browser-flows.mjs` intercept toàn bộ `/api/v1/**`; endpoint không biết khiến test thất bại, không lọt yêu cầu sang backend thật. Fixture nằm duy nhất trong test và không được import vào runtime. Bảy workspace test đã chạy thành công:

1. Customer chọn slot, quote, lỗi mạng giữ chỗ rồi thử lại với cùng idempotency key; tạo nhóm; phân bổ sai tổng bị chặn; phân bổ CUSTOM hợp lệ; tạo thanh toán đúng memberId và xử lý DEMO SUCCESS.
2. Customer nhận listing, tạo payment đúng acquisitionId; thanh toán thành công nhưng handoff còn pending không hiển thị vé như đã chuyển thành công.
3. Owner upload ảnh đúng facility ID và multipart boundary; bỏ trường courtId rỗng; đóng modal giờ hoạt động không vô tình submit; lưu intervals đúng payload; không tràn ngang ở mobile 360 px.
4. Staff chỉ có SCHEDULE_READ xem được lịch/giá; không gọi booking list cần BOOKING_READ và không được tạo booking quầy.
5. Admin khóa tài khoản đúng reason/duration và chỉ sau xác nhận modal.
6. Guest đi qua Guard đến login giữ nguyên invitation code trong next URL.
7. Guest đăng ký Owner multipart đủ request/ba tệp; legal/facility/setup đúng cấu trúc, đủ ngày mở cửa và idempotency key; chuyển đến verify theo response.

Có thể chạy lại với một package Playwright sẵn có:

```powershell
$env:PLAYWRIGHT_MODULE = 'đường dẫn tuyệt đối tới package playwright'
$env:PLAYWRIGHT_CHANNEL = 'msedge'
$env:FRONTEND_QA_URL = 'http://localhost:3000'
node tests/browser-flows.mjs
```

Không đặt credential thật trong source test. Script xuất screenshot QA vào thư mục tạm của máy. Nếu Playwright được cài và Chromium có sẵn, không cần hai biến đầu.

## Giới hạn của kết quả

Các luồng mutation đã được đối chiếu DTO/service và kiểm tra payload/UI bằng fixture; không tuyên bố đã chạy mọi mutation end-to-end với backend thật. Không kiểm tra live thanh toán thật, email/SMS delivery, hoàn tiền/payout, phá lỗi saga, tải lớn, upload trên production, container image build, vận hành TLS/reverse proxy hoặc xác nhận từng nhánh đặc tả. Những chức năng backend còn thiếu nằm trong `rebuild-audit.md` và `requirements-api-map.csv`.

Đây là kiểm chứng triển khai frontend và tích hợp đọc API hiện có, không phải chứng nhận toàn bộ nền tảng đã hoàn thành 66 Use Case nghiệp vụ.
