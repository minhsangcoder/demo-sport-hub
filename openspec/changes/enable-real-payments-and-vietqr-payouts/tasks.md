> **HISTORY ONLY — NOT AN ACTIVE REQUIREMENT SOURCE.**
> Read `openspec/CURRENT_CHANGE.md` at the project root for the current proposal.
> Do not apply, merge, or resume requirements/tasks from this document unless
> the user explicitly requests this historical change. Task counts are historical.

# Tasks

Mọi checkbox dưới đây dành cho apply sau khi người dùng yêu cầu. Proposal không thực thi code, migration hoặc giao dịch ngân hàng. Dùng design D1–D12 và 8 specs làm hợp đồng; không thực hiện nhánh chi hộ/escrow legacy để đạt các task này.

## 1. Hợp đồng và schema bổ sung

- [x] 1.1 Mở rộng OpenAPI Payment/Booking/Transfer/Facility/Admin và hợp đồng nghiệp vụ cho mode, kênh nhận, bank destination revision, QR session, xác nhận chi bằng bankTransactionReference và confirmationMethod; kiểm bằng contract tests, minh họa rõ không có mức2% mặc định hoặc phí SportHub.
- [x] 1.2 Thêm migration Payment sau V14 cho channel/secret revisions, QR sessions/orderCode uniqueness, bank observations và destination revisions; kiểm fresh/upgrade PostgreSQL, FK/unique/immutability, giữ nguyên rows/mode/constraints lịch sử và không backfill ngân hàng.
- [x] 1.3 Thêm migration Booking sau V16 cho cohort/proof REAL local, settlement theo phí Owner, refund entitlements/allocations và manual operations/receipts/claims; kiểm fresh/upgrade, P=F+N, fee0..10000, một business obligation và receipt không sửa/xóa được.
- [x] 1.4 Bổ sung protocol/terms/cohort REAL của Transfer sau migrations hiện hữu, giữ v1/DEMO; kiểm snapshot/version/identity bất biến, fee3%/7% không bị constraint legacy200bps và public terms không chứa STK.

## 2. Kênh Owner và tài khoản người nhận

- [x] 2.1 Cài mã hóa authenticated encryption cho credential/STK bằng khóa môi trường, projection chỉ trả masked fields; kiểm roundtrip, thiếu/sai khóa fail closed và không lộ plaintext qua response/log/error. Ghi hướng dẫn cấu hình/rotation không có secret thật.
- [x] 2.2 Thêm API Owner cấu hình/verify/disable kênh payOS đúng facility, URL webhook channelId ổn định và receiver/credential revisions; kiểm Owner/Staff/ngoài scope, probe không tạo tiền, rotation không mất order cũ, disable không ngừng đối soát. Cập nhật OpenAPI/hướng dẫn Owner.
- [x] 2.3 Thêm API bank destination Customer và Guest qua operator BOOKING_REFUND, kiểm ownership/consent/version; kiểm STK giữ số0, revision người khác bị chặn, sửa profile không đổi nghĩa vụ đã chốt, không giả bank-verified từ masked token legacy. Ghi contract private projection.
- [x] 2.4 Thêm danh mục BIN cùng origin cache server làm mới theo ngày, validate số tài khoản số không rỗng tối đa19 ký tự; kiểm upstream timeout/dữ liệu lỗi/cache có thời điểm, BIN không hỗ trợ và không có request ngân hàng cho mỗi render form. Ghi rõ định dạng hỗ trợ và chưa tự xác minh chủ STK.

## 3. Thu tiền payOS và phục hồi order

- [x] 3.1 Mở rộng PaymentProvider và adapter PAYOS dùng context server/result có QR/link; triển khai create/get/cancel bằng HTTP có timeout và canonical HMAC native. Description lưu bền vững chỉ ASCII chữ/số, tối đa9 ký tự cho cả VA; kiểm boundary/unique/replay, fixture chính thức, response chữ ký/binding sai, URL lạ/amount/expiry; giữ regression DEMO và cập nhật contract provider.
- [x] 3.2 Tách payment intent/session đã commit khỏi lời gọi provider ngoài transaction, tạo orderCode duy nhất và claim khởi tạo có thể phục hồi; kiểm concurrent keys, timeout sau provider commit, restart và CREATE_UNKNOWN chỉ truy vấn/retry chính order đó, không giữ DB lock khi chờ mạng.
- [x] 3.3 Thêm webhook payOS theo channelId qua Gateway với xác thực native và durable observations trước ACK; kiểm chữ ký sai, mẫu, reference xung đột, ngân hàng/kênh khác, key rotation và crash/retry. Chỉ public đúng webhook, không mở payment/internal routes; ghi contract status/ACK.
- [x] 3.4 Thêm processor đối chiếu payOS ngoài transaction, cộng credits không trùng, settle canonical due và outbox v2 nguyên tử; kiểm40k+60k/100k,110k/100k, hai khoản thu trùng nghĩa vụ, amountPaid không đủ chứng minh và callback/query racing. Phần thừa/không áp dụng luôn có fact cần hoàn riêng.
- [x] 3.5 Thêm worker/query phục hồi pending/unknown/late orders có backoff, jitter, budget mỗi kênh và POST refresh rate-limited; kiểm mất webhook khi tab đóng,429, timeout, cancel/expiry rồi vẫn nhận tiền và kill switch tạo mới không mất receipt. Ghi runbook đối soát và cửa sổ truy vấn.
- [x] 3.6 Tách REAL evidence khỏi capability của movement adapter và chặn demo endpoints/outcomes trong LIVE; kiểm profile prod+demo/local, forged mode/provider từ client và receipt v1 không thể cấp quyền REAL, không gọi worker chi hộ hoặc fallback mô phỏng khi cấu hình thiếu.

## 4. Booking REAL và check-in

- [x] 4.1 Chốt cohort REAL/expected receiver từ server cho hold/booking/counter/nhóm ở LIVE, thêm BANK_QR cho luồng quầy hiện hành; kiểm thiếu kênh/policy r chặn tạo mới, client không chọn mode, Guest holder NULL và linked Customer không thành Staff owner. Cập nhật hợp đồng payable/counter.
- [x] 4.2 Mở rộng consumer receipts và local collection assessment cho evidence v2 REAL, giữ binding/amount/currency/canonical cùng court transaction; kiểm booking chỉ CONFIRMED khi đầy đủ và còn hold, delayed/replayed events không cấp sớm, không gọi Payment/provider dưới court lock.
- [x] 4.3 Nối contribution REAL vào group payment/confirmation theo từng nghĩa vụ member; kiểm partial/full, organizer trả hộ, duplicate receipt, DEMO không bù REAL, member QR không được cấp cho khoản chưa trả và guard sửa allocation đã thu vẫn giữ.
- [x] 4.4 Áp cùng guard REAL cho QR/mã/HTTP check-in và credential issuance, giữ cửa sổ thời gian/token rotation/CHECKED_IN→COMPLETED; kiểm thiếu tiền, thiếu proof, sai cơ sở, Guest, concurrent check-in/cancel và credential A sau handoff. Cập nhật mô tả trạng thái sử dụng.

## 5. Chuyển nhượng và nghĩa vụ trả A

- [x] 5.1 Tạo terms REAL tái dùng phép tính Owner fee có tham số, bind receiver/destination revisions và policy/hash; kiểm0/2% Owner chọn rõ/3/7/100%, P25/x50% và không có fallback200bps hoặc phí SportHub. Ghi công thức F/N và r độc lập trong hợp đồng.
- [x] 5.2 Sửa quote/create/edit listing và acquisition consent để bắt destination của A trước publish, giữ destination sau khóa và không lộ bank qua listing DTO; kiểm thiếu STK, revision người khác, đổi profile/fee sau khóa, hash cũ và listing DEMO không vào chợ REAL.
- [x] 5.3 Cho payable/transfer consumer xử lý collection REAL canonical đúng acquisition và deadline; kiểm thiếu/đủ/dư, wrong buyer/receiver/terms, duplicate event và PENDING_HANDOFF trước QR của B, không đòi duyệt Owner hoặc payout đã xong.
- [x] 5.4 Mở rộng complete/abort quyết định một lần cùng court transaction với holder/token/F/N hoặc full technical entitlement; kiểm concurrent complete/abort, response mất sau commit, late callback sau ABORTED, N0 và B check-in khi N chưa trả/REVIEW. Giữ consumers v1 và ghi protocol/decision contract.

## 6. Hủy và quyền hoàn REAL

- [x] 6.1 Mở cancellation-quote/cancel-with-policy cho REAL từ applied evidence, đúng base trước/sau resale và destination binding; route/chặn đường cancel cũ có thể bỏ qua policy đối với paid REAL. Kiểm r0/80/100%, quote stale, hủy trước giờ chơi/check-in race và không trừ thêm x trên H; cập nhật contract cancellation.
- [x] 6.2 Tạo allocation nhóm theo payer/payment và Guest bank recipient riêng; kiểm largest remainder, partial group, organizer không khai STK thay thành viên, missing destinations chờ bổ sung, Guest không hoàn vào Staff mặc định và hoàn một allocation chưa hoàn cả nhóm.
- [x] 6.3 Nối collection unapplied/partial-expired/overpayment/duplicate/ABORTED vào technical entitlements theo tiền thực nhận, kể cả chưa có tài khoản nhận; kiểm hoàn đủ không áp x/r, replay không tạo lại H và COMPLETED không bị coi là ABORTED. Ghi rõ cách khách bổ sung STK.
- [x] 6.4 Bảo vệ capacity hoàn dùng chung giữa policy/technical allocations và operation đã xác nhận/đang xử lý/không rõ; kiểm racing refunds, quote sau handoff khác, partial confirmations và không vượt actual collection. Giữ lịch sử paidAt/policy và guards nhóm khi timeout.

## 7. VietQR chi trả và mã giao dịch thủ công

- [x] 7.1 Thêm prepare/replay một manual operation/claim theo settlement/allocation với số tiền và destination server chốt; kiểm hai operator chỉ một claim, N/H0 không tạo chi, reload ổn định và missing destination không mất nghĩa vụ. Cập nhật OpenAPI prepare/read.
- [x] 7.2 Dựng encoder VietQR chuyển tài khoản hẹp và ảnh bằng ZXing hiện có, nội dung ASCII≤25 ký tự/ref duy nhất; kiểm independent golden payload/CRC, decode ảnh ra BIN/STK/amount/memo chính xác, số0 đầu, không dùng URL STK công khai hoặc API Generate. Ghi nguồn format và giới hạn đã kiểm.
- [x] 7.3 Thêm complete manual operation bắt bankTransactionReference hợp lệ, current grant/claim/version, khóa/unique và immutable receipt; kiểm mã trống, body outcome SUCCESS/amount/payee giả, replay response mất, reference trùng giữa payout/refund và operator sai cơ sở. Lưu MANUALLY_RECORDED/confirmedAt, không tạo chứng từ bank-verified.
- [x] 7.4 Thêm xử lý REVIEW/Owner tiếp nhận khi đã chuyển nhưng chưa confirm, release chỉ khi xác định chưa chuyển, và thay destination có consent/version; kiểm revoke sau prepare, tab đóng không auto giải phóng, QR cũ không yêu cầu chuyển lại và receipt thành công không sửa/xóa. Ghi hướng dẫn xử lý trùng/sai STK trong app ngân hàng.

## 8. Frontend thu tiền thật

- [x] 8.1 Làm màn Owner cấu hình kênh payOS theo cơ sở bằng API mới, chỉ trả cờ đã cấu hình credential/receiver được che/status và lỗi tiếng Việt; kiểm browser đúng/sai Owner, Staff không có nút đổi kênh, verify/disable/reload và apiKey/checksumKey không nằm localStorage/URL/response kể cả một phần.
- [x] 8.2 Tạo payment instruction view dùng chung trong payment-page với QR/ngân hàng/số tiền/nội dung/hạn/checkout link, trạng thái partial/verified/applying/expired/review; kiểm không có demo complete/Tôi đã trả tự SUCCESS trong REAL, return URL giả không cấp quyền và QR khác với QR vào sân.
- [x] 8.3 Nối counter-payment/counter modal/businessApi vào QR REAL và đợi backend áp dụng, giữ idempotency khi đóng/mở modal; kiểm Guest và linked Customer, Staff payer không thành holder, operator bị thu hồi quyền và điều hướng kết quả theo ownership hiện tại.
- [x] 8.4 Nối payment nhóm/acquisition và usePolling theo deadline server, không dùng trần15 giây DEMO làm thất bại ngân hàng; kiểm fake-clock/visibility/unmount, reload/re-login, request không chồng,401/403 không replay mutation, B không chờ A được trả. Ghi mô tả các trạng thái chờ trong UI.

## 9. Frontend ngân hàng người nhận và thao tác cơ sở

- [x] 9.1 Tạo form chọn ngân hàng theo tên/BIN, STK text và chọn revision của chính người dùng; kiểm số0 đầu, validation/server errors, danh mục lỗi, session switch và không cache bank details xuyên tài khoản. Đọc hướng dẫn Next cài trong dự án trước khi sửa code FE.
- [x] 9.2 Nối form tài khoản vào listing-form trước quote/publish/edit, reset consent/hash khi destination/giá đổi; kiểm A không đăng được khi thiếu STK kể cả N0, destination người khác bị chặn và B/public market không thấy STK A.
- [x] 9.3 Nối form refund REAL theo allocation cho Customer/nhóm và capture Guest tại cơ sở; kiểm yêu cầu chủ động thiếu tài khoản, technical entitlement chờ bổ sung, đúng buyer sau A→B→C, organizer không nhập ngân hàng của mọi người và quyền Staff hiện hành.
- [x] 9.4 Làm panel Owner/Staff với QR prepare, số tiền/người nhận/nội dung, field mã giao dịch bắt buộc và Hoàn tất chuyển khoản; nối lịch sử A/B với nhãn cơ sở xác nhận. Kiểm hai operator/claim, double-click, mất response/revoke và lỗi/empty/mobile/a11y; không chỉ đổi nhãn panel DEMO hoặc tự gọi chi hộ.

## 10. Facts, báo cáo Owner và giám sát Admin

- [x] 10.1 Mở PaymentReportFacts/PaymentEvidenceClient cho REAL bank observations, amountApplied/extra, mode/receiver/proof và coverage đúng scope; kiểm native transaction dedup, provider name giả, v1 fallback, partial credits qua ngày và giới hạn batch500. Cập nhật contract facts không lộ bank details.
- [x] 10.2 Mở report snapshot/mode assessment/assembler cho settlement/refund/manual receipts REAL và bộ lọc cohort; kiểm R/P/F/N/H, thu chưa áp dụng/dư, H từng allocation, công nợ trước kỳ/chi sau cutoff, lịch sử sân và không gọi mạng dưới snapshot transaction.
- [x] 10.3 Nối dashboard/chart/bảng sân/giao dịch Owner với mode/dateBasis/nhãn xác nhận thủ công, default REAL ở LIVE; kiểm cùng filter, response cũ, phân trang và ví dụ R120k/P100k/x7%→R+F127k, H80k→47k, N93k riêng. Giữ dữ liệu DEMO đọc riêng và PARTIAL/UNAVAILABLE.
- [x] 10.4 Mở giám sát Admin chỉ đọc cho collection/application/entitlements/manual receipt/review; kiểm Admin không override tiền vào/hoàn tất tiền ra, Staff không có dashboard Owner, cross-facility/participant privacy và không có KPI doanh thu SportHub. Ghi cách đọc số liệu và mức xác nhận trong tài liệu bàn giao.

## 11. Tích hợp hệ thống, cấu hình và kiểm tra hồi quy

- [x] 11.1 Cập nhật cấu hình/application/.env.example/Compose/Gateway và tài liệu triển khai: HTTPS webhook ổn định, secret encryption, collection kill switch, worker budgets, log redaction và profile/seed riêng. Kiểm config thiếu fail closed, prod không có demo buttons/endpoints/seed và vẫn nhận/query order cũ khi tắt tạo mới.
- [x] 11.2 Mở harness E2E độc lập bằng mock payOS native HTTP/HMAC đi qua BFF→Gateway→services→PostgreSQL/Rabbit; kiểm online/quầy/Guest/nhóm/A→B→C, đủ tiền mới check-in, B trước trả A, QR chi decode đúng và manual reference bắt buộc. Không sửa status bằng SQL hoặc chạm database/dịch vụ người dùng.
- [x] 11.3 Kiểm E2E sự cố: create timeout sau provider commit, webhook mất/trùng/sai, late money sau release, crash trước ACK/outbox, complete/abort race và hai operator/revoke/response mất; kiểm không thu/chi/hoàn/đổi holder trùng. Lưu bằng chứng synthetic và kết quả, không gọi API/ngân hàng thật từ CI.
- [x] 11.4 Chạy regression backend liên quan và frontend test/typecheck/lint/format/module budget/build fixture production; kiểm OpenAPI và OpenSpec strict, ≤500 LOC module logic, không tăng import QR/bank/report vào route chung hoặc polling/bank query theo mỗi render. Ghi lệnh/kết quả thật của lần chạy, không dùng kết quả cũ của DEMO để đánh dấu đạt LIVE.

## 12. Nghiệm thu tiền thật có kiểm soát

- [x] 12.1 Bàn giao runbook nghiệm thu payOS không có sandbox: cấu hình credential Owner qua màn hình/môi trường riêng, HTTPS callback, giao dịch giá trị nhỏ do người dùng thực hiện, nhận tiền/query/QR/claim/manual reference và rollback tắt tạo mới; kiểm tài liệu có bước và expected result cho từng bên, không chứa secret hoặc tài khoản thật.
- [ ] 12.2 Khi Owner đã cấu hình và người dùng chủ động thực hiện thử: xác minh QR thu quét được/tiền vào đúng Owner/đủ tiền mới có quyền, QR trả A và hoàn khách điền đúng BIN/STK/số tiền/nội dung, reference hoàn tất cập nhật đúng báo cáo. Lưu receipt IDs và bằng chứng đã che thông tin; chưa thử ngân hàng thật thì giữ task này chưa hoàn thành và không tuyên bố live-ready.

## Workflow follow-up

- Sau khi người dùng duyệt kế hoạch mới bắt đầu `$openspec-apply-change` cho `enable-real-payments-and-vietqr-payouts`; proposal không tự khởi động apply.
- Sync/archive các change chỉ khi được yêu cầu riêng; giữ requirement DEMO và REAL theo đúng cohort, không xóa lịch sử hoặc tự đổi policy trong quá trình sync.
- Không tự thực hiện chuyển khoản thật, thu thập mật khẩu ngân hàng hoặc triển khai/live-enable trong quá trình viết kế hoạch.
