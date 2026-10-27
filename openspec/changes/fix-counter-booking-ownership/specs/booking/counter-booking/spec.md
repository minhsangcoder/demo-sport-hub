> **HISTORY ONLY — NOT AN ACTIVE REQUIREMENT SOURCE.**
> Read `openspec/CURRENT_CHANGE.md` at the project root for the current proposal.
> Do not apply, merge, or resume requirements/tasks from this document unless
> the user explicitly requests this historical change. Task counts are historical.

# Spec Delta

## Purpose

Quy định quyền sở hữu và quyền vận hành booking tại quầy xuyên suốt Backend và Frontend để khách vãng lai không bị đồng nhất với nhân viên tạo booking. Bảo đảm booking online, Customer liên kết, thanh toán, check-in, chuyển nhượng và chuyển đổi dữ liệu cũ giữ được tính đúng đắn và bằng chứng nghiệp vụ.

## ADDED Requirements

### Requirement: Online customer ownership
Hệ thống SHALL giữ Customer đặt online làm người tạo và chủ sở hữu tài khoản của booking; quyền QR và chuyển nhượng hợp lệ không thay đổi bởi hỗ trợ Guest tại quầy.

#### Scenario: Customer creates an online booking
- **WHEN** Customer tạo booking online từ hold của mình
- **THEN** booking và quyền sử dụng reservation gắn với Customer đó
- **AND** booking tiếp tục tuân theo thanh toán, QR và chính sách chuyển nhượng hiện hành

### Requirement: Counter booking for a linked customer
Hệ thống SHALL ghi nhận người vận hành là người tạo và Customer được Identity xác minh là chủ sở hữu khi đặt tại quầy cho tài khoản liên kết hợp lệ.

#### Scenario: Staff books for an eligible customer
- **WHEN** Staff có quyền tại cơ sở tạo counter booking và lookup trả một Customer hợp lệ
- **THEN** createdBy là Staff, customerId và currentHolderId là Customer
- **AND** quyền sử dụng reservation thuộc Customer, snapshot giữ người vận hành và liên kết lúc tạo
- **AND** Customer thấy booking trong tài khoản và có quyền owner phù hợp trạng thái

#### Scenario: A staff member is also the verified customer
- **WHEN** tài khoản đồng thời có STAFF và CUSTOMER được lookup liên kết đúng với chính nó
- **THEN** tài khoản có quyền Customer thực sự, không bị coi là operator Guest chỉ vì hai ID trùng nhau

### Requirement: Counter guest has no account owner
Hệ thống SHALL tạo booking Guest không có chủ sở hữu tài khoản khi lookup hợp lệ không trả Customer; SHALL giữ người tạo và snapshot khách riêng, không gán UUID Customer giả hoặc tài khoản người vận hành làm owner.

#### Scenario: Staff books for an unlinked guest
- **WHEN** người vận hành có quyền tạo counter và lookup trả customerId NULL
- **THEN** customerId và currentHolderId của booking là NULL, quyền sử dụng tài khoản của reservation đã chuyển đổi cũng vắng mặt
- **AND** createdBy và operator vẫn là người vận hành, snapshot giữ tên và điện thoại hợp lệ của khách
- **AND** hệ thống không tự tạo tài khoản Identity cho khách

### Requirement: Verified lookup fails closed
Hệ thống SHALL giữ quy tắc khớp số điện thoại chính xác, đã xác minh, tài khoản ACTIVE có CUSTOMER và không ADMIN; lỗi lookup SHALL được phân biệt với kết quả không tìm thấy.

#### Scenario: A phone has no eligible match
- **WHEN** số điện thoại không khớp chính xác một tài khoản đủ điều kiện, kể cả tài khoản chưa xác minh hoặc bị khóa
- **THEN** counter booking sử dụng nhánh Guest, không tự đoán tài khoản khác

#### Scenario: Lookup is unavailable
- **WHEN** dịch vụ không thể xác minh kết quả lookup
- **THEN** yêu cầu thất bại thay vì tạo Guest hoặc gán caller làm owner
- **AND** API counter một bước không để lại hold, booking, snapshot hoặc idempotency mới dở dang

### Requirement: Equivalent counter creation entrypoints
Hệ thống SHALL áp dụng cùng chính sách ownership, kiểm tra giá, quyền cơ sở và snapshot cho API counter một bước và luồng hold/create có counter=true; mỗi giao dịch tạo booking SHALL commit đầy đủ hoặc rollback.

#### Scenario: Either counter entrypoint creates a guest
- **WHEN** người vận hành dùng một trong hai đường tạo counter hợp lệ
- **THEN** kết quả sở hữu Guest và dữ liệu liên hệ đáp ứng cùng quy tắc
- **AND** lỗi trong transaction tạo booking không để lại booking thiếu snapshot hoặc reservation sai owner

### Requirement: Counter idempotency rechecks authority
Hệ thống SHALL trả cùng booking cho retry cùng actor, key và payload; SHALL từ chối payload đổi hoặc quyền vận hành đã bị thu hồi, không cấp thêm quyền owner qua response replay.

#### Scenario: Counter creation is retried concurrently
- **WHEN** các yêu cầu cùng key và payload tới đồng thời
- **THEN** chỉ có một booking, một snapshot khách và một sự kiện tạo tương ứng

#### Scenario: A former operator replays a request
- **WHEN** người tạo dùng key cũ sau khi mất quyền counter tại cơ sở
- **THEN** replay bị từ chối và không tiết lộ booking hoặc mã qua quyền creator cũ

### Requirement: Hold authority survives conversion safely
Hệ thống SHALL giữ hold chưa chuyển đổi thuộc tài khoản thao tác; sau khi có booking, truy cập hold SHALL theo quyền booking và không cho phép xóa hold để bỏ qua lifecycle booking.

#### Scenario: An unconverted counter hold is accessed
- **WHEN** người khác chỉ biết hold ID nhưng không phải actor giữ hold
- **THEN** người đó không được đọc hoặc release hold

#### Scenario: A guest booking already uses the hold
- **WHEN** người được phép đọc booking truy cập hold đã chuyển thành booking Guest có holderId NULL
- **THEN** GET hold trả dữ liệu hợp lệ theo quyền booking, không lỗi vì owner NULL
- **AND** DELETE hold hoặc tạo booking mới từ hold đó bị từ chối; hủy booking phải đi qua API hủy

### Requirement: Reservation limits and exclusivity are preserved
Hệ thống SHALL tiếp tục tính counter hold pending theo người vận hành và chống đặt trùng sân/thời gian dù holder đã chuyển sang Customer hoặc NULL.

#### Scenario: An operator reaches the pending limit
- **WHEN** các counter booking pending của người vận hành đã đạt giới hạn
- **THEN** yêu cầu giữ chỗ tiếp theo bị từ chối, kể cả các booking trước thuộc Customer liên kết hoặc Guest

#### Scenario: Counter and online requests compete for one interval
- **WHEN** counter và online cùng đặt một khoảng sân
- **THEN** chỉ một yêu cầu giữ được reservation còn hiệu lực

### Requirement: Personal booking lists exclude operational creation
Hệ thống SHALL loại booking chỉ do người gọi tạo tại quầy khỏi danh sách cá nhân; danh sách chỉ phản ánh quyền owner hoặc quan hệ lịch sử Customer được phép, thống nhất giữa các API cá nhân.

#### Scenario: Staff has only created bookings for customers and guests
- **WHEN** Staff gọi danh sách cá nhân
- **THEN** các booking chỉ có quan hệ creator/operator không được trả như booking của Staff
- **AND** danh sách vận hành có scope cơ sở vẫn hiển thị chúng khi được phép

#### Scenario: A linked customer or legitimate previous holder requests bookings
- **WHEN** Customer có quyền owner hoặc quan hệ lịch sử chuyển nhượng hợp lệ
- **THEN** các API cá nhân trả booking theo cùng chính sách quan hệ
- **AND** quyền xem lịch sử không cấp mã hoặc QR của owner mới

### Requirement: Operational access is scoped to the facility
Hệ thống SHALL kiểm tra permission hiện hành của đúng cơ sở cho quyền vận hành; createdBy, UUID booking và mã check-in SHALL không tự cấp quyền đọc hoặc thao tác.

#### Scenario: A staff member targets another facility
- **WHEN** Staff yêu cầu đọc vận hành hoặc check-in tại cơ sở không có quyền tương ứng
- **THEN** yêu cầu bị từ chối dù Staff biết booking ID hoặc mã

#### Scenario: An unrelated customer guesses an identifier
- **WHEN** Customer không sở hữu, không có quan hệ lịch sử hợp lệ và không có quyền cơ sở yêu cầu detail
- **THEN** dữ liệu booking và liên hệ khách không được trả

### Requirement: Owner QR requires genuine current account ownership
Hệ thống SHALL chỉ cấp và chấp nhận QR chủ sở hữu cho current account owner hợp lệ; operator Guest SHALL không nhận QR này qua quyền tạo, đọc hoặc check-in tại cơ sở.

#### Scenario: An operator opens a confirmed guest booking
- **WHEN** operator có cả quyền đọc và check-in mở detail hoặc operations của booking Guest
- **THEN** checkinToken và checkinQrSvg theo quyền owner là NULL
- **AND** operator tiếp tục quản lý booking bằng quyền cơ sở và mã thủ công

#### Scenario: An old operator QR is presented
- **WHEN** signed token từng được cấp theo Staff fallback được gửi cho booking đã nhận diện là Guest
- **THEN** token bị từ chối mà không đổi trạng thái booking

### Requirement: Guest manual check-in keeps existing guards
Hệ thống SHALL cho nhân viên có BOOKING_CHECK_IN check-in Guest bằng mã booking tại đúng cơ sở, giữ kiểm tra CONFIRMED, bằng chứng thanh toán, transfer lock và cửa sổ thời gian hiện hành.

#### Scenario: A paid guest arrives within the check-in window
- **WHEN** nhân viên đủ quyền gửi mã hợp lệ từ 30 phút trước startsAt đến trước endsAt và không có transfer acquisition LOCKED
- **THEN** booking chuyển sang CHECKED_IN đúng một lần, actor là người thực hiện và phương thức là BOOKING_CODE
- **AND** không yêu cầu Guest có tài khoản

#### Scenario: Check-in violates a guard
- **WHEN** booking chưa trả tiền, đã hủy/hết hạn/check-in, sai mã, ngoài cửa sổ hoặc nhân viên không đủ quyền
- **THEN** check-in bị từ chối và không phát sinh chuyển trạng thái

### Requirement: Counter payment payer is independent of booking owner
Hệ thống SHALL giữ payable DEMO cho Customer đang sở hữu hoặc original counter operator có BOOKING_CREATE_COUNTER hợp lệ; payer SHALL là tài khoản thực hiện payment và không được biến thành booking owner vì thao tác thanh toán.

#### Scenario: The original operator pays for a guest
- **WHEN** original operator đủ quyền yêu cầu thanh toán cho Guest PENDING còn payable
- **THEN** payable có payerId của operator và đúng booking/amount/currency/deadline
- **AND** owner của booking vẫn NULL

#### Scenario: Another cashier or unrelated customer tries to pay
- **WHEN** người không phải current owner hoặc original operator yêu cầu payable, kể cả Staff khác có quyền thu ngân
- **THEN** yêu cầu bị từ chối theo chính sách hiện tại, không tự mở rộng quyền payer trong change này

### Requirement: Payment outcomes remain reliable for ownerless bookings
Hệ thống SHALL áp dụng payment hợp lệ cho Guest mà không cần owner tài khoản; SHALL giữ kiểm tra payer, amount/currency, deadline, reservation, chống xử lý lặp và đối soát hiện hành.

#### Scenario: A valid guest payment succeeds
- **WHEN** payment thành công từ original operator phù hợp payable đến khi booking và reservation còn hợp lệ
- **THEN** booking được xác nhận, reservation thành BOOKED và receipt được ghi một lần
- **AND** owner không đổi từ NULL sang payer

#### Scenario: A duplicate or invalid outcome arrives
- **WHEN** callback/event lặp hoặc có payer sai, tiền sai, đến muộn hay booking đã hủy/hết hạn
- **THEN** hệ thống giữ hành vi deduplication hoặc reconciliation tương ứng
- **AND** không hồi sinh booking, không thay bằng chứng tiền và không lỗi vì owner NULL

### Requirement: Cancellation expiry and lifecycle remain compatible
Hệ thống SHALL giữ lifecycle và chính sách hủy hiện tại; operator Guest SHALL không được dùng chính sách Customer tự hủy đã thanh toán chỉ vì là creator hoặc payer.

#### Scenario: An unpaid guest booking expires
- **WHEN** thời hạn thanh toán của Guest PENDING kết thúc
- **THEN** booking thành EXPIRED, reservation được giải phóng và audit có owner NULL hợp lệ

#### Scenario: An operator cancels a paid guest booking
- **WHEN** operator yêu cầu hủy booking Guest đã thanh toán
- **THEN** hệ thống giữ điều kiện BLOCKED_RULE của hủy phía cơ sở khi protocol refund chưa có
- **AND** không áp dụng CUSTOMER_SELF_NO_REFUND thay khách

#### Scenario: Checked-in play reaches completion
- **WHEN** actor đủ quyền hoàn tất booking CHECKED_IN sau endsAt
- **THEN** booking chuyển sang COMPLETED theo quy tắc hiện hành
- **AND** không có trạng thái IN_PROGRESS mới hoặc thay đổi quyền Staff trong catalog

### Requirement: Transfer requires an account owner
Hệ thống SHALL chặn Guest khỏi chuyển nhượng và trả lý do chặn ổn định cho downstream; chuyển nhượng Customer hợp lệ SHALL tiếp tục chuyển quyền tài khoản và thu hồi mã/QR cũ.

#### Scenario: A guest is inspected or offered for transfer
- **WHEN** downstream inspect hoặc người gọi yêu cầu đăng chuyển nhượng booking không có account owner
- **THEN** inspect trả lý do không đủ điều kiện, còn yêu cầu chuyển nhượng bị từ chối
- **AND** không có lỗi server do NULL và không tạo seller/lease giả

#### Scenario: A customer handoff completes
- **WHEN** Customer thực sự chuyển nhượng booking đủ điều kiện cho buyer đã thanh toán hợp lệ
- **THEN** customerId, currentHolderId và quyền reservation chuyển sang buyer; creator và snapshot liên kết lúc tạo giữ nguyên
- **AND** mã/QR cũ không dùng được và owner mới có quyền sử dụng hợp lệ

### Requirement: Historical access does not disclose guest contact or new codes
Hệ thống SHALL áp dụng cùng chính sách che dữ liệu cho Booking và snapshot Guest lồng trong operations; quan hệ lịch sử SHALL không tự cấp liên hệ khách khác hoặc mã/QR owner mới.

#### Scenario: An intermediate seller reads after two handoffs
- **WHEN** Customer B từng mua từ A rồi bán cho C và yêu cầu lịch sử booking counter của A
- **THEN** B chỉ nhận dữ liệu lịch sử được phép, không nhận snapshot liên hệ Guest của A hoặc mã/QR hiện tại của C

### Requirement: Ownership consistency is enforced at commit
Hệ thống SHALL từ chối booking thiếu nhất quán về owner/reservation; ownerless SHALL chỉ hợp lệ cho counter Guest có snapshot, còn online và booking nhóm SHALL có account owner.

#### Scenario: Incomplete ownerless data is submitted
- **WHEN** transaction tạo một owner NULL còn owner kia có giá trị, Guest thiếu snapshot hoặc holder reservation khác owner booking
- **THEN** transaction không được commit

#### Scenario: Online or group ownership is removed
- **WHEN** transaction cố xóa account owner của booking online hoặc nhóm
- **THEN** transaction bị từ chối mà không làm yếu các invariant thanh toán hoặc attendance hiện hành

#### Scenario: Counter creation assembles records in one transaction
- **WHEN** transaction tạo booking, snapshot và đổi holder reservation theo thứ tự các ghi hiện có
- **THEN** dữ liệu hợp lệ được commit khi các invariant thỏa ở cuối transaction

### Requirement: Nullable ownership is explicit in the API contract
Hệ thống SHALL trả owner vắng mặt bằng JSON null và mô tả nullable trong contract; SHALL giữ createdBy, endpoint và response shape hiện có. Frontend SHALL tiêu thụ contract nullable trong cùng change.

#### Scenario: A guest booking or converted hold is serialized
- **WHEN** người được phép nhận response Guest hoặc hold đã chuyển đổi
- **THEN** customerId/currentHolderId hoặc holderId tương ứng là JSON null, không phải chuỗi null hoặc UUID giả
- **AND** createdBy của booking vẫn là UUID operator thật

#### Scenario: Full change acceptance is reported
- **WHEN** change được báo cáo hoàn tất
- **THEN** Backend/API và Frontend cùng đạt acceptance cho online, linked counter và Guest
- **AND** cập nhật type nullable, điều hướng và check-in bằng mã trên UI đã hoàn thành, không bị để lại ngoài scope

### Requirement: Guest audit and code issuance preserve evidence
Hệ thống SHALL ghi lịch sử hợp lệ khi owner NULL và phát hành mã duy nhất cho Guest; SHALL giữ lịch sử, snapshot khách và registry đã có bất biến.

#### Scenario: A guest lifecycle event is recorded
- **WHEN** tạo booking, payment, expiry, check-in hoặc review tạo audit cho Guest
- **THEN** audit lưu owner NULL thực sự và actor/source đúng mà không làm rollback chỉ vì owner vắng mặt

#### Scenario: A guest receives a manual code
- **WHEN** booking Guest mới được phát hành mã
- **THEN** mã có thể được dùng tại cơ sở theo quyền vận hành, bản ghi phát hành không giả định Staff là owner
- **AND** mã lịch sử không được tái gán cho booking khác

### Requirement: Known legacy fallbacks grant no customer ownership
Hệ thống SHALL nhận diện Guest fallback legacy có đủ bằng chứng để che effective owner và chặn quyền Customer ngay cả trước correction vật lý; SHALL không tự sửa row hoặc đoán Customer cho dữ liệu mơ hồ.

#### Scenario: A proven legacy fallback is read before correction
- **WHEN** snapshot chưa liên kết và dữ liệu xác nhận ba holder vẫn là operator mà không có bằng chứng nhận sở hữu hợp lệ
- **THEN** response công khai biểu diễn effective owner NULL và không cấp danh sách cá nhân, QR hoặc chuyển nhượng theo Staff fallback
- **AND** dữ liệu lưu trữ và audit cũ được giữ để đối soát

### Requirement: Legacy classification is read-only by default
Công cụ chuyển đổi SHALL mặc định chỉ đọc, phân biệt ứng viên chắc chắn với dữ liệu cần review, và không phân loại chỉ từ role Staff, số điện thoại hiện tại hoặc đẳng thức creator/owner.

#### Scenario: A preflight is run
- **WHEN** người vận hành chọn rõ database đích và chạy phân loại không bật chế độ ghi
- **THEN** công cụ trả ID/version và lý do phân loại mà không đổi dữ liệu
- **AND** thiếu snapshot, lệch scope/holder, nhóm, transfer hoặc đối soát chưa giải quyết được đưa vào review

#### Scenario: The operator is a legitimate linked customer or buyer
- **WHEN** booking có bằng chứng Customer liên kết thực hoặc đã nhận chuyển nhượng
- **THEN** booking không được đưa vào nhóm sửa Guest chỉ vì owner có role Staff hoặc trùng creator

### Requirement: Legacy correction is approved atomic and repeatable
Công cụ correction SHALL chỉ ghi cho manifest được duyệt với version/giá trị kỳ vọng còn đúng, sửa owner/reservation nguyên tử và thêm audit; SHALL không sửa danh tính payer, trạng thái, tiền hoặc chứng cứ cũ.

#### Scenario: An approved unchanged candidate is corrected
- **WHEN** yêu cầu ghi được phê duyệt cho database đích và manifest còn khớp bằng chứng
- **THEN** hai owner và holder reservation thành NULL, version/token version tăng và một audit correction được thêm
- **AND** manual code, trạng thái, thời gian, giá, payment identity, Guest snapshot và registry lịch sử giữ nguyên

#### Scenario: A candidate changes or correction is retried
- **WHEN** row đã thay đổi từ preflight hoặc đã được sửa trước đó
- **THEN** row thay đổi được bỏ qua để review, row đã sửa không bị sửa/audit lần hai
- **AND** không tự gán Staff lại làm owner để rollback code

#### Scenario: Schema upgrade or application startup runs
- **WHEN** schema mới được áp dụng hoặc ứng dụng khởi động
- **THEN** không có backfill ownership hay correction dữ liệu legacy tự động

### Requirement: Frontend represents nullable customer ownership correctly
Frontend SHALL xử lý owner nullable mà không gán người tạo làm Customer; màn hình vận hành SHALL phân biệt Guest, Customer liên kết và người tạo theo dữ liệu được phép đọc.

#### Scenario: An operator views a guest booking
- **WHEN** người vận hành được phép mở danh sách hoặc chi tiết Guest có owner NULL
- **THEN** giao diện hiển thị thông tin khách và nhãn khách vãng lai phù hợp, không hiển thị Staff như chủ sở hữu
- **AND** không có lỗi render/type hoặc UUID giả để lấp owner trống

#### Scenario: A linked customer booking is displayed
- **WHEN** booking counter có account owner hợp lệ
- **THEN** giao diện không coi booking là Guest chỉ vì source là OFFLINE_COUNTER
- **AND** nếu hiển thị người tạo thì dùng nhãn tách khỏi chủ sở hữu

### Requirement: Frontend counter navigation follows the booking relationship
Frontend SHALL đưa operator counter về trang vận hành phù hợp sau thanh toán và giữ điều hướng cá nhân cho Customer thực sự; role Staff hoặc payerId riêng lẻ SHALL không quyết định quyền sở hữu.

#### Scenario: Staff pays for a guest or another customer
- **WHEN** Staff tạo và trả DEMO cho counter booking của Guest hoặc Customer khác
- **THEN** điều hướng sau xác nhận và link xem kết quả dẫn tới chi tiết vận hành được phép
- **AND** không đưa Staff tới trang cá nhân với thông điệp booking là của Staff

#### Scenario: A customer including a staff customer pays for their own booking
- **WHEN** current Customer owner thực hiện thanh toán, kể cả tài khoản có thêm role Staff
- **THEN** link kết quả dẫn tới booking cá nhân; luồng nhóm và acquisition tiếp tục dùng trang kết quả tương ứng
- **AND** query param hoặc việc chỉ là creator không tạo quyền truy cập mới

### Requirement: Frontend provides manual check-in in the facility workflow
Frontend SHALL cho người có BOOKING_CHECK_IN nhập mã booking trong luồng danh sách/chi tiết vận hành và gọi API check-in bằng mã tại đúng cơ sở; SHALL giữ luồng signed QR của Customer.

#### Scenario: Staff checks in a paid guest from the operational interface
- **WHEN** nhân viên đủ quyền gửi mã hợp lệ của Guest CONFIRMED từ màn hình vận hành
- **THEN** UI gọi check-in-by-code với cơ sở đang chọn hoặc cơ sở của booking được phép đọc
- **AND** sau thành công tải lại booking/lịch sử và hiển thị CHECKED_IN theo nhãn hiện hành
- **AND** Guest không cần account, owner QR hoặc thao tác trực tiếp với API ngoài giao diện

#### Scenario: Manual check-in fails or is already being submitted
- **WHEN** mã sai, Backend từ chối điều kiện hoặc một yêu cầu check-in đang chờ
- **THEN** UI hiển thị lỗi phù hợp, không tự đổi trạng thái và ngăn gửi lặp khi đang chờ
- **AND** input mã thủ công không bị gửi như signed QR token

#### Scenario: A staff member can only read the booking
- **WHEN** nhân viên chỉ có quyền đọc booking của cơ sở
- **THEN** UI không mở thao tác check-in, dù có thể đọc mã vận hành trong response được phép
- **AND** việc cố gửi request trực tiếp vẫn bị Backend kiểm tra quyền

### Requirement: Frontend owner actions require actual current ownership
Frontend SHALL chỉ hiển thị QR/mã cá nhân và thao tác chuyển nhượng theo current account owner hợp lệ; creator, operator và người xem lịch sử SHALL không được suy thành owner ở giao diện.

#### Scenario: Staff opens a guest booking or its history
- **WHEN** Staff chỉ có quan hệ operator với booking Guest mới hoặc legacy đã được nhận diện
- **THEN** UI không hiển thị QR mang nghĩa của Customer hoặc nút đăng chuyển nhượng cho Staff
- **AND** mã check-in vận hành nếu được hiển thị có nhãn vận hành, không mang nghĩa sở hữu cá nhân

#### Scenario: A real customer opens their confirmed booking
- **WHEN** Customer thực sự sở hữu booking đủ điều kiện
- **THEN** UI giữ QR/mã cá nhân và luồng chuyển nhượng hiện hành; owner cũ không nhận mã của owner mới

### Requirement: Frontend payment reflects backend authority and demo synchronization
Frontend SHALL hiển thị thao tác payment đúng chính sách payer Backend hiện tại, mô tả rõ DEMO và chỉ xác nhận booking khi server xác nhận; SHALL không tuyên bố đã ghi nhận tiền mặt/POS hoặc dữ liệu thu tiền chưa được lưu.

#### Scenario: Another staff member has only counter payment permission
- **WHEN** Staff khác người tạo có BOOKING_PAYMENT_COUNTER nhưng không phải current owner hoặc original operator được Backend cho phép
- **THEN** UI không mở thao tác payment như thể người đó được phép trả booking
- **AND** change không tự mở rộng quyền payer tại Backend

#### Scenario: Demo payment succeeds before booking confirmation arrives
- **WHEN** payment DEMO trả SUCCESS nhưng booking chưa CONFIRMED
- **THEN** UI thể hiện đang chờ đồng bộ, giữ payment/idempotency khi retry và không báo booking đã xác nhận sớm
- **AND** chỉ hiển thị xác nhận booking sau khi đọc trạng thái CONFIRMED từ Backend

#### Scenario: Confirmation times out or the operation is denied
- **WHEN** đồng bộ chưa hoàn tất, payment thất bại hoặc Backend từ chối quyền
- **THEN** UI hiển thị trạng thái/lỗi và cách tải lại hoặc tiếp tục phù hợp, không tạo lại booking/payment chỉ để che lỗi
- **AND** không thông báo đã thu CASH/POS hoặc lưu tên người thu khi chưa có bằng chứng tương ứng
