> **HISTORY ONLY — NOT AN ACTIVE REQUIREMENT SOURCE.**
> Read `openspec/CURRENT_CHANGE.md` at the project root for the current proposal.
> Do not apply, merge, or resume requirements/tasks from this document unless
> the user explicitly requests this historical change. Task counts are historical.

# Design

## Context

Xem proposal.md — Why. Khảo sát source tại commit `0a6ca46`, ngày 23/10/2026; đây là thiết kế đề xuất, chưa phải chức năng đã triển khai.

- [PaymentProvider.java:8](<D:/PersonalProject/antigravity sport-court-booking/sport-court-booking-system/backend/payment-service/src/main/java/com/sporthub/payment/provider/PaymentProvider.java:8>) chỉ trả reference; [DemoPaymentProvider.java:15](<D:/PersonalProject/antigravity sport-court-booking/sport-court-booking-system/backend/payment-service/src/main/java/com/sporthub/payment/provider/DemoPaymentProvider.java:15>) chỉ chạy local/demo. [PaymentService.java:47](<D:/PersonalProject/antigravity sport-court-booking/sport-court-booking-system/backend/payment-service/src/main/java/com/sporthub/payment/service/PaymentService.java:47>) gọi provider trong transaction tạo order; không thể chỉ thay tên DEMO thành PAYOS rồi dùng cho ngân hàng.
- [PaymentCallbacks.java:23](<D:/PersonalProject/antigravity sport-court-booking/sport-court-booking-system/backend/payment-service/src/main/java/com/sporthub/payment/service/PaymentCallbacks.java:23>) đã có kiểm callback, identity, dedup, canonical obligation và outbox. [VerifiedCollections.java:22](<D:/PersonalProject/antigravity sport-court-booking/sport-court-booking-system/backend/payment-service/src/main/java/com/sporthub/payment/service/VerifiedCollections.java:22>) lại phân loại tiền qua capability của movement adapter; nhánh thu thật cần căn cứ chứng từ payOS riêng.
- [TransferService.java:34](<D:/PersonalProject/antigravity sport-court-booking/sport-court-booking-system/backend/transfer-service/src/main/java/com/sporthub/transfer/service/TransferService.java:34>) luôn tạo DemoTransferTerms, bỏ qua beneficiary đầu vào; [listing-form.tsx:60](<D:/PersonalProject/antigravity sport-court-booking/sport-court-booking-system/frontend/features/transfers/listing-form.tsx:60>) nói không cần tài khoản ngân hàng. [SellerPayoutAccounts.java:18](<D:/PersonalProject/antigravity sport-court-booking/sport-court-booking-system/backend/payment-service/src/main/java/com/sporthub/payment/service/SellerPayoutAccounts.java:18>) chỉ nhận maskedDestination và yêu cầu adapter xác minh, không có BIN/STK dùng để chuyển khoản thủ công.
- [TransferFeeTerms.java:9](<D:/PersonalProject/antigravity sport-court-booking/sport-court-booking-system/backend/sporthub-common/src/main/java/com/sporthub/common/money/TransferFeeTerms.java:9>), [Payment V10:3](<D:/PersonalProject/antigravity sport-court-booking/sport-court-booking-system/backend/payment-service/src/main/resources/db/migration/V10__verified_settlement_and_movement_schema.sql:3>) còn overload/constraint legacy 200bps. Nhánh mới phải dùng policy Owner; không đưa dữ liệu REAL mới vào giao thức legacy này.
- [BookingLifecycle.java:43](<D:/PersonalProject/antigravity sport-court-booking/sport-court-booking-system/backend/booking-service/src/main/java/com/sporthub/booking/service/BookingLifecycle.java:43>) đã kiểm đủ tiền khi CHECKED_IN. [TransferLeaseService.java:116](<D:/PersonalProject/antigravity sport-court-booking/sport-court-booking-system/backend/booking-service/src/main/java/com/sporthub/booking/service/TransferLeaseService.java:116>) ghi quyết định handoff rồi gọi finance DEMO cùng transaction; đây là ranh giới nguyên tử cần giữ.
- [Booking V16:12](<D:/PersonalProject/antigravity sport-court-booking/sport-court-booking-system/backend/booking-service/src/main/resources/db/migration/V16__demo_facility_money.sql:12>) có settlement/refund/event DEMO với identity bất biến. [BookingDemoFinance.java:104](<D:/PersonalProject/antigravity sport-court-booking/sport-court-booking-system/backend/booking-service/src/main/java/com/sporthub/booking/service/BookingDemoFinance.java:104>) hiện hoàn tất chỉ bằng outcome; không thể dùng lệnh đó cho chi tiền thật.
- [payment-page.tsx:94](<D:/PersonalProject/antigravity sport-court-booking/sport-court-booking-system/frontend/features/booking/payment-page.tsx:94>) và [counter-payment.ts:24](<D:/PersonalProject/antigravity sport-court-booking/sport-court-booking-system/frontend/features/operations/counter-payment.ts:24>) tự gọi demo/complete. [ReportPaymentModes.java:88](<D:/PersonalProject/antigravity sport-court-booking/sport-court-booking-system/backend/booking-service/src/main/java/com/sporthub/booking/service/ReportPaymentModes.java:88>) loại NON_DEMO; báo cáo cần mở rộng theo cohort, không chỉ đổi nhãn.
- Main specs hiện trống. Change DEMO chưa sync có 6 capability nghiệp vụ liên quan; change mới giữ đường dẫn, bổ sung requirement REAL với tên khác. Quy tắc miễn bank proof chỉ thuộc DEMO; không sửa/xóa kế hoạch lịch sử trong bước này.

## Goals / Non-Goals

**Goals:** một tích hợp thu payOS cho nhiều cơ sở, quyền được cấp từ tiền vào có xác thực, nghĩa vụ trả/hoàn qua VietQR có audit và phân quyền, dữ liệu REAL tách DEMO, không mở transaction sân để chờ mạng.

**Non-Goals:** gọi API chi hộ, giữ tiền/escrow SportHub, ví nội bộ hoặc nguồn tiền phải nạp, xác minh tự động chủ tài khoản A, tự động đọc app ngân hàng của Owner, thêm provider thứ hai, thẻ/chargeback, đổi kiến trúc microservice hay tuyên bố toàn hệ thống đã production-ready chỉ vì có QR.

## Decisions

### D1. Hai loại xác nhận và một mô hình tiền của cơ sở

Thu tiền: `BANK_VERIFIED` từ adapter payOS đã xác thực và đối chiếu. Chi/hoàn: `MANUALLY_RECORDED` sau lệnh của Owner/Staff có quyền và mã giao dịch bắt buộc. QR được render, mã giao dịch người dùng nhập và trạng thái ngân hàng là ba dữ kiện khác nhau; chỉ loại đầu tiên được dùng để chứng minh tiền khách đã trả.

B trả P vào cơ sở. Với feeBps Owner cấu hình, F=HALF_UP(P×feeBps/10000), N=P−F. F thuộc cơ sở; không có mức 2% mặc định/bắt buộc hoặc phí SportHub. Giữ policy hoàn hủy r hiện có, dùng H=HALF_UP(S×rateBps/10000); không trừ thêm x trên H. `feeBps` là phần giữ lại khi trả A, `rateBps` là phần khách được hoàn khi hủy; không tự đổi tên trường thành tỷ lệ đối nhau hoặc gộp hai policy đã chốt.

Tái dùng Money/TransferFeeTerms có tham số policy và phép chia nhóm hiện có. 0%/100% là cấu hình tường minh; N/H bằng 0 có quyết định NOT_REQUIRED/NO_REFUND, không dựng QR chuyển 0 đồng. A vẫn phải khai ngân hàng trước khi đăng tin theo yêu cầu người dùng, kể cả N dự kiến bằng 0.

Phương án tự động chi qua movement worker bị loại khỏi scope đã chọn. Bank reference thủ công không được chuyển thành chứng từ payOS hoặc cập nhật provider callback.

### D2. Kết nối payOS theo cơ sở và bảo vệ credential

Chốt khi apply: API `/confirm-webhook` không trả BIN. Owner chọn BIN từ danh mục khi cấu hình; server lấy STK/tên nhận từ API này. BIN chỉ là cấu hình khai báo cho tới khi đối chiếu với phản hồi tạo link có chữ ký: BIN/STK lệch thì không công bố QR, chuyển REVIEW và chặn áp dụng. Không tạo đơn thu thử chỉ để xác minh kênh; mẫu webhook không tạo chứng từ tiền.

Payment giữ revision kênh nhận tiền: facilityId, receiverOwnerId, provider PAYOS, bank BIN/STK/tên nhận do kênh trả về, credential mã hóa, version, trạng thái PREPARING/READY/DISABLED. Owner cấu hình cơ sở mình; Staff không được đổi kênh dù có quyền chi/hoàn. Secret chỉ gửi vào server qua HTTPS, không trả về GET, log, broker hoặc frontend bundle. Khóa mã hóa lấy từ môi trường triển khai; thiếu khóa/credential/kênh hợp lệ thì fail closed, không fallback DEMO.

Xác thực kết nối bằng credential và xác nhận webhook của kênh; đối chiếu thông tin tài khoản trả về với kênh định gắn. Lưu trạng thái probe, không dùng webhook mẫu làm tiền thật. Các bước xác thực cá nhân/ngân hàng của payOS thực hiện tại payOS; SportHub không thu CCCD hoặc mật khẩu ngân hàng. Theo [payOS API](https://payos.vn/docs/api/), kênh có API tạo link trả QR, truy vấn link, hủy link và xác nhận webhook; số tiền VND nguyên. Đây là API của kênh nhận tiền, không phải chọn STK tùy ý cho từng order.

Mỗi facility dùng kênh riêng cho đợt đầu. Một Owner có nhiều cơ sở vẫn chọn đúng kênh của từng cơ sở; chưa hỗ trợ dùng chung một kênh giữa nhiều cơ sở để tránh thay webhook/routing của nhau. Channel có ID ổn định; revision receiver/credential đã dùng bởi payment giữ context lịch sử cần đối soát. Xoay key cùng kênh không đổi URL webhook; đổi tài khoản nhận dùng kênh mới, không chuyển order cũ sang tài khoản mới. Owner/người nhận trong nghĩa vụ đã chốt không đổi theo cập nhật profile.

### D3. Khởi tạo order có thể phục hồi và không giữ khóa khi gọi payOS

Tái dùng payment_orders/payment_obligations/payment_requests. Mở rộng provider contract bằng context server đã chốt và kết quả có native reference/QR/link/deadline; vẫn chỉ triển khai PAYOS và DEMO local, không tạo framework plugin/provider tổng quát.

1. Đọc payable và context cơ sở có xác thực trước transaction Payment. Server xác định booking/member/acquisition, payer ứng dụng, facility/Owner, mode, amount/currency, policy, receiver revision và hạn; browser không chọn provider hoặc người nhận.
2. Transaction ngắn khóa obligation, kiểm key/replay, lưu payment PENDING với reference nội bộ ổn định và QR session INITIALIZING; cấp orderCode nguyên duy nhất trong phạm vi kênh từ database, không dùng giây hiện tại làm khóa duy nhất. Financial identity giữ bất biến. Native paymentLinkId được lưu ở session để không phải sửa provider_reference bị trigger bảo vệ.
3. Gọi payOS ngoài transaction/khóa bằng orderCode đã lưu. Lưu kết quả trong transaction ngắn có compare/version. Worker và request cùng tranh một claim; timeout tạo CREATE_UNKNOWN và truy vấn chính orderCode trước khi gửi/tạo mới. Khi provider xác định chưa có đơn và deadline còn hiệu lực mới retry cùng business identity.
4. QR chỉ dùng khi session READY, đúng kênh/người nhận/số tiền và còn hạn. expiredAt không vượt hold/acquisition và cutoff chuyển nhượng; khởi tạo quá hạn không gia hạn sân. Hủy link chỉ đóng yêu cầu thanh toán, không tự hoàn tiền ngân hàng đã nhận.

returnUrl/cancelUrl/webhook URL dựng từ origin cấu hình, không nhận URL client; checkoutUrl chỉ chấp nhận origin payOS được cho phép. GET của ứng dụng chỉ đọc database; refresh là POST rate-limited đặt lịch query, không đánh dấu SUCCESS.

Nội dung thu payOS dùng mã tham chiếu lưu bền vững `SH` + orderCode dạng base36 viết hoa đủ 7 ký tự (ví dụ `SH0000001`), luôn đúng 9 ký tự ASCII chữ/số, không dấu/khoảng trắng/PII. Giới hạn sequence là 36^7−1; hết dải thì chặn tạo mới, không cắt chuỗi hoặc tái sử dụng mã. Cách này đáp ứng cả kênh VA giới hạn 9 ký tự lẫn kênh giới hạn 25; replay/query/cancel luôn dùng cùng orderCode và description. Kiểm boundary, collision/concurrency và chữ ký trên đúng nội dung gửi đi.

Projection cấu hình kênh chỉ có các cờ `credentialsConfigured`, trạng thái và receiver được che. `apiKey`/`checksumKey` là write-only, không trả cả giá trị đầy đủ lẫn một phần qua API Payment, Facility, Admin, exception hoặc request echo; `clientId` cũng không cần trả. Không serialize entity chứa credential hoặc ciphertext. Log HTTP không ghi body/header nhạy cảm.

Dùng RestClient, Jackson, HMAC và timeout hiện có cho API hẹp này; không thêm SDK/HTTP stack trùng lặp. Canonicalization/chữ ký phải kiểm bằng fixture chính thức và negative tests, không chuyển webhook payOS thành callback DEMO rồi ký lại.

### D4. Webhook bền vững và đối soát thu tiền

Public POST theo kênh `/payments/providers/payos/{channelId}/webhook` qua Gateway; chỉ endpoint này bỏ bearer, vẫn xác thực chữ ký tại Payment. ChannelId định tuyến credential hợp lệ của kênh; orderCode/nativeLink chọn receiver revision đã chốt. Xoay key giữ URL và khả năng đối soát order cũ; callback retry ký bằng key cũ được kiểm theo revision lịch sử còn được phép, rồi vẫn query/đối chiếu provider bằng credential hiện hành. Dùng nguyên cấu trúc native để kiểm HMAC của `data`, success/code và các trường binding; không dùng giới hạn ±5 phút của DemoPaymentProvider cho transactionDateTime ngân hàng. [Quy tắc signature payOS](https://payos.vn/docs/tich-hop-webhook/kiem-tra-du-lieu-voi-signature/) yêu cầu sắp dữ liệu và chuẩn hóa giá trị đúng quy định.

Sau xác thực, lưu quan sát bất biến với channel/reference/orderCode/paymentLinkId, số tiền, tài khoản nhận, thời điểm và body hash; 2xx chỉ sau commit nhận bền vững. Payload sai chữ ký bị từ chối. Mẫu đăng ký hoặc đơn không thuộc SportHub không tạo order/receipt/quyền; payload có chữ ký nhưng xung đột vào quarantine/REVIEW, có thể ACK sau lưu để không lặp vô hạn. Không log full account, credential hoặc QR.

Worker query payOS ngoài transaction để đối chiếu khoản thu/amountPaid và binding kênh của chính order. Webhook mất/chậm được khôi phục bằng query định kỳ cho session pending/unknown và cửa sổ đơn hết hạn; có backoff/jitter, ngân sách mỗi kênh, timeout và xử lý 429. Không để mỗi lần frontend poll phát sinh query ngân hàng. Webhook retry, query và replay cùng đi qua một processor idempotent.

Mỗi bank reference trong cùng receiving channel chỉ ghi tiền một lần. Canonical settlement có unique theo obligation; các lần thu thật dư/trùng vẫn được lưu như tiền cần xử lý, không bị bỏ vì obligation đã settled. Cập nhật collection chuẩn, payment SUCCESS, audit và outbox trong một transaction; consumers dùng event v2 chứa mode/receiver revision/proof identity, vẫn đọc v1 cho legacy/DEMO. Evidence REAL căn cứ adapter thu tiền, không phụ thuộc capability của adapter chi hộ.

Thu nhiều lần thiếu: cộng các giao dịch không trùng đã đối chiếu đúng order; chưa đủ thì không SUCCESS/cấp quyền. Khi tổng đạt due, chỉ áp dụng due một lần; phần vượt due là khoản phải hoàn kỹ thuật riêng, không thêm doanh thu/phí. Nếu provider không cung cấp được chi tiết đủ phân biệt các giao dịch, giữ REVIEW thay vì tự cộng số tổng không chứng minh được. paidAt áp dụng là lúc đủ tiền theo evidence ngân hàng, receivedAt/verifiedAt ghi riêng; timezone không lấy từ máy client.

FAILED/CANCELLED/EXPIRED của link không chứng minh không có tiền vào. Giao dịch đến sau expiry hoặc một kết quả cuối trái ngược vẫn lưu bằng chứng và nghĩa vụ xử lý, không sửa lịch sử final order bị trigger bảo vệ. Trạng thái thu, áp dụng booking và hoàn trả được hiển thị riêng.

### D5. Chỉ cấp quyền từ collection REAL hợp lệ

Booking/nhóm chốt mode REAL khi tạo mới ở triển khai LIVE; Payment/acquisition kế thừa mode, không cho browser hạ về DEMO. Receipt local phải có proof identity và money class đúng cohort, amount/currency/facility/Owner/channel/obligation khớp; dùng outbox/inbox và lớp collection local để không gọi mạng dưới court lock. Consumer không chấp nhận một chuỗi `moneyClass=REAL` tự khai trong v1/client như chứng từ ngân hàng.

Booking PENDING chỉ CONFIRMED khi thu đủ và reservation còn hợp lệ lúc áp dụng. Nhóm chỉ CONFIRMED sau tổng contributions thực áp dụng bằng nghĩa vụ, không cộng một payment hai lần hoặc dùng DEMO bù REAL. Giữ payer/holder/createdBy tách biệt. Counter dùng BANK_QR thay lệnh demo/complete trong LIVE: operator hiện có quyền mở QR cho Guest/linked Customer quét bằng ngân hàng; payer ứng dụng không chứng minh ngân hàng của người scan thuộc Staff, không tạo Customer giả hoặc trả tiền cho Staff mặc định.

Giữ guard đủ tiền tại lifecycle và mọi QR/mã thủ công/HTTP trực tiếp, BOOKING_CHECK_IN đúng cơ sở, cửa sổ startsAt−30 phút đến trước endsAt, transfer lock, token version và CONFIRMED→CHECKED_IN→COMPLETED. Không phát QR sử dụng cho pending/chưa handoff. QR thu tiền, QR chuyển khoản hoàn trả và QR vào sân có nhãn/route/mục đích riêng.

Nếu tiền hợp lệ nhưng giữ sân đã hết hạn/đã cấp cho người khác, không tự phục hồi booking hoặc kéo dài lease. Lưu khoản thu chưa áp dụng và nghĩa vụ hoàn kỹ thuật đủ tiền đã nhận. Có thể trả tiền sau thời gian chơi từ nghĩa vụ đã chốt; không cho sử dụng sân đã mất quyền. Giữ hạn server hiện hữu; UI hiển thị đúng rủi ro đến muộn, không hứa thanh toán trước hạn luôn được áp dụng nếu hết lease trước khi xử lý.

### D6. Thông tin ngân hàng của người nhận

Payment sở hữu customer_bank_destination_revisions: principal Customer hoặc Guest booking reference, BIN, STK mã hóa, masked label, revision, consent/actor/time, trạng thái hoạt động. STK là string số không rỗng, tối đa19 ký tự và giữ số0 đầu; BIN6 chữ số trong danh mục hỗ trợ chuyển khoản. Đợt đầu không nhận số thẻ, alias/VA hoặc tự đoán ngân hàng. Tên chủ tài khoản có thể nhập để đối chiếu, không bắt buộc và không được gọi là bank-verified.

Tái dùng service authentication và ownership để đọc destination phục vụ quote/chuẩn bị QR; không biến SellerPayoutAccounts legacy masked token thành tài khoản thật, không giả kết quả verify của movement adapter. Có thể tái dùng phần audit/version phù hợp; không kích hoạt beneficiary/KYC/funding pipeline cũ.

A nhập/chọn tài khoản của mình ngay trong form đăng/sửa tin. Server quote/create/edit bắt buộc destination revision hợp lệ thuộc A, kể cả gọi HTTP bỏ qua FE; chốt revision ID/hash vào terms. Màn hình chợ/B chỉ cần thông tin phí và cơ sở, không trả BIN/STK/seller destination qua DTO công khai. Listing chưa khóa được đổi ngân hàng bằng revision mới + requote/reconsent; acquisition đã khóa không tự đổi theo profile.

Khách yêu cầu khoản hoàn dương của mình cung cấp destination của đúng người được hoàn. Với nhóm là từng payer của allocation, không tài khoản organizer thay mọi thành viên. Hủy nhóm không bắt organizer khai STK người khác: mỗi allocation chưa có destination ở AWAITING_BANK_DETAILS để chính payer bổ sung; không trì hoãn quyết định hủy hoặc nghĩa vụ của thành viên khác. Guest cung cấp BIN/STK tại cơ sở; operator có BOOKING_REFUND ghi nhận theo Guest booking với audit/consent, không tự chọn STK Staff.

Nghĩa vụ tự sinh do lỗi kỹ thuật/timeout vẫn được tạo ngay dù chưa có ngân hàng nhận, ở AWAITING_BANK_DETAILS. Người được hoàn bổ sung sau, không mất quyền hoàn. Thay tài khoản cho nghĩa vụ chưa trả dùng revision mới có consent của payee, lý do và version fence dưới khóa nghĩa vụ; không đổi số tiền/người hưởng. Không sửa bản ghi đã hoàn tất. QR cũ ngoài app ngân hàng không thể bị SportHub vô hiệu hóa; người thao tác phải thấy phiên bản hiện tại và cảnh báo không chuyển lại nếu đã chuyển theo bản cũ.

### D7. Bàn giao và nghĩa vụ chi cùng một quyết định

Tạo FacilityTransferTerms REAL có version protocol riêng: các identity hiện tại, Owner policy P/F/N/r, receiving channel revision, seller destination revision và termsHash; tái dùng phép tính/handoff fingerprint. Không đi vào nhánh legacy cố định 200bps hoặc coi REAL là DemoTransferTerms. A/B xem và chấp nhận phí cơ sở/giá/số nhận tương ứng trước khi khóa/thanh toán.

Payment canonical REAL đủ P → transfer.collection.verified → PENDING_HANDOFF. TransferLeaseService chỉ complete từ collection đúng acquisition và lease hợp lệ; cùng court transaction cập nhật holder B, reservation holder, token version, transfer_handoff, quyết định COMPLETED và một facility seller payable N/F. B có quyền sau commit, dù N UNPAID/PREPARED/REVIEW/MANUALLY_RECORDED; trạng thái N không tham gia guard quyền B.

Complete và abort dùng decision fence hiện có. Timeout/response mất sau commit phải đọc lại quyết định, không tạo refund vội. ABORTED có collection tạo nghĩa vụ hoàn kỹ thuật đủ P cho B và F/N=0; late collection cũng đi vào quyết định đó. Acquisition đã ABORTED không complete/relock muộn. N=0 thì NOT_REQUIRED, không QR chi. Hủy sau COMPLETED không đảo F/N hoặc hoàn thêm tiền gốc cho A.

### D8. Chuẩn bị VietQR và hoàn tất bằng mã giao dịch

Booking giữ facility_transfer_settlements, facility_refund_entitlements/allocations và facility_disbursement_operations của cohort REAL. Đây là sổ nghiệp vụ theo cơ sở, không phải tài khoản tiền SportHub. Phí/nghĩa vụ ghi cùng quyết định sân để không tạo khoảng trống giữa cấp quyền và khoản phải trả.

Owner hoặc Staff active có TRANSFER_PAYOUT/BOOKING_REFUND đúng cơ sở mở thao tác trả/hoàn. Server tạo/replay một operation PREPARED cho đúng settlement/allocation, lấy destination đã chốt, số còn phải trả và reference chuẩn từ DB. Chỉ một operator có claim hoạt động để hiển thị QR thực hiện; operator khác thấy đang do ai xử lý. UI mở modal tự prepare, không thêm bước duyệt nhiều cấp.

QR dựng tại server: payload VietQR chuyển vào tài khoản (BIN/STK, VND, amount, nội dung), encode bằng ZXing hiện có, không encode một UUID tùy ý rồi gọi là mã ngân hàng. Nội dung ASCII không dấu, tối đa 25 ký tự, ví dụ `SH PA <CODE>` / `SH HO <CODE>`; CODE sinh duy nhất có ràng buộc database, ổn định khi tải lại cùng operation. Không chèn tên/số điện thoại khách. Trả PNG/SVG/data URL từ endpoint có auth, private no-store; không đưa STK vào URL public hoặc gọi ảnh QR từ bên thứ ba.

Danh mục BIN lấy [API Banks chính thức](https://www.vietqr.io/api-bank), cache server làm mới theo ngày và phục vụ FE cùng origin. Tạo QR offline giúp giữ dữ liệu ngân hàng riêng và tránh API Generate: [thông báo VietQR ngày 14/10/2026](https://www.vietqr.io/thong-bao-cap-nhat) cho biết Generate ngừng từ 01/01/2027. Không thêm phụ thuộc vào endpoint này hoặc thư viện chưa xác minh; encoder VietQR hẹp dùng TLV/CRC chuẩn, đối chiếu payload chuẩn độc lập và scan bằng app ngân hàng trong nghiệm thu.

Modal hiển thị ngân hàng/STK đích, số tiền, nội dung, loại nghĩa vụ và nhãn Chưa xác nhận chuyển khoản. Operator quét bằng app ngân hàng rồi điền `bankTransactionReference`, bấm **Hoàn tất chuyển khoản**. API chỉ nhận operation/version/mã giao dịch và Idempotency-Key; amount/payee/BIN/STK/mode do server lấy, không có `outcome=SUCCESS` của LIVE.

Transaction hoàn tất khóa nghĩa vụ/operation, kiểm quyền hiện tại, claim/version/destination, mã tham chiếu 1–120 ký tự được kiểm tra và chuẩn hóa; lưu nguyên bản nhập lẫn giá trị dedup. Unique theo nguồn chi của cơ sở + bank reference, kiểm chéo các loại payout/refund; khi chưa phân biệt được nguồn chi thì dùng scope Owner bảo thủ và đưa va chạm REVIEW, không vượt bằng đổi key. Một operation chỉ hoàn tất một lần; cùng key/body trả receipt cũ, body khác bị từ chối. Không sửa/xóa receipt thành công. UI chỉ nhập mã giao dịch; nguồn chi mặc định lấy context tài khoản cơ sở, không đòi nhập lại credential ngân hàng.

Ghi status PAID/REFUNDED kèm `confirmationMethod=MANUALLY_RECORDED`, confirmedAt, performedBy, bank reference, destination hash và amount trong cùng transaction với money event/capacity. UI ghi **Cơ sở xác nhận đã chuyển**; thời điểm báo cáo là confirmedAt, không giả là bank settledAt. QR generation/claim/GET không tạo PAID/REFUNDED.

Mất response sau hoàn tất: replay key cũ/đọc lại, không yêu cầu chuyển lần hai. Operator báo đã chuyển nhưng mã còn thiếu/không rõ: giữ REVIEW/claim, Owner xử lý từ giao dịch đã có; không tự hết hạn claim để operator khác chuyển lại. Chỉ release/reprepare với lý do xác định chưa chuyển; ghi audit và kiểm version. Thu hồi quyền Staff có hiệu lực ở confirm/retry; Owner vẫn có thể tiếp nhận ghi nhận giao dịch đã chuyển, không ra lệnh chuyển lại.

Khóa ứng dụng chỉ hạn chế thao tác trùng trên SportHub; không thể ngăn hai lần chuyển độc lập ngoài app. Khi phát hiện tiền chi trùng/sai người, ghi case điều chỉnh/audit, không xóa lịch sử, tự thu hồi tiền hoặc tự trả thêm. Không cần tích hợp ngân hàng chi để nghiệm thu chức năng thủ công này.

### D9. Hoàn theo policy và các khoản tự sinh

Tái dùng quote/cancel math, policy snapshot, ownership version và capacity theo payment. Khách chủ động yêu cầu H>0 phải cung cấp destination đúng payee trước khi submit; trường hợp tự sinh thiếu destination vẫn có entitlement AWAITING_BANK_DETAILS. H=0 không tạo QR/lệnh chi. Khách không được tự gọi hoàn tất tiền, A không tự đánh dấu đã được trả.

Trước resale, S là tiền court/contribution thực đã áp dụng; sau resale S là P của buyer cuối đã nhận quyền, hoàn buyer đó. Group phân theo payment/payer bằng largest remainder; mỗi allocation có destination/QR/receipt riêng. Hoàn một allocation không đánh dấu cả nhóm đã hoàn; lỗi hoặc thiếu STK của một người giữ riêng phần đó. Guest không tự trở thành Staff payee vì payment payer kỹ thuật.

Hủy trước chơi quyết định nguyên tử với check-in/handoff; CANCELLED không dùng sân dù tiền hoàn chưa xong. Policy H không tạo thêm phí x; N trả A và H trả B độc lập. Full technical refund/overpayment/thu trùng/tiền đến muộn là dòng tiền trả lại khoản chưa áp dụng, không dùng policy r, không tạo phí hoặc doanh thu mới. Mọi đường hoàn chia sẻ một capacity từ giao dịch thu thật, gồm phần đang PREPARED/REVIEW để không hứa hoàn vượt số có thể hoàn.

### D10. Database và ranh giới lưu trữ

Chỉ migration bổ sung trong apply; không sửa V1–V16 Booking, V1–V14 Payment hoặc migrations Transfer đã phát hành.

- **Payment:** channel revisions có encrypted secrets; QR sessions một payment một session và unique(channel,orderCode/nativeLink); bank observations có unique(channel,native transaction reference), immutable body/fingerprint và trạng thái xử lý riêng; bank destination revisions mã hóa. Tái dùng order/obligation/callback review/verified_payment_collections/outbox. Mở rộng canonical proof cho tổng thu so với phần áp dụng; giữ phần dư để hoàn/đối soát.
- **Booking:** collection REAL local liên kết receipt/payment, expected receiver và moneyClass; mode của booking/nhóm; facility_transfer_settlements một acquisition/một canonical payment, P=F+N, feeBps 0..10000, F round đúng; refund entitlements + allocations theo business source/payment/payee; disbursement operations + append-only receipts với business uniqueness và claim/version. N/H=0 lưu ở parent, không tạo operation amount0.
- **Transfer:** terms/protocol/mode/receiver/destination revision trong snapshot bất biến, giữ listing/acquisition price/deadline hiện có. Tạo event v2 và giữ parser v1 theo cohort cũ. Bank full details không vào broker/public listing snapshot.
- FK thực chỉ trong database sở hữu; liên kết cross-service là UUID/version/hash được kiểm qua giao tiếp service hiện có, không tạo FK xuyên database. Index worker theo state/nextAttemptAt, report theo facility/mode/effectiveAt và unique bắt nghiệp vụ.

Chọn bảng nghiệp vụ REAL trung tính, bổ sung cạnh dữ liệu DEMO có trigger bất biến; không đổi hàng DEMO thành REAL hoặc nới trigger chỉ để nhận tiền thật vào bảng có tên demo. Tách persistence cohort nhưng tái dùng phép tính/quyền/quyết định/report assembler; không sao chép toàn bộ finance engine hay mở ledger/funding/escrow legacy cố định2%. Nếu tái dùng schema sẵn có phải đáp ứng đầy đủ mode/owner fee/manual confirmation và không làm hai bảng cùng sở hữu một nghĩa vụ.

### D11. API và Frontend

Đường dẫn đề xuất có prefix `/api/v1`; cập nhật OpenAPI và type FE trong apply. Giữ GET cũ/DTO trường hiện có; trường bổ sung nullable theo cohort lịch sử, trường bắt buộc REAL kiểm tại server.

- Payment: GET/PUT `/payments/facilities/{id}/channel` cho Owner; POST `/.../verify` và disable có version; Customer GET/POST `/payments/bank-destinations`, revision chỉ của mình; Guest destination qua operator đúng booking/quyền. Không có endpoint public STK A.
- `POST /payments` vẫn tạo theo booking/member/acquisition và Idempotency-Key. GET detail thêm collection status, QR session/readiness, receiver label, mode và application status. GET `/{id}/qr` chỉ trả instruction; POST `/{id}/refresh` đặt query cùng order, có rate limit/quyền payer/operator hiện hành.
- Transfer quote/create/edit nhận destination revision; public/acquisition DTO không lộ bank details. B vẫn tạo payment từ acquisition, không bị ép khai tài khoản nhận tiền chỉ để mua.
- Booking: giữ cancellation-quote/cancel-with-policy nhưng thêm destination binding theo allocation REAL; endpoint scoped `/bookings/facility-finance/...` đọc nghĩa vụ, prepare QR, complete manual operation và tiếp nhận REVIEW. Owner/Staff chỉ các phần được grant; Customer chỉ nghĩa vụ của mình và cập nhật destination được phép.
- LIVE không public demo callback/complete/finance outcomes; DEMO endpoint chỉ local/demo và chỉ record DEMO. Gateway chỉ mở public chính xác webhook payOS, không cả `/payments/**` hay internal routes.

FE dùng chung một payment instruction view cho online, quầy, nhóm và acquisition; hiển thị QR ngân hàng, tên cơ sở/tài khoản nhận, số tiền, nội dung, hạn và liên kết checkout payOS trên thiết bị đang dùng. Không có nút Tôi đã trả tiền tự cấp SUCCESS. Phân biệt đang tạo QR, chờ nhận tiền, đã nhận đang áp dụng, handoff đang đồng bộ, đã cấp quyền, hết hạn/cần hoàn và cần đối soát.

Poll backend bằng usePolling hiện có, không chồng request, dừng khi ẩn/unmount và làm mới khi trở lại. Deadline dựa server; kết thúc ngân sách poll chỉ dừng auto-refresh, không kết luận giao dịch ngân hàng thất bại hoặc tạo order khác. Không giữ trần DEMO15 giây như hạn thanh toán LIVE. Refresh trang/re-login mở lại cùng session đang chờ; lỗi401/403 không tự replay mutation.

Form seller/refund có chọn ngân hàng tìm theo tên/BIN, STK text giữ số0, lỗi tiếng Việt và chấp nhận snapshot. Modal operator có QR, nút copy thông tin, field mã giao dịch bắt buộc, claim/permission state và chống double-click; dữ liệu bank không cache toàn cục. Tách payment QR/VietQR chi/QR vào sân bằng nội dung dễ hiểu. Giữ module một trách nhiệm, mục tiêu ≤350 LOC logic/ngưỡng500 và boundary lazy-load sau refactor, không đưa chart/bank catalog/QR tooling vào mọi route.

### D12. Báo cáo và giám sát

Mở rộng report snapshot/mode assessment/assembler để chọn REAL hoặc DEMO rõ, default REAL ở LIVE; UNKNOWN không gộp tự động. Một bộ range/timezone/cutoff cho KPI/chart/sân/giao dịch như hiện có. Trạng thái nhận tiền và trạng thái áp dụng là hai cột riêng; collection chưa áp dụng nằm dòng tiền/đối soát, chưa phải R hay F.

R là court/contributions REAL đã áp dụng; P là thu resale, F chỉ phát sinh handoff COMPLETED, N là nghĩa vụ trả A. H policy chỉ trừ khi cơ sở đã xác nhận chuyển đủ allocation; technical refunds/overpayment tách riêng. Công thức cơ sở R+F và sau hoàn R+F−H giữ nguyên; N/P không thêm vào doanh thu, SportHub không hưởng F. Hiển thị rõ phần H/N được xác nhận thủ công, không gọi là tổng đã đối soát ngân hàng.

R dùng paidAt lúc đủ nghĩa vụ của collection đã áp dụng, không ngày xử lý lại event. Dòng tiền P và tiền chưa áp dụng dùng thời điểm từng bank credit; tổng nhận tiền không tăng lại khi khoản thu được áp dụng. F dùng handoffAt; N/H dùng manual confirmedAt có nhãn Thời điểm cơ sở xác nhận. Công nợ tại cutoff gồm nợ trước kỳ, chỉ trừ confirmations trước cutoff. Report tách amountApplied/extra và tiền cần hoàn để không mất dòng tiền; refund khác kỳ không xóa thu gốc, GET không thực hiện chi hoặc query bank làm biến dữ liệu báo cáo.

Tái dùng PaymentEvidenceClient batch≤500 bên ngoài report transaction; data thiếu trả PARTIAL/UNAVAILABLE. Owner xem cơ sở mình; Staff quyền thao tác không tự có dashboard Owner; Admin theo dõi chỉ đọc với bank details/mã giao dịch nhạy cảm được che theo nhu cầu. Không có KPI doanh thu nền tảng hoặc quyền Admin đánh dấu thu đủ/thực hiện tiền thay Owner.

## Risks / Trade-offs

- [Mã giao dịch chi thủ công sai/giả] → chỉ operator được cấp quyền, nhãn MANUALLY_RECORDED, audit/capacity/unique reference; vẫn là xác nhận trách nhiệm cơ sở, không đảm bảo ngân hàng đã chuyển. Muốn tự xác minh tiền ra là change khác.
- [Hai người chuyển ngoài SportHub hoặc chuyển QR cũ] → một claim/version hiện hành, hiển thị người thực hiện, không tự release khi không rõ; có case đối soát, không hứa DB ngăn được chuyển độc lập trong app ngân hàng.
- [Sai STK do khách nhập] → validate format/BIN, khách xác nhận tài khoản, snapshot riêng, operator đối chiếu người nhận app ngân hàng; không giả rằng danh mục BIN xác minh chủ tài khoản.
- [payOS chậm/mất thông báo, tiền đến muộn] → raw receipt/query bền vững, retry cùng order, giữ guard reservation/decision, full technical entitlement nếu không áp dụng; không dùng nút duyệt thủ công để bypass receipt.
- [Credential/kênh ngân hàng thay đổi] → encrypted revisions, dữ liệu cũ bám revision cũ; disable chỉ chặn tạo mới, webhook/query/đối soát đơn đang có vẫn hoạt động. Không xóa key đang phục vụ receipt chưa rõ.
- [payOS không có sandbox riêng] → test tự động dùng mock HMAC/API với khóa synthetic, môi trường độc lập. [Tài liệu test payOS](https://payos.vn/docs/moi-truong-test/) yêu cầu giao dịch thực nếu kiểm end-to-end ngân hàng; đợt thử giá trị nhỏ chỉ khi người dùng chủ động cấu hình và thực hiện, không chạy từ proposal hoặc CI.
- [Migration nhầm DEMO thành tiền thật hoặc bank PII lộ qua report] → additive/mode tường minh, preflight cohort, private projections và test fresh/upgrade; không backfill proof, bank info, phí hoặc mã giao dịch tưởng tượng.

## Migration Plan

1. Giữ `fix-owner-reports-and-revenue` là lịch sử DEMO và giữ change refactor; khi sync/archive sau này, requirement mới áp REAL, cũ áp DEMO. Không sync/archive trong proposal/apply này tự động.
2. Apply sau triển khai schema mở rộng trước với live creation tắt, enum/triggers/index cho cohort mới; chạy fresh/upgrade tests trên fixture riêng. Không rename/xóa dữ liệu hoặc migration cũ, không auto seed vào môi trường thật.
3. Thêm API/provider/worker/v2 consumers trước FE; legacy parser vẫn đọc v1. Ghi rõ real mode vào booking/acquisition mới; DEMO cũ tiếp tục nhãn DEMO, không dùng làm sân trả tiền thật/chợ LIVE hoặc bù contribution REAL. Pending cũ hoàn tất/đóng theo cohort cũ, không đổi phương thức giữa chừng.
4. Bật Owner channel setup, policy x/r, destination forms và operator QR/receipt; migration không tạo tài khoản ngân hàng hoặc grant Staff mặc định. Trước LIVE, Owner phải có kênh READY và policy; thiếu cấu hình chặn tạo khoản thu/new booking, không chặn xử lý khoản đã thu.
5. Test unit/integration/migration/contract/FE và E2E mock cả online/counter/Guest/nhóm/A→B→C, duplicate/partial/late/abort/claim/revoke/report. Không dùng kết quả DEMO cũ làm bằng chứng LIVE.
6. Nghiệm thu riêng trên HTTPS public với credential Owner được cấu hình ngoài chat: QR thật quét được, tiền vào đúng Owner, webhook/query phục hồi, B nhận quyền, A payout/refund QR đọc đúng và operator nhập mã giao dịch. Người dùng thực hiện giao dịch thử; lưu receipt ID/bằng chứng che thông tin, không hard-code giá trị nhỏ vào code sản phẩm. Nếu chưa thử thật thì báo chưa nghiệm thu ngân hàng, không đánh dấu live-ready.
7. Mở collection mới theo facility sau nghiệm thu; theo dõi webhook backlog/create unknown/unapplied collection/refund debt/duplicate manual reference. Rollback bằng tắt tạo live order/acquisition mới và giữ code/worker đọc chứng từ đang phát sinh; không rollback phá schema hoặc hạ xuống phiên bản không đọc cohort REAL, không xóa receipt để chạy lại.

## Open Questions

Không còn câu hỏi nghiệp vụ chặn kế hoạch. Domain HTTPS, credential/kênh payOS của từng cơ sở và tài khoản thử sẽ được cấu hình khi triển khai; không quyết định lại mô hình tiền, mức phí hay phạm vi chi thủ công.
