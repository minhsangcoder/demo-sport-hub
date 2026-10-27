# Spec Delta

## Purpose

Giải quyết tranh chấp QUYỀN SỬ DỤNG A–B bằng chứng cứ và quyết định của chủ cơ sở; tiền chuyển thẳng A–B do hai bên tự xử lý, không phát sinh Owner hoàn tiền P2P.

## ADDED Requirements

### Requirement: Timeout batches contain only attempts due for resolution

Worker SHALL lọc phiên đủ điều kiện xử lý trước giới hạn batch. Phiên có attendance đúng giờ nhưng chưa đến endsAt SHALL không che các phiên khác đã đến hạn. Khi quyết định, worker SHALL kiểm lại thời gian và attendance dưới khóa hiện hành.

#### Scenario: One hundred attended disputes precede an expired acquisition
- **WHEN** 100 phiên có attendance đúng giờ còn chờ đến endsAt và một phiên khác đã hết payBy
- **THEN** batch vẫn chọn phiên hết payBy để giải phóng; 100 phiên đang được phép chờ không bị kết thúc sớm

### Requirement: A valid peer dispute freezes usage without inventing a booking status

PAYMENT_DECLARED/DISPUTED SHALL chặn sửa/hủy/đăng lại/check-in khi chưa có quyết định cuối, giữ nguyên booking đã CONFIRMED và holder A. B SHALL được khiếu nại ngay khi đang chờ A; ảnh hoặc lời phủ nhận chưa đủ để thay chủ sân.

#### Scenario: Buyer claims transfer but seller says no receipt
- **WHEN** A từ chối nhận sau khai đúng hạn
- **THEN** cả hai chưa được dùng sân bằng credential đó; case nhận bổ sung chứng cứ của hai bên

### Requirement: Only the current facility owner adjudicates peer usage disputes

Owner hiện hành đúng facility SHALL được xem evidence/timeline và chỉ quyết định bàn giao B hoặc A giữ quyền, có reason/evidence/version. Lệnh phân xử SHALL không có số tiền/recipient/hoàn tiền hoặc quyền đóng thay xác nhận tiền của A/B. Staff mặc định và Owner cơ sở khác SHALL không được phân xử hoặc tạo xác nhận ngân hàng.

#### Scenario: A staff refund grant is reused for dispute resolution
- **WHEN** Staff có BOOKING_REFUND gọi resolve peer case
- **THEN** bị từ chối; grant hoàn booking không là quyền phân xử chuyển nhượng

#### Scenario: Owner grants the buyer usage
- **WHEN** Owner xác nhận B có quyền theo chứng cứ trước giờ chơi
- **THEN** chỉ sau handoff commit B mới dùng sân; mức xác nhận là OWNER_ADJUDICATED

#### Scenario: Owner attempts to order or complete a peer refund
- **WHEN** Owner gửi số tiền hoàn, người nhận hoặc yêu cầu ghi Owner đã hoàn P cho B qua case
- **THEN** bị từ chối; quyết định quyền sân không tạo nghĩa vụ chi, receipt hoặc khoản trừ doanh thu cơ sở

#### Scenario: Buyer attends on time but resolution finishes after play begins
- **WHEN** B được ghi có mặt không muộn hơn startsAt, booking chưa check-in/chưa quyết định cuối và Owner phân xử trước endsAt
- **THEN** Owner có thể bàn giao B phần thời gian còn lại, giữ lịch gốc, không tự bù giờ hoặc cấp quyền cho phần đã trôi qua

### Requirement: Physical attendance is independently recorded at the facility

Owner hoặc Staff có quyền check-in đúng cơ sở SHALL ghi A/B có mặt với actor/thời điểm, độc lập cấp quyền sử dụng. Đăng nhập, chat hoặc user tự khai có mặt SHALL không tự thành attendance tại sân.

#### Scenario: Buyer requests help while waiting at the venue
- **WHEN** Staff xác nhận B có mặt nhưng chưa có quyết định case
- **THEN** ghi được attendance mà không check-in B hoặc tự chuyển holder

### Requirement: Both absent at play time leaves usage with the seller

Tới startsAt, chưa có handoff/quyết định bàn giao hợp lệ và cả A/B chưa được ghi có mặt đến mốc đó SHALL kết thúc tranh quyền dùng sân với A giữ quyền. SHALL release freeze/claim đúng attempt nhưng không tạo CHECKED_IN, strike hoặc xác nhận hoàn tiền.

#### Scenario: Neither party attends before the usage deadline
- **WHEN** worker xử lý mốc startsAt và không có attendance hay quyết định bàn giao
- **THEN** A vẫn là holder, không tiêu quyền resale thành công và money case của B vẫn mở nếu cần

#### Scenario: One party is present or a handoff already committed
- **WHEN** có attendance của ít nhất một bên hoặc B đã được handoff trước cutoff
- **THEN** không tự áp mặc định cả hai vắng; chờ Owner xử lý hoặc giữ B nếu đã completed

### Requirement: Expired usage never erases unresolved money

Sau endsAt SHALL không cấp quyền dùng sân quá khứ hoặc kéo dài booking. Case tiền/bằng chứng SHALL vẫn xử lý được; A giữ sân không tự chứng minh B chưa chuyển, A đã hoàn hoặc B gian dối.

#### Scenario: Buyer paid but seller never acknowledged before play
- **WHEN** quyền dùng kết thúc về A nhưng B còn cung cấp biên lai
- **THEN** giữ lịch sử khai tiền để A/B tự trao đổi/xác nhận; Owner không quyết định khoản phải hoàn hoặc trả thay A

### Requirement: Peer money returns are acknowledged by the counterparties separately from facility refunds

Tiền trả lại A→B SHALL do A khai và B xác nhận, hoặc hai bên cùng xác nhận hết yêu cầu tiền; mức xác nhận không là BANK_VERIFIED. Owner SHALL chỉ xem phục vụ phân xử quyền sân, không chọn số tiền, ra lệnh trả hoặc hoàn tất tiền thay A/B. Không tự áp r, trừ ngân hàng A hoặc tạo phiếu chi cơ sở.

#### Scenario: The seller returns the failed purchase directly
- **WHEN** A khai đã trả lại P100000đ kèm reference/evidence và B xác nhận đã nhận
- **THEN** ghi xác nhận giữa A/B, không trở thành facility H80000đ hoặc Owner payout; A tự khai chưa đủ để ghi B đã nhận

#### Scenario: Owner completes the usage decision while money remains disputed
- **WHEN** Owner đã quyết định A hoặc B dùng sân nhưng hai bên chưa thống nhất tiền
- **THEN** quyết định chỉ có hiệu lực quyền sử dụng; không tự ghi đã hoàn, đã nhận hoặc bắt cơ sở chi tiền

### Requirement: Fraud penalties require a reasoned intentional fraud finding

Case SHALL phân biệt chưa chứng minh được, xác nhận không có tiền, và cố ý khai chuyển giả. Chỉ finding cố ý gian dối có actor/reason/evidence SHALL tạo strike theo policy; quyết định retry không tạo finding mới.

#### Scenario: The evidence is inconclusive
- **WHEN** Owner chưa đủ căn cứ hoặc hai bên đều không đến
- **THEN** không tự cấm chợ/strike, dù quyền dùng sân vẫn phải kết thúc theo hạn

### Requirement: Conflicting terminal commands are fenced and recoverable

Seller ack, Owner resolution, expiry và check-in SHALL dùng cùng authoritative usage fence. Một quyết định cuối đã commit SHALL không bị lời khai muộn, worker hoặc response mất lật ngược; các bên đọc được trạng thái cần đồng bộ thay vì bị yêu cầu trả lại.

#### Scenario: Owner resolution races the both absent timeout
- **WHEN** hai lệnh cạnh tranh ở startsAt hoặc retry sau mất response
- **THEN** chỉ một quyết định hợp lệ thắng, cùng holder/token/claim nhất quán và money case không tự bị hoàn hai lần

### Requirement: Final usage decisions release only the dispute bank restriction

Case P2P SHALL khóa ngân hàng chỉ khi chưa có quyết định cuối quyền sân tại Booking. Quyết định cuối SHALL gỡ riêng nguồn case dù case tiền còn OPEN, không đóng tiền hoặc xóa lịch sử. Booking còn lịch và nghĩa vụ trả/hoàn đã xác nhận chưa xong SHALL vẫn khóa riêng. Owner SHALL không đóng tiền thay A/B hoặc mở khóa toàn bộ user.

#### Scenario: Canonical decisions are final despite a stale projection
- **WHEN** Booking đã ghi COMPLETED/ABORTED_A_RETAINS/TIMED_OUT_A_RETAINS/EXPIRED nhưng Transfer chưa nhận projection
- **THEN** nguồn khóa case đã hết hiệu lực; chỉ đóng chat hoặc projection đơn lẻ không thay thế quyết định Booking

#### Scenario: Acknowledged money remains unfinished
- **WHEN** A đã nhận trách nhiệm trả B nhưng B chưa xác nhận hoàn tất, hoặc khoản hoàn/chi còn PENDING/PROCESSING/UNKNOWN
- **THEN** kiểm nguồn nghĩa vụ tiền riêng dù quyền sân đã chốt; lời khai tiền đơn phương chưa được bên trả chấp nhận không tạo nguồn này

#### Scenario: Both absent default returns usage to the seller
- **WHEN** A giữ sân theo timeout nhưng B vẫn khiếu nại tiền đã chuyển
- **THEN** giải phóng usage freeze/claim và nguồn khóa tranh chấp; case tiền vẫn lưu nhưng lời khai B không giữ khóa A, còn booking của A và nghĩa vụ trả đã xác nhận vẫn được kiểm riêng; không cần đổi CHECKED_IN

#### Scenario: Owner decides usage but another booking remains
- **WHEN** Owner ghi quyết định cuối quyền sân nhưng user còn lịch sân chưa kết thúc
- **THEN** nguồn khóa case được gỡ và khóa ngân hàng vẫn còn theo booking, không ghi Owner đã hoàn tiền

#### Scenario: An operator attempts to override the personal restriction
- **WHEN** Owner/Staff có quyền vận hành hoặc hoàn booking gọi bỏ khóa ngân hàng A/B khi case còn mở
- **THEN** bị từ chối; quyền cơ sở không cấp quyền thay thông tin nhận tiền cá nhân
