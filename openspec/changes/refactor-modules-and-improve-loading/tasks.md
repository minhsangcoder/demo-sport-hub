> **HISTORY ONLY — NOT AN ACTIVE REQUIREMENT SOURCE.**
> Read `openspec/CURRENT_CHANGE.md` at the project root for the current proposal.
> Do not apply, merge, or resume requirements/tasks from this document unless
> the user explicitly requests this historical change. Task counts are historical.

# Tasks

Change thuần refactor/tối ưu, dùng `skip_specs: true`. Mỗi đợt phải giữ các bất biến ở design, chạy kiểm tra của phần vừa thay đổi và cập nhật hướng dẫn cùng đợt; không đợi cuối change mới kiểm tra. Các task dưới đây chưa được thực hiện trong bước propose.

## 1. Baseline và môi trường đo có thể chạy lại

- [x] 1.1 Ghi source fingerprint của working tree và kết quả kiểm tra hiện có; tạo fixture frontend/backend cách ly với dữ liệu nhỏ và bộ lịch sử tối thiểu 1.000 booking theo design. Xác minh script có thể tạo lại fixture, không dùng database khách thật, không reset/reseed hoặc dừng runtime người dùng; lưu cách chạy trong `docs/performance-baseline.md`.
- [x] 1.2 Thêm script Playwright/Node đo production cho trang chủ, login, tìm sân, danh sách Staff và báo cáo Owner: tối thiểu 5 lượt cache lạnh/ấm, LCP/nội dung chính, byte JS/ảnh/font và request trùng. Xác minh đúng vai trò/nội dung fixture, mỗi chunk chỉ tính một lần, lưu baseline và cấu hình máy/mạng trong tài liệu đo.
- [x] 1.3 Thêm phép đo API tìm sân/báo cáo sau warm-up, tối thiểu 30 mẫu, kèm số dependency call/kế hoạch truy vấn và payload đối chiếu. Xác minh script chạy lại được với cùng dataset, ghi p50/p95 và các timestamp được loại riêng khi so sánh; không đưa token hoặc liên hệ thật vào kết quả.

## 2. Chuẩn định dạng và kiểm soát kích thước module

- [x] 2.1 Thêm cấu hình/script Prettier khóa phiên bản cho phạm vi frontend mục tiêu, loại generated/data/legacy phù hợp; định dạng riêng các file được refactor trong từng đợt. Xác minh format-check chạy đúng, typecheck không đổi hành vi và diff định dạng tách khỏi diff xử lý.
- [x] 2.2 Áp dụng một cách định dạng Java thống nhất cho các lớp Booking/Discovery/Reporting/Schedule trong phạm vi; không định dạng toàn backend cùng lúc. Xác minh formatter lặp lại không đổi file và các lớp vẫn compile; ghi lệnh/cách chia đợt trong `docs/refactor-and-loading.md`.
- [x] 2.3 Thêm kiểm tra module dựa trên source đã định dạng: báo mốc 350 LOC, chặn file logic mới/đã refactor trên 500 LOC khi không có ngoại lệ hợp lệ, phát hiện dòng logic bị nén; kiểm kê riêng dictionary/generated/JSON/migration. Xác minh bằng mẫu file nén, file dữ liệu và module tách hợp lệ; ghi baseline, allowlist và nguyên tắc sở hữu module trong hướng dẫn.

## 3. Dữ liệu frontend dùng chung và polling an toàn

- [x] 3.1 Cho `api()` chuyển tiếp và phân biệt AbortSignal/AbortError, giữ `ApiError` và refresh single-flight hiện có. Xác minh test hủy GET không hiện lỗi mạng, không hủy refresh chung, vẫn giữ header/cookie/fieldErrors/details và không tự replay mutation; chạy regression proxy/auth trong `frontend.test.mjs` và `google-auth.test.mjs`.
- [x] 3.2 Bổ sung bộ quản lý GET nhỏ phía dưới `useApi`, chia sẻ request đang chạy và đếm consumer, giữ interface hiện tại. Xác minh test hai consumer cùng khóa chỉ gọi một request; một consumer rời không hủy consumer còn lại; hết consumer thì abort; đổi URL không nhận kết quả cũ.
- [x] 3.3 Thêm thế hệ session/request và invalidation cho reload sau mutation, đổi cơ sở/tài khoản/quyền. Xác minh test phản hồi đảo thứ tự, GET trước mutation hoàn tất muộn, 401/403 và A đăng xuất rồi B đăng nhập; dữ liệu riêng tư của A không được hiển thị cho B dù có request đang chờ.
- [x] 3.4 Cho phép TTL tối đa 60 giây chỉ ở danh mục công khai được khai báo, tích hợp làm mới với các mutation/sự kiện tiền hiện có. Xác minh danh mục dùng lại trong TTL, lỗi không bị giữ như kết quả thành công; lịch trống, quyền, quote, tiền và report không dùng TTL; cập nhật danh sách policy/invalidation trong hướng dẫn.
- [x] 3.5 Tách hook polling không chồng request, dùng cho xác nhận payment, QR thành viên và trò chuyện theo chu kỳ hiện có; dừng khi tab ẩn/unmount, làm mới một lần khi trở lại. Xác minh test fake clock/visibility giữ trần chờ payment, hạn server, không cấp quyền sớm và không tạo timer/request mồ côi; ghi rõ chu kỳ và điều kiện dừng.

## 4. Điểm nạp mã và dữ liệu tải ban đầu

- [x] 4.1 Tách các hàm payload đăng ký/pháp lý/cơ sở khỏi TSX; loại import kéo ngược giao diện vào tiện ích dùng chung. Xác minh test payload Customer/Owner, nullable/thông tin địa chỉ và upload giữ nguyên; graph import login không đi qua công cụ vị trí/dữ liệu xã phường của Owner.
- [x] 4.2 Tách `auth-form.tsx` thành facade, shell và nhóm form/điều phối theo mode; chỉ nạp phần Owner khi nhánh đăng ký Owner cần dùng. Xác minh login, đăng ký thường/Owner, xác minh, quên/đặt lại mật khẩu và Google redirect/Strict Mode vẫn hoạt động; đo lại chunk login và cập nhật sơ đồ trách nhiệm trong hướng dẫn.
- [x] 4.3 Sinh danh sách tỉnh/thành nhẹ từ JSON hành chính hiện có, tách phần xã/phường để nạp khi form cần. Xác minh mọi mã/tên sinh ra khớp nguồn, tra cứu theo mã/tên không đổi và hồ sơ có địa chỉ cũ giữ được lựa chọn; Network của trang chủ/tìm sân không tải danh sách xã/phường đầy đủ.
- [x] 4.4 Tách điểm vào các màn hình của `RouteView` và các module `integration` đa chức năng; dùng import động tường minh theo màn hình, giữ route grammar/export chuyển tiếp cần thiết. Xác minh test URL thật/URL sai/quyền từng vai trò và bản dựng production không kéo công cụ quản trị vào trang Staff/Customer chỉ vì bộ điều phối chung.
- [x] 4.5 Tách parser QR chuỗi khỏi decoder ảnh/video và tách `qr-checkin.tsx` thành upload, camera/dialog và hook xử lý; nạp decoder/camera theo thao tác mở. Xác minh các test QR hiện có, nhập mã thủ công, ảnh lỗi, đóng/mở và quyền camera bị từ chối; browser xác nhận track/RAF dừng khi đóng/ẩn tab và decoder chưa tải trước thao tác; ghi cách kiểm tra trong hướng dẫn.
- [x] 4.6 Tách trang chủ thành phần tĩnh render phù hợp trên server và phần tìm kiếm/lịch trống/điều hướng vai trò trên client. Xác minh Staff/Owner/Admin vẫn điều hướng như trước, Customer vẫn đặt sân được, không có dữ liệu session trong cache server; đo lại mốc nội dung chính và kiểm tra hydration tiếng Việt.

## 5. Tách các màn hình lớn và file JSX nén

- [x] 5.1 Tách `AppHeader` thành menu/bảng liên kết theo vai trò và thành phần điều hướng; tách context khỏi sidebar/breadcrumb của `facility-context.tsx`. Xác minh menu desktop/mobile, đóng khi đổi URL, đổi cơ sở và các quyền Owner/Staff qua test render/browser; giữ export và ghi chủ sở hữu từng phần trong hướng dẫn.
- [x] 5.2 Tách trang hồ sơ và liên hệ thành avatar, thông tin cá nhân và form bảo mật/xác minh, giữ API/hành vi lưu hiện có. Xác minh test lưu, validation, sao chép mã và đổi liên hệ cần xác minh; mọi nhãn còn tiếng Việt, các module logic đạt ngân sách kích thước.
- [x] 5.3 Tách màn hình nhân viên thành danh sách, tạo tài khoản, thẻ hồ sơ, đổi mật khẩu và bộ chọn quyền. Xác minh cấp/thu hồi BOOKING_REFUND và TRANSFER_PAYOUT độc lập, Staff không tự cấp quyền, stale version/403 vẫn hiển thị; chạy browser fixture quyền và cập nhật điểm vào mới trong hướng dẫn.
- [x] 5.4 Tách admin shell/nav khỏi `account-list.tsx`; di chuyển màn hình hỗ trợ/hoàn tiền/danh mục đang nằm chung trong `integration/admin.tsx` về module riêng. Xác minh lọc/phân trang/đổi vai trò/ghi chú qua request thật của component và URL cũ; admin monitor vẫn chỉ đọc đối với các khoản tiền cơ sở.
- [x] 5.5 Tách `LocationPicker` thành xử lý đường dẫn/tọa độ, provider bản đồ và giao diện điều khiển. Xác minh parser URL/tọa độ, Google khi có key, Leaflet khi không có key, trường chỉ đọc và cleanup listener; không tải provider ở trang không sử dụng; giữ cấu hình media/map hiện có.
- [x] 5.6 Tách tổng quan cơ sở thành thông tin, ảnh và danh sách sân, giữ nguyên các truy vấn/quyền đang có. Xác minh upload/xóa ảnh, lỗi truy cập và link quản lý; không nhân đôi request cơ sở/sân sau khi chia component.
- [x] 5.7 Tách `integration/group.tsx` và phần điều phối nhóm thành QR thành viên, trò chuyện, điểm danh và tái sử dụng nhóm; dùng polling ở nhóm 3. Xác minh ẩn/rời/giữ/tái sử dụng nhóm, quyền tổ chức/thành viên, hạn góp tiền và điểm danh không đổi; cập nhật test để chạy qua phần thực thi mới, không chỉ stub facade.
- [x] 5.8 Định dạng và tách pricing, schedule và danh sách/chi tiết vận hành thành bộ lọc, bảng/thẻ và form thao tác theo domain. Xác minh đổi giá/ngoại lệ/bảo trì, cảnh báo booking bị ảnh hưởng, Guest/Customer liên kết, payment tại quầy và check-in theo cơ sở; chạy regression counter ownership và giữ `CHECKED_IN`.
- [x] 5.9 Tách `report-page.tsx` thành bộ lọc kỳ, KPI, biểu đồ ngày, bảng sân và giao dịch; giữ `demo-report.ts` là nơi định dạng/kiểm tra đầu vào thuần. Xác minh tổng tiền/kỳ/timezone/phân trang, dữ liệu PARTIAL/UNAVAILABLE và lỗi tải qua fixture report; không tính lại doanh thu từ các dòng đang hiển thị; ghi cấu trúc mới trong hướng dẫn.
- [x] 5.10 Tách từ điển lỗi theo domain, nhãn trạng thái/trường khi cần, giữ facade dịch đồng bộ và fallback tiếng Việt. Xác minh `vietnamese-ui.test.mjs` cùng các consumer ApiError/record vẫn qua; kiểm tra LOC sau định dạng và không ghi nhận lợi ích bundle nếu mọi từ điển vẫn được import chung.

## 6. Ảnh, font và tải tài nguyên

- [x] 6.1 Tạo biến thể WebP/AVIF phù hợp cho ảnh hero cục bộ, cập nhật kích thước/sizes/ưu tiên tải tại nơi sử dụng. Xác minh trang chủ/xác thực ở desktop/mobile không méo ảnh/nhảy bố cục đáng kể, media upload/redirect giữ nguyên và byte ảnh giảm trên phép đo production; ghi thông số chuyển đổi/ảnh gốc trong hướng dẫn.
- [x] 6.2 Tối ưu font cục bộ sang WOFF2 khi công cụ hỗ trợ, giữ các weight/glyph tiếng Việt/giấy phép và swap; nếu môi trường chưa hỗ trợ, ghi rõ giới hạn và kiểm chứng phần giảm byte còn lại thay vì tuyên bố đã chuyển font. Xác minh nội dung tiếng Việt hiển thị đầy đủ, build không cần CDN và kết quả byte ảnh/font so baseline được lưu.

## 7. Backend: module đọc và công việc dư trên đường tải trang

- [x] 7.1 Tách `BookingAvailabilityService` với facade `BookingService.availability` tương thích, giữ Spring proxy/transaction, hàng rào và bước đọc lại preview. Xác minh integration test race cấu hình/giữ chỗ, quote thay đổi và lỗi dependency giữ hành vi; kiểm tra không mất AOP do self-invocation và ghi ranh giới transaction trong hướng dẫn.
- [x] 7.2 Thu hẹp reservation query theo khoảng giao ngày/preview thay vì mọi reservation lịch sử của sân. Xác minh test cùng ngày/khác ngày, qua nửa đêm, timezone/DST, HOLD còn/hết hạn và BOOKED trả cùng lịch trống; dùng fixture nhiều lịch sử để chứng minh số hàng đọc giảm, không bỏ hàng rào cho mục tiêu tốc độ.
- [x] 7.3 Tách chọn ứng viên/điều phối/tổng hợp trong `DiscoveryService`, giữ thứ tự quét 200/trả 100/truncated và sort. So sánh tuần tự với executor theo nhóm cơ sở mặc định 2, trần 4; chỉ giữ song song khi có lợi. Xác minh test dependency call/timeout/cùng facility guard, đầu ra và lỗi tương đương; lưu benchmark và lý do giữ/không giữ executor trong tài liệu.
- [x] 7.4 Tách snapshot reader, bộ tổng hợp thu/phí/hoàn/công nợ và bộ tạo giao dịch khỏi `FacilityDemoReportingService`, giữ API và độ chính xác tiền. Xác minh test hiện có cộng thêm đối chiếu output trước/sau cho nhóm, Guest, chuyển nhượng, giao dịch trùng, thiếu proof và paging; xác nhận enrichment Payment vẫn ở ngoài transaction snapshot.
- [x] 7.5 Thu hẹp các query báo cáo theo tập booking/giao dịch thực sự liên quan kỳ và nghĩa vụ đến cutoff. Xác minh payment trước kỳ vẫn đủ proof, công nợ kỳ trước, tất toán sau cutoff lịch sử, refund r0%, trả A lỗi/hoàn B lỗi và không cộng P thành doanh thu; chứng minh event ngoài kỳ không bị tải toàn bộ, lưu p95/EXPLAIN trước/sau.
- [x] 7.6 Tách trách nhiệm đọc lịch, giờ/ngoại lệ, quản lý phiên bản giá và cấu hình đăng ký khỏi `ScheduleService`, giữ facade và `PricingEngine`. Xác minh Schedule integration tests về rollback toàn lệnh, REPEATABLE_READ, lock/configuration changes, ảnh hưởng booking và lịch sử giá; các lớp sau định dạng đạt ngân sách và hướng dẫn nêu rõ ranh giới transaction.
- [x] 7.7 Đối chiếu query plan với index V1/V16 hiện có trên fixture và ghi kết luận. Chỉ tạo migration index bổ sung khi có bằng chứng thiếu index; nếu cần, xác minh Flyway/rollback index, ràng buộc cũ và cải thiện plan, không sửa migration đã có; nếu không cần, lưu bằng chứng để khép nhánh này mà không tạo migration thừa.

## 8. Kiểm tra tích hợp, số đo cuối và bàn giao

- [x] 8.1 Chạy `pnpm test`, `pnpm typecheck`, build production, format/module check và lint frontend; chạy các test contract OpenAPI liên quan. Xác minh không có lỗi mới so baseline, lint phạm vi đã sửa phải đạt; ghi rõ lỗi nền tồn đọng nếu có, không disable kiểm tra hoặc sửa cây legacy để che lỗi.
- [x] 8.2 Chạy Maven test Booking/Schedule/common trên database fixture và các suite Identity/Payment/Transfer bị ảnh hưởng hợp đồng. Xác minh facade/public API, nullable Guest, transaction/guard, precision và idempotency không đổi; không chạy migration/correction lên dữ liệu người dùng.
- [x] 8.3 Chạy browser/system E2E qua UI cho online, linked counter, Guest, góp nhóm và A→B: thu đủ mới vào sân, B không chờ trả A, phí/hoàn do Owner đặt, Staff theo quyền/cơ sở, thất bại/retry và báo cáo. Xác minh các module tách/lazy/cache/polling đều tham gia luồng thực; kiểm tra đổi tài khoản, tab ẩn/hiện và lỗi tải chunk.
- [x] 8.4 Chạy lại đúng bộ đo production/dataset của nhóm 1 và đối chiếu các mục tiêu ở design: JS login/Staff, ảnh/font, nội dung chính, API p95 và route kiểm soát. Xác minh có kết quả trước/sau và nguyên nhân cho mục chưa đạt; không đóng bằng số dòng giảm hoặc số đo từ dev server.
- [x] 8.5 Hoàn thiện `docs/refactor-and-loading.md` và `docs/performance-baseline.md` với sơ đồ module/entrypoint, cache/polling, command kiểm tra, số đo và cách hoàn tác từng đợt. Xác minh lệnh tài liệu chạy được, change chỉ thay phạm vi đã duyệt, các phần Việt hóa/nghiệp vụ trước đó được bảo toàn và không có triển khai tự động.
