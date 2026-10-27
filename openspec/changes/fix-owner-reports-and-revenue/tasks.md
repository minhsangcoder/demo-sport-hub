> **HISTORY ONLY — NOT AN ACTIVE REQUIREMENT SOURCE.**
> Read `openspec/CURRENT_CHANGE.md` at the project root for the current proposal.
> Do not apply, merge, or resume requirements/tasks from this document unless
> the user explicitly requests this historical change. Task counts are historical.

# Tasks

Checklist sau quyết định **DEMO đồ án**: B thanh toán giả lập cho cơ sở → tự nhận quyền → Owner/Staff có quyền trả A sau phí x% → hủy/hoàn theo r riêng. Thao tác server ghi SUCCESS/PAID/REFUNDED là đủ; không kiểm ngân hàng, merchant/beneficiary, số dư, cấp nguồn, reserve hoặc chứng từ tiền thật.

Chỉ apply khi người dùng yêu cầu. Lần update này chỉ sửa planning, không sửa code/migration/dữ liệu hoặc đánh dấu implementation mới hoàn thành. Các tick dưới đây giữ nguyên đầu việc độc lập đã hoàn tất trước đây; code phí/quyền làm dở vẫn cần kiểm theo acceptance DEMO nên chưa tick thêm.

Đã bỏ khỏi checklist bắt buộc các hạng mục production: merchant onboarding/verification, bank adapter/final proof, funding/reserve, bank return/chargeback, key TTL/region, approval nhiều cấp, signed snapshot pagination, benchmark10.000 và live acceptance. Đây là ngoài phạm vi, không phải đã thực hiện. Code/migrations/chứng từ tương ứng được giữ nguyên; không xóa hoặc reset để giảm scope. REAL/UNKNOWN DTO cũ và sổ cân bằng ở3.2 là tài sản kỹ thuật đã có, không buộc mở rộng hoặc hiển thị thành dashboard production để demo.

Thứ tự: contract/date → policy/grant → dữ liệu DEMO tối thiểu → điều khoản/phí → payment/handoff → trả A/hoàn khách → facts/check-in → báo cáo/FE/giám sát → tích hợp. Mỗi nhóm cập nhật test và tài liệu của chính nhóm đó; không trì hoãn kiểm tra đơn vị đến nghiệm thu cuối.

## 1. Contract và định nghĩa báo cáo DEMO

- [x] 1.1 Bổ sung DTO dashboard/transactions với decimal string, window/dateBasis/asOf/coverage, gross/policyRefundExecuted/afterRefund và nghĩa vụ chờ/đang hoàn; xác nhận OpenAPI validate, tách REAL/DEMO/UNKNOWN, không đổi response cũ.
- [x] 1.2 Nối fromDate/toDate theo timezone cơ sở, range inclusive tối đa366 ngày lịch và default7 ngày/preset7/30/tháng này; unit/API test đầu-cuối ngày, DST, timezone browser khác và400 cho input sai, ghi rõ quy tắc ngày trong API docs.
- [x] 1.3 Đồng bộ OpenAPI/DTO cho modeDEMO, receiving facility/Owner, outcome/status/actor và page/size25 max100; contract tests giữ endpoint/shape/quyền cũ, client không chọn số tiền/người nhận hoặc dùng lệnh DEMO cho record không thuộc DEMO.
- [x] 1.4 Cập nhật metric docs theo D3/D10: R/P paidAt, F handoffAt, N demo paidAt, H demo refundedAt và công nợ tại cutoff; kiểm ví dụ R120.000/P100.000/x5%/F5.000/N95.000/H80.000 cho tổng125.000/sau hoàn45.000, không revenue SportHub.

## 2. Policy cơ sở và quyền thực hiện

- [x] 2.1 Thêm policy versions/active pointer trong Facility và API Owner với rate 0–10.000bps, CAS/audit/immutable; test 0/50/80/100%, sai độ chính xác/ngoài khoảng, Owner khác cơ sở và Staff không được đổi policy.
- [x] 2.2 Thêm BOOKING_REFUND vào catalog Java, DB CHECK, invitation/access validation bằng migration mới; test grant/revoke/version/audit, bindings/invitations cũ không tự được cấp và không khôi phục BOOKING_COMPLETE/BOOKING_CANCEL_PENDING đã bỏ.
- [x] 2.3 Thêm schema policy snapshot Booking, chốt/hiển thị trước thanh toán online/counter/nhóm; test policy thiếu chặn đặt mới, 0% cấu hình rõ vẫn hợp lệ, hold cũ cần tạo lại và Owner đổi tỷ lệ không đổi snapshot; nối acquisition ở 4.3.
- [x] 2.4 Thêm UI Owner cấu hình tỷ lệ hoàn r và nhãn BOOKING_REFUND trong cấp/thu hồi quyền Staff, mặc định không chọn; browser tests Owner/Staff/chéo cơ sở/version conflict và kiểm chứng API kiểm tra quyền độc lập UI.
- [x] 2.5 Thiết kế preflight/explicit adoption cho booking legacy thiếu snapshot và chứng từ có phần số không hỗ trợ; test không sửa payment/history/cancellationPolicy cũ, không tự áp tỷ lệ mới hoặc tạo refund khi migration/startup.
- [x] 2.6 Đồng bộ contract và hướng dẫn x/r/quyền theo DEMO; contract tests x độc lập r, F thuộc cơ sở, Owner/Staff đúng grant và không còn phí2% nền tảng hoặc điều kiện ngân hàng trong luồng demo.
- [x] 2.7 Hoàn thiện fee policy versions/head/CAS/public quote/Owner UI đang làm dở; test x0/5/10/50/100%, precision/bounds/version/cross facility, Staff không sửa, thiếu x chỉ chặn chuyển nhượng mới, không seed/inherit phí và snapshot cũ bất biến.
- [x] 2.8 Gắn receiving facility/Owner từ booking phía server và bỏ gate merchant/bank/beneficiary/funding khỏi DEMO; integration test cơ sở đã cấu hình x/r nhưng chưa có bank setup vẫn tạo payment DEMO đúng bên nhận, client không chuyển người nhận sang SportHub/cơ sở khác.
- [x] 2.9 Hoàn thiện TRANSFER_PAYOUT catalog/CHECK/invitation/grants/UI opt-in đang làm dở; test cấp/thu hồi/active/version/audit, mặc định không cấp, không self-grant, không suy BOOKING_REFUND hoặc quyền report Owner và không vượt scope cơ sở.

## 3. Dữ liệu và thao tác server DEMO tối thiểu

- [x] 3.1 Kiểm kê schema/code đã có rồi chỉ bổ sung trường/bảng cần cho settlement/refund/event DEMO theo D8, ưu tiên tái dùng tương đương; kiểm fresh/upgrade/unique business key/P=F+N/bounds/immutable identity bằng DB tests, không sửa migration đã chạy hoặc tạo các bảng production không cần thiết.
- [x] 3.2 Thêm accounts/journals/postings với effect key duy nhất, deferred balance check và currency/money-class isolation; test journal rỗng/debit≠credit/amount âm/cross class, unique account scope NULL và UPDATE/DELETE chứng từ đã post bị chặn.
- [x] 3.3 Chuẩn hóa lệnh DEMO SUCCESS/FAILED và kết quả DB/audit trong local transaction với idempotency/unique success; service tests double-click, payload conflict, hai actor cạnh tranh, mất response sau commit và từ chối biến record REAL/legacy thành DEMO, tài liệu hóa ý nghĩa mô phỏng.

## 4. Phí và điều khoản trước thanh toán

- [x] 4.1 Thay fee200bps cố định bằng snapshot Owner x, tính F HALF_UP nguyên VND rồi N=P−F; unit tests P25/x50→F13/N12, x0/x100, precision và không tính lại khi retry, cập nhật công thức trong tài liệu nghiệp vụ.
- [x] 4.2 Chốt/hiển thị facility/Owner/A/B/P/x/version/F/N/r/hash trước A đăng-sửa và B mua, không đòi bank account; API/browser test policy đổi cần requote khi chưa khóa nhưng không đổi acquisition đã chốt, A≠B và N0 không đòi payout.
- [x] 4.3 Nối payable/payment với snapshot refund của booking, buyer/seller/receiver và modeDEMO phía server; integration tests amount/currency/purpose/binding tamper, snapshot r trước thanh toán và giữ payer khác holder sau counter/transfer.

## 5. Thanh toán DEMO và bàn giao tự động cho B

- [x] 5.1 Bổ sung Booking acquisition decision và complete/abort dưới cùng court lock; test race chỉ một COMPLETED/ABORTED, abort chặn complete/relock muộn, complete đã commit trả lại proof thay vì cho abort thắng sau.
- [x] 5.2 Tận dụng DemoPaymentProvider/endpoint demo complete và receipt/outbox hiện có để canonical SUCCESS đủ P tự handoff/rotate credential; integration test không có bước Owner duyệt, B nhận quyền khi N chưa trả/FAILED, delayed event phục hồi cùng acquisition và chưa commit thì chưa QR.
- [x] 5.3 Hoàn thiện FAILED/timeout/abort/replay cho DEMO; test payment FAILED không F/N/quyền, response mất sau COMPLETED không refund sai, ABORTED chặn complete muộn, callback lặp/canonical obligation không tạo hai handoff hoặc thu phí trùng; cập nhật mô tả trạng thái UI/API.

## 6. Owner hoặc Staff trả A bằng DEMO

- [x] 6.1 Tạo đúng một khoản F/N sau handoff đã commit, N dương UNPAID và N0 NOT_REQUIRED; integration tests replay/event muộn sau hủy không tạo nợ mới, B đã nhận quyền, không tự trả A hoặc yêu cầu tài khoản ngân hàng.
- [x] 6.2 Thêm/nối thao tác Trả tiền DEMO cho A trên API/UI Owner/Staff TRANSFER_PAYOUT, ghi PAID/paidAt/actor và event cùng transaction; permission/idempotency/browser tests đúng cơ sở, revoke, N/payee từ server, không cần proof/funding/Admin hay bước duyệt khác, cập nhật API docs.
- [x] 6.3 Thêm mô phỏng FAILED và retry chỉ khoản chưa trả, hiển thị nợ/trạng thái cho cơ sở và A; service/browser tests failure→success một N, response loss/replay, A không tự đánh dấu paid và B check-in được khi N FAILED.

## 7. Hoàn kỹ thuật khi bàn giao ABORTED

- [x] 7.1 Nối SUCCESS đã thu với quyết định ABORTED để tạo đúng một yêu cầu hoàn đủ P/F0/N0, thực hiện qua lệnh hoàn DEMO ở8.2; integration tests x/r không giảm full refund, chưa thu không refund, UNKNOWN ứng dụng chưa giả abort và COMPLETED không bị hoàn do timeout, tài liệu phân biệt lỗi kỹ thuật với hủy policy.

## 8. Hủy và hoàn theo policy r bằng DEMO

- [x] 8.1 Hoàn thiện quote/cancel nguyên tử theo S/r/H đã chốt, Guest/payment gốc, nhóm contribution và buyer cuối sau resale; unit/API tests r0/partial/100, làm tròn/largest remainder tie paymentId, stale/expired quote, A→B→C, cancel trước startsAt và race check-in/handoff, ghi rõ cơ sở tính trong API docs.
- [x] 8.2 Thêm/nối Hoàn tiền DEMO Owner/Staff BOOKING_REFUND với REFUNDED/refundedAt/actor/event từ server cho policy và technical refund; integration/browser tests không funding/bank gate, Customer chỉ request, wrong grant/inactive/cross facility bị chặn và H0 NO_REFUND không transaction tiền0.
- [x] 8.3 Hoàn thiện FAILED/retry/capacity/idempotency, giữ CANCELLED và F/N sau hủy; tests nhiều allocation, tổng không vượt payment, double-click/mất response không hoàn trùng, paid-group member/allocation guard vẫn giữ và timeout nhóm không tự thành policy cancellation; cập nhật hướng dẫn trạng thái lỗi.

## 9. Facts ứng dụng và tổng tiền chỉ đọc

- [x] 9.1 Nối R từ applied court payments/contributions, P từ demo collection, F/N từ handoff/settlement và H từ demo refund SUCCESS; integration tests receiver đúng cơ sở, DEMO đủ dữ liệu tính đầy đủ không cần bank metadata, không cộng P/N vào R+F và technical refund tách H, cập nhật metric/fact docs.
- [x] 9.2 Giữ binding/fingerprint/currency/purpose/service auth và dedup theo payment/effect, không nhân khi join member/history/review; API tests payments bằng tiền vẫn khác identity, invalid binding/source timeout báo partial/unavailable thay0 và GET không mutation, tái dùng hạ tầng hiện có thay pipeline ngân hàng mới.
- [x] 9.4 Thêm client Booking chia lô 500, dedup, timeout/deadline và concurrency hữu hạn; test 501 payment tạo hai lô, request lỗi trả coverage thiếu rõ ràng và không thực hiện remote call dưới transaction/lock vận hành.

## 10. Dữ liệu cũ và phạm vi DEMO

- [x] 10.1 Nối preflight/adoption hiện có vào thông báo dễ hiểu, giữ mode/payment/policy/holder/history cũ và đưa DEMO mới qua nhánh đơn giản; regression tests không backfill x2%/r0/100/receipt, không sửa refund review thành executed, DEMO có receipt hợp lệ không bị UNKNOWN do thiếu bank proof; cập nhật hướng dẫn tạo dữ liệu DEMO mới khi legacy chưa đủ.
- [x] 10.6 Bổ sung read-only preflight xuất danh sách bất thường và coverage legacy theo cơ sở; kiểm chứng trên database kiểm thử/snapshot được phép rằng script/query không UPDATE/DELETE, không tự tạo receipt, đổi holder hoặc sửa lịch sử.

## 11. Trả đủ DEMO mới check-in

- [x] 11.1 Nối application receipt/payment/paidAt/contribution từ DEMO SUCCESS vào Booking guard đủ tiền, không gọi xác minh ngân hàng hoặc tự dựng proof; integration tests đúng amount/currency/binding, SUCCESS chưa áp dụng bị chặn và DEMO hợp lệ được check-in, cập nhật lỗi đồng bộ/thiếu payment.
- [x] 11.2 Kiểm QR/mã/HTTP cho online, counter linked Customer/Guest, nhóm và buyer sau transfer; tests thiếu/đủ/tổng contribution sai, holderNULL/createdBy audit, payer khác holder, N UNPAID/FAILED không chặn B, credential cũ hết hiệu lực, facility/time/transfer lock và CONFIRMED→CHECKED_IN→COMPLETED giữ nguyên.
- [x] 11.5 Tài liệu hóa lỗi check-in thiếu proof và đường đối soát legacy, cập nhật nhãn lỗi FE trong luồng vận hành nếu cần; kiểm chứng thông báo dễ hiểu, không hướng dẫn bỏ qua tiền hoặc tự cấp quyền Customer cho operator.

## 12. API báo cáo cơ sở

- [x] 12.1 Tổng hợp KPI/daily/one-row-per-court từ cùng facts/timezone với R/F/H và P/N riêng, booked/used hours theo overlap; API tests ví dụ125.000/45.000, partial group, paidAt khác startsAt, inactive court giữ lịch sử, CHECKED_IN quá endsAt không kéo dài giờ hoặc tự đổi status, cập nhật report docs.
- [x] 12.2 Triển khai transactions page/size25 max100 sort effectiveAt/id, filter/scope và kind/cashflow/revenue flags; API tests nhiều dòng cùng timestamp không lặp/bỏ khi dữ liệu không đổi, không cần HMAC/distributed snapshot, F không giả thêm deposit và booking link không dùng QR/code bí mật.
- [x] 12.3 Tính N/H còn phải thực hiện tại cutoff=min(cuối kỳ,asOf) từ nghĩa vụ trừ success trước cutoff, gồm nợ kỳ trước; tests phát sinh ngoài kỳ/chỉ trả sau kỳ/failure không mất nợ, nhãn basis rõ và owner/cross facility/Staff grants không vượt report scope.

## 13. FE Báo cáo & Doanh thu

- [x] 13.1 Thay thẻ booking rời bằng KPI, chart R/F/H và một dòng/sân dùng cùng range, presets, nhãn DEMO và giá trị API; browser tests loading/empty/error/partial/retry, timezone/date sync, không occupancy giả hoặc enum/UUID làm nhãn chính, hiển thị responsive/accessible và không thay cây FE legacy ngoài scope.
- [x] 13.2 Thay text hoạt động bằng bảng giao dịch có kind/status/amount/time/link/phân trang đơn giản, tách dòng tiền với revenue; browser tests đổi filter reset trang, request cũ không ghi đè kết quả mới, mutation DEMO refresh report và từng component khớp KPI, cập nhật hướng dẫn đọc số tiền/trạng thái.

## 14. Giám sát Admin hiện có

- [x] 14.1 Điều chỉnh nhãn/facts của view giám sát hiện có cho DEMO thuộc cơ sở, không xây bank console hoặc revenue SportHub; API/browser regression tests quyền đọc cũ, Admin không thay quyền Owner để thực hiện tiền, GET read-only/source lỗi không0 giả và không lộ PII/credential, cập nhật mô tả scope.

## 15. FE thanh toán và giao dịch của A/B

- [x] 15.1 Nối nút Thanh toán DEMO và trạng thái A/B/Owner/Staff với kết quả server, hiển thị x/F/N/r, bên nhận cơ sở và nhãn không phát sinh tiền thật; browser tests bỏ yêu cầu nhập/verify ngân hàng, không chờ trả A để cấp quyền B, N lỗi/retry/H pending-success rõ, Customer không tự execute tiền và không còn fee2% cố định, cập nhật kịch bản demo.

## 16. Nghiệm thu tích hợp DEMO

- [x] 16.1 Chạy E2E không bank setup: online/counter/Guest/nhóm → trả đủ → check-in, A→B thanh toán DEMO → B nhận quyền/check-in khi N chưa trả → Owner/Staff trả A, hủy/hoàn policy và ABORTED full refund; kiểm số tiền/trạng thái/quyền trong DB/API/FE và report, lưu kết quả trình diễn.
- [x] 16.2 Chạy bộ regression phù hợp Backend/FE/contracts/typecheck/browser và kịch bản lỗi/replay/race/cross facility/legacy cho phần đã đổi; ghi lệnh/kết quả/giới hạn còn lại trong tài liệu bàn giao, không yêu cầu benchmark production/ngân hàng thật, không tick nếu còn lỗi nghiệp vụ và không tự triển khai/đổi dữ liệu người dùng.

## Workflow follow-up

- Sau update planning, chỉ thực hiện implementation khi người dùng gọi openspec-apply-change; không tự chạy apply từ lần update này.
- Sau implementation đã kiểm thử và được người dùng duyệt, archive/sync là thao tác riêng theo yêu cầu.
- Tích hợp thanh toán/chi/hoàn tiền thật, funding/reserve/đối soát ngân hàng và P2 xuất Excel/PDF/so sánh kỳ/giờ cao điểm thuộc change tương lai, không là điều kiện nghiệm thu DEMO hiện tại.
