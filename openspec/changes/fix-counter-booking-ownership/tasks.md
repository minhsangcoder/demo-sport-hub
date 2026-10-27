> **HISTORY ONLY — NOT AN ACTIVE REQUIREMENT SOURCE.**
> Read `openspec/CURRENT_CHANGE.md` at the project root for the current proposal.
> Do not apply, merge, or resume requirements/tasks from this document unless
> the user explicitly requests this historical change. Task counts are historical.

# Tasks

Implementation được tiếp tục theo yêu cầu sửa các lỗi review ngày 2026-10-21. Các task chưa đạt hoặc cần xác minh lại đã được mở lại; không dùng checkbox cũ làm bằng chứng hoàn thành. Phạm vi gồm Backend, Frontend, contract và test tích hợp cho phương án A. Test dùng PostgreSQL dùng một lần qua Testcontainers hoặc DB test riêng được chỉ định; không dùng dữ liệu vận hành hiện có. Không tự chạy deployment hoặc correction trên DB thật.

## 1. Chính sách ownership và reader tương thích

- [x] 1.1 Bổ sung fixture/test phân biệt online, counter liên kết, Guest nullable và legacy fallback; kiểm tra trường hợp Staff-Customer liên kết tự thân hoặc nhận handoff không bị phân loại Guest, còn creator đơn thuần không được owner rights.
- [x] 1.2 Tập trung hóa phép kiểm tra current account owner, historical participant, original operator và Guest legacy có chứng cứ trong Booking; xác minh test không dùng NULL=NULL, role Staff hoặc creator=holder độc lập để quyết định quyền.
- [x] 1.3 Sửa `BookingRepository.history` hỗ trợ owner NULL bằng JSON null, giữ actor/source và audit cũ; xác minh test tạo/history/payment-failure/review không NPE, không ghi chuỗi null và không sửa chứng cứ cũ.
- [x] 1.4 Đồng nhất quan hệ cá nhân trong `BookingRepository.mine`, `BookingService.mine` và personal `OperationalBookingService.search`, giữ role gate hiện có; xác minh Staff creator không thấy booking khách ở list cá nhân, Customer liên kết/chủ cũ hợp lệ vẫn có dữ liệu đúng chính sách.
- [x] 1.5 Áp dụng projection effective owner NULL cho legacy fallback chắc chắn ở public detail/list/facility/operations/replay; xác minh DB chưa đổi, Guest không lộ Staff như owner và row mơ hồ được giữ để review.
- [x] 1.6 Sửa redaction `counterGuest`, contact, mã và QR nhất quán với quyền xem lịch sử; xác minh test chuyển nhượng A->B->C không tiết lộ contact gốc hoặc mã mới cho B chỉ nhờ lịch sử.
- [x] 1.7 Ghi lại ý nghĩa owner/operator và hành vi list/legacy projection trong tài liệu Backend của change; kiểm tra mô tả khớp các test 1.1-1.6 và phân biệt acceptance Backend từng phần với acceptance chung Backend/FE.

## 2. Schema nullable và các invariant PostgreSQL

- [x] 2.1 Tạo test nâng cấp từ V10 với online, nhóm, linked counter, Guest fallback và nhiều trạng thái; xác minh fixture lưu đủ giá, code registry, payment, snapshot và history để so sánh trước/sau.
- [x] 2.2 Thêm migration mới sau V10 nới nullable cho hai owner Booking, reservation holder và registry holder, giữ creator/operator NOT NULL cùng các FK/state/exclusion hiện có; xác minh Flyway migrate/validate trên DB sạch và fixture V10 đều thành công mà không backfill ownership.
- [x] 2.3 Thêm CHECK và constraint trigger deferred cho owner pair, Guest snapshot/scope, reservation chưa/chuyển đổi và counter INSERT mới; xác minh direct SQL từ chối row thiếu snapshot, lệch holder hoặc online ownerless, cho phép transaction counter hợp lệ theo thứ tự ghi hiện tại.
- [x] 2.4 Gia cố kiểm tra owner nhóm và điều kiện xoay mã theo NULL semantics, giữ handoff buyer và registry chỉ được ghi thêm; xác minh test nhóm NULL bị từ chối, handoff Customer vẫn xoay mã, correction sang Guest không xoay mã và không mở bypass bằng SQL UNKNOWN.
- [x] 2.5 Bổ sung test legacy non-null được giữ nguyên qua schema upgrade, cấu trúc không nhất quán bị báo rõ và snapshot liên kết không bị ép theo buyer sau handoff; xác minh không sửa V1-V10 hoặc vô hiệu trigger audit toàn cục.
- [x] 2.6 Cập nhật phần schema/preflight của runbook theo migration thực tế; kiểm tra hướng dẫn chỉ ra bốn cột nullable, ranh giới legacy/new INSERT và không tự sửa dữ liệu khi ứng dụng khởi động.

## 3. Tạo booking và quản lý hold

- [x] 3.1 Sửa lựa chọn owner trong `BookingService.create` cho ba trường hợp A/B/C, ghi Guest snapshot và reservation đồng bộ; xác minh integration test cả `/bookings/counter` và hold/create `counter=true` cho Customer liên kết và Guest NULL.
- [x] 3.2 Bổ sung test lookup không tìm thấy, unverified/inactive/Admin và lookup lỗi; xác minh giữ chính sách Identity hiện tại, lỗi không biến thành Guest và API một bước rollback hold/booking/snapshot/idempotency.
- [x] 3.3 Sửa đọc/release/create từ hold đã chuyển đổi, truyền caller token qua controller khi cần kiểm tra quyền booking/cơ sở; xác minh GET authorized không NPE, outsider bị chặn, DELETE/tạo lần hai bị từ chối còn retry đúng key vẫn hoạt động.
- [x] 3.4 Bảo vệ replay bằng quyền hiện hành và projection thống nhất; xác minh retry đồng thời chỉ một booking/snapshot/history, đổi payload bị từ chối và operator bị thu hồi quyền không lấy lại response qua key cũ.
- [x] 3.5 Giữ giới hạn pending theo operator và chống trùng sân; xác minh test nhiều counter linked/Guest đạt quota, cùng test tranh chấp online-counter trên PostgreSQL thực.
- [x] 3.6 Cập nhật ví dụ request/response Backend cho hai đường counter và hold chuyển đổi; kiểm tra ví dụ Guest có owner NULL nhưng creator/operator thật, không thêm endpoint claim hoặc fake Customer.

## 4. Lifecycle, payment, check-in và transfer

- [x] 4.1 Sửa `BookingLifecycle.hasLiveHold` để so sánh owner NULL-safe có xác minh Guest; xác minh Guest hợp lệ payable được, thiếu snapshot/lệch scope/expiry/state vẫn bị chặn và nhóm/online giữ kiểm tra hiện có.
- [x] 4.2 Sửa nullable ở payable và `PaymentOutcomeConsumer`, giữ original operator/Customer payer, event fingerprint và reconciliation; xác minh DEMO Guest được CONFIRMED đúng một lần, owner vẫn NULL, Staff khác hoặc Customer ngoài không trả được.
- [x] 4.3 Bổ sung hồi quy payment duplicate, payload conflict, payer/amount mismatch, late callback và callback sau cancel/expiry; xác minh không hồi sinh booking, không đổi payer/receipt và không phát sinh lỗi owner NULL.
- [x] 4.4 Sửa nullable ở cancel/expiry và các nhánh audit liên quan; xác minh Guest PENDING hủy/hết hạn giải phóng sân, Guest paid không dùng CUSTOMER_SELF_NO_REFUND, giá/payment/state audit được bảo toàn.
- [x] 4.5 Chặn cấp/chấp nhận owner QR cho Guest và Staff fallback legacy, giữ manual code facility API; xác minh operator có READ+CHECK_IN vẫn không nhận owner QR, QR legacy bị từ chối và Customer thật vẫn dùng signed QR.
- [x] 4.6 Bổ sung test manual check-in Guest đã trả tiền, đúng cơ sở và cửa sổ thời gian; xác minh CHECKED_IN đúng một lần, chặn lost permission/sai cơ sở/sai mã/unpaid/transfer LOCKED, rồi chỉ COMPLETED sau endsAt với actor đủ quyền.
- [x] 4.7 Sửa transfer inspect/eligibility cho owner vắng mặt và giữ handoff Customer; xác minh internal inspect trả lý do chặn ổn định, command Guest không tạo lease, buyer nhận cả ba owner và mã/QR cũ hết hiệu lực.
- [x] 4.8 Chạy hồi quy online, nhóm, contribution, group attendance, Staff-Customer tự liên kết và Staff mua lại booking; xác minh giữ CHECKED_IN, bất biến giá và snapshot liên kết gốc sau nhiều handoff.
- [x] 4.9 Cập nhật tài liệu API Backend về payer/operator, QR và manual code theo kết quả test; kiểm tra không tự đổi catalog quyền Staff, không gọi DEMO là thu tiền mặt/POS thật và không thêm IN_PROGRESS.

## 5. Công cụ phân loại và correction dữ liệu legacy

- [x] 5.1 Xây dựng preflight mặc định chỉ đọc, yêu cầu DB đích rõ ràng và xuất manifest ID/version/giá trị kỳ vọng cùng lý do; xác minh test không đọc mặc định production từ `.env`, không ghi dữ liệu và không log contact không cần thiết.
- [x] 5.2 Kiểm thử bộ phân loại với snapshot Guest chưa liên kết, ba holder/operator khớp, scope/state/payment đúng; xác minh thiếu snapshot, liên kết thực, nhóm, mọi transfer evidence, lệch cấu trúc và reconciliation chưa xử lý được đưa vào review hoặc loại khỏi correction tự động.
- [x] 5.3 Thêm chế độ correction có manifest được duyệt và actor thao tác thực, khóa court/đọc lại trước ghi; xác minh test chỉ đổi hai owner/reservation holder cùng version/token version và thêm audit trong một transaction, row đổi từ preflight bị bỏ qua.
- [x] 5.4 Kiểm thử rollback transaction, retry và race correction với callback/expiry trên DB dùng một lần; xác minh không partial update, row đã sửa không tăng version/audit lần hai, không thay state, code, tiền, payer, registry hay snapshot lịch sử.
- [x] 5.5 Hoàn thiện runbook gồm dry-run, review manifest, phê duyệt DB đích, post-check, thứ tự reader-schema-writer và rollback hỗ trợ nullable; kiểm tra tài liệu nêu rõ apply source không tự chạy correction hoặc deployment và không gán Staff lại để rollback.

## 6. Contract API và tích hợp Frontend

- [x] 6.1 Cập nhật DTO serialization và `contracts/openapi/v1/booking.yaml` cho Booking owner/Hold holder nullable, giữ createdBy bắt buộc; xác minh HTTP response dùng JSON null, shape ổn định và tài liệu counter/operations/manual check-in khớp endpoint thực trong phạm vi.
- [x] 6.2 Thêm kiểm thử HTTP access/replay/list/detail/operations với Guest mới và legacy, Customer thật và Staff mất quyền; xác minh code/PII không lộ qua endpoint khác và internal transfer consumer chấp nhận nullable cùng blocked reason.
- [x] 6.3 Validate contract bằng `pwsh -File scripts/openapi/openapi.ps1 -Action validate -Contract booking` từ repo root; xác minh thành công với Docker sẵn có, chỉ validate thay vì tự generate client vào cây Frontend legacy không được sử dụng.
- [x] 6.4 Đồng bộ ví dụ response nullable, chính sách payer và manual check-in với consumer FE trong cùng change; xác minh tài liệu không còn coi type nullable hoặc manual check-in UI là follow-up ngoài scope.

## 7. Frontend ownership, counter flow và manual check-in

- [x] 7.1 Cập nhật `features/booking/types.ts` và các consumer/API helper đang được route sử dụng cho Booking owner/Hold holder `string | null`; xác minh typecheck không cần non-null assertion hoặc fake ID và test logic xử lý UUID/NULL đúng.
- [x] 7.2 Cập nhật các màn hình counter/danh sách/chi tiết vận hành để phân biệt Guest, Customer liên kết và người tạo; xác minh test render/browser không hiển thị Staff như owner của Guest, không coi mọi OFFLINE_COUNTER là Guest và không lộ contact ngoài quyền.
- [x] 7.3 Sửa link quay lại, link kết quả và điều hướng trong hold/payment flow theo quan hệ booking/caller; xác minh operator Guest/linked counter về trang vận hành phù hợp, còn Customer thật/Staff-Customer owner giữ trang cá nhân và nhóm/acquisition giữ route riêng.
- [x] 7.4 Gia cố `my-bookings` và booking detail về QR/mã cá nhân, nút chuyển nhượng, hủy và payment theo quan hệ thật; xác minh Guest/operator/history không được owner controls và Customer online/liên kết vẫn có các thao tác được phép.
- [x] 7.5 Thêm helper gọi check-in-by-code và form mã thủ công ở danh sách/chi tiết vận hành, dùng facilityId đã tải có quyền và giữ QR Customer; xác minh test gửi đúng API/body, normalize mã, kiểm tra permission/state, chống double-submit và tải lại CHECKED_IN/history sau thành công.
- [x] 7.6 Đồng bộ `CounterPaymentModal` và các nút payment với payable Backend hiện tại, trình bày đúng DEMO và chờ booking CONFIRMED; xác minh test không mở payment cho Staff khác chỉ có BOOKING_PAYMENT_COUNTER, retry giữ payment/key và không thông báo đã thu CASH/POS hay booking đã xác nhận sớm.
- [x] 7.7 Bổ sung test logic FE cùng browser scenarios trong test suite hiện có cho owner NULL, legacy projection, route, manual check-in, async payment và mất quyền; xác minh test dựa trên hành vi/requests/render thực của component đang dùng, không chỉ tìm text source và không sửa cây `frontend/src` legacy để làm test qua.
- [ ] 7.8 Chạy `pnpm test`, `pnpm typecheck`, `pnpm lint` và `pnpm build` từ `frontend`; xác minh các kiểm tra qua, không che lỗi nullable bằng assertion, bỏ test hoặc disable lint/typecheck.
- [x] 7.9 Cập nhật hướng dẫn thao tác FE và thứ tự phát hành nullable cùng Backend trong runbook; xác minh hướng dẫn có tạo Guest, thanh toán DEMO, mã check-in vận hành, trạng thái chờ/lỗi và giữ CHECKED_IN, không yêu cầu công cụ API ngoài giao diện để hoàn tất luồng Guest.

## 8. Kiểm tra tích hợp và bàn giao

- [x] 8.1 Chạy `mvn -pl booking-service -am test` từ `backend` trên PostgreSQL test dùng một lần; xác minh toàn bộ regression Booking/common và migration tests qua, gồm ba mô hình sở hữu và bảo vệ dữ liệu cũ.
- [x] 8.2 Chạy các test tích hợp Identity/Payment/Transfer liên quan qua `mvn -pl identity-service,payment-service,transfer-service -am test` từ `backend` với DB test dùng một lần; xác minh lookup, payer/event và handoff không đổi giao thức hoặc chính sách ngoài phạm vi.
- [ ] 8.3 Thực hiện API smoke trên môi trường kiểm thử với online, linked counter và Guest: tạo -> DEMO payment -> manual check-in -> hoàn tất hợp lệ; xác minh Guest không có quyền cá nhân/chuyển nhượng và toàn bộ audit giữ actor, state CHECKED_IN và bằng chứng tiền đúng.
- [x] 8.4 Chạy browser/system E2E qua harness hiện có với runtime config và DB test dùng một lần, mở rộng `frontend/tests/system-e2e.mjs` khi cần; xác minh cả ba trường hợp đi qua UI thực, operator về trang vận hành, Guest check-in bằng mã, Customer có QR/chuyển nhượng, lỗi quyền/cơ sở và mất quyền được hiển thị đúng.
- [ ] 8.5 Đối chiếu spec với test/runbook/contract và diff cuối; xác minh Backend/Frontend cùng đạt acceptance, thay đổi FE chỉ trong luồng liên quan, không có correction dữ liệu thực tế hoặc deployment tự động, ghi rõ kiểm tra đã chạy trước khi bàn giao.
