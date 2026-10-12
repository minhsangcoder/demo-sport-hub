# Backend — Checklist sau Phase 00

Ngày lập: **12/10/2026**. **Phase 00 hoàn tất. Phase 01–08 chưa bắt đầu.**

Đây là kế hoạch theo thứ tự phụ thuộc, không phải xác nhận các chức năng đã được triển khai. Chỉ bắt đầu phase tiếp theo khi có prompt của người dùng. Tên, phạm vi và thứ tự phase có thể được điều chỉnh trong prompt đó. Finding **A01–A24**, quyết định **D01–D13**, API thực tế, ma trận 66 UC và baseline xem [backend-implementation-progress.md](backend-implementation-progress.md).

## Phase 00 — Audit và baseline

- [x] Kiểm tra branch, HEAD và working tree; xác minh frontend mới trong working tree.
- [x] Đổi tên local branch thành `features/frontend-rebuild`, giữ HEAD `ae8449336b57f921df0164da7bec190d6f2473c4` và các thay đổi chưa commit.
- [x] Ghi nhận chưa thể tạo `features/backend-business-completion` từ commit chứa đầy đủ frontend mới; không tự checkpoint, stash, reset hoặc checkout.
- [x] Đọc bốn tài liệu thật, ghi provenance/hash; dùng `base-bussines.pdf` làm chuẩn.
- [x] Đối chiếu 59 đặc tả chi tiết trong PDF chuẩn và bảy UC-5.2–5.8 bằng FR/bổ sung không mâu thuẫn; không loại UC khỏi phạm vi.
- [x] Kiểm chứng frontend API/migration inventory bằng source; ghi lại lỗi ánh xạ và contract cũ.
- [x] Audit sáu service, Common/Gateway, authentication/authorization, schema/constraints, transaction, inbox/outbox, REST và tests/development configuration.
- [x] Phân loại đủ 66 UC: 5 đầy đủ, 37 một phần, 5 thiếu API, 17 sai Business Rules, 1 thiếu test trực tiếp, 1 chưa thể kết luận do policy mâu thuẫn.
- [x] Ghi 24 finding, migration dự kiến và 13 nhóm quyết định nghiệp vụ; phân biệt lỗi source, bằng chứng runtime và hạn chế môi trường.
- [x] Chạy baseline: ghi Testcontainers lỗi môi trường, lỗi extension/test isolation và lần cuối 140 tests pass trên DB test riêng đã chuẩn bị extension.
- [x] Validate chín OpenAPI contracts; ghi rõ backend không có lint task riêng.
- [x] Bảo toàn source/frontend/env/tài liệu hiện có; chỉ thêm hai tài liệu Phase 00. Không sửa API/schema/logic, không chạy migration trên dữ liệu ứng dụng hoặc truy cập production.
- [x] Dừng tại Phase 00.

## Điều kiện chung cho các phase triển khai

- [ ] Có prompt triển khai phase từ người dùng, xác định scope và những quyết định tiền/quyền còn cần chốt.
- [ ] Xác minh lại Git và baseline; không làm mất thay đổi chưa commit. Ghi commit bắt đầu và phạm vi file được phép sửa.
- [ ] Mọi rule dựa trên tài liệu chuẩn; bổ sung không ghi đè rule đã rõ. Không thêm nghiệp vụ ngoài tài liệu.
- [ ] Mỗi invariant có đúng service sở hữu; UUID ở DB khác là logical reference, không thêm FK xuyên service.
- [ ] Contract/request/response/error/security khớp controller và client; ghi rõ operation mới chỉ sau khi thật sự triển khai.
- [ ] Migration mới theo version kế tiếp, có fresh/upgrade test và preflight duplicate/orphan report; không sửa migration đã áp dụng hoặc xóa lịch sử để làm constraint pass.
- [ ] Chạy test có thể mutation chỉ trên DB/container test đã xác minh tên, label, port và credential riêng; không dùng `.env` ứng dụng làm external test DB.
- [ ] Trạng thái review/block không được trình bày như refund, settlement, transfer hoặc reconciliation đã hoàn tất.
- [ ] Cập nhật ma trận UC, finding, test evidence và checklist sau từng phase; chỉ đánh dấu khi có chứng cứ.

## Phase 01 — Điểm xuất phát an toàn và P0 nền tảng

Ưu tiên: **P0**. Finding: **A01, A02, A06, A19, A22**; test infrastructure **A20**. Phụ thuộc quyết định: phần rate-limit/permission trong **D05**, thời điểm payment trong **D08**. Chưa có thay đổi nào được thực hiện.

- [ ] Checkpoint frontend theo chỉ dẫn của người dùng; xác minh commit chứa rebuild và tạo `features/backend-business-completion` khi an toàn.
- [ ] Làm baseline tự đủ trong CI/test environment: extension schema ổn định, mỗi suite cô lập, không phụ thuộc thứ tự chạy lớp hoặc Testcontainers fallback ngầm.
- [ ] Bảo vệ Actuator/Gateway management bằng đúng security boundary; test anonymous read/write bị chặn theo config, health đáp ứng nhu cầu development.
- [ ] Chặn cấp OWNER qua generic Admin roles API nếu chưa APPROVED; giữ coordinator OwnerApplication là đường cấp quyền đúng rule.
- [ ] Thiết kế nghĩa vụ thanh toán canonical tại Booking/Transfer và claim tại Payment: không dựa riêng vào payer + idempotency key; generation và expiry/abort rõ.
- [ ] Đảm bảo hai người trả cùng contribution, key mới sau SUCCESS và consumer lag không tạo lần thu thứ hai cho cùng nghĩa vụ.
- [ ] Provider request có idempotency/recovery; crash sau gửi provider nhưng trước commit không tạo debit mới khi retry.
- [ ] Login attempt/backoff và chống enumeration theo NFR-3.5; thống nhất trusted rate-limit key khi request qua BFF/proxy và phản hồi UC trùng contact.
- [ ] Quy định immutability/audit cho tiền và history: actor, reason, before/after, correlation; không để append-only strategy ngăn migration/test hợp lệ.
- [ ] Test concurrency bằng barrier/parallel request, duplicate/reordered callback/event và revoked/locked session; không chỉ test một request tuần tự.

Migration dự kiến: Booking V5+ / Payment V6+ nếu thêm obligation/claim; Identity V8+ nếu cần state cho throttle/audit. Guard A01/A06 có thể không cần migration. Version chỉ là kế hoạch, phải kiểm tra lại.

Điều kiện hoàn thành: baseline chạy được từ môi trường test mới; các regression P0 trên pass; snapshot giá, session revocation, GiST overlap và callback dedup cũ vẫn đúng; chưa cấp usage cho payment review hoặc tiền chưa thu.

## Phase 02 — Booking, tại quầy và thay cấu hình đồng thời

Ưu tiên: **P0**. Finding: **A07, A08**, phần **A13/A15/A22**. UC: **2.6/2.7, 3.1/3.2, 4.3–4.5, 5.2/5.6/5.7**. Quyết định: **D05, D07, D09**.

- [ ] Tách operator tạo booking tại quầy khỏi customer/current holder; định nghĩa guest/account linkage và claim ticket theo policy.
- [ ] Kiểm tra quyền vận hành theo active facility binding ở mọi request; creator lịch sử không được duy trì quyền xem PII/QR sau revoke.
- [ ] Booking giữ authority của reservation và check-in; Facility/Schedule đưa configuration revision/context, không cùng ghi bảng booking.
- [ ] Thiết kế prepare → impact preview → confirm → activate cho disable, maintenance, hours, exception và suspension liên quan booking.
- [ ] Liệt kê affected booking/hold và phương án xử lý; giữ paid booking/history/snapshot, không tự xóa hay đổi thời gian đã trả tiền.
- [ ] Kiểm tra config revision tại điểm chấp nhận hold/payment; xử lý race quote → đổi lịch → commit và thời gian maintenance bắt đầu.
- [ ] Chốt counter cash/deposit/debt/surcharge/no-show/early check-in; không hardcode các tỷ lệ ví dụ của supplement.
- [ ] Bổ sung manual lookup/code, complete/auto-complete/sự cố trong phạm vi đã chốt; payment facts vẫn thuộc Payment.
- [ ] Test overlap `[start,end)`, hold expiry, maintenance và config activation cạnh tranh payment/check-in; revoke Staff với booking cũ, foreign facility và transferred QR.
- [ ] Test ngày/timezone/lead-time/horizon và giữ immutable price snapshot sau chỉnh config.

Migration dự kiến: Facility V9+, Schedule V4+, Booking V5+ theo protocol/counter model đã chốt. Không dùng bảng Schedule legacy làm maintenance authority khi chưa có cơ chế sync.

Điều kiện hoàn thành: không cấp hai reservation trùng sân/giờ; booking không xác nhận bằng cấu hình đã bị thay mà chưa xử lý impact; guest/holder/operator và quyền hiện tại được phân biệt trong test.

## Phase 03 — Hủy, hoàn tiền, recovery và hạch toán chuyển nhượng

Ưu tiên: **P0**. Finding: **A03, A04, A05, A09**, phần tiền của **A22**. UC: **5.1/5.4/5.5, 6.8, 7.5/7.6/7.8, 8.10/8.11**. Quyết định trước phần phụ thuộc: **D01, D02, D08, D10, D12**.

- [ ] Cho phép Customer tự hủy booking cá nhân paid theo chuẩn: CANCELLED + release slot + audit, **không refund**. Không áp dụng tier 24h/12h của supplement cho trường hợp này.
- [ ] Hủy phía cơ sở và booking nhóm theo eligibility đã chốt, phân biệt với self-cancel cá nhân; reason và notification đúng actor.
- [ ] Refund thật theo cause/payment/original payer, cap tổng amount và idempotency; PAID_BY_OWNER trả về người trả thực tế.
- [ ] Refund state machine với provider reference/retry/reconciliation; không đánh dấu completed trước xác nhận provider hoặc nghiệp vụ tiền tại quầy hợp lệ.
- [ ] Ledger bất biến và đối ứng cho debit/held funds/refund/seller receivable/payout; sum bảo toàn theo currency, không dùng booking_value làm balance.
- [ ] Khi handoff hoàn tất, ghi nhận seller accounting theo chuẩn; thời điểm payout, commission và dispute hold theo D02, không tự trì hoãn mọi accounting tới sau trận.
- [ ] Signed handoff result/dedup và settlement acknowledgment; handoff thất bại không trả seller.
- [ ] Recovery commands có scope/reason/audit cho REQUIRES_REVIEW/PENDING_AUDIT; một transaction không vừa refund vừa grant booking/holder.
- [ ] PaidAt vs processedAt, payment đúng hạn nhưng consumer trễ, slot đã giải phóng hoặc người khác chiếm được xử lý theo D08.
- [ ] Bổ sung sandbox provider contract sau chọn provider/config test; DEMO vẫn chỉ local/demo. Không thử thu/hoàn tiền thật ở production.
- [ ] Test cancel/payment/check-in/acquire race, duplicate refund/worker/decision, partial crash, handoff replay và late transfer payment.

Migration dự kiến: Booking V5+ resolution/state; Payment V6+ refund/ledger/settlement; Transfer V2+ acknowledgment/recovery/audit. Preflight orders/refunds hiện hữu, không migration tự chuyển tiền hoặc viết lại lịch sử.

Điều kiện hoàn thành: từng payment có đường kết thúc có kiểm chứng; audit/ledger liên kết cause và payer; settlement/refund replay không gây giao dịch tiền thứ hai; paid self-cancel không sinh refund.

## Phase 04 — Staff, cơ sở, lịch, giá và danh mục

Ưu tiên: **P1**, quyền là gate security. Finding: **A10, A11, A12, A16, A21**. UC: **1.8/1.9, 2.2–2.7, 3.1–3.5, 8.6/8.7**. Quyết định: **D05, D06, D07, D11**; phụ thuộc protocol Phase 02.

- [ ] Chốt catalog Staff với yêu cầu chuẩn và catalog năm quyền đã duyệt trước; pricing write vẫn Owner-only theo UC-3.3/3.4 BR.
- [ ] Staff profile/lifecycle nếu cần; assign/update/reactivate/revoke tách rõ, cùng concurrency guard và permissions before/after audit.
- [ ] Gắn capability cần thiết cho facility/court/maintenance/schedule/cancel/reconcile; không dùng global STAFF làm quyền vượt facility.
- [ ] Exception validation không nhận ngày quá khứ, dùng facility timezone; special open vs general closed/maintenance theo precedence đã chốt.
- [ ] Validate sport category existence/active bằng boundary Facility; court type độc lập chỉ sau D06, không tự coi category là type.
- [ ] Price range/effective dates và giờ override đúng scope; versioned edit và preview draft không thay persisted snapshot booking.
- [ ] Category edit/deactivate/history/description/icon/related counts và chính sách giữ sân cũ theo D11.
- [ ] Facility suspension/lock có reason/duration/effective time/impact; ngăn booking mới theo state, bảo toàn paid history.
- [ ] Sửa OpenAPI Staff ghost paths và bổ sung helper/private operations đang thiếu; conformance tests cho method/path/payload/security/error.
- [ ] Mapping lỗi validation/conflict/not-found/dependency outage đúng cause; 204, null data và trace rõ theo contract.
- [ ] Test revoked/locked Staff, cross-facility, stale update sau revoke, inactive/nonexistent category, same-day timezone và price ambiguity.

Migration dự kiến: Identity V8+, Facility V9+, Schedule V4+ theo model đã chọn; không sửa V1–Vn đang có. Tài liệu frontend có thể cập nhật ở scope được người dùng cho phép sau, Phase 00 không sửa.

Điều kiện hoàn thành: đúng actor/capability theo từng UC; contract không quảng bá operation không tồn tại; price/exception preview phản ánh đúng kết quả quote thực tế.

## Phase 05 — Nhóm, chia tiền và check-in từng thành viên

Ưu tiên: **P1**, các invariant tiền tiếp tục **P0**. Finding: **A14, A15**, phần **A02/A04/A18**. UC: **6.1–6.9**. Quyết định bắt buộc: **D03, D04**, phần member check-in của **D09**; phụ thuộc Phase 01/03.

- [ ] Hỗ trợ đủ Owner pay-all trước rồi thành viên góp lại và member contribution trước confirm; beneficiary/obligation không thu lại tiền sân.
- [ ] Chia đều/custom và allocation revision sau paid theo xác nhận, notice và delta/refund policy; bảo toàn tổng tiền và original payer.
- [ ] GET payable không để intent vô thời hạn; acquire/abort/expire intent rõ, failed create payment không khóa allocation vĩnh viễn.
- [ ] Regenerate invitation vô hiệu mã cũ; uniqueness/expiry/limit/confirmed membership theo flow chuẩn.
- [ ] Visibility payment tracker theo D03; test DTO không lộ phần của người khác nếu policy chọn self-only.
- [ ] Hoàn thiện reminder trong cả hai modes; dedup/cooldown/recipient server-derived, không gửi ngoài danh sách có nghĩa vụ.
- [ ] QR/check-in/member thuộc Booking, token ngắn hạn và cập nhật theo membership/paid/state; booking QR không thay thế member QR.
- [ ] Group cancel/timeout refund nguyên payer, join/leave sau confirm theo D04; không reset paid contribution khi allocation đổi.
- [ ] Test Owner/member cùng trả, fractional VND policy, remainder/sum, stale invite, owner withdrawal, unpaid/member revoke và duplicate member scan.

Migration dự kiến: Booking V5+ mode, allocation generation, invite version và member check-in; Payment V6+ reimbursement obligation/beneficiary nếu cần. Legacy groups backfill giữ contribution-before-confirm, không biến thành paid-all.

Điều kiện hoàn thành: cả hai mode chạy được qua sandbox flow, tracker đúng visibility; mỗi thành viên check-in độc lập; không mất/đếm lại contribution khi retry hoặc revise.

## Phase 06 — Vận hành, đối soát, tranh chấp và báo cáo

Ưu tiên: **P1**, giải quyết tiền/tranh chấp **P0**. Finding: **A09, A13, A16, A17, A22**. UC: **5.8, 8.1–8.13**. Quyết định: **D01, D02, D05, D10, D12, D13**; phụ thuộc ledger/recovery Phase 03.

- [ ] Owner/Staff đối soát theo period, import/compare/difference/review/finalization và quyền; không coi GET admin reconciliation là UC-5.8 đã xong.
- [ ] Finalized period/audit và adjustment ledger, không sửa payment gốc để khớp sao kê; lưu nguồn/provider references theo retention policy.
- [ ] Complaint/dispute case trong bounded context đã chọn, evidence, actor, states, reason và financial command/reference; không tự tạo service thứ bảy.
- [ ] Dispute freeze/release trên booking/transfer/settlement phù hợp rule, giữ original owner/transaction và notification.
- [ ] Account status/lifecycle command theo D13; không tự thêm Root Admin role từ supplement.
- [ ] Monitoring filters theo user/facility/play date/payment/provider transaction/case; toàn tập có pagination, stable sort và tenant scope.
- [ ] Booking history/audit/reconciliation timeline có before/after/correlation để thực hiện recovery có kiểm chứng.
- [ ] Owner reports dùng tiền thực thu/net/refund/transfer theo D12; utilization có denominator từ hours/exceptions/maintenance, không chỉ reserved_minutes.
- [ ] Platform report từ nguồn từng service/read-model có định nghĩa metric/timezone/date basis, không query chéo DB hay cộng rời rạc thành doanh thu.
- [ ] Drilldown/export trong phạm vi tài liệu, hạn chế private PII/evidence theo actor.
- [ ] Test duplicate statement/import/recovery, finalized-period immutability, disputed payout, wrong-scope access và booked value khác settled revenue.

Migration dự kiến: Payment V6+ reconciliation periods/lines/ledger; case model trong service được D10 chọn; Facility/Identity lifecycle metadata khi cần; version phải kiểm tra lại sau các phase trước.

Điều kiện hoàn thành: một discrepancy/case có thể xử lý tới trạng thái kết thúc đúng tiền/quyền/audit; report không gọi booking_value là doanh thu đã settled.

## Phase 07 — Những flow mở rộng đã có trong tài liệu

Ưu tiên: **P2** hoặc P1 theo UC liên quan. Finding: **A13, A18, A23**. UC: **4.1/4.2 A-2, 6.10, 7.2/7.7, 8.12/8.13**.

- [ ] Fix search/market filter trước limit, pagination/truncated đúng, match ngoài tập 100/200 ban đầu không bị mất.
- [ ] Favorite cơ sở theo UC-4.2 A-2: uniqueness/user scope/lifecycle, không triển khai ngoài nội dung nguồn.
- [ ] Nhóm chơi chat/keep/disband/reuse theo UC-6.10; chốt retention/visibility và read-only sau giải tán.
- [ ] Bổ sung test trực tiếp edit listing UC-7.7: price/deadline/state/same-seller, acquired listing, workflow compensation và retry.
- [ ] Domain notifications cho booking/group/transfer còn thiếu; dedup, recipient và trạng thái delivery truy vết được.
- [ ] Realtime lịch/slot theo yêu cầu nguồn, có scope/version/watermark và reconnect, không để event cũ ghi đè state mới.
- [ ] Hoàn thiện các report filters/drilldown/export còn thiếu trong phạm vi đã chốt.
- [ ] Test chat outsider/revoked membership, favorite duplicate, stale event và market pagination stable sort/privacy.

Migration dự kiến: favorite/chat state trong bounded context xác định từ UC; notification/outbox state tại service sở hữu event. Không tạo model chỉ để làm UI giả thành công.

Điều kiện hoàn thành: API trả dữ liệu thật đủ tập/phạm vi; các operation mở rộng có test trực tiếp, không bỏ P0 chưa xong để ưu tiên tính năng mới.

## Phase 08 — End-to-end, resilience và điều kiện release

Ưu tiên: kiểm tra **P0 security/tiền**, **P1 NFR**. Finding: **A18, A20, A21, A22, A24**. Tất cả chỉ trên môi trường non-production riêng.

- [ ] CI từ checkout sạch build/test/migration/contract; DB credentials mỗi service least privilege, không dùng một superuser cho toàn bộ runtime.
- [ ] Broker integration: publisher confirm/return/unroutable, retry/DLQ routing, inbox replay; facility notice DL routing đúng và broker policy có chứng cứ.
- [ ] Fault-inject REST/broker outage, process crash, duplicate/reorder và recovery đủ sáu service; không chỉ mock dependencies.
- [ ] E2E real service sandbox cho signup/Owner approval, quote/hold/payment/cancel/check-in/complete, hai group modes và transfer/refund/settlement.
- [ ] Security regression public/internal/management, session revoke/role change, private file access, OTP/login throttle và headers spoof; xác minh TLS>=1.2/secure cookie tại deployment test.
- [ ] Load theo NFR: 200 sessions; p95 thông thường 2s, search/create 3s; report 12 tháng 10s. Ghi dataset, throughput, timeout/error và resource limits.
- [ ] Correlation, metrics/alerts/outbox lag/consumer review age; không coi SLA 99,5% đã đạt chỉ vì một test ngắn.
- [ ] Backup/restore rehearsal trên DB synthetic: daily + WAL theo NFR, RPO15m/RTO4h; không dùng hoặc khôi phục vào production.
- [ ] Sandbox provider/SMS/SMTP/MinIO contracts và crash recovery; chưa cấu hình thì ghi “chưa xác minh”, không “pass”.
- [ ] Re-audit 66 UC và release gates: F/P/M/W/T/U có chứng cứ mới, money/state migration upgrade giữ lịch sử và không còn P0 chưa xử lý.

Điều kiện hoàn thành: báo cáo test có provenance, topology/schema riêng và phạm vi thực sự đã xác minh; deploy/publish vẫn theo yêu cầu riêng của người dùng. Checklist này không cho phép truy cập production hay tự deploy.

---

**Điểm dừng hiện tại: Phase 00.** Không có checkbox triển khai nào của Phase 01–08 được đánh dấu hoàn thành.
