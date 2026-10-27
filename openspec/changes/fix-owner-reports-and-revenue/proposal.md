> **HISTORY ONLY — NOT AN ACTIVE REQUIREMENT SOURCE.**
> Read `openspec/CURRENT_CHANGE.md` at the project root for the current proposal.
> Do not apply, merge, or resume requirements/tasks from this document unless
> the user explicitly requests this historical change. Task counts are historical.

# Proposal

## Why

SportHub phục vụ đồ án cần trình diễn đầy đủ đặt sân, chuyển nhượng, trả người bán, hoàn tiền và báo cáo. Các khoản tiền được giả lập và ghi nhận phía server để luồng nghiệp vụ hoạt động từ đầu đến cuối; không cần tích hợp hoặc xác minh ngân hàng thật.

## What Changes

- **Phạm vi đã chốt: DEMO.** Khách bấm Thanh toán DEMO, Owner/Staff bấm Trả tiền DEMO hoặc Hoàn tiền DEMO; server kiểm tra quyền, số tiền và trạng thái rồi lưu kết quả thành công/thất bại. Kết quả DEMO trong database là căn cứ chuyển giai đoạn, không cần tiền thật, sao kê hoặc xác nhận ngân hàng.
- **Giữ chủ thể kinh doanh:** B thanh toán DEMO cho cơ sở; server gắn facility/Owner nhận tiền theo booking, không để browser chọn tùy ý. SportHub không hưởng phí/doanh thu. Không cần Owner đăng ký merchant, kết nối tài khoản ngân hàng hay xác minh tài khoản nhận trước khi demo.
- **Phí do Owner đặt:** mỗi cơ sở có phí chuyển nhượng x%, độc lập tỷ lệ hoàn hủy r%. F=HALF_UP(P×x/10000), N=P−F, bps nguyên và VND nguyên đồng cho giao dịch mới. A/B thấy P/x/F/N trước chấp nhận; lưu snapshot để Owner đổi phí không đổi giao dịch đã chốt. Không mặc định phí 2% của nền tảng.
- **B dùng sân độc lập trả A:** thanh toán DEMO đủ P thành công kích hoạt bàn giao booking tự động cho B, không có bước Owner duyệt thanh toán. B được check-in theo lịch/guard hiện có, không chờ Owner trả N cho A.
- **Trả A bằng thao tác DEMO:** handoff thành công tạo một khoản cơ sở phải trả N cho A. Owner hoặc active Staff được cấp TRANSFER_PAYOUT tại cơ sở bấm Trả tiền DEMO cho A để hoàn tất; có thể giả lập thất bại/thử lại. Không cần kiểm tra số dư, giữ tiền, tài khoản ngân hàng hoặc chứng từ final từ provider thật.
- **Hoàn hủy theo r:** Owner hoặc Staff BOOKING_REFUND thực hiện Hoàn tiền DEMO theo quote server. Trước resale dùng khoản đã thanh toán hợp lệ; sau resale dùng P của buyer hiện tại và hoàn buyer đó. Nhóm phân bổ đúng contribution/payer; Guest không cần Customer giả. r0% không tạo giao dịch tiền0. Trả N cho A và hoàn H cho B là hai nghiệp vụ/quyền riêng.
- **Lỗi vẫn đầy đủ nghiệp vụ:** payment DEMO thất bại không cấp quyền; handoff đã xác nhận thất bại sau thu P tạo quyền hoàn đủ P/F0/N0. Trả A lỗi không mất nợ hoặc quyền B; hoàn lỗi chưa tính đã hoàn. Bấm trùng/retry không thu, chi, hoàn hoặc đổi holder hai lần. Timeout kỹ thuật không tự kết luận thất bại nếu thao tác đã commit.
- **Báo cáo từ database:** tách R tiền sân, F phí cơ sở, N phải trả/đã trả A, H hoàn DEMO thành công và P cashflow. Khoản thu kinh doanh cơ sở R+F, sau hoàn R+F−H; không cộng toàn bộ P thành doanh thu. KPI/chart/bảng sân/giao dịch cùng kỳ theo timezone cơ sở và có nhãn Dữ liệu DEMO. Không dùng số mẫu viết cứng để thay giao dịch.
- **Giữ guard và quyền:** thu đủ DEMO mới check-in, giữ CONFIRMED→CHECKED_IN→COMPLETED, nullable Guest/payer khác holder/createdBy audit. Owner cấu hình x/r và cấp quyền; Staff không tự cấp, đổi số tiền/người nhận hoặc tự có report Owner. Kiểm tra quyền tại mỗi thao tác HTTP, không cần quy trình duyệt nhiều cấp.
- **Cấu hình bắt buộc chỉ phục vụ nghiệp vụ:** giữ quyết định chưa có refund policy thì chặn booking mới; chưa có x thì chặn chuyển nhượng mới. Explicit0% hợp lệ. Không có điều kiện xác minh ngân hàng, merchant, nguồn tiền hoặc beneficiary để mở luồng DEMO.
- **Rút gọn kỹ thuật:** tận dụng provider DEMO, payment/receipt/history, outbox/inbox và các guard hiện có. Lưu giao dịch DEMO/settlement/refund đủ amount/status/actor/thời gian/idempotency; không bắt buộc xây sổ tài chính ngân hàng, funding/reserve, bank-return/chargeback, key TTL/region, đối soát sao kê, nhiều người duyệt hoặc live acceptance.
- **P2 và tiền thật:** Excel/PDF, phân tích nâng cao và tích hợp thanh toán thật thuộc change riêng sau đồ án, không phải điều kiện nghiệm thu DEMO.

## Capabilities

### New Capabilities

- `reporting/collection-evidence`: giao dịch DEMO phía server, khoản đã áp dụng, chống cộng trùng và tách trạng thái thu/trả/hoàn.
- `reporting/facility-performance`: báo cáo Owner cùng kỳ, tiền sân/phí cơ sở/hoàn/công nợ và giao dịch có nhãn DEMO.
- `booking/paid-check-in`: thanh toán DEMO đủ và có quyền mới sử dụng sân; không phụ thuộc việc trả A.
- `booking/facility-refunds`: quote/hủy/hoàn DEMO theo r của Owner, đúng người nhận và quyền BOOKING_REFUND.
- `transfers/settlement`: B trả DEMO cho cơ sở, nhận quyền; Owner/Staff có quyền trả N DEMO cho A, kèm lỗi/thử lại.
- `reporting/platform-revenue`: giữ đường dẫn spec hiện có, nội dung là theo dõi giao dịch DEMO và quyền Admin; không tạo doanh thu SportHub hoặc hệ thống đối soát ngân hàng.

### Modified Capabilities

Giữ tương thích change counter ownership: Customer/linked Customer/Guest, nullable holder và quyền vận hành. Không sửa main specs hoặc tự archive/sync trong update này.

## Impact

- Backend: Payment/Transfer/Booking dùng kết quả DEMO đã lưu để tiến bước; Facility/Identity giữ phí/policy/quyền theo cơ sở. Mọi amount/payee/facility do server tính và kiểm tra.
- Frontend: các thao tác Thanh toán DEMO, Trả tiền DEMO cho A, Hoàn tiền DEMO; cấu hình x/r, quyền Staff, báo cáo Owner và trạng thái A/B rõ ràng. Không yêu cầu tài khoản ngân hàng, không sửa cây frontend/src legacy.
- Database/API: tái dùng schema và endpoints phù hợp; bổ sung ít trường/bảng DEMO cho settlement/refund/audit khi cần. Giữ dữ liệu và migrations cũ; không đổi refund review cũ thành hoàn thành công hoặc áp x hồi tố.
- Code tài chính production đã viết dở được giữ nguyên trong lần cập nhật kế hoạch. Apply sau sẽ bỏ các bước bank/merchant/funding khỏi luồng DEMO và tái dùng phần đơn giản phù hợp; không cần hoàn thiện các module production để đạt acceptance.
- Chỉ cập nhật proposal/design/specs/tasks ở bước này. Không sửa code, tạo/chạy migration, đổi dữ liệu, triển khai hoặc tự tiếp tục apply.
