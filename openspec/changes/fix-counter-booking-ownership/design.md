> **HISTORY ONLY — NOT AN ACTIVE REQUIREMENT SOURCE.**
> Read `openspec/CURRENT_CHANGE.md` at the project root for the current proposal.
> Do not apply, merge, or resume requirements/tasks from this document unless
> the user explicitly requests this historical change. Task counts are historical.

# Design

## Context

Xem `proposal.md` cho vấn đề và phạm vi. Người dùng đã chọn phương án A: owner tài khoản nullable, giữ operator và snapshot khách riêng, đồng thời yêu cầu implementation bao gồm Frontend. Bộ tài liệu này chỉ lập kế hoạch; chưa thay đổi source, migration hay dữ liệu ứng dụng.

Các điểm source quyết định thiết kế:

- [BookingService.java:78](<D:/PersonalProject/antigravity sport-court-booking/sport-court-booking-system/backend/booking-service/src/main/java/com/sporthub/booking/service/BookingService.java:78>): lookup Customer, fallback sang caller và ghi ba holder. Cả API counter một bước và hold/create có `counter=true` đi qua logic này.
- [CounterCustomerController.java:26](<D:/PersonalProject/antigravity sport-court-booking/sport-court-booking-system/backend/identity-service/src/main/java/com/sporthub/identity/web/CounterCustomerController.java:26>): chỉ khớp số điện thoại chính xác, đã xác minh, tài khoản ACTIVE có CUSTOMER và không ADMIN. Không tìm thấy không đồng nghĩa chắc chắn khách chưa từng đăng ký.
- [BookingRepository.java:20](<D:/PersonalProject/antigravity sport-court-booking/sport-court-booking-system/backend/booking-service/src/main/java/com/sporthub/booking/repository/BookingRepository.java:20>): `mine` bao gồm mọi `created_by`; history tại dòng 26 dereference hai owner. [OperationalBookingService.java:31](<D:/PersonalProject/antigravity sport-court-booking/sport-court-booking-system/backend/booking-service/src/main/java/com/sporthub/booking/service/OperationalBookingService.java:31>) đã có scope cá nhân khác với `mine`.
- [BookingLifecycle.java:25](<D:/PersonalProject/antigravity sport-court-booking/sport-court-booking-system/backend/booking-service/src/main/java/com/sporthub/booking/service/BookingLifecycle.java:25>): live hold so sánh `holder_id=current_holder_id`; state machine chỉ dùng PENDING, CONFIRMED, CHECKED_IN, COMPLETED, CANCELLED, EXPIRED.
- [V1:3](<D:/PersonalProject/antigravity sport-court-booking/sport-court-booking-system/backend/booking-service/src/main/resources/db/migration/V1__booking_holds_and_snapshots.sql:3>) và [V9:7](<D:/PersonalProject/antigravity sport-court-booking/sport-court-booking-system/backend/booking-service/src/main/resources/db/migration/V9__operational_booking_records.sql:7>): holder/owner đang NOT NULL, kể cả code registry. V9 bảo vệ snapshot Guest, origin và các bảng audit chỉ được ghi thêm.
- [PaymentOutcomeConsumer.java:54](<D:/PersonalProject/antigravity sport-court-booking/sport-court-booking-system/backend/booking-service/src/main/java/com/sporthub/booking/service/PaymentOutcomeConsumer.java:54>): payer có thể là holder hoặc creator counter. [PaymentService.java:38](<D:/PersonalProject/antigravity sport-court-booking/sport-court-booking-system/backend/payment-service/src/main/java/com/sporthub/payment/service/PaymentService.java:38>) lấy payer từ payable/caller, không bắt buộc là booking owner.
- [TransferLeaseService.java:82](<D:/PersonalProject/antigravity sport-court-booking/sport-court-booking-system/backend/booking-service/src/main/java/com/sporthub/booking/service/TransferLeaseService.java:82>): handoff đổi cả hai owner và reservation holder sang buyer, xoay mã và tăng token version; dòng 90 hiện chặn operator counter chưa được xác minh sở hữu.
- [V7:12](<D:/PersonalProject/antigravity sport-court-booking/sport-court-booking-system/backend/booking-service/src/main/resources/db/migration/V7__group_contribution_integrity.sql:12>) ràng buộc chủ nhóm với holder; [V8:57](<D:/PersonalProject/antigravity sport-court-booking/sport-court-booking-system/backend/booking-service/src/main/resources/db/migration/V8__transfer_handoff_receipts.sql:57>) ràng buộc cả ba owner khi handoff commit.
- [types.ts:3](<D:/PersonalProject/antigravity sport-court-booking/sport-court-booking-system/frontend/features/booking/types.ts:3>) chưa khai báo owner nullable; [bookings-page.tsx:27](<D:/PersonalProject/antigravity sport-court-booking/sport-court-booking-system/frontend/features/operations/bookings-page.tsx:27>) chỉ gọi check-in bằng signed token. Cả hai điểm được sửa trong cùng change.
- [payment-page.tsx:16](<D:/PersonalProject/antigravity sport-court-booking/sport-court-booking-system/frontend/features/booking/payment-page.tsx:16>) điều hướng mọi payment BOOKING đã xác nhận về trang Customer. [counter-payment-modal.tsx:49](<D:/PersonalProject/antigravity sport-court-booking/sport-court-booking-system/frontend/features/operations/counter-payment-modal.tsx:49>) gọi payment DEMO nhưng thông báo như đã ghi nhận thu tiền và booking đã xác nhận; UI cần phản ánh đúng kết quả server và quan hệ operator.

Chưa có khảo sát database thực tế. Không suy ra số lượng bản ghi lỗi, phiên bản Flyway đã áp dụng hoặc topology triển khai từ source.

## Goals / Non-Goals

**Goals:**

- Dùng cùng định nghĩa chủ sở hữu tài khoản trong tạo booking, response, danh sách, QR, payer và transfer; creator/operator không tự trở thành owner.
- Bảo vệ tính nhất quán booking/reservation ở cuối transaction, hỗ trợ owner NULL mà không suy yếu online, nhóm, state machine hoặc bằng chứng tiền.
- Phân biệt schema upgrade với correction dữ liệu có phê duyệt; hỗ trợ reader an toàn trước khi backfill.
- Hoàn thiện hành trình FE từ counter hold/tạo booking đến DEMO payment và check-in bằng mã; kiểm thử cả nullable, điều hướng và các quyền hiển thị.

**Non-Goals:**

- Không tạo bảng party/identity Guest mới; không đổi ý nghĩa `customer_id` thành Customer gốc bất biến.
- Không gộp quyền tạo counter và quyền thu ngân thành chính sách mới. Giữ cơ chế original operator/linked Customer của payable DEMO hiện tại.
- Không cấp tài khoản cho Guest, tự nhận booking bằng số điện thoại hoặc triển khai tiền mặt/POS/hoàn tiền thật.
- Không thiết kế lại toàn bộ giao diện, đổi design system hoặc chỉnh cây `frontend/src` legacy không được route hiện tại sử dụng. FE chỉ thay đổi các luồng chịu tác động của ownership, thanh toán DEMO và check-in.

## Decisions

### 1. Owner là liên kết tài khoản tùy chọn; origin và contact vẫn bất biến

- Online: `created_by=customer_id=current_holder_id=reservation.holder_id=Customer` khi tạo.
- Counter có Customer liên kết: creator/operator là người vận hành; hai owner và reservation holder là Customer; giữ `linked_customer_id` trong snapshot.
- Counter chưa liên kết: creator/operator là người vận hành; hai owner và holder của reservation đã chuyển đổi là NULL; giữ snapshot hợp lệ và `linked_customer_id=NULL`.
- Hold chưa có booking vẫn bắt buộc có tài khoản thao tác trong `holder_id`. Chỉ được chuyển holder thành NULL cùng transaction tạo booking Guest.
- Không thêm cờ sở hữu lưu trữ song song. Guest được nhận diện từ hai owner vắng mặt và snapshot counter hợp lệ. Không dùng riêng `source='OFFLINE_COUNTER'` để phân loại Guest vì nguồn này còn chứa Customer thật.
- `customer_id` tiếp tục đổi cùng `current_holder_id` khi chuyển nhượng; snapshot `linked_customer_id` chỉ mô tả liên kết lúc tạo, không được ép bằng buyer của mọi handoff sau đó.

So với mô hình party mới, lựa chọn này tận dụng snapshot hiện hữu và giữ giao thức Identity/Payment. So với tài khoản Guest tự sinh hoặc giữ Staff làm owner, nó thể hiện đúng việc không có tài khoản mà không tạo dữ liệu Identity giả.

### 2. Tập trung hóa chính sách tài khoản, operator và lịch sử

Tách các phép kiểm tra theo ý nghĩa nghiệp vụ: current account owner, historical participant, original counter operator và facility permission. Có thể dùng helper nội bộ Booking; không cần framework phân quyền hoặc service mới.

- Owner account chỉ có khi UUID thật tồn tại và người gọi có quan hệ sở hữu hợp lệ. Không dùng `Objects.equals(NULL,NULL)` làm bằng chứng quyền sở hữu hoặc transfer eligibility.
- Tài khoản đồng thời có Staff và Customer được giữ quyền khi chính nó được lookup hợp lệ hoặc nhận handoff. Không cấm owner chỉ vì có role Staff; không coi `created_by=current_holder_id` là bằng chứng Guest độc lập.
- Creator counter chỉ có quyền vận hành sau khi kiểm tra permission của đúng cơ sở. Khi mất binding/permission, quyền creator không cho phép bypass detail, replay, payment, cancel hoặc check-in.
- `/bookings` và personal management search dùng cùng định nghĩa quan hệ cá nhân: current owner hoặc quan hệ lịch sử Customer đang được hỗ trợ; chỉ tạo counter không tạo quan hệ cá nhân. Giữ yêu cầu role hiện có của từng endpoint: legacy list có thể trả rỗng cho Staff-only; management search không có facility tiếp tục yêu cầu CUSTOMER.
- API facility/operations vẫn hiển thị Guest để vận hành. Mã thủ công có thể được trả cho người có quyền đọc booking ở cơ sở; quyền sử dụng mã luôn được kiểm tra lại tại check-in.
- Redaction áp dụng nhất quán cho nested `counterGuest`, guest contact trên Booking, mã và QR; người bán trung gian sau nhiều handoff không được nhận snapshot liên hệ gốc chỉ nhờ quyền xem lịch sử.

Đối với legacy fallback xác định chắc chắn từ snapshot và ba holder cùng operator, reader công khai biểu diễn effective owner là NULL và không cấp owner QR/quyền cá nhân dù correction vật lý chưa chạy. Audit, lifecycle và preflight vẫn đọc giá trị lưu trữ để đối soát. Legacy thiếu bằng chứng được giữ nguyên trong nhóm review, không đoán Customer mới hoặc tự sửa DB; creator vẫn không được cấp quyền owner chỉ nhờ trường `created_by`.

### 3. Schema mở rộng có ràng buộc; tách invariant cấu trúc khỏi dữ liệu legacy

Tạo migration mới sau V10; không sửa V1-V10 đã phát hành. Nới NOT NULL cho `bookings.customer_id`, `bookings.current_holder_id`, `slot_reservations.holder_id`, `booking_code_registry.holder_id`. Giữ NOT NULL của creator/operator và các FK/scope, thời gian, state, payment uniqueness và exclusion chống trùng sân.

Invariant cho trạng thái mới:

- Hai owner booking cùng có giá trị hoặc cùng NULL; booking ONLINE và booking nhóm phải có account owner.
- Booking ownerless chỉ được là OFFLINE_COUNTER có snapshot Guest hợp lệ: operator khớp creator, contact khớp snapshot bất biến và linked customer NULL.
- Reservation đã gắn booking phải khớp owner hiện tại bằng phép so sánh NULL-safe, đồng thời giữ nguyên các kiểm tra scope/expiry/state. Reservation chưa có booking không được mất tài khoản giữ hold.
- Booking counter mới phải có snapshot ở cuối transaction; khi lookup không có Customer, cả ba owner phải NULL. Khi tạo cho Customer, snapshot liên kết và owner phải khớp lúc tạo.
- Không duy trì ràng buộc `linked_customer_id=current_holder_id` suốt đời booking vì handoff đã được cho phép cập nhật owner.
- Owner nhóm phải hiện diện và khớp bằng kiểm tra NULL-safe; giữ nguyên invariant đóng góp và attendance CHECKED_IN.

Dùng CHECK cho điều kiện nội bộ một row; dùng constraint trigger deferred cho quan hệ nhiều bảng để cho phép insert booking, snapshot và cập nhật reservation trong cùng transaction. Không đặt truy vấn bảng khác vào CHECK, không thêm FK sang Identity DB.

Đường nâng cấp phải phân biệt legacy: constraint ownerless được áp dụng ngay cho row NULL; quy tắc hình dạng tạo counter mới áp dụng cho INSERT mới, không tự kết luận các row legacy non-null là Customer hoặc bắt chúng phải được sửa trong Flyway. Preflight báo lỗi cấu trúc thực sự trước khi siết các invariant chung. Không thêm điều kiện cho phép sửa owner tùy ý dưới danh nghĩa legacy.

`record_booking_code` được giữ: row Guest mới được phát hành registry với `holder_id=NULL`; row handoff có buyer thật. Registry phản ánh thời điểm phát hành, không phải projection owner hiện tại. Giữ trigger cấm UPDATE/DELETE và unique mã toàn lịch sử.

Sửa điều kiện xoay mã trong `protect_operational_booking_fields` bằng kiểm tra NULL tường minh: chỉ handoff account-to-account hợp lệ ở CONFIRMED mới được xoay mã theo luồng hiện hành. Correction account-to-NULL giữ nguyên mã; không để SQL UNKNOWN vô tình mở đường đổi mã. Source, creator, guest contact và giá chốt vẫn bất biến.

### 4. Xử lý NULL theo từng điểm dùng, không chỉ đổi fallback

- Tạo booking: chọn caller cho online, linked Customer cho counter liên kết, NULL cho counter Guest. Ghi booking/snapshot/reservation/history/idempotency trong transaction hiện tại.
- Repository history: ghi JSON null thực sự cho owner vắng mặt; không `.toString()` trên NULL, không chuỗi `"null"`, không UUID thay thế và không sửa lịch sử cũ.
- `hasLiveHold`: so sánh holder NULL-safe, kèm điều kiện booking Guest hợp lệ; giữ kiểm tra ID, scope, expiry và state. Hai giá trị NULL trên dữ liệu thiếu snapshot không tạo live hold hợp lệ.
- Hold access: hold chưa chuyển đổi chỉ thuộc actor. Sau chuyển đổi, GET hold kiểm tra quyền trên booking liên quan bằng cùng cơ chế owner/facility đang dùng; cập nhật chữ ký controller/service để có token khi cần. DELETE hold luôn bị từ chối khi đã có booking, sau khi kiểm tra quyền, thay vì dereference NULL hoặc giải phóng sân trực tiếp. Tạo booking lần hai từ hold đã chuyển đổi bị từ chối; retry đúng key đi qua replay hợp lệ.
- Áp dụng cùng projection/quyền cho create, detail, list, facility search, operations và idempotent replay; replay vẫn kiểm tra quyền hiện hành.
- Transfer inspect kiểm tra owner hiện diện trước so sánh và trả `transferBlockedReason` ổn định cho Guest; command register/lock/complete không được cấp quyền cho Guest hoặc biến NULL thành seller.

### 5. Payer và owner là hai quan hệ khác nhau

Giữ payable của booking cá nhân: current Customer có thể trả; original operator counter có thể trả sau kiểm tra BOOKING_CREATE_COUNTER theo chính sách Backend hiện tại. Không mở quyền cho mọi Staff có BOOKING_PAYMENT_COUNTER và không gọi DEMO là bằng chứng thu tiền mặt/POS thật.

`Payable.payerId` và `payment_orders.payer_id` tiếp tục là tài khoản thực hiện payment. `PaymentOutcomeConsumer` dùng kiểm tra NULL-safe cho owner và giữ ngoại lệ creator counter. Không thay event envelope, fingerprint, paidAt, amount/currency, deduplication, thời hạn, reconciliation hoặc receipt. Creator exception trong callback xác minh quan hệ payment đã phát sinh, không phải một quyền owner mới.

Guest PENDING vẫn hết hạn/hủy theo lifecycle hiện tại; Guest đã trả tiền không được áp dụng nhánh CUSTOMER_SELF_NO_REFUND chỉ vì người thao tác là creator. Giữ cơ chế BLOCKED_RULE cho hủy đã thanh toán từ phía cơ sở khi protocol refund chưa có.

### 6. Check-in qua mã cơ sở; giữ nguyên vòng đời

Giữ `POST /api/v1/bookings/facility/{id}/check-in-by-code` và cơ chế lookup mã tại đúng cơ sở. Guest không cần đăng nhập và không được cấp owner QR; Staff vận hành cũng không nhận owner QR từ detail chỉ vì có quyền check-in.

Check-in vẫn yêu cầu BOOKING_CHECK_IN, trạng thái CONFIRMED, payment evidence hợp lệ, cửa sổ từ 30 phút trước startsAt đến trước endsAt, không có acquisition LOCKED và chỉ thực hiện một lần. Signed QR vẫn phục vụ Customer thật; token của Guest/legacy operator bị từ chối, không dựa vào việc còn thời hạn để cho qua.

Giữ `CONFIRMED -> CHECKED_IN -> COMPLETED` và quy tắc hoàn tất sau endsAt. Không tái cấp BOOKING_COMPLETE hoặc BOOKING_CANCEL_PENDING cho Staff vì Identity V12 đã bỏ các quyền này khỏi catalog; giữ hành vi phân quyền hiện có, không sửa catalog trong change.

### 7. Contract và Frontend tương thích

Giữ endpoint và shape. Đánh dấu `customerId`, `currentHolderId` của Booking và `holderId` của Hold nullable trong OpenAPI 3.0.3; các property owner được xuất dưới dạng JSON null khi không có account. `createdBy` không nullable. Cập nhật mô tả các API counter, operations và check-in bằng mã thuộc phạm vi để phản ánh source thực tế, không refactor toàn bộ contract.

Không thêm API công khai sửa owner hoặc claim booking Guest. Không thay chữ ký giao thức của Identity/Payment/Transfer nếu không cần; internal booking inspect phải trả nullable và lý do chặn Guest tương thích consumer.

Frontend được triển khai trong cùng change, dựa trên component đang dùng dưới `frontend/features`, `frontend/lib` và route `frontend/app` liên quan:

- Cập nhật `Booking.customerId`, `Booking.currentHolderId` và `Hold.holderId` thành `string | null`. Rà soát API helper, props và consumer; không non-null assertion, không dùng creator hoặc UUID giả để lấp owner trống.
- Tại trang vận hành, hiển thị Guest bằng thông tin khách được phép đọc và nhãn khách vãng lai; tách thông tin người tạo khỏi owner. Customer đã liên kết vẫn được nhận diện là Customer có tài khoản. Không tự kết luận Guest chỉ từ source OFFLINE_COUNTER.
- Danh sách cá nhân dùng scope Backend đã sửa; detail chỉ hiển thị mã/QR cá nhân và chuyển nhượng cho owner tài khoản hiện tại được phép. Lịch sử không trở thành owner rights; Staff-Customer có booking thật không bị chặn chung theo role Staff.
- Giữ route hold/payment dùng chung khi cần, nhưng thay các link quay lại, link kết quả và điều hướng sau xác nhận theo booking/caller thật. Operator thanh toán counter cho người khác hoặc Guest quay về `/{staff|owner}/bookings/{id}` phù hợp quyền cơ sở; Customer thật, kể cả Staff-Customer đang sở hữu, giữ route cá nhân. Không tin query param hoặc việc là payer để suy ra ownership; API tiếp tục quyết định quyền truy cập.
- Thêm API helper cho `POST /bookings/facility/{id}/check-in-by-code` và form nhập mã tách khỏi signed QR ở danh sách/chi tiết vận hành. Mã được chuẩn hóa/kiểm tra định dạng trước gửi; facilityId lấy từ cơ sở đang chọn hoặc booking đã tải có quyền đọc. Hiển thị manual booking code trong phạm vi được phép, giữ tải/quét QR của Customer hiện có, chỉ mở thao tác check-in khi permission/state phù hợp.
- Chặn gửi lặp trong lúc check-in, hiển thị lỗi Backend và tải lại booking/lịch sử sau thành công để thể hiện CHECKED_IN. Thao tác bằng mã không tạo owner QR hoặc làm Staff thành owner.
- Đồng bộ nút payment với chính sách payable Backend hiện tại: current Customer hoặc original counter operator có BOOKING_CREATE_COUNTER; BOOKING_PAYMENT_COUNTER của Staff khác không tự mở quyền. Trình bày đúng thanh toán DEMO, không hứa lưu phương thức CASH/POS, số thu hoặc tên người thu nếu API không lưu. Giữ idempotency/payment hiện có khi retry và chỉ báo booking CONFIRMED khi Backend đã xác nhận; trong lúc đồng bộ hiển thị trạng thái đang chờ thay vì thành công giả.

Không thêm capability flag mới chỉ để giải quyết hiển thị nếu response và access context hiện có đã đủ. FE là lớp hướng dẫn người dùng; mọi kiểm tra quyền vẫn có ở Backend. Bổ sung test logic cho owner nullable/điều hướng và browser test các luồng thực; không chỉ kiểm tra text source hoặc ảnh giao diện. Hoàn thành change đòi cả acceptance Backend và FE, không để manual check-in UI thành follow-up ngoài scope.

## Risks / Trade-offs

- [Reader cũ dereference NULL] -> cập nhật toàn bộ reader/audit/callback và kiểm thử trước khi schema/writer mới cho phát sinh NULL; không chạy writer mới cùng reader cũ không tương thích.
- [SQL NULL làm bỏ qua kiểm tra] -> dùng IS NULL/IS DISTINCT FROM/IS NOT DISTINCT FROM đúng ngữ cảnh, chặn null owner rõ ràng ở transfer và nhóm, test direct SQL trên PostgreSQL thật.
- [Constraint mới vô tình buộc rewrite legacy] -> tách kiểm tra INSERT mới khỏi ownerless invariant, có preflight và review; không backfill tự động trong Flyway.
- [Snapshot liên kết bị dùng làm owner hiện tại] -> kiểm tra khớp chỉ lúc tạo, kiểm thử handoff nhiều lần và Staff mua lại booking hợp lệ.
- [Callback/expiry đụng correction] -> khóa cùng court và kiểm tra version lại trong một transaction; giữ nguyên payment identity và receipt.
- [FE và Backend lệch contract/quyền/điều hướng] -> cập nhật nullable, UI vận hành, payment và manual check-in trong cùng change; chạy typecheck cùng browser test ba trường hợp sở hữu, giữ Backend làm nguồn quyết định quyền.
- [Có legacy không đủ chứng cứ] -> không đoán từ role/số điện thoại hiện tại; trả danh sách review, bảo vệ quyền và giữ nguyên chứng cứ.
- [Phức tạp hơn một dòng fallback] -> chấp nhận vì nullable chạm audit, registry, giữ chỗ và quyền; tái sử dụng bảng/giao thức hiện có để giới hạn phạm vi.

## Migration Plan

### Schema và code

1. Trên database kiểm thử dùng một lần, tạo fixture V10 gồm online, nhóm, counter liên kết, Guest fallback và các trạng thái đã trả tiền/check-in/kết thúc; xác minh nâng cấp không sửa row hoặc chứng cứ cũ ngoài schema.
2. Chuẩn bị reader Backend và FE hỗ trợ cả UUID/NULL, projection Guest legacy, kiểm tra quyền, history và callback tương thích dữ liệu cũ; đưa cả bản FE nullable vào kế hoạch phát hành trước khi mở writer NULL. Không thêm feature flag nếu quy trình chuyển đổi phối hợp đã bảo đảm được thứ tự này.
3. Tạm ngăn tạo counter trong cửa sổ chuyển schema/writer; chạy migration schema mới sau preflight; chuyển sang writer mới và bỏ writer cũ trước khi mở lại tạo counter. Các reader xử lý callback phải đã hỗ trợ NULL. Chi tiết thao tác phụ thuộc môi trường thực tế, không giả định có Kubernetes hoặc nhiều replica.
4. Kiểm thử API và trình duyệt trên môi trường kiểm thử cho online, linked counter và Guest: tạo, payment, điều hướng, kiểm tra list/detail, manual check-in và trạng thái CHECKED_IN. Chỉ bàn giao change hoàn tất sau khi FE/Backend cùng đạt acceptance; không tự triển khai từ workflow cập nhật tài liệu hoặc apply chỉ mã nguồn.

### Preflight và correction legacy tách biệt

Công cụ mặc định chỉ đọc; phải nhận đích DB rõ ràng, không tự lấy production từ `.env`. Xuất danh sách ID/version/giá trị kỳ vọng và lý do phân loại; tránh log contact khách không cần thiết. Không cập nhật chỉ từ một biểu thức `created_by=customer_id`.

Ứng viên chắc chắn phải có OFFLINE_COUNTER; snapshot Guest bất biến với linked customer NULL; operator bằng creator; contact khớp; hai owner và reservation holder đều bằng operator; scope/state/expiry nhất quán. Không thuộc nhóm; không có bất kỳ lease/handoff chuyển nhượng hoặc reconciliation đang cần xử lý. Payment đã có phải phù hợp bằng chứng hiện hữu; nếu không xác minh được, chuyển review. Không phân loại từ role Staff hiện tại hoặc phone lookup hiện tại.

Chế độ ghi chỉ nhận manifest ID/version/giá trị kỳ vọng đã được duyệt rõ ràng. Với mỗi booking, khóa court theo cơ chế Booking, đọc lại và so sánh manifest; nếu thay đổi hoặc thiếu bằng chứng thì không sửa booking đó và báo review. Trong một transaction, đặt hai owner và reservation holder NULL, tăng booking version/token version, ghi thêm audit correction chứa ID trước/sau và danh tính thực của thao tác sửa. Không giả làm actor tạo booking ban đầu. Chạy lại không tăng version hoặc tạo audit lần hai cho bản ghi đã sửa.

Giữ manual booking code, `created_by`, Guest snapshot, state, thời gian, quote, amount/currency, payment_id/paid_at, payment order, callback, refund beneficiary, transfer history và các audit cũ. Registry cũ giữ Staff ID tại thời điểm phát hành như bằng chứng legacy, không UPDATE/DELETE; quyền hiện tại luôn lấy từ booking/policy. Vô hiệu QR cũ bằng policy và token version, không cần xoay manual code khi correction sang Guest.

Xác minh sau correction: chỉ manifest đủ điều kiện đã đổi; owner/reservation đồng nhất, mã vẫn duy nhất, trạng thái và tổng tiền/receipt không đổi; Customer thật và nhóm không bị ảnh hưởng. Việc chạy correction trên dữ liệu hiện có cần phê duyệt đích DB và manifest riêng, không được tự chạy khi apply source.

### Rollback

Trước khi có NULL, có thể tắt writer mới và giữ schema mở rộng. Sau khi có Guest ownerless, không rollback về Backend/FE cũ không hỗ trợ nullable hoặc SET NOT NULL vì sẽ làm lỗi reader/UI hoặc phải gán owner giả. Khi có sự cố, ngăn tạo counter và dùng bản code hỗ trợ nullable để sửa tiến; giữ schema, dữ liệu Guest đúng và audit. Không khôi phục Staff làm owner để làm cho ứng dụng cũ chạy được.
