/**
 * SportHub error translation utility.
 * Translates backend API error messages and domain codes into natural Vietnamese.
 *
 * Supports language toggling so that when English is selected in settings in the future,
 * raw or English messages will be shown directly.
 */

export type SupportedLanguage = 'vi' | 'en'

let currentLanguage: SupportedLanguage = 'vi'

export function setLanguage(lang: SupportedLanguage) {
  currentLanguage = lang
  if (typeof window !== 'undefined') {
    localStorage.setItem('sporthub_lang', lang)
    window.dispatchEvent(new CustomEvent('sporthub-lang-changed', { detail: lang }))
  }
}

export function getLanguage(): SupportedLanguage {
  if (typeof window !== 'undefined') {
    const saved = localStorage.getItem('sporthub_lang')
    if (saved === 'en' || saved === 'vi') return saved
  }
  return currentLanguage
}

/**
 * Exact dictionary mapping English backend messages and codes to Vietnamese.
 */
const EXACT_TRANSLATIONS: Record<string, string> = {
  // Facility Review & Owner Application
  'Facility review checks failed; correct the draft or request a supplement':
    'Kiểm tra hồ sơ cơ sở không đạt. Vui lòng hoàn thiện bản nháp (thêm sân, lịch mở cửa, giá, ảnh và 2 tài liệu pháp lý) trước khi gửi thẩm định.',
  'Attach identity and location legal documents before submitting':
    'Vui lòng đính kèm ít nhất 2 tài liệu pháp lý (giấy tờ định danh và giấy tờ địa điểm) trước khi gửi thẩm định.',
  'Attach a facility image before submitting':
    'Vui lòng tải lên ít nhất 1 ảnh cơ sở trước khi gửi thẩm định.',
  'Add at least one enabled court before submitting':
    'Vui lòng thêm ít nhất một sân đang bật (hoạt động) trước khi gửi thẩm định.',
  'Configure operating hours before submitting':
    'Vui lòng cấu hình lịch mở cửa trước khi gửi thẩm định.',
  'Operating hours could not be verified':
    'Không thể xác minh khung giờ mở cửa. Vui lòng kiểm tra lại.',
  'Configure operating hours and prices before submitting':
    'Vui lòng cấu hình đầy đủ giờ mở cửa và bảng giá trước khi gửi thẩm định.',
  'Configure a future playable slot before submitting':
    'Vui lòng cấu hình ít nhất 1 slot có thể đặt trong tương lai trước khi gửi thẩm định.',
  'Only a DRAFT facility can be submitted':
    'Chỉ có cơ sở ở trạng thái bản nháp (DRAFT) mới có thể gửi thẩm định.',
  'Submit the linked Owner application instead':
    'Vui lòng gửi hồ sơ đăng ký Owner liên kết thay vì gửi riêng cơ sở.',
  'Applicant is locked or unverified; activation is paused':
    'Tài khoản người đăng ký đang bị khóa hoặc chưa xác minh; tạm dừng kích hoạt.',
  'A dependency is unavailable; the operation will retry safely':
    'Dịch vụ liên kết tạm thời gián đoạn; hệ thống sẽ tự động thử lại an toàn.',
  'Supply a review reason of up to 2000 characters':
    'Vui lòng cung cấp lý do thẩm định (tối đa 2000 ký tự).',
  'Rejection/supplement reason must contain at least 20 characters':
    'Lý do từ chối hoặc yêu cầu bổ sung phải có ít nhất 20 ký tự.',
  'Facility is not pending review':
    'Cơ sở hiện không ở trạng thái chờ thẩm định.',
  'Pending review missing':
    'Không tìm thấy yêu cầu thẩm định đang chờ xử lý.',
  'Unsupported review action':
    'Hành động thẩm định không được hỗ trợ.',
  'Choose an explicit platform commission percentage':
    'Vui lòng chọn tỷ lệ phần trăm hoa hồng nền tảng rõ ràng.',
  'Application is not pending review or has a different decision':
    'Hồ sơ không ở trạng thái chờ thẩm định hoặc đã có quyết định khác.',
  'No activation/decision is pending':
    'Không có yêu cầu kích hoạt hoặc quyết định nào đang chờ xử lý.',
  'At most 200 application ids are allowed':
    'Tối đa 200 mã hồ sơ được xử lý cùng lúc.',
  'Applicant account is locked or unverified':
    'Tài khoản người đăng ký bị khóa hoặc chưa xác minh.',
  'Owner application not found':
    'Không tìm thấy hồ sơ đăng ký Owner.',
  'Application belongs to another applicant':
    'Hồ sơ này thuộc về người đăng ký khác.',
  'Application is frozen; wait for a supplement decision or create a new application after rejection':
    'Hồ sơ đang bị đóng băng để thẩm định. Vui lòng chờ phản hồi từ Admin.',
  'Admin role required':
    'Yêu cầu quyền Quản trị viên (Admin).',
  'Owner role required':
    'Yêu cầu quyền Chủ cơ sở (Owner).',
  'Owner or editable application workspace required':
    'Yêu cầu quyền Owner hoặc quyền chỉnh sửa hồ sơ cơ sở.',
  'Invalid application input':
    'Thông tin hồ sơ không hợp lệ. Vui lòng kiểm tra lại.',
  'Choose a sport category and supply the first court name/code':
    'Vui lòng chọn môn thể thao và nhập tên/mã sân đầu tiên.',
  'Choose at least one operating weekday':
    'Vui lòng chọn ít nhất một ngày mở cửa trong tuần.',
  'Operating hours must contain whole slots of 5–720 minutes':
    'Khung giờ mở cửa phải chứa các slot trọn vẹn từ 5 đến 720 phút.',
  'Supply a positive VND price with at most two decimal places':
    'Vui lòng nhập giá tiền (VND) hợp lệ lớn hơn 0.',
  'Invalid first facility or inactive sport category':
    'Thông tin cơ sở đầu tiên không hợp lệ hoặc môn thể thao đang ngừng hoạt động.',
  'Facility validation is unavailable; retry safely':
    'Hệ thống kiểm tra cơ sở tạm thời bận; vui lòng thử lại sau.',
  'Retry must use the original registration data and password':
    'Thử lại phải sử dụng đúng thông tin đăng ký và mật khẩu ban đầu.',
  'Registration account is inactive':
    'Tài khoản đăng ký đang không hoạt động.',
  'Facility is outside your ownership':
    'Cơ sở không thuộc quyền sở hữu của bạn.',
  'Facility ownership could not be verified':
    'Không thể xác minh quyền sở hữu cơ sở.',
  'Application workspace is read-only':
    'Không gian làm việc của hồ sơ hiện ở chế độ chỉ đọc.',
  'Facility configuration is frozen during review':
    'Cấu hình cơ sở đang được đóng băng trong thời gian thẩm định.',
  'Submitted/rejected facility profile cannot be edited; await a supplement decision or create a new draft':
    'Hồ sơ cơ sở đã gửi hoặc bị từ chối không thể sửa đổi; vui lòng chờ quyết định bổ sung hoặc tạo bản nháp mới.',
  'First facility activation is awaiting application commit':
    'Cơ sở đầu tiên đang chờ hoàn tất phê duyệt hồ sơ.',
  'Application schedule is frozen during review':
    'Lịch và giá của cơ sở đang được đóng băng trong thời gian thẩm định.',
  'Application facility binding differs':
    'Liên kết giữa cơ sở và hồ sơ đăng ký không khớp.',
  'First-facility Owner approval has not committed':
    'Phê duyệt cơ sở đầu tiên chưa được hoàn tất ghi nhận.',
  'First-facility approval could not be verified':
    'Không thể xác minh trạng thái phê duyệt cơ sở đầu tiên.',
  'Invalid first facility or location':
    'Thông tin cơ sở hoặc tọa độ vị trí không hợp lệ.',
  'Coordinates must be numeric':
    'Tọa độ phải là định dạng số.',
  'Invalid timezone':
    'Múi giờ không hợp lệ.',
  'Court does not belong to facility':
    'Sân không thuộc cơ sở này.',
  'Image must be between 1 byte and 10 MB':
    'Kích thước ảnh phải từ 1 byte đến 10 MB.',
  'Image must be between 1 byte and 10 MB (demo technical limit)':
    'Kích thước ảnh phải từ 1 byte đến 10 MB.',
  'Object storage is unavailable':
    'Dịch vụ lưu trữ tệp tạm thời gián đoạn. Vui lòng thử lại sau.',
  'Image not found':
    'Không tìm thấy hình ảnh.',
  'Image replacement failed; the previous database reference was preserved':
    'Thay thế ảnh thất bại; ảnh cũ vẫn được giữ nguyên.',
  'Invalid facility status decision':
    'Quyết định trạng thái cơ sở không hợp lệ.',
  'Facility not found':
    'Không tìm thấy cơ sở thể thao.',
  'Facility changed; preview the impact again':
    'Dữ liệu cơ sở đã thay đổi; vui lòng xem lại tác động trước khi tiếp tục.',
  'Invalid facility status transition':
    'Chuyển đổi trạng thái cơ sở không hợp lệ.',
  'Booking impact changed; preview and confirm again':
    'Tác động đến các lượt đặt sân đã thay đổi; vui lòng kiểm tra và xác nhận lại.',
  'Facility id already exists':
    'Mã cơ sở đã tồn tại trong hệ thống.',
  'Unsupported operational permission':
    'Quyền vận hành không được hỗ trợ.',
  'Maintenance start must precede end':
    'Thời gian bắt đầu bảo trì phải trước thời gian kết thúc.',
  'Maintenance cannot start in the past':
    'Thời gian bảo trì không thể bắt đầu trong quá khứ.',
  'Maintenance intervals overlap':
    'Khoảng thời gian bảo trì bị trùng lặp với lịch bảo trì khác.',
  'Past maintenance cannot be changed':
    'Lịch bảo trì trong quá khứ không thể thay đổi.',
  'Maintenance not found':
    'Không tìm thấy lịch bảo trì.',
  'Invalid court type':
    'Loại sân không hợp lệ.',
  'Court type not found':
    'Không tìm thấy loại sân.',
  'Court type must be active and belong to this facility':
    'Loại sân phải đang hoạt động và thuộc cơ sở này.',
  'Court type must be active and belong to the facility':
    'Loại sân phải đang hoạt động và thuộc cơ sở này.',
  'Sport category must be active':
    'Môn thể thao phải ở trạng thái đang hoạt động.',
  'Invalid category update':
    'Cập nhật môn thể thao không hợp lệ.',
  'Sport category was updated; reload before saving':
    'Môn thể thao đã được cập nhật bởi thao tác khác; vui lòng tải lại.',
  'Sport category not found':
    'Không tìm thấy môn thể thao.',
  'Invalid category input':
    'Thông tin môn thể thao không hợp lệ.',
  'Invalid category name':
    'Tên môn thể thao không hợp lệ.',

  // Schedule & Pricing
  'Price interval must fit effective operating hours and slot boundaries':
    'Khoảng giờ giá phải nằm trọn trong khung giờ mở cửa và khớp với mốc chia slot.',
  'Opening interval must contain whole slots':
    'Khoảng thời gian mở cửa phải chứa các slot trọn vẹn (bội số 60 phút).',
  'Operating intervals overlap':
    'Các khoảng thời gian mở cửa bị trùng lặp nhau.',
  'Price per slot must be strictly positive':
    'Giá mỗi slot phải lớn hơn 0.',
  'Choose exactly one weekday or specific date':
    'Vui lòng chọn duy nhất một thứ trong tuần hoặc một ngày cụ thể.',
  'Rule has no applicable date inside its effective period':
    'Quy tắc giá không có ngày áp dụng hợp lệ trong khoảng thời gian hiệu lực.',
  'Price interval is outside every matching court operating calendar':
    'Khoảng giờ giá nằm ngoài lịch mở cửa của tất cả các sân phù hợp.',
  'Court must be enabled':
    'Sân phải ở trạng thái đang bật (hoạt động).',
  'Price scope does not match the court classification':
    'Phạm vi giá không khớp với phân loại sân.',
  'If-Match must contain the current price version':
    'Phiên bản quy tắc giá không khớp; vui lòng tải lại.',
  'Price version changed; reload the rule before editing':
    'Phiên bản quy tắc giá đã thay đổi; vui lòng tải lại trước khi sửa.',
  'Create a new rule instead of editing a disabled price':
    'Vui lòng tạo quy tắc mới thay vì chỉnh sửa quy tắc giá đã ngừng hoạt động.',
  'Price version changed during update':
    'Phiên bản quy tắc giá đã thay đổi trong lúc cập nhật.',
  'Price rules conflict at the same scope or precedence; change the scope or priority':
    'Quy tắc giá bị xung đột tại cùng phạm vi hoặc độ ưu tiên; vui lòng thay đổi phạm vi hoặc thứ tự ưu tiên.',
  'Invalid pricing fields':
    'Thông tin bảng giá không hợp lệ.',
  'Price interval must end after it starts':
    'Giờ kết thúc khoảng giá phải sau giờ bắt đầu.',
  'Effective period is invalid':
    'Khoảng thời gian hiệu lực không hợp lệ.',
  'Opening time must precede closing time':
    'Giờ mở cửa phải trước giờ đóng cửa.',
  'Special opening hours must have a valid interval':
    'Khung giờ mở cửa đặc biệt phải có khoảng thời gian hợp lệ.',
  'Special opening interval must contain whole slots':
    'Khung giờ mở cửa đặc biệt phải chứa các slot trọn vẹn.',
  'Special opening interval has already ended':
    'Khung giờ mở cửa đặc biệt đã kết thúc.',
  'Exceptions for the same scope overlap':
    'Các ngoại lệ trong cùng phạm vi bị trùng lặp thời gian.',
  'Past exceptions cannot be created':
    'Không thể tạo ngày ngoại lệ trong quá khứ.',
  'Started or past exceptions cannot be changed':
    'Không thể thay đổi ngoại lệ đã bắt đầu hoặc trong quá khứ.',
  'CLOSED exceptions apply to the whole local date':
    'Ngoại lệ đóng cửa áp dụng cho trọn vẹn ngày đó.',
  'Suspended facilities cannot be specially opened':
    'Cơ sở đang tạm dừng hoạt động không thể mở cửa đặc biệt.',
  'Configured slots must have an unambiguous price':
    'Các slot đã cấu hình phải có bảng giá rõ ràng và không mâu thuẫn.',
  'Choose between 1 and 200 courts':
    'Vui lòng chọn từ 1 đến 200 sân.',
  'Signup cannot replace existing configuration':
    'Đăng ký ban đầu không thể thay thế cấu hình đã có sẵn.',
  'Schedule times must use whole minutes':
    'Thời gian trong lịch phải tính theo phút tròn.',
  'SCHEDULE_READ requires an active Staff binding at this facility':
    'Xem lịch yêu cầu quyền Nhân viên (Staff) đang hoạt động tại cơ sở này.',
  'Booking interval is invalid':
    'Khoảng thời gian đặt sân không hợp lệ.',
  'Choose slots within one local date':
    'Vui lòng chọn các slot trong cùng một ngày.',
  'Interval does not align with operating slots':
    'Khoảng thời gian không khớp với các khung giờ slot hoạt động.',

  // Booking & Operations
  'Slot was just held or booked by another user':
    'Slot này vừa được người khác giữ chỗ hoặc đặt thành công. Vui lòng chọn slot khác.',
  'Slot is outside the booking window':
    'Slot nằm ngoài khoảng thời gian cho phép đặt trước.',
  'Slot is outside the booking window after schedule validation':
    'Slot nằm ngoài thời gian mở đặt sân sau khi đối soát lịch.',
  'Too many slots in one hold':
    'Số lượng slot trong một lần giữ chỗ vượt quá giới hạn.',
  'Court facility changed':
    'Thông tin cơ sở của sân đã thay đổi.',
  'Price changed. Review the updated quote before holding again':
    'Giá sân đã được cập nhật. Vui lòng xem lại bảng giá mới trước khi giữ chỗ.',
  'Pending hold limit reached':
    'Bạn đã đạt giới hạn số lượt giữ chỗ đang chờ. Vui lòng thanh toán hoặc hủy bớt lượt giữ chỗ.',
  'Hold belongs to another user':
    'Lượt giữ chỗ thuộc về người dùng khác.',
  'Cancel the booking instead':
    'Lượt đặt đã được xác nhận; vui lòng thực hiện thao tác hủy booking.',
  'Hold expired or was released':
    'Lượt giữ chỗ đã hết hạn hoặc đã được giải phóng.',
  'Hold expired during price validation':
    'Lượt giữ chỗ đã hết hạn trong quá trình kiểm tra giá.',
  'Hold expired during customer validation':
    'Lượt giữ chỗ đã hết hạn trong quá trình kiểm tra thông tin khách hàng.',
  'A booking already uses this hold; retry with the original Idempotency-Key':
    'Đã có lượt đặt sử dụng mã giữ chỗ này.',
  'Guest name and phone are required':
    'Vui lòng nhập họ tên và số điện thoại của khách.',
  'Quote scope changed':
    'Phạm vi báo giá đã thay đổi.',
  'Price changed. Review price and create a new hold':
    'Giá đã thay đổi. Vui lòng kiểm tra lại giá và tạo lượt giữ chỗ mới.',
  'Only the booking payer or original counter operator can pay':
    'Chỉ người thanh toán hoặc nhân viên tạo đơn tại quầy mới có thể thanh toán.',
  'Booking is no longer payable':
    'Lượt đặt này hiện không còn trong trạng thái có thể thanh toán.',
  'Pay group contributions through the group screen':
    'Vui lòng thanh toán phần đóng góp nhóm tại màn hình nhóm chơi.',
  'Cancel group bookings through the group screen':
    'Vui lòng hủy lượt đặt nhóm tại màn hình nhóm chơi.',
  'A cancellation reason is required':
    'Vui lòng cung cấp lý do hủy đặt sân.',
  'Booking transition is not permitted':
    'Thao tác chuyển trạng thái booking không được phép.',
  'Booking no longer has a live payment hold':
    'Booking không còn giữ chỗ thanh toán hợp lệ.',
  'Reservation changed before confirmation':
    'Thông tin đặt chỗ đã thay đổi trước khi xác nhận.',
  'Payment hold has not expired':
    'Thời gian giữ chỗ thanh toán chưa hết hạn.',
  'Reservation is inconsistent with booking status':
    'Thông tin giữ chỗ không nhất quán với trạng thái booking.',
  'Booking changed before transition':
    'Thông tin booking đã thay đổi trước khi chuyển trạng thái.',
  'Schedule or pricing is no longer eligible':
    'Lịch sân hoặc giá không còn đủ điều kiện áp dụng.',
  'Court is unavailable':
    'Sân hiện không khả dụng.',
  'Session is invalid or expired':
    'Phiên đăng nhập đã hết hạn hoặc không hợp lệ. Vui lòng đăng nhập lại.',
  'Counter customer lookup is not authorized':
    'Bạn không có quyền tra cứu khách hàng tại quầy.',
  'Customer lookup could not be verified':
    'Không thể xác minh thông tin khách hàng.',

  // Transfer Market
  'Only your current booking can be transferred':
    'Chỉ booking bạn đang sở hữu mới có thể chuyển nhượng.',
  'Booking already has an active transfer listing':
    'Lượt đặt này đã có tin chuyển nhượng đang hoạt động.',
  'Invalid transfer search filters':
    'Bộ lọc tìm kiếm chuyển nhượng không hợp lệ.',
  'You cannot acquire your own listing':
    'Bạn không thể tự nhận lại tin chuyển nhượng của chính mình.',
  'Listing is locked, expired or no longer eligible':
    'Tin chuyển nhượng đã bị khóa, hết hạn hoặc không còn khả dụng.',
  'Transfer acquisition deadline elapsed during validation':
    'Thời hạn nhận lượt chuyển nhượng đã trôi qua trong quá trình xác thực.',
  'Acquisition belongs to another buyer':
    'Giao dịch chuyển nhượng thuộc về người mua khác.',
  'Transfer acquisition is no longer payable':
    'Giao dịch chuyển nhượng không còn trong trạng thái có thể thanh toán.',
  'Booking does not hold the matching transfer lease':
    'Lượt đặt không khớp với giao dịch chuyển nhượng.',
  'Transfer deadline elapsed during payable validation':
    'Thời hạn chuyển nhượng đã kết thúc trong khi xác thực thanh toán.',
  'Only an unpaid pending acquisition can be cancelled':
    'Chỉ có giao dịch chưa thanh toán và đang chờ mới có thể hủy.',
  'Only an eligible unlocked active listing can be edited':
    'Chỉ có tin chuyển nhượng đang hoạt động và chưa bị khóa mới có thể chỉnh sửa.',
  'Only an unlocked active listing can be withdrawn':
    'Chỉ có tin chuyển nhượng chưa khóa mới có thể rút khỏi chợ.',
  'Only technical retry exhaustion can be retried; business or financial review needs resolution':
    'Chỉ lỗi kết nối kỹ thuật mới có thể thử lại tự động.',
  'Transfer synchronization belongs to another seller or buyer':
    'Tiến trình bàn giao thuộc về người bán hoặc người mua khác.',
  'Listing belongs to another seller':
    'Tin chuyển nhượng thuộc về người bán khác.',
  'A paid confirmed future booking is required':
    'Yêu cầu booking đã thanh toán, đã xác nhận và diễn ra trong tương lai.',
  'BLOCKED_RULE: facility Owner must configure its transfer eligibility policy':
    'Chủ cơ sở chưa cấu hình điều kiện chuyển nhượng cho cơ sở này.',
  'Facility transfer policy does not permit this booking':
    'Chính sách chuyển nhượng của cơ sở không cho phép chuyển nhượng lượt đặt này.',
  'Transfer price must be positive and no greater than the original paid amount':
    'Giá chuyển nhượng phải lớn hơn 0 và không vượt quá số tiền đã thanh toán ban đầu.',
  'Transfer deadline must be in the future and no later than play starts':
    'Hạn nhận chuyển nhượng phải ở tương lai và không muộn hơn giờ bắt đầu chơi.',

  // Identity & Auth
  'Invalid credentials':
    'Email, số điện thoại hoặc mật khẩu không chính xác.',
  'Bad credentials':
    'Email, số điện thoại hoặc mật khẩu không chính xác.',
  'Email is already registered':
    'Email này đã được đăng ký tài khoản.',
  'Phone is already registered':
    'Số điện thoại này đã được đăng ký tài khoản.',
  'Account is not active':
    'Tài khoản chưa được kích hoạt hoặc đang bị khóa.',
  'Challenge has already been used':
    'Mã xác minh đã được sử dụng trước đó.',
  'Challenge has expired':
    'Mã xác minh đã hết hạn. Vui lòng yêu cầu mã mới.',
  'Please wait 60 seconds before requesting another code':
    'Vui lòng đợi 60 giây trước khi yêu cầu mã xác minh mới.',
  'A token or challenge code is required':
    'Cần cung cấp mã hoặc đường dẫn xác thực.',
  'At least one profile field is required':
    'Vui lòng điền ít nhất một trường thông tin.',
  'No contact change is pending':
    'Không có yêu cầu thay đổi liên hệ nào đang chờ.',
  'Staff profile changed; reload before saving':
    'Hồ sơ nhân viên đã thay đổi; vui lòng tải lại trước khi lưu.',
  'An active unlinked Staff profile is required':
    'Yêu cầu hồ sơ nhân viên đang hoạt động và chưa được liên kết.',
  'Owner cannot invite their own account as Staff':
    'Chủ cơ sở không thể tự mời tài khoản của chính mình làm nhân viên.',
  'Account is already linked or invited to another Staff profile at this facility':
    'Tài khoản này đã được liên kết hoặc đã được mời vào hồ sơ nhân viên khác tại cơ sở.',
  'Cancel the existing invitation before inviting another account or changing permissions':
    'Vui lòng hủy lời mời hiện tại trước khi mời tài khoản khác hoặc thay đổi quyền hạn.',

  // Common Technical Errors
  'Validation failed':
    'Dữ liệu nhập vào không hợp lệ. Vui lòng kiểm tra lại.',
  'Internal server error':
    'Hệ thống gặp sự cố. Vui lòng thử lại sau.',
  'An unexpected error occurred. Please try again later.':
    'Đã xảy ra lỗi không mong muốn. Vui lòng thử lại sau.',
  'Resource not found':
    'Không tìm thấy dữ liệu yêu cầu.',
  'Resource conflict':
    'Dữ liệu bị xung đột hoặc đã bị thay đổi bởi thao tác khác.',
  'Unauthorized access':
    'Yêu cầu đăng nhập để tiếp tục.',
  'Access forbidden':
    'Bạn không có quyền thực hiện thao tác này.',
  'Business rule violation':
    'Không thỏa mãn điều kiện nghiệp vụ.',
  'Resource was modified by another user':
    'Dữ liệu đã được cập nhật bởi thao tác khác. Vui lòng tải lại trang.',
  'A resource with these values already exists or is referenced':
    'Dữ liệu này đã tồn tại hoặc đang được liên kết với bản ghi khác.',
  'Failed to fetch':
    'Không thể kết nối đến máy chủ SportHub. Vui lòng kiểm tra kết nối mạng.',
  'NetworkError when attempting to fetch resource.':
    'Không thể kết nối đến máy chủ SportHub. Vui lòng kiểm tra kết nối mạng.',
  'Load failed':
    'Không thể tải dữ liệu từ máy chủ. Vui lòng kiểm tra kết nối mạng.',

  // Technical Sync & Precondition Codes
  'BOOKING_UNAVAILABLE': 'Dịch vụ booking tạm thời gián đoạn',
  'BOOKING_RECEIPT_MISSING': 'Thiếu biên nhận xác nhận booking',
  'BOOKING_PROTOCOL_ERROR': 'Lỗi giao thức kết nối booking',
  'FACILITY_UNAVAILABLE': 'Dịch vụ cơ sở tạm thời gián đoạn',
  'BOOKING_PRECONDITION_REJECTED': 'Điều kiện tiên quyết của booking bị từ chối',
  'BOOKING_PROOF_UNAVAILABLE': 'Không tìm thấy chứng từ xác nhận booking',
  'LEGACY_HANDOFF_RECEIPT_MISSING': 'Thiếu biên nhận bàn giao quyền sử dụng',
}

/**
 * Reason translation for slot eligibility issues.
 */
const SLOT_REASON_TRANSLATIONS: Record<string, string> = {
  PRICE_NOT_CONFIGURED: 'Chưa cấu hình bảng giá',
  AMBIGUOUS_PRICE: 'Bảng giá bị trùng lặp hoặc không rõ ràng',
  MAINTENANCE: 'Sân đang trong thời gian bảo trì',
  CLOSED: 'Sân đóng cửa trong khung giờ này',
  BOOKED: 'Slot đã có người đặt',
  HELD: 'Slot đang có người tạm giữ chỗ',
}

/**
 * Translates any error message or code into Vietnamese,
 * respecting the configured language setting.
 */
export function translateError(message?: string | null): string {
  if (!message || typeof message !== 'string') return ''

  const trimmed = message.trim()
  if (!trimmed) return ''

  // If language is set to English, return the message as is
  if (getLanguage() === 'en') {
    return trimmed
  }

  // 1. Direct match in dictionary
  if (EXACT_TRANSLATIONS[trimmed]) {
    return EXACT_TRANSLATIONS[trimmed]
  }

  // 2. Already in Vietnamese? Check if it contains accented Vietnamese characters
  if (/[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ]/i.test(trimmed)) {
    return trimmed
  }

  // 3. Pattern / Prefix matches
  if (trimmed.startsWith('Slot is not eligible:')) {
    const rawReason = trimmed.replace('Slot is not eligible:', '').trim()
    const viReason = SLOT_REASON_TRANSLATIONS[rawReason] || rawReason
    return `Slot không đủ điều kiện đặt: ${viReason}`
  }

  if (trimmed.startsWith('Missing required parameter:')) {
    const param = trimmed.replace('Missing required parameter:', '').trim()
    return `Thiếu tham số bắt buộc: ${param}`
  }

  if (trimmed.startsWith('Missing facility permission:')) {
    const perm = trimmed.replace('Missing facility permission:', '').trim()
    return `Bạn chưa có quyền thực hiện thao tác này tại cơ sở (${perm})`
  }

  const typeMismatchMatch = trimmed.match(/^Parameter '([^']+)' must be of type (.*)$/)
  if (typeMismatchMatch) {
    return `Tham số '${typeMismatchMatch[1]}' phải là định dạng ${typeMismatchMatch[2]}`
  }

  if (trimmed.startsWith('TECHNICAL_RETRY_EXHAUSTED:')) {
    return 'Đã thử kết nối lại nhiều lần nhưng không thành công. Vui lòng thử lại sau.'
  }

  if (trimmed.startsWith('Supply valid Owner information and first facility')) {
    return 'Vui lòng điền đầy đủ và chính xác thông tin chủ sân và cơ sở thể thao đầu tiên.'
  }

  if (trimmed.includes('Facility review checks failed')) {
    return 'Kiểm tra hồ sơ cơ sở không đạt. Vui lòng hoàn thiện bản nháp (thêm sân, lịch mở cửa, giá, ảnh và 2 tài liệu pháp lý) trước khi gửi thẩm định.'
  }

  if (trimmed.includes('Attach identity and location')) {
    return 'Vui lòng đính kèm ít nhất 2 tài liệu pháp lý (giấy tờ định danh và giấy tờ địa điểm) trước khi gửi thẩm định.'
  }

  if (trimmed.includes('Attach a facility image')) {
    return 'Vui lòng tải lên ít nhất 1 ảnh cơ sở trước khi gửi thẩm định.'
  }

  if (trimmed.includes('Add at least one enabled court')) {
    return 'Vui lòng thêm ít nhất một sân đang bật trước khi gửi thẩm định.'
  }

  if (trimmed.includes('Configure operating hours')) {
    return 'Vui lòng cấu hình lịch mở cửa trước khi gửi thẩm định.'
  }

  if (trimmed.includes('Operating intervals overlap')) {
    return 'Các khoảng thời gian mở cửa bị trùng lặp nhau.'
  }

  if (trimmed.includes('Opening interval must contain whole slots')) {
    return 'Khoảng thời gian mở cửa phải chứa các slot trọn vẹn (bội số 60 phút).'
  }

  if (trimmed.includes('Price interval must fit')) {
    return 'Khoảng giờ giá phải nằm trọn trong khung giờ mở cửa và khớp với mốc chia slot.'
  }

  // Fallback: Return original trimmed message if no pattern matched
  return trimmed
}
