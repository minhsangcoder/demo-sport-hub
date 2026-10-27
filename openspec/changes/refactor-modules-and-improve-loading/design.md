> **HISTORY ONLY — NOT AN ACTIVE REQUIREMENT SOURCE.**
> Read `openspec/CURRENT_CHANGE.md` at the project root for the current proposal.
> Do not apply, merge, or resume requirements/tasks from this document unless
> the user explicitly requests this historical change. Task counts are historical.

# Design

## Context

Xem động cơ thay đổi trong `proposal.md`. Khảo sát ngày 22/10/2026 dùng **source hiện có trong working tree**, gồm phần Việt hóa chưa commit; không xem HEAD là toàn bộ baseline.

### Những gì đã đọc và xác nhận

1. Frontend đang chạy có 226 file TS/TSX trong `app`, `features`, `components`, `lib`. Các file lớn theo số dòng vật lý: `features/auth/auth-form.tsx` 831; `app/customer/profile/page.tsx` 664; `lib/error-translator.ts` 649; `components/layout/app-header.tsx` 637; `features/operations/qr-checkin.tsx` 612; `features/integration/staff.tsx` 585; `features/admin/account-list.tsx` 564; `app/page.tsx` 537. Đây là kiểm kê source, không phải số đo hiệu năng.
2. Có vấn đề định dạng độc lập với số dòng: `features/integration/group.tsx:16` chứa phần trò chuyện, polling, rời/giữ/tái sử dụng nhóm trong một dòng hơn 4.000 ký tự; `features/schedule/pricing-page.tsx:14` và `features/facility/report-page.tsx:85` cũng ghép nhiều trách nhiệm. Cần định dạng rồi tách trách nhiệm, không thu gọn code để giảm LOC.
3. `frontend/features/auth/auth-form.tsx:7` import `OwnerSignupFields`, và dòng 10 lấy `facilityFrom` từ file giao diện. `features/auth/owner-signup-fields.tsx:3` import `FacilityFields`; `features/facility/facility-fields.tsx:7` import công cụ địa chỉ và dòng 8 import danh mục hành chính. `features/facility/administrative-divisions.ts:1` import toàn bộ JSON 128.217 byte, trong khi trang chủ/tìm sân chỉ cần danh sách tỉnh/thành.
4. `frontend/features/discovery/search-page.tsx:16` **đã** dùng `next/dynamic` cho bản đồ và chỉ render khi `mapView` được chọn; `features/facility/facility-fields.tsx:11` cũng đã nạp `LocationPicker` bằng import động. `features/facility/location-picker.tsx:155` quản lý Google/Leaflet trong effect. Không kết luận mọi bản đồ đều được tải sớm; vấn đề cần kiểm chứng là chuỗi import phần đăng ký/giao diện/dữ liệu đi cùng trang không sử dụng chúng.
5. `frontend/features/groups/qr-decoder.ts:1` import `jsqr` cùng các hàm phân tích mã bằng chuỗi. `features/operations/qr-checkin.tsx:18` và `features/groups/camera-qr-scanner-modal.tsx:6` cùng dùng module này. Tách parser thuần khỏi xử lý ảnh/video sẽ tạo ranh giới tải rõ ràng.
6. `frontend/lib/use-api.ts:4` có state/effect riêng cho từng consumer; cleanup dòng 12 chỉ bỏ kết quả, chưa hủy request hoặc chia sẻ request. `lib/api.ts:30` và `app/api/v1/[...segments]/route.ts:91` giữ `no-store`; gateway proxy còn giữ cookie, origin và giới hạn upload. Không thay proxy thành cache chung.
7. `frontend/components/route-view.tsx:3` import tĩnh nhiều khu vực khách/nhân viên/quản trị vào cùng bộ điều phối. `features/integration/group.tsx:15` có timer QR, dòng 16 có timer trò chuyện; `features/booking/payment-page.tsx:26` polling xác nhận thanh toán bằng callback async của `setInterval`. `features/facility/report-page.tsx:42` làm mới theo sự kiện tiền, focus và storage.
8. `frontend/next.config.mjs:8` đang tắt tối ưu ảnh toàn cục. Ba ảnh hero PNG trong `public/images` khoảng 1,31–1,69 MB/ảnh; năm font TTF trong `public/fonts` tổng 618.188 byte. `app/fonts.css:1` đã dùng `font-display: swap`. Dung lượng file gốc chưa đồng nghĩa toàn bộ đều tải trên mỗi trang.
9. `backend/booking-service/.../DiscoveryService.java:38` lặp cơ sở và dòng 46 lặp sân, gọi `bookings.availability` ở dòng 49. `BookingService.java:218` đọc preview, lấy hàng rào cấu hình, đọc preview lại, rồi dòng 232 đọc reservation chưa giới hạn theo ngày đang xem. Hai lần preview nằm quanh hàng rào ở dòng 221–225 là biện pháp bảo vệ tính nhất quán, không được tùy tiện bỏ.
10. `backend/booking-service/.../FacilityDemoReportingService.java:37` đọc mọi booking của cơ sở, các collection/settlement/refund/event liên quan, sau đó lọc và phân trang trong bộ nhớ tại dòng 105–106. `LocalAppliedCollections.java:14` đã có snapshot xác minh khoản thu; phần bổ sung bằng chứng từ Payment trong reporting ở dòng 45 chạy **sau** transaction snapshot. Các đặc tính này phải giữ nguyên.
11. `backend/schedule-service/.../ScheduleService.java:16` giữ isolation `REPEATABLE_READ`, đồng thời xử lý đọc lịch, cấu hình đăng ký, giờ mở cửa, ngoại lệ và phiên bản giá. File có 359 dòng nhưng 38.431 byte; `BookingService` có 271 dòng/30.325 byte. `PricingEngine` đã tồn tại và được dùng tại `ScheduleService.java:24`, không tạo engine mới song song.

Các đường dẫn backend viết gọn `...` ở trên đều nằm trong `src/main/java/com/sporthub/<service>/service/`. Không có baseline LCP, bundle theo route hoặc p95 API được đo mới trong bước propose; các điểm trên là ứng viên tối ưu từ source, chưa phải kết luận định lượng nguyên nhân chậm.

## Goals / Non-Goals

**Goals:**

- Module có chủ thể nghiệp vụ/trách nhiệm rõ; route và service facade dễ đọc; giảm liên kết import giữa màn hình không liên quan.
- Các module logic được refactor nhắm mức tối đa 350 LOC đã định dạng, không tính dòng trống/comment; chặn file mới/đã refactor vượt 500 LOC nếu không có ngoại lệ được ghi rõ. File danh mục thuần dữ liệu, generated code và migration được kiểm kê riêng, không dùng để giấu JSX/hook/logic.
- Đo được tải ban đầu nhẹ hơn, ít request dư hơn, truy vấn đọc ít dữ liệu không liên quan hơn; nghiệm thu cả tính đúng và tốc độ.
- Hoàn thành bằng công cụ và hạ tầng sẵn có của đồ án; mỗi đợt có thể kiểm tra và hoàn tác độc lập.

**Non-Goals:**

- Đổi UI/luồng nghiệp vụ, đổi ngôn ngữ, đổi public route/DTO, đổi trạng thái booking, đổi chính sách tiền hoặc thêm xác minh ngân hàng thật.
- Viết lại toàn bộ microservice, chuyển sang reactive, thêm Redis/cache phân tán, thêm event bus hoặc thay framework frontend.
- Dọn cây `frontend/src` cũ, nâng Next/React/Spring, sửa toàn bộ lint tồn đọng hoặc hoàn thành các change khác trong cùng đợt.

## Decisions

### 1. Đo trên bản dựng production và chia thành các đợt có điểm dừng

Dùng fixture và Playwright/Node hiện có, lưu baseline source fingerprint, phiên bản runtime, máy chạy, viewport, chế độ cache, cấu hình mạng và dữ liệu thử. Có ít nhất các route `/`, `/login`, `/search`, `/staff/bookings`, báo cáo Owner của cơ sở thử; mỗi route dùng đúng vai trò, session và dữ liệu cố định.

- Frontend: ít nhất 5 lượt đo cho mỗi trường hợp cache lạnh/ấm; ghi median LCP, mốc nội dung chính sẵn sàng, JavaScript nén theo các chunk duy nhất thực sự tải ban đầu, tổng byte ảnh/font và số API request theo URL. Đo tải lần đầu riêng với điều hướng giữa trang; không cộng một chunk nhiều lần.
- API: làm nóng JVM rồi đo ít nhất 30 lần cho tìm sân/báo cáo trên cùng fixture, ghi median/p95, số lần gọi dependency và số hàng đọc khi có thể đo. So sánh payload chuẩn hóa, thứ tự và lỗi, bỏ riêng các timestamp quan sát động khi đối chiếu.
- Fixture backend có nhiều cơ sở/sân và lịch sử ngoài kỳ, tối thiểu 1.000 booking cùng các sự kiện tiền đủ tình huống; có nhóm dữ liệu nhỏ để kiểm tra chi phí ở quy mô demo thông thường. Chỉ tạo trên database kiểm thử dùng một lần.
- Công cụ đo là script nhỏ và kết quả có thể chạy lại; không triển khai hệ thống telemetry riêng. Không ghi token, thông tin tài khoản thật hoặc dữ liệu khách thật vào kết quả.

**Mục tiêu nghiệm thu:** JavaScript nén tải ban đầu của `/login` và `/staff/bookings` giảm ít nhất 20% so baseline; tổng byte ảnh/font tải lần đầu ở trang chủ giảm ít nhất 30%; median nội dung chính sẵn sàng của trang chủ/tìm sân giảm ít nhất 20% ở fixture mạng đã chốt. p95 API tìm sân/báo cáo trên fixture lịch sử lớn nhắm giảm ít nhất 20%, đồng thời có bằng chứng truy vấn/dependency ít công việc dư hơn. Các route kiểm soát còn lại không chậm hơn quá mức lớn hơn giữa 10% và 50 ms. Khi baseline quá nhỏ hoặc môi trường không ổn định, báo rõ giới hạn đo, không nhận là đã đạt hoặc tự hạ mục tiêu.

Tách file thuần túy không được tính là cải thiện runtime. Đợt tải ban đầu và request có thể nghiệm thu trước đợt refactor backend; không trộn thay đổi giao thức ghi tiền vào cùng tối ưu đọc.

### 2. Tách theo trách nhiệm, giữ facade chuyển tiếp và định dạng nhất quán

Giữ tổ chức `features/<domain>`; mỗi domain có thành phần giao diện, hook điều phối, hàm chuyển dữ liệu thuần và type khi thực sự cần. Không ép tất cả domain vào cấu trúc nhiều tầng giống nhau, không tạo một component cho từng vài dòng HTML.

- **Xác thực:** giữ export `AuthForm`, tách shell/trang trí và biểu mẫu theo nhóm đăng nhập, đăng ký, khôi phục/xác minh. Payload đăng ký chủ cơ sở được nạp cùng nhánh Owner; login không import UI hoặc dữ liệu địa chỉ của nhánh này. Đưa hàm tạo payload khỏi file TSX; tránh chuyển hàm ra file mới nhưng vẫn kéo ngược toàn bộ giao diện qua barrel export.
- **Điều hướng:** tách bảng liên kết theo vai trò, menu tài khoản và menu di động khỏi `AppHeader`; tách context cơ sở khỏi sidebar/breadcrumb của `facility-context.tsx`. Giữ điều hướng Staff, URL quay lại và guard vai trò hiện hành.
- **Hồ sơ/quản trị:** tách avatar, thông tin cá nhân, bảo mật/liên hệ; tách admin shell/nav khỏi tìm kiếm, bộ lọc và danh sách tài khoản. Các form nhân viên tách khỏi thẻ hồ sơ và bộ chọn quyền.
- **QR/vị trí:** parser chuỗi, đọc ảnh/video, hook camera/clipboard và dialog là các phần riêng. `LocationPicker` tách phần giải đường dẫn, provider bản đồ và form tọa độ; giữ hành vi Google khi có key, Leaflet khi không có key.
- **Màn hình nén:** định dạng và tách `integration/admin.tsx`, `integration/group.tsx`, `integration/facility.tsx`, pricing, lịch vận hành và báo cáo thành các màn hình/thành phần đúng domain. Tách bộ lọc, KPI, biểu đồ, bảng sân và bảng giao dịch của báo cáo; không tạo bản sao công thức tính tiền trên frontend.
- **Thông báo:** `error-translator.ts` giữ facade đồng bộ, chia từ điển theo domain và tách nhận diện lỗi khỏi dữ liệu nhãn. Nếu facade vẫn import các từ điển thì chưa có lợi ích bundle; không tuyên bố chỉ chia file đã giảm byte JavaScript.
- **Backend:** giữ facade `BookingService` và `ScheduleService` khi cần tương thích controller/test. Tách `BookingAvailabilityService`, phần đọc/tổng hợp/giao dịch báo cáo; tách query, giờ mở cửa/ngoại lệ, quản lý giá và khởi tạo lịch đăng ký của Schedule. Tận dụng `PricingEngine`, `BookingLifecycle`, `LocalAppliedCollections` đang có.

Dùng Prettier cho các file frontend thuộc phạm vi và một cách định dạng Java thống nhất cho các lớp được chạm; tách commit định dạng khỏi commit thay đổi hành vi/thực thi. Công cụ kiểm tra module báo cả LOC sau định dạng và những dòng code dài bất thường; có baseline/allowlist rõ ràng cho phần ngoài scope. Mục tiêu độ dài hàm là tín hiệu review, không phải lý do cắt một transaction thành nhiều transaction.

**Phương án không chọn:** chỉ đặt `max-lines`, chia thành `part-1/part-2`, hoặc di chuyển mọi hàm vào `utils.ts`; các cách này không xác định chủ sở hữu logic và có thể giữ nguyên chuỗi import nặng.

### 3. Mở rộng hook đọc dữ liệu đang có, không thay toàn bộ bằng framework mới

Giữ interface `{data,error,loading,reload}` và `api()` cho consumer. Thêm module quản lý GET nhỏ, có test; không áp dụng vào POST/PUT/PATCH/DELETE hoặc tự thử lại thao tác tiền.

- Khóa request gồm URL đầy đủ và thế hệ session cho dữ liệu riêng tư. Consumer cùng khóa có thể dùng chung request **đang chạy**. Cleanup một consumer không được hủy request của consumer khác; dùng số người đăng ký, chỉ abort khi không còn người dùng.
- Khi đổi path/cơ sở, invalidate hoặc session, bỏ kết quả của thế hệ cũ. `reload()` sau mutation phải tạo/lấy một request mới của thế hệ mới; không nhập chung với GET cũ bắt đầu trước mutation.
- Chỉ `/sport-categories` và danh mục công khai được cho phép rõ ràng mới có TTL tối đa 60 giây trong bộ nhớ browser. Mặc định các đường còn lại không dùng TTL. Không lưu dữ liệu riêng tư trong localStorage, cache server dùng chung hoặc Service Worker.
- Giữ `no-store` của transport/proxy. Dữ liệu quyền truy cập, lịch trống, giá/điều khoản, booking, payment, settlement/refund và báo cáo phải làm mới theo thao tác/sự kiện hiện có. Quote/hold/payment/handoff/check-in luôn được server xác minh; cache danh mục không được thay các guard này.
- Session/login/logout/đổi tài khoản hoặc quyền phải xóa dữ liệu riêng tư trước khi hiển thị phiên tiếp theo; lỗi 401/403 xử lý theo cơ chế xác thực hiện có. Giữ `refreshSession` single-flight; không hủy lần refresh chung chỉ vì một GET hết người theo dõi.
- `api()` phân biệt hủy có chủ đích với mất kết nối. Hủy do đổi màn hình không hiện thông báo mạng lỗi, không kết luận giao dịch thất bại; giữ `ApiError.code`, `fieldErrors`, `details` và thông báo tiếng Việt.
- Các API độc lập khởi chạy cùng lượt render; chỉ giữ phụ thuộc thật như payment → booking/acquisition. Không thêm endpoint tổng hợp chỉ để che một hook gây tải trùng.

**Phương án chưa chọn:** TanStack Query/SWR hoặc cache toàn cục có thời gian sống dài. Chúng thêm đợt thay đổi lớn và vẫn cần giải quyết isolation/invalidation; chỉ đánh giá ở change riêng nếu hook nhỏ không đáp ứng được yêu cầu.

### 4. Tải mã theo tính năng và giữ các phần tĩnh ngoài client khi phù hợp

- `RouteView` tiếp tục phân giải các URL hiện có nhưng dùng điểm vào riêng cho từng màn hình; import động phải là đường dẫn tường minh. Không import lại một file `integration` xuất nhiều khu vực rồi coi đó là đã tách bundle.
- Nhánh đăng ký Owner, dialog camera/quét ảnh, và công cụ bản đồ chỉ được mount/nạp khi cần. Parser mã và nhập mã thủ công vẫn dùng được độc lập với decoder ảnh.
- Giữ cách tải `SearchMap` hiện có; không thêm độ trễ không cần thiết khi người dùng chọn chế độ bản đồ. Công cụ vị trí đang hiển thị phải hoạt động và có trạng thái đang tải/thử lại bằng tiếng Việt.
- Trang chủ tách phần giới thiệu/tĩnh có thể render trên server khỏi tìm kiếm/lịch trống và điều hướng theo vai trò cần client. Các màn hình riêng tư tiếp tục dùng cơ chế xác thực hiện có; không chuyển token sang props server hoặc cache nội dung tài khoản trên server để lấy tốc độ.
- Hook polling dùng vòng lặp không chồng request, dừng khi tab ẩn/unmount và làm mới một lần khi quay lại. Giữ mốc hết hạn phía server, thông báo timeout và trần chờ của luồng thanh toán; pause/resume không cấp quyền sớm hoặc kéo dài giữ chỗ. Camera phải dừng track/RAF khi đóng, đổi trang hoặc tab ẩn.

Tuân thủ tài liệu Next **được cài trong repo**: `node_modules/next/dist/docs/01-app/02-guides/lazy-loading.md` nêu `ssr:false` chỉ trong Client Component; import động từ Server Component không tự bảo đảm chia Client bundle. Xác minh chunk thực tế sau build, không chỉ kiểm tra có chữ `dynamic` trong source.

### 5. Tối ưu tài nguyên cục bộ có phạm vi

Tạo ảnh hero WebP/AVIF phù hợp kích thước sử dụng, khai báo `sizes` và kích thước/tỷ lệ để tránh nhảy bố cục; chỉ ảnh thực sự là LCP có ưu tiên cao. Chuyển font cục bộ sang WOFF2 nếu công cụ hỗ trợ, giữ giấy phép, các weight được dùng, glyph tiếng Việt và `font-display: swap`; không tải font từ CDN lúc build.

Tách dữ liệu tỉnh/thành nhẹ khỏi danh sách xã/phường. Sinh từ một nguồn JSON hiện có, kiểm tra mã/tên khớp nguồn; tải phần xã/phường khi form cần và không làm mất lựa chọn địa chỉ cũ trong hồ sơ.

Không bật tối ưu ảnh toàn cục trước khi kiểm tra URL media, redirect có chữ ký và cấu hình remote/private network. Ảnh upload, URL có quyền truy cập và bản đồ ngoài có thể giữ cơ chế cũ nếu chưa có phương án tương thích. Không đưa các thay đổi này vào lý do để bỏ kiểm tra media.

### 6. Tối ưu backend theo đường đọc, bảo toàn các vùng nhạy cảm

**Lịch trống và tìm sân:** tách phần `availability` thành bean được Spring quản lý, giữ read transaction, hàng rào cấu hình và bước đọc lại sau hàng rào. Reservation query chỉ lấy khoảng giao với ngày/khung preview đang xem; mốc ngày tính theo timezone cơ sở, có kiểm thử ngày giao biên/DST. Không bỏ một lần preview hoặc đưa preview trước hàng rào vào cache rồi dùng lại sau hàng rào.

Sau bước trên, triển khai tìm kiếm theo danh sách ứng viên có thứ tự ổn định, giữ giới hạn quét 200/trả 100 và cờ `truncated`. Có thể chạy nhóm cơ sở độc lập qua executor hữu hạn, mặc định 2 và trần 4; các sân trong một cơ sở giữ thứ tự để tránh tranh cùng hàng rào. Mỗi lần availability phải đi qua Spring proxy và transaction riêng đúng như trước, không dùng self-invocation hoặc transaction của thread cha. Kết quả hoàn thành bất đồng bộ vẫn được sort/tie-break như cũ. Dependency lỗi tiếp tục trả lỗi, không âm thầm coi sân lỗi là còn trống hoặc bỏ khỏi kết quả. Chỉ giữ executor nếu benchmark chứng minh lợi ích và không gây tăng contention; nếu không, giữ tuần tự và ghi bằng chứng cho quyết định.

**Báo cáo:** tách `ReportSnapshotReader`, bộ tổng hợp tiền/thời gian thuần và bộ tạo dòng giao dịch/phân trang; tất cả dùng cùng snapshot. Thu hẹp dữ liệu bằng tập booking liên quan: bắt đầu/giao với kỳ xem, có khoản thu/giao dịch thực hiện trong kỳ, hoặc liên quan công nợ trước cutoff. Với mỗi booking được chọn, vẫn tải **đủ** bằng chứng thanh toán cần xác định đã trả đủ, kể cả khoản trả trước kỳ; không chỉ lọc proof theo ngày thanh toán trong kỳ.

Giữ khoản phải trả/hoàn từ kỳ trước, kể cả khoản đã tất toán sau cutoff của một báo cáo lịch sử. Các khoản thu, phí, công nợ và dòng tiền không được lọc bằng chung một mốc ngày. Giữ loại trùng payment/event, BigDecimal/decimal string, trạng thái dữ liệu chưa đủ, thứ tự phân trang và tổng tiền độc lập với trang đang xem. Chỉ sự kiện thực hiện nằm trong kỳ cần vào tập dòng giao dịch; không cần đọc mọi event lịch sử. Không bắt buộc thay phân trang hiện tại bằng một truy vấn UNION phức tạp nếu dữ liệu sau lọc đã đạt mục tiêu.

`EXPLAIN (ANALYZE, BUFFERS)` chỉ chạy trên database fixture. Các index theo cơ sở/ngày, settlement, refund và event đã tồn tại trong migrations V1/V16; tận dụng chúng trước. Index bổ sung là nhánh có điều kiện: chỉ thêm migration index độc lập khi kế hoạch truy vấn chứng minh index hiện có chưa đáp ứng, kèm cách hoàn tác; không sửa migration cũ hoặc lược bỏ constraint/trigger. Bổ sung bằng chứng Payment vẫn chạy sau transaction snapshot, không nới quyền Owner/Staff để giảm lần gọi kiểm tra.

**Schedule:** facade giữ chữ ký public/controller, các service con nhận trách nhiệm đọc lịch, giờ/ngoại lệ, phiên bản giá, cấu hình đăng ký. Giữ `REPEATABLE_READ`, lock/configuration protocol, audit, kiểm tra ảnh hưởng booking và snapshot giá. Không chia một lệnh cập nhật thành nhiều lần commit hoặc thay thế `PricingEngine`.

### 7. Tính tương thích là điều kiện bắt buộc cho mọi đợt

Giữ public API/route, nullable `customerId/currentHolderId`, phân biệt người tạo/người thanh toán/người sở hữu, QR cá nhân và mã vận hành của Guest. Chỉ thanh toán đủ mới vào sân; giữ `CONFIRMED → CHECKED_IN → COMPLETED` và quyền theo cơ sở.

Giữ B trả cho cơ sở, nhận quyền sau thanh toán và bàn giao thành công, không chờ khoản trả A; Owner chọn phí x% và tỷ lệ hoàn riêng, Staff chỉ thao tác khi được cấp quyền; SportHub không hưởng doanh thu. Idempotency, retry, lỗi/timeout, khoản chưa trả/chưa hoàn và báo cáo đúng số tiền không được thay đổi để làm demo nhanh hơn.

Các consumer/export cũ được giữ bằng facade/re-export mỏng trong giai đoạn di chuyển. Kiểm thử phải đi qua API/render/hành vi thực của module mới, không chỉ stub facade đến mức bỏ qua phần đã di chuyển. Nếu thêm dependency formatter, khóa phiên bản bằng lockfile; không dùng cơ hội này để nâng framework.

## Risks / Trade-offs

- [Cache hiển thị dữ liệu tài khoản/cơ sở trước] → Khóa theo thế hệ session, xóa đồng bộ dữ liệu riêng tư, kiểm thử A đăng xuất rồi B đăng nhập và quyền bị thu hồi.
- [GET cũ hoàn tất sau mutation] → Thế hệ request và invalidation; reload không dùng lại request trước mutation; kiểm thử đảo thứ tự phản hồi.
- [Chia module nhưng bundle không nhỏ đi] → Kiểm tra graph/chunk và request thực; loại các chuỗi import ngược qua barrel; tách payload/data khỏi UI.
- [Chia service làm mất transaction hoặc race guard] → Giữ facade/bean proxy, giữ thứ tự lock/quote/audit, kiểm thử rollback và cạnh tranh cấu hình/giữ chỗ.
- [Lọc báo cáo mất công nợ hoặc tiền gốc ngoài kỳ] → Fixture historical cutoff, payment trước kỳ, trả A/hoàn B sau kỳ, nhóm/Guest và đối chiếu cùng bộ tính tiền hiện tại.
- [Song song tìm sân gây tranh lock/pool hoặc đổi kết quả] → Chỉ nhóm cơ sở độc lập, trần executor nhỏ, giữ thứ tự chọn ứng viên/sort và hành vi lỗi; có thể hoàn tác executor riêng.
- [Tải theo nhu cầu chậm lần mở đầu hoặc lỗi chunk] → Placeholder có kích thước, retry rõ ràng và kiểm thử lần mở đầu; không dùng lazy loading cho nội dung chính đang cần đọc.
- [Định dạng làm diff lớn, che thay đổi tiền/quyền] → Chia đợt/commit theo module và tách định dạng khỏi tối ưu; không sửa diện rộng ngoài scope.

## Migration Plan

1. Ghi baseline trên working tree hiện có và fixture cách ly; lưu nhận diện source để phần Việt hóa không bị bỏ khỏi so sánh.
2. Đưa chuẩn định dạng/kiểm tra vào phạm vi mục tiêu; tách module kèm facade nhưng giữ hành vi trước, chạy regression của phần vừa di chuyển.
3. Cải thiện shared GET, điểm nạp mã, dữ liệu địa chỉ và ảnh/font; đo lại các route trước khi mở rộng thay đổi.
4. Tách và tối ưu lịch trống/tìm sân/báo cáo/Schedule từng phần, kiểm tra hợp đồng, số tiền, race và hiệu năng trước/sau. Index bổ sung chỉ thực hiện khi đủ điều kiện ở quyết định 6.
5. Chạy bộ kiểm tra liên quan và đo production cuối; ghi phần đạt/chưa đạt, command, dataset và kết quả. Không dùng `next dev` hoặc thời gian build làm số đo tốc độ tải trang.
6. Triển khai/khởi động môi trường người dùng chỉ khi có yêu cầu riêng. Mỗi đợt hoàn tác bằng code/asset của đợt đó; request store chỉ nằm trong bộ nhớ, không cần chuyển đổi dữ liệu. Nếu có index bổ sung, hoàn tác riêng index đó; không hoàn tác bằng reset/reseed database hoặc sửa dữ liệu tài chính.
