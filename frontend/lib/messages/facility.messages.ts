export const facilityMessages: Record<string, string> = {
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
    'Vui lòng cấu hình ít nhất 1 khung giờ có thể đặt trong tương lai trước khi gửi thẩm định.',
  'Only a DRAFT facility can be submitted':
    'Chỉ cơ sở ở trạng thái bản nháp mới có thể gửi thẩm định.',
  'Submit the linked Owner application instead':
    'Vui lòng gửi hồ sơ đăng ký chủ cơ sở liên kết thay vì gửi riêng cơ sở.',
  'Applicant is locked or unverified; activation is paused':
    'Tài khoản người đăng ký đang bị khóa hoặc chưa xác minh; tạm dừng kích hoạt.',
  'A dependency is unavailable; the operation will retry safely':
    'Dịch vụ liên kết tạm thời gián đoạn; hệ thống sẽ tự động thử lại an toàn.',
  'Supply a review reason of up to 2000 characters':
    'Vui lòng cung cấp lý do thẩm định (tối đa 2000 ký tự).',
  'Rejection/supplement reason must contain at least 20 characters':
    'Lý do từ chối hoặc yêu cầu bổ sung phải có ít nhất 20 ký tự.',
  'Facility is not pending review': 'Cơ sở hiện không ở trạng thái chờ thẩm định.',
  'Pending review missing': 'Không tìm thấy yêu cầu thẩm định đang chờ xử lý.',
  'Unsupported review action': 'Hành động thẩm định không được hỗ trợ.',
  'Choose an explicit platform commission percentage':
    'Vui lòng chọn tỷ lệ phần trăm hoa hồng nền tảng rõ ràng.',
  'Application is not pending review or has a different decision':
    'Hồ sơ không ở trạng thái chờ thẩm định hoặc đã có quyết định khác.',
  'No activation/decision is pending':
    'Không có yêu cầu kích hoạt hoặc quyết định nào đang chờ xử lý.',
  'At most 200 application ids are allowed': 'Tối đa 200 mã hồ sơ được xử lý cùng lúc.',
  'Applicant account is locked or unverified':
    'Tài khoản người đăng ký bị khóa hoặc chưa xác minh.',
  'Owner application not found': 'Không tìm thấy hồ sơ đăng ký chủ cơ sở.',
  'Application belongs to another applicant': 'Hồ sơ này thuộc về người đăng ký khác.',
  'Application is frozen; wait for a supplement decision or create a new application after rejection':
    'Hồ sơ đang bị đóng băng để thẩm định. Vui lòng chờ phản hồi từ quản trị viên.',
  'Admin role required': 'Yêu cầu quyền Quản trị viên.',
  'Owner role required': 'Yêu cầu quyền Chủ cơ sở.',
  'Owner or editable application workspace required':
    'Yêu cầu quyền chủ cơ sở hoặc quyền chỉnh sửa hồ sơ cơ sở.',
  'Invalid application input': 'Thông tin hồ sơ không hợp lệ. Vui lòng kiểm tra lại.',
  'Choose a sport category and supply the first court name/code':
    'Vui lòng chọn môn thể thao và nhập tên/mã sân đầu tiên.',
  'Choose at least one operating weekday': 'Vui lòng chọn ít nhất một ngày mở cửa trong tuần.',
  'Operating hours must contain whole slots of 5–720 minutes':
    'Khung giờ mở cửa phải chứa các khung giờ trọn vẹn từ 5 đến 720 phút.',
  'Supply a positive VND price with at most two decimal places':
    'Vui lòng nhập giá tiền (VND) hợp lệ lớn hơn 0.',
  'Invalid first facility or inactive sport category':
    'Thông tin cơ sở đầu tiên không hợp lệ hoặc môn thể thao đang ngừng hoạt động.',
  'Facility validation is unavailable; retry safely':
    'Hệ thống kiểm tra cơ sở tạm thời bận; vui lòng thử lại sau.',
  'Retry must use the original registration data and password':
    'Thử lại phải sử dụng đúng thông tin đăng ký và mật khẩu ban đầu.',
  'Registration account is inactive': 'Tài khoản đăng ký đang không hoạt động.',
  'Facility is outside your ownership': 'Cơ sở không thuộc quyền sở hữu của bạn.',
  'Facility ownership could not be verified': 'Không thể xác minh quyền sở hữu cơ sở.',
  'Application workspace is read-only': 'Không gian làm việc của hồ sơ hiện ở chế độ chỉ đọc.',
  'Facility configuration is frozen during review':
    'Cấu hình cơ sở đang được đóng băng trong thời gian thẩm định.',
  'Submitted/rejected facility profile cannot be edited; await a supplement decision or create a new draft':
    'Hồ sơ cơ sở đã gửi hoặc bị từ chối không thể sửa đổi; vui lòng chờ quyết định bổ sung hoặc tạo bản nháp mới.',
  'First facility activation is awaiting application commit':
    'Cơ sở đầu tiên đang chờ hoàn tất phê duyệt hồ sơ.',
  'Application schedule is frozen during review':
    'Lịch và giá của cơ sở đang được đóng băng trong thời gian thẩm định.',
  'Application facility binding differs': 'Liên kết giữa cơ sở và hồ sơ đăng ký không khớp.',
  'First-facility Owner approval has not committed':
    'Phê duyệt cơ sở đầu tiên chưa được hoàn tất ghi nhận.',
  'First-facility approval could not be verified':
    'Không thể xác minh trạng thái phê duyệt cơ sở đầu tiên.',
  'Invalid first facility or location': 'Thông tin cơ sở hoặc tọa độ vị trí không hợp lệ.',
  'Coordinates must be numeric': 'Tọa độ phải là định dạng số.',
  'Invalid timezone': 'Múi giờ không hợp lệ.',
  'Court does not belong to facility': 'Sân không thuộc cơ sở này.',
  'Image must be between 1 byte and 10 MB': 'Kích thước ảnh phải từ 1 byte đến 10 MB.',
  'Image must be between 1 byte and 10 MB (demo technical limit)':
    'Kích thước ảnh phải từ 1 byte đến 10 MB.',
  'Object storage is unavailable': 'Dịch vụ lưu trữ tệp tạm thời gián đoạn. Vui lòng thử lại sau.',
  'Image not found': 'Không tìm thấy hình ảnh.',
  'Image replacement failed; the previous database reference was preserved':
    'Thay thế ảnh thất bại; ảnh cũ vẫn được giữ nguyên.',
  'Invalid facility status decision': 'Quyết định trạng thái cơ sở không hợp lệ.',
  'Facility not found': 'Không tìm thấy cơ sở thể thao.',
  'Facility changed; preview the impact again':
    'Dữ liệu cơ sở đã thay đổi; vui lòng xem lại tác động trước khi tiếp tục.',
  'Invalid facility status transition': 'Chuyển đổi trạng thái cơ sở không hợp lệ.',
  'Booking impact changed; preview and confirm again':
    'Tác động đến các lượt đặt sân đã thay đổi; vui lòng kiểm tra và xác nhận lại.',
  'Facility id already exists': 'Mã cơ sở đã tồn tại trong hệ thống.',
  'Unsupported operational permission': 'Quyền vận hành không được hỗ trợ.',
  'Maintenance start must precede end': 'Thời gian bắt đầu bảo trì phải trước thời gian kết thúc.',
  'Maintenance cannot start in the past': 'Thời gian bảo trì không thể bắt đầu trong quá khứ.',
  'Maintenance intervals overlap': 'Khoảng thời gian bảo trì bị trùng lặp với lịch bảo trì khác.',
  'Past maintenance cannot be changed': 'Lịch bảo trì trong quá khứ không thể thay đổi.',
  'Maintenance not found': 'Không tìm thấy lịch bảo trì.',
  'Invalid court type': 'Loại sân không hợp lệ.',
  'Court type not found': 'Không tìm thấy loại sân.',
  'Court type must be active and belong to this facility':
    'Loại sân phải đang hoạt động và thuộc cơ sở này.',
  'Court type must be active and belong to the facility':
    'Loại sân phải đang hoạt động và thuộc cơ sở này.',
  'Sport category must be active': 'Môn thể thao phải ở trạng thái đang hoạt động.',
  'Invalid category update': 'Cập nhật môn thể thao không hợp lệ.',
  'Sport category was updated; reload before saving':
    'Môn thể thao đã được cập nhật bởi thao tác khác; vui lòng tải lại.',
  'Sport category not found': 'Không tìm thấy môn thể thao.',
  'Invalid category input': 'Thông tin môn thể thao không hợp lệ.',
  'Invalid category name': 'Tên môn thể thao không hợp lệ.',
}
