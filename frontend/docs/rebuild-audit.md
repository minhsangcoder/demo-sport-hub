# Đối chiếu nghiệp vụ và phạm vi frontend mới

Ngày phân tích: 12/10/2026. Frontend cũ được bảo toàn ở commit `ae84493`; bản triển khai mới nằm trên nhánh `codex/frontend-rebuild`. Git sạch trước khi thay thế. Tất cả thay đổi của lần xây dựng này nằm trong `frontend/`.

## Nguồn nghiệp vụ

Đã đối chiếu nội dung Use Case, các luồng chính/phụ, ngoại lệ và business rules trong bốn tài liệu:

- `Document/dacn-NS-2809.docx` trong repository.
- `base-bussines.pdf`, 68 trang, bản chuẩn trong thư mục `Document` của checkout gốc `D:/PersonalProject/sport-court-booking-system`.
- `Dac_Ta_UC3_UC4_UC7.docx` trong cùng thư mục.
- `Dac_Ta_UC5_UC6_UC8.pdf` trong cùng thư mục.

`base-bussines.pdf` là đặc tả nghiệp vụ mục tiêu, theo thứ tự ưu tiên đã ghi trong `docs/implementation-progress.md`. Không dùng tên Use Case để thay thế nội dung đặc tả. Tài liệu bổ sung chỉ áp dụng khi không mâu thuẫn với bản chuẩn. Tệp tài liệu gốc không bị sửa hay di chuyển. Mã SHA-256 của PDF chuẩn: `0dbb4e10eaa7c2c807519779d1c35380b843e752832f4287370e9f1fb0092172`.

## Hợp đồng và dữ liệu

[`requirements-api-map.csv`](requirements-api-map.csv) ánh xạ 66 Use Case đến actor mục tiêu, quyền thực tế, màn hình, HTTP method/path, request/response, bảng dữ liệu và phần còn thiếu. Đường dẫn API trong bảng được tính từ `/api/v1`. Trạng thái hỗ trợ mô tả khả năng backend và phần frontend đã nối, không phải chứng nhận mọi nhánh nghiệp vụ đã chạy end-to-end.

[`api-inventory.json`](api-inventory.json) lưu vị trí và mapping của 27 controller, bao gồm cả API nội bộ để phân biệt với API frontend được sử dụng. [`database-inventory.json`](database-inventory.json) lưu nội dung và SHA-256 của 28 migration đã đọc; không chạy migration, cập nhật schema hoặc SQL dữ liệu.

Sáu service nghiệp vụ có database riêng:

- **Identity:** users, user_roles, user_profiles, refresh_tokens, otp_codes, owner_profiles, owner_applications, owner_application_submissions, owner_signup_receipts, staff_facility_bindings, staff_permissions, audit_log, identity_notifications và event inbox.
- **Facility:** sport_categories, facilities, facility_amenities, courts, maintenance_windows, facility_images, facility_documents, facility_reviews, facility_audit, facility_transfer_policies, first_facility_applications, media_cleanup và event outbox.
- **Schedule:** court_references, operating_hours, exception_calendar, maintenance_windows, pricing_rules, schedule_audit, application_configuration_freeze.
- **Booking:** slot_reservations, bookings, booking_history, booking_groups, group_members, group_contributions, request_idempotency, payment_reconciliation, transfer_leases, transfer_lease_audit và event inbox/outbox.
- **Payment:** payment_orders, provider_callbacks, payment_audit, payment_requests, refunds, transfer_escrow, owner_wallets và event inbox/outbox.
- **Transfer:** transfer_listings, transfer_acquisitions, transfer_audit, transfer_reconciliation, request_idempotency và event inbox/outbox.

UUID tham chiếu service khác không được giả định là foreign key xuyên database. Booking có FK đến reservation; nhóm dùng cùng ID và FK đến booking; thành viên và đóng góp liên kết nhóm. Reservation có exclusion constraint chống chồng thời gian đang HOLD/BOOKED. Snapshot giá booking bất biến ở database, frontend không sửa snapshot. Giá dùng NUMERIC; phân bổ nhóm phải là số nguyên và tổng đúng số tiền booking. Chi tiết khóa, enum, unique, FK và CHECK nằm nguyên văn trong inventory migration.

## Actor và ranh giới quyền

Guest xem cơ sở công khai, tìm sân, xem slot và quote. CUSTOMER giữ slot, đặt sân, thanh toán, tham gia nhóm, chuyển nhượng, chỉnh hồ sơ và đăng ký Owner. Booking Owner là người tổ chức nhóm, không đồng nghĩa với role OWNER. Chủ hồ sơ chưa duyệt vẫn là CUSTOMER và chỉ sửa cơ sở đầu tiên gắn với hồ sơ mình.

OWNER quản lý cơ sở thuộc sở hữu, sân, media/tài liệu, lịch/giá, bảo trì, chính sách chuyển nhượng, nhân viên và báo cáo. STAFF chỉ dùng các quyền được binding cấp tại cơ sở: BOOKING_READ, BOOKING_CREATE_COUNTER, BOOKING_CHECK_IN, BOOKING_COMPLETE, SCHEDULE_READ. ADMIN thẩm định hồ sơ, quản trị tài khoản, giám sát booking/thanh toán và xem báo cáo. Guard frontend phục vụ UX; backend vẫn quyết định quyền cho từng yêu cầu.

Access token chỉ ở bộ nhớ. Refresh cookie HttpOnly do backend cấp; refresh đồng thời được gộp. Không lưu token/password vào localStorage. Các mutation có idempotency trong hợp đồng giữ cùng key khi thử lại cùng ý định sau lỗi mạng. Upload dùng FormData và giới hạn định dạng/kích thước theo API.

## Khác biệt và thiếu sót phải giữ rõ

1. UC-1.8: chưa có hồ sơ nhân viên độc lập chưa liên kết tài khoản; chỉ binding tài khoản STAFF đã hoạt động. Không có API tìm trước tài khoản dành cho Owner; endpoint binding tự kiểm tra identifier.
2. UC-1.10: API khóa 7 ngày/30 ngày/vĩnh viễn và mở khóa; chưa có thao tác tự chọn SUSPENDED/DEACTIVATED. Admin không được đổi tài khoản chính mình hoặc tài khoản Admin khác.
3. UC-2/3: tài liệu cho Staff nhiều quyền ghi nhưng backend chỉ Owner/applicant được ghi cấu hình. Frontend không cấp các thao tác này cho Staff.
4. UC-2.7: API thêm bảo trì chưa kiểm tra xung đột booking đang tồn tại. UI yêu cầu kiểm tra lịch trước; không tuyên bố backend đã kiểm tra hoặc hủy booking cũ.
5. UC-3.5: preview Schedule trả ELIGIBLE/BLOCKED theo cấu hình, không phải lịch trống thật. Lịch booking dùng availability của Booking Service để cộng booking/hold. Cấu hình mới không đổi snapshot booking đã tạo. Chưa có API danh sách đầy đủ booking bị ảnh hưởng/luồng gửi thông báo thay đổi cấu hình.
6. UC-4.2: chưa có API yêu thích hoặc đánh giá. Ảnh cơ sở lấy từ media API; cơ sở không có ảnh hiển thị trạng thái thiếu ảnh. Ảnh trang chủ có giấy phép và được ghi là ảnh minh họa.
7. UC-4.5: booking quầy dùng tên và điện thoại khách; chưa có tìm/liên kết Customer theo điện thoại hoặc phương thức thu tiền mặt riêng trong hợp đồng.
8. UC-5.1/7.5: provider hiện có DEMO; UI nêu rõ không chuyển tiền thật. Không bịa đường dẫn checkout cho provider chưa trả checkout URL.
9. UC-5.4: hủy cá nhân chỉ hỗ trợ PENDING. Đặc tả hủy đã thanh toán không hoàn tiền chưa được backend thực hiện. Owner/Staff không có hủy thay qua endpoint hiện có.
10. UC-5.5/6.8: endpoint refund chỉ tạo bản ghi BLOCKED_RULE để rà soát. Không có thực thi, số tiền được quyết định hay retry hoàn tiền. UI Admin chỉ ghi nhận yêu cầu và xem kết quả thực tế.
11. UC-5.8: có danh sách reconciliation; chưa có API giải quyết sai lệch thủ công.
12. UC-6: sau khi một thành viên đã trả tiền hoặc đã tạo yêu cầu thanh toán, backend chặn thay thành viên, phân bổ, rời/hủy nhóm. Các luồng tài liệu điều chỉnh sau thanh toán cần chính sách/backend bổ sung.
13. UC-6.9: một mã QR booking, không có QR/check-in riêng từng thành viên. Chỉ người giữ quyền sử dụng nhận mã; Staff nhập token với quyền BOOKING_CHECK_IN.
14. UC-6.10: chưa có chat nhóm, nhóm bền vững sau trận, giải tán hoặc dùng lại nhóm cho nhiều buổi. Không dựng chat giả.
15. UC-7: handoff và saga có thật; escrow HELD_POLICY_BLOCKED chưa chi trả người bán. Thành công thanh toán và thành công handoff được hiển thị riêng; người chuyển không lộ dữ liệu cá nhân trên listing.
16. UC-8.6/8.11: chưa có API khóa/tạm ngừng cơ sở hoặc xử lý khiếu nại. Các quyết định duyệt cơ sở chỉ APPROVE/REJECT/SUPPLEMENT_REQUIRED.
17. UC-8.7: chỉ GET danh mục và POST tạo bộ môn; chưa có sửa tên/ngừng sử dụng.
18. UC-8.12/13: báo cáo trả giá trị booking/đơn thanh toán và số phút đặt, không có tài chính đã tất toán, lịch sử payout hay tỷ lệ lấp đầy có mẫu số. UI ghi rõ nguồn và cơ sở thời gian; không tạo doanh thu/occupancy giả.

Tên wire PENDING_APPROVAL và SUPPLEMENT_REQUIRED được giữ nguyên, dù bản chuẩn gọi tương ứng PENDING_REVIEW/REQUIRE_REVISION. Không thay enum hoặc trạng thái database để khớp wording giao diện.

## Kiến trúc và triển khai

Toàn bộ trang/component/layout/style cũ được thay bằng `src/modules`, `src/components` và `src/lib`. Một route dispatcher có grammar rõ ràng, trả HTTP 404 cho URL không tồn tại. Module chia theo nghiệp vụ; UI primitives, model wire, transport, auth, state và selection tách riêng. Layout khách hàng dùng website đặt sân; ba workspace quản lý có sidebar riêng. Không có mock dữ liệu trong mã runtime.

Design system dùng xanh rừng `#164b35`, lime `#d1ec83`, nền trung tính và font Be Vietnam Pro tự host theo OFL. Card/ticket cho khách hàng; lịch, bảng, bộ lọc và panel cho vận hành. Focus, skip link, label, modal native có focus trap, keyboard, loading/error/empty, mobile navigation và bảng cuộn ngang được dùng nhất quán. Bản đồ Leaflet/OpenStreetMap dùng tọa độ thật và attribution.

`/api/v1/[...segments]` thay rewrite trực tiếp bằng gateway proxy phía Next: giữ Authorization, cookie, query, status, response envelope và multipart; kiểm tra origin của yêu cầu ghi, bỏ Origin ở chặng server-to-server, tối đa 31 MB, timeout 30 giây. Việc này giải quyết CORS khi cổng preview khác cổng frontend cũ. `GATEWAY_URL`, output standalone và cấu hình unoptimized images được giữ. Docker runtime nhận cùng build arg GATEWAY_URL và có thể override bằng environment khi chạy. Không thay backend CORS, env gốc hay deployment ngoài frontend.

Các thư viện UI cũ không còn sử dụng và assets placeholder cũ đã gỡ. Lockfile được cập nhật cùng package.json; dependency chính còn Next/React/Tailwind/Lucide/Leaflet. Chi tiết kiểm chứng nằm trong [`verification.md`](verification.md).
