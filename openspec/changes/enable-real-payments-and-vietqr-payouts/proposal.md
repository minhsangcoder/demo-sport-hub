> **HISTORY ONLY — NOT AN ACTIVE REQUIREMENT SOURCE.**
> Read `openspec/CURRENT_CHANGE.md` at the project root for the current proposal.
> Do not apply, merge, or resume requirements/tasks from this document unless
> the user explicitly requests this historical change. Task counts are historical.

# Proposal

## Why

SportHub hiện cấp quyền từ thanh toán DEMO; adapter chỉ trả mã tham chiếu, chưa tạo QR chuyển khoản hoặc xác nhận tiền thật. Cần đưa luồng thu tiền vào sử dụng thực tế qua payOS, đồng thời giúp cơ sở trả người bán/hoàn khách bằng VietQR và lưu mã giao dịch do người có quyền xác nhận.

## What Changes

- **Thu tiền thật qua payOS:** khách thanh toán vào tài khoản Owner đã gắn đúng cơ sở. Server xác thực webhook, kiểm tra lại giao dịch, số tiền, đơn hàng và tài khoản nhận; chỉ xác nhận booking/bàn giao khi đủ tiền và điều kiện giữ sân còn hợp lệ. Return URL, ảnh chuyển khoản và nút trên trình duyệt không cấp quyền.
- **B nhận quyền độc lập trả A:** B trả đủ P cho cơ sở, handoff thành công thì có quyền theo lịch; Owner còn nghĩa vụ trả A. SportHub không giữ tiền hoặc hưởng phí/doanh thu giao dịch.
- **Chỉ dùng tỷ lệ Owner cấu hình:** F=HALF_UP(P×feeBps/10000), A nhận N=P−F. Đây là phí cơ sở giữ lại khi hoàn trả người sang nhượng; không có mức cố định/mặc định 2%, không thêm phí nền tảng. Chính sách hoàn hủy đã chốt của cơ sở vẫn xác định số được hoàn, không bị tính thêm một lần phí chuyển nhượng.
- **Thông tin nhận tiền bắt buộc:** A chọn ngân hàng/BIN và nhập số tài khoản trước khi đăng sang nhượng; khách yêu cầu khoản hoàn dương phải cung cấp tài khoản nhận của mình. Lưu phiên bản/snapshot cho từng nghĩa vụ; thông tin ngân hàng không xuất hiện trên chợ công khai. Guest vẫn không cần tài khoản SportHub.
- **VietQR cho chi trả thủ công:** màn hình Owner/Staff có quyền tự dựng QR đúng ngân hàng, số tài khoản, số tiền phải trả/hoàn và nội dung tham chiếu chuẩn. Sau khi chuyển khoản qua app ngân hàng, operator bắt buộc nhập mã giao dịch rồi bấm Hoàn tất. Server lưu số tiền/người nhận, mã giao dịch, actor, thời điểm và chống xác nhận trùng; QR chưa có nghĩa là đã chuyển.
- **Phân biệt mức xác nhận:** tiền vào là xác nhận từ payOS; tiền trả A/hoàn khách là cơ sở xác nhận đã chuyển khoản thủ công. Không giả rằng nhập mã giao dịch là xác thực ngân hàng; không gọi API chi hộ hoặc thu thập thông tin đăng nhập ngân hàng.
- **Lỗi và báo cáo:** phục hồi cùng đơn khi timeout/mất webhook; thu thiếu, thừa, trùng hoặc đến muộn đều có dữ liệu đối soát. Handoff xác nhận ABORTED sau thu tiền tạo nghĩa vụ hoàn đủ khoản thu kỹ thuật, không áp phí/policy hủy. Báo cáo tách REAL/DEMO/UNKNOWN, R tiền sân, P dòng tiền mua lại, F phí cơ sở, N trả A và H hoàn khách.
- **BREAKING — hành vi LIVE:** không cho thao tác DEMO cấp quyền hoặc hoàn tất nghĩa vụ tiền thật; API đăng sang nhượng/yêu cầu hoàn bổ sung destination revision bắt buộc theo nghiệp vụ. Giữ CHECKED_IN, quyền theo cơ sở, Guest holder NULL và dữ liệu/migrations cũ.

## Capabilities

### New Capabilities

`openspec list --specs` hiện chưa có main spec. Vì vậy dùng ADDED cho các requirement REAL mới; giữ nguyên các đường dẫn capability đã có trong change DEMO chưa sync, với tên requirement phân biệt và phạm vi REAL rõ ràng.

- `payments/bank-qr`: kết nối payOS theo cơ sở, tạo QR thu tiền, xác thực, đối soát và tách môi trường.
- `payments/manual-disbursements`: tài khoản nhận có phiên bản, VietQR chi/hoàn, thao tác nhập mã giao dịch và audit có phân quyền.
- `booking/paid-check-in`: xác nhận đủ tiền thật và kiểm soát mọi đường cấp credential/check-in cho online, quầy và nhóm.
- `transfers/settlement`: điều khoản REAL theo phí Owner, bàn giao độc lập trả A, nghĩa vụ chi và lỗi kỹ thuật.
- `booking/facility-refunds`: hoàn theo policy, đúng buyer/payer/Guest, thông tin ngân hàng và xác nhận hoàn thủ công.
- `reporting/collection-evidence`: chứng từ tiền vào, bản ghi chuyển khoản thủ công, phân loại tiền và chống cộng trùng.
- `reporting/facility-performance`: báo cáo cơ sở có dữ liệu REAL và mức xác nhận minh bạch.
- `reporting/platform-revenue`: giám sát chỉ đọc, lỗi đối soát và bảo đảm không quy phí cơ sở thành doanh thu SportHub.

### Modified Capabilities

Không có main spec đã phát hành để dùng MODIFIED. Change này bổ sung phạm vi REAL; các yêu cầu DEMO của `fix-owner-reports-and-revenue` chỉ tiếp tục áp dụng cho môi trường/dữ liệu DEMO, không làm điều kiện miễn xác thực tiền thật.

## Impact

- Backend: Payment provider/order/callback/evidence/sync; Booking collection/check-in/lease/cancellation/finance/report; Transfer quote/listing/acquisition/workflow; Facility policy/context và Identity grants hiện hữu; Gateway webhook route. Tái dùng outbox/inbox, khóa sân, receipts và phép tính VND.
- Frontend đang dùng: thanh toán online/quầy/nhóm/chuyển nhượng; form đăng tin và yêu cầu hoàn; cấu hình kênh nhận tiền Owner; màn hình VietQR và nhập mã giao dịch Owner/Staff; lịch sử khách, báo cáo Owner và giám sát Admin. Giữ tiếng Việt, không sửa cây `frontend/src` legacy.
- Database/API: migration bổ sung trong apply để lưu kênh payOS, QR session, chứng từ REAL, destination revisions và xác nhận chi thủ công; mở rộng OpenAPI/hợp đồng và giữ nguyên lịch sử DEMO/legacy. Không sửa migration đã phát hành hoặc đổi tiền DEMO thành REAL.
- Tích hợp ngoài: payOS và danh mục BIN ngân hàng; dựng VietQR chi trả tại server bằng thư viện QR hiện có, không phụ thuộc API Generate sắp ngừng hỗ trợ. Chi tiết nguồn chính thức, lựa chọn kỹ thuật và giới hạn nằm trong design.
- Ngoài phạm vi: tự động chi hộ/ngân hàng, ví hoặc giữ tiền SportHub, phí nền tảng, xác minh danh tính chủ tài khoản ngân hàng tự động, chargeback thẻ, thay toàn bộ hệ thống hoặc triển khai thực tế trong bước proposal. Kiểm thử tự động dùng mock; thử tiền thật là bước nghiệm thu có kiểm soát riêng.
