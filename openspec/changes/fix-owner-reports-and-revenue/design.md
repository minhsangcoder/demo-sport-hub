> **HISTORY ONLY — NOT AN ACTIVE REQUIREMENT SOURCE.**
> Read `openspec/CURRENT_CHANGE.md` at the project root for the current proposal.
> Do not apply, merge, or resume requirements/tasks from this document unless
> the user explicitly requests this historical change. Task counts are historical.

# Design

## Context

Xem proposal.md. Người dùng đã chốt đồ án DEMO: đủ nghiệp vụ/chức năng, giao dịch tiền được giả lập phía server; không cần chứng minh tiền vào ngân hàng rồi mới duyệt hoặc chuyển giai đoạn.

Source đã có DemoPaymentProvider và endpoint POST /api/v1/payments/{id}/demo/complete để mô phỏng SUCCESS/FAILED. Có thể tái dùng callback nội bộ, idempotency, outbox/inbox và receipts hiện có. Đây là biên nhận của ứng dụng, không phải bằng chứng ngân hàng. Fee policy theo cơ sở và quyền TRANSFER_PAYOUT đang làm dở có thể tái dùng; các module merchant/ledger/funding/bank execution không phải điều kiện nghiệm thu đồ án.

Các ràng buộc kinh doanh giữ nguyên: B thanh toán cho cơ sở, Owner hưởng x% và chịu trả phần còn lại cho A, Staff chỉ thực hiện khi được Owner cấp quyền. B nhận quyền sau thanh toán DEMO thành công và dùng theo lịch, không chờ trả A. Policy hoàn hủy r là cấu hình khác x. CHECKED_IN và mô hình Guest/linked Customer/payer hiện tại được giữ.

## Goals / Non-Goals

**Goals:** demo trọn luồng đặt/thanh toán/check-in, chuyển nhượng, trả A, hủy/hoàn B, lỗi/thử lại và báo cáo từ dữ liệu thật trong database. Tính tiền/permission/status/idempotency vẫn do server quyết định.

**Non-Goals:** thanh toán/chi/hoàn tiền thật; xác minh merchant, KYC/tài khoản ngân hàng/beneficiary, sao kê, kiểm tra khả dụng/giữ tiền, bank return/chargeback, quy trình duyệt nhiều cấp, region/key TTL/provider live acceptance hoặc hệ thống kế toán tài chính. Không xây thêm hạ tầng production để đạt DEMO. Không triển khai Excel/PDF và phân tích P2 trong change này.

## Decisions

### D1. DEMO là mô phỏng thực thi, không phải dữ liệu giao diện viết cứng

- Giao dịch mới mang mode/provider DEMO. Browser gọi thao tác được phép; server tự lấy amount/currency/payer/payee/facility/Owner từ booking/acquisition/policy.
- Lệnh giả lập chỉ thực hiện trên record DEMO do server xác định; client không được đổi mode của record REAL/legacy để đánh dấu đã trả/hoàn. Quyền chọn outcome mô phỏng vẫn theo payer/operator và scope nghiệp vụ.
- Chọn thành công tạo kết quả ứng dụng SUCCESS và biên nhận/history/event phù hợp trong database. Kết quả đó đủ cho phase tiếp theo; không có bước kiểm ngân hàng hoặc Owner duyệt thanh toán B.
- Chọn thất bại tạo kết quả FAILED, giữ trạng thái nghiệp vụ đúng. Có thể thử lại bằng lệnh mới hợp lệ khi chưa có kết quả thành công; bấm lặp cùng key trả kết quả cũ.
- Màn hình và báo cáo ghi “Dữ liệu DEMO — không phát sinh giao dịch tiền thật”. DEMO thành công được tính vào tổng demo đầy đủ; không bị coi thiếu tiền/UNKNOWN chỉ vì không có bank proof. Giữ metadata REAL/UNKNOWN cũ nếu đã có nhưng không bắt đồ án triển khai các chế độ đó.
- Tận dụng DemoPaymentProvider hiện có. Không tạo webhook/provider protocol mới cho ngân hàng. ServiceCalls, kiểm chữ ký nội bộ và outbox/inbox đã dùng có thể giữ vì là giao tiếp microservice, không là điều kiện chứng minh tiền thật.

### D2. Người nhận và chính sách theo cơ sở

Gắn receiverFacilityId/receiverOwnerId theo Facility/Booking đã đọc phía server. B thấy tên cơ sở nhận thanh toán DEMO, không tài khoản SportHub. Không yêu cầu owner đăng ký merchant, nhập bank account, xác minh tài khoản A hoặc nạp tiền để demo. A nhận theo sellerId của acquisition; Staff thực hiện không trở thành payee/holder.

Owner cấu hình x=transferFeeBps và r=refundRateBps độc lập, bps nguyên 0..10000, độ chính xác0,01%. Tái dùng policy version/hash/CAS/audit đã có để snapshot rõ ràng. Không seed2% hoặc áp tỷ lệ chung. Chưa có r chặn booking mới theo quyết định trước; chưa có x chỉ chặn chuyển nhượng mới. Explicit0% hợp lệ, không điều kiện bank kèm theo.

### D3. Công thức và snapshot

R là tiền sân đã thanh toán DEMO/áp dụng hợp lệ. P là giá B mua lại. F=HALF_UP(P×x/10000) nguyên VND, N=P−F; F thuộc cơ sở, N cơ sở nợ A. Tính F một lần rồi N phần dư, không float. x0→F0/N=P; x100→F=P/N0 với consent rõ, không giao dịch tiền0.

Listing/acquisition lưu facility/receivingOwner/seller/buyer/P/x/version/F/N/r snapshot/deadline và terms hash; không cần merchant/beneficiary token. A thấy và chấp nhận điều khoản khi đăng/sửa, B lúc mua. Đổi policy sau khi đã chốt không đổi giao dịch, retry không tính phí lại. Payable server là nguồn amount/binding, body client không chọn người nhận hoặc phí.

### D4. B thanh toán và nhận quyền

1. A đăng tin từ booking đủ tiền, holder đúng, transfer policy/lead time hợp lệ. A và B khác người; một acquisition khóa hợp lệ theo cơ chế hiện có.
2. B tạo payment từ payable server, bấm **Thanh toán DEMO**. P được ghi cho cơ sở nhận tiền, không thu thật.
3. SUCCESS ứng dụng/canonical obligation kích hoạt complete ngay. Booking commit holder B, quay credential và lưu receipt/handoff dưới court lock; Transfer ghi SUCCESS/outbox theo cơ chế đang có. Không gọi Owner authorize trả A trong nhánh này.
4. B có quyền dùng sân theo lịch sau commit. Nếu mạng làm event chậm, UI báo đã thanh toán/đang đồng bộ quyền và retry/query lệnh đã có, không thu lại P hoặc đợi Owner trả A. Không cấp QR cho B trước khi quyền đã commit.
5. Handoff thành công tạo một demo settlement: F cơ sở hưởng và N phải trả A. N dương ở UNPAID; N0 ở NOT_REQUIRED, không yêu cầu bank account hoặc payout0.

Payment thất bại thì không quyền/fee/nợ A/refund của tiền chưa thu. Handoff được ứng dụng xác nhận ABORTED sau SUCCESS thì tạo quyền hoàn đủ P/F0/N0. Giữ complete/abort fence đã có để response mất sau commit không bị hiểu thành thất bại; đây là nhất quán quyền booking, không quy trình đối soát ngân hàng.

### D5. Owner/Staff trả A bằng thao tác giả lập

Danh sách/chi tiết hiển thị cơ sở chịu trả, A, P/x/F/N và trạng thái UNPAID/PAID/FAILED/NOT_REQUIRED. Nút **Trả tiền DEMO cho A** chỉ có cho Owner hoặc active Staff TRANSFER_PAYOUT đúng facility. Mỗi POST kiểm quyền server từ request hiện tại; không authorization receipt nhiều bước, worker nền chờ quyền, duyệt Admin hoặc bank availability.

Server lấy N/payee/facility từ settlement, khóa bản ghi, kiểm chưa PAID, ghi event DEMO và cập nhật PAID+paidAt+performedBy cùng transaction. Kết quả này được coi đã trả trong thế giới DEMO ngay; không cần bank final proof. Tùy chọn giả lập FAILED giữ khoản còn nợ; thử lại chỉ phần chưa thành công. Double-click/key replay không tạo hai event thành công hoặc chi N hai lần. N0 không nút trả tiền.

Quyền TRANSFER_PAYOUT mặc định không cấp và tách BOOKING_REFUND. Owner cấp/thu hồi tại cơ sở. Staff không sửa x/N/A/receivingOwner, tự cấp quyền hoặc vào dashboard doanh thu Owner. Revoke trước thao tác mới bị từ chối; không cần protocol revoke-vs-bank-submit vì không có giao dịch bank bất đồng bộ.

B luôn giữ quyền đã nhận khi N chờ/lỗi; A không thể tự xác nhận Owner đã trả mình. Không cần tài khoản ngân hàng xác minh hoặc upload chứng từ ở màn A/Owner.

### D6. Hủy và hoàn DEMO

Quote hủy do server tính từ policy r đã chốt, trạng thái/quyền booking và các payment ứng dụng SUCCESS đã áp dụng:

- Chưa resale: S là tiền sân hợp lệ, không giá chưa thu. Group S=tổng contribution đã trả, phân H=HALF_UP(S×r/10000) về payer/payment gốc bằng largest remainder, tie paymentId, tổng allocations=H. Guest không tạo Customer giả hoặc trả Staff chỉ vì createdBy.
- Sau A→B: S=P của B, hoàn B. Sau A→B→C: S=P của C, hoàn C, không người đã bán tiếp. H không tự đảo F/N, hoàn R cho A hoặc phục hồi booking hủy.
- ABORTED trước handoff: hoàn đủ P, không áp x/r hoặc trừ phí. Lỗi trả N sau handoff không là lý do full refund B.
- r0/H0 ghi quyết định hủy và NO_REFUND, không transaction tiền0.

Customer có thể yêu cầu hủy hợp lệ nhưng không tự thực hiện tiền. Owner/Staff BOOKING_REFUND bấm **Hoàn tiền DEMO**, server kiểm request/permission/amount/payee/idempotency, cập nhật REFUNDED và event ứng dụng trong local transaction. Không phải cấp nguồn, nạp tiền, verify ngân hàng hoặc duyệt nhiều cấp. Có thể mô phỏng FAILED, giữ nghĩa vụ và retry chưa thành công. Tổng refund không vượt payment đã trả, không tạo H mới mỗi lần nhấn.

Cancel theo điều kiện trước startsAt dưới cùng court lock/version với check-in/handoff. CHECKED_IN/COMPLETED không dùng nhánh hủy trước chơi. Active transfer phải resolve trước. Chỉ thay guard paid/group cancellation khi quote/cancel/refund DEMO đầy đủ; không bỏ guard member/allocation đã thu tiền hoặc đổi timeout nhóm thành policy cancellation.

### D7. Check-in vẫn cần đủ tiền trong thế giới DEMO

Tái dùng local paymentId/paidAt/receipt/history/contribution do flow DEMO thành công tạo ra, kiểm amount/currency/fingerprint/binding và trạng thái đã áp dụng. Đây là bằng chứng trong ứng dụng; không đối chiếu ngân hàng, merchant verified hoặc nguồn khả dụng. Payment SUCCESS nhưng chưa áp dụng/chưa handoff thì đồng bộ tiếp, không bypass ownership.

QR/mã thủ công/HTTP trực tiếp đều guard cùng tiền/quyền/time/transfer lock. Group cần đủ chính xác tổng; Guest holderNULL check-in bằng mã; linked Customer giữ sở hữu; payer tiền sân gốc khác holder sau transfer là hợp lệ. B đã trả/nhận quyền không phụ thuộc settlement N PAID.

Giữ CONFIRMED→CHECKED_IN→COMPLETED, check-in từ startsAt−30 phút tới trước endsAt, complete sau endsAt. Report không tự đổi status hoặc thêm IN_PROGRESS. CANCELLED không check-in dù hoàn DEMO còn pending/FAILED.

### D8. Database tối thiểu cho DEMO

Tái dùng payment_orders/requests/obligations, booking receipts/history/contributions/cancellation records, listing/acquisition và policy/grants đã có. Chỉ bổ sung dữ liệu chưa có; không mở rộng toàn bộ schema tài chính production.

- Payment context: mode=DEMO, receiverFacilityId/receiverOwnerId và receiving label phía server. Không bắt merchantBinding/verifiedBank/beneficiary token. Dữ liệu cũ không bị đổi mode hoặc người nhận ngầm.
- `demo_transfer_settlements`: acquisition unique, payment/booking/facility/Owner/A/B refs, P/x/policyVersion/F/N, terms hash, handoffAt, payoutStatus/paidAt/performedBy/version. Một acquisition một N/F, P=F+N, N>=0; financial identity đã chốt không đổi tùy ý.
- `demo_refund_requests`: cancellation/acquisition business key, reason POLICY_CANCELLATION hoặc TECHNICAL_TRANSFER_FAILURE, facility/Owner/booking, S/r/H/policy/allocations/payee/payment refs, PENDING/REFUNDED/FAILED/NO_REFUND và thời điểm. Có thể tái dùng record yêu cầu hiện hữu nếu đáp ứng, nhưng không nới trigger refund review cũ để giả đã thực thi.
- `demo_money_events`: append-only operation/kind/entity/facility/payer/payee/amount/currency/DEMO/outcome/actor/time/idempotency. Request key replay ổn định, một thành công cho mỗi nghĩa vụ/payment allocation. Thất bại không tăng tổng đã trả/hoàn. Tái dùng audit hiện có khi phù hợp thay thêm hệ thống history thứ hai.

Các projection settlement/refund và success event cập nhật cùng local transaction, unique business key và khóa entity để chống double-click. Không cần accounts/journals/deferred ledger/funding/reservations/beneficiary verification cho DEMO. Module/sổ đã viết dở có thể giữ ngoài luồng demo, không xóa migration/chứng từ để làm nhẹ scope. Nội dung tương đương có sẵn thì tái dùng, không bắt tạo thêm cả ba bảng nếu không cần.

Vị trí triển khai: Booking sở hữu `demo_transfer_settlements`, `demo_refund_requests`, `demo_money_events` và command receipts. Settlement F/N được ghi cùng transaction với quyết định handoff COMPLETED; ABORTED tạo quyền hoàn đủ P sau khi có collection. Nhờ vậy không có khoảng trống giữa chuyển quyền và tạo nghĩa vụ. Payment giữ snapshot `demo_transfer_payment_terms` và phát canonical collection qua outbox hiện hữu; Booking lưu metadata DEMO của collection để báo cáo mà không cần xác minh ngân hàng. Guest có tham chiếu booking guest riêng trên event hoàn, không dùng Staff làm người nhận.

### D9. API, facts và quyền

Tái dùng POST payments/{id}/demo/complete với outcome SUCCESS/FAILED, giới hạn đúng payer/operator hợp lệ. API thực hiện nằm cùng dữ liệu Booking: POST bookings/demo-finance/settlements/{id}/pay-seller và POST bookings/demo-finance/refunds/{id}/complete. GET bookings/demo-finance/facility/{id} cho cơ sở, GET bookings/demo-finance/mine cho participant; Admin chỉ GET admin/bookings/demo-finance. Hủy dùng POST bookings/{id}/cancellation-quote rồi POST bookings/{id}/cancel-with-policy. GET chỉ đọc; không side effect từ mở màn hình.

Server không tin amount/fee/payee/facility từ client; body chỉ outcome giả lập cùng idempotency và lý do khi cần. Owner kiểm ownership, Staff kiểm active grant đúng cơ sở tại request; A/B chỉ participant của mình. Admin hiện có được đọc view quản lý theo role nhưng không thay quyền Owner để bấm trả A/hoàn B.

Facts báo cáo lấy dữ liệu DEMO đã commit: applied court payments, handoff phí, demo payout/refund SUCCESS. Tái dùng client batch500 và ServiceCalls đang có nếu cần giữa microservice, không gọi remote dưới court lock/TX và không dùng API ngân hàng. Không bắt xây batch evidence ngân hàng, signed cursor đa nguồn hoặc pipeline đối soát production.

### D10. Báo cáo và công thức DEMO

Một range fromDate/toDate inclusive theo timezone Facility, default7 ngày/preset7/30/tháng này, backend validate/chuẩn hóa nửa mở và giới hạn366 ngày lịch inclusive. Date basis: R/P paidAt, F handoffAt, N paidAt của payout DEMO, H refundedAt; booking cohort startsAt, giờ theo overlap/check-in đã xác định. Cùng bộ lọc cho cả trang, không panel30 ngày ẩn.

Số còn phải trả A/hoàn khách tại cutoff=min(cuối kỳ,asOf) bằng nghĩa vụ phát sinh trước cutoff trừ thành công trước cutoff, gồm nợ từ kỳ trước. Ghi rõ đây là công nợ đến cutoff, không chỉ dòng tiền phát sinh trong kỳ. Đã trả/đã hoàn vẫn theo paidAt/refundedAt trong range; có thể tính từ settlement/refund/events ứng dụng, không cần sổ ngân hàng hoặc statement.

Owner thấy R tiền sân, F phí cơ sở, tổng R+F, H refund DEMO thành công, số sau hoàn R+F−H và N còn phải trả/đã trả/thất bại. P là cashflow, không cộng toàn bộ vào revenue. Pending/FAILED refund chưa giảm tổng; N chưa trả không thêm phí. Các metric là mô phỏng, không lợi nhuận đã đối soát hoặc doanh thu SportHub.

Một dòng/sân, chart R/F/H theo ngày, bảng giao dịch có kind/amount/status/effectiveAt/booking link an toàn. Ghi nhận F là phí từ P, không bank deposit mới; N là khoản trả người bán, không refundB. BookingCode/QR/guest phone không hiện trong bảng tài chính.

Phân trang đơn giản page/size25, max100, sort effectiveAt/id, scope server. Bảng có thể hiển thị R/P/F/N/H/technical refund với kind/cashflow/revenue flag rõ; đối chiếu từng component, không cộng tất cả dòng thành revenue. Thay filter reset page; có phát sinh mới thì refresh bộ báo cáo, không cần distributed snapshot/HMAC cursor. KPI/daily/court/tổng dữ liệu giao dịch cùng filter khớp khi dữ liệu không đổi. Tái dùng API cũ, thêm DTO/endpoint mới khi cần, không phá client cũ.

Đọc không tạo collection/chi/hoàn/handoff/check-in. Loading/empty/error/retry rõ; thiếu nguồn ứng dụng/upstream không vẽ thành tổng0 đầy đủ. Bộ DEMO đủ dữ liệu hiện tổng DEMO đầy đủ, không cần REAL/UNKNOWN dashboard. Admin chỉ theo dõi DEMO và phân quyền hiện có; không báo cáo doanh thu nền tảng/đối soát bank mới. Giữ đường dẫn spec reporting/platform-revenue hiện có với Purpose này, không tạo/rename artifact mới.

### D11. Dữ liệu cũ và cách triển khai sau duyệt

Giữ holder/payer/payment/history/refund review/migrations cũ; không tự dựng receipt hoặc backfill policy thành0/100%. Legacy thiếu dữ liệu ứng dụng cần preflight/consent rõ, hoặc tạo booking/giao dịch DEMO mới để trình diễn. Không yêu cầu bank proof cho dữ liệu được ghi nhận là DEMO.

Apply sau đọc code đã làm dở, tái dùng fee/permission/receipt/guard phù hợp và đưa DEMO qua đường xử lý đơn giản. Gỡ điều kiện xác minh merchant/account/funding, provider-final/manual-proof, worker submit/UNKNOWN bank khỏi luồng demo/UI. Không xóa code/dữ liệu cũ hàng loạt hoặc sửa migration đã chạy; nếu cần schema thì migration cộng thêm kiểm thử fresh/upgrade.

Giữ test có giá trị về permission/math/idempotency/ownership/legacy. Ngân hàng/ledger/benchmark10.000/reconciliation production không là tiêu chí đạt đồ án. Checklists phản ánh phần đã hoàn thành và phần DEMO còn phải nối, không tick mới chỉ vì giảm scope. Tích hợp live thật và P2 cần change riêng, không gate khả năng demo.

## Risks / Trade-offs

- [DEMO bị hiểu là đã thanh toán ngoài đời] → nhãn DEMO tại checkout/actions/report và mode trong database; không tạo khẳng định ngân hàng đã nhận/chuyển.
- [Chỉ giả giao diện, backend vẫn chờ bank/funding] → test HTTP xuyên suốt xác nhận payment→handoff→check-in, payout/refund hoàn tất chỉ từ thao tác server DEMO.
- [Client giả số tiền hoặc tự trả mình] → server tính từ snapshot, scope facility/participant, grant tại request, unique success/idempotency và audit.
- [B bị chặn vì Owner chưa trả A] → E2E trả đủ DEMO/nhận quyền/check-in trong khi N UNPAID/FAILED.
- [Làm mất dữ liệu cũ khi cắt production scope] → giữ migrations/records, chỉ đổi đường DEMO ở apply có tests, không sửa dữ liệu ở update này.

## Acceptance Examples

- R120.000/P100.000, Owner chọn x5%: B bấm Thanh toán DEMO, nhận quyền; cơ sở F5.000/nợA95.000, không doanh thu SportHub. B check-in theo lịch ngay khi đủ guard dù N UNPAID.
- Owner/Staff đúng quyền bấm Trả tiền DEMO: A hiện PAID95.000 và thời điểm/actor, không nhập bank/verify số dư. Lặp request không thêm khoản trả.
- Giả lập trả A FAILED: còn nợ95.000, B giữ booking. Retry SUCCESS chỉ hoàn tất một N, không phí mới.
- Payment FAILED: B chưa quyền/QR/F/N. SUCCESS nhưng handoff ABORTED: hoàn DEMO đủ100.000 cho B, F/N0; response mất sau COMPLETED thì khôi phục quyền, không hoàn như abort.
- Handoff thành công rồi hủy r80%: H80.000 trả B bằng Hoàn tiền DEMO, F5.000/N95.000 giữ. Cùng kỳ R+F125.000, sau hoàn45.000; H pending/FAILED chưa trừ. No bank funding gate.
- Nhóm300.000 mới100.000: R100.000, chưa check-in; nếu hủy r50% thì H50.000 đúng payer. Trường hợp không hủy, đóng thêm200.000 hợp lệ thì đủ tổng300.000, không cộng booking.amount lần hai.
- Owner khác cơ sở, Staff không grant/inactive, A tự đánh dấu Owner đã trả hoặc Customer tự execute refund đều bị chặn; grant đúng cơ sở thực hiện được, thu hồi trước thao tác mới thì bị chặn.
- QR/mã/counter linked Customer/Guest và lifecycle CHECKED_IN giữ nguyên. Mở report/đổi ngày không tự chi/hoàn hoặc đổi booking.
