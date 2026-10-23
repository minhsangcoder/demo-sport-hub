export const scheduleMessages: Record<string, string> = {
  'Price interval must fit effective operating hours and slot boundaries':
    'Khoảng giờ giá phải nằm trọn trong khung giờ mở cửa và khớp với mốc chia khung giờ.',
  'Opening interval must contain whole slots':
    'Khoảng thời gian mở cửa phải chia thành các khung giờ trọn vẹn.',
  'Operating intervals overlap': 'Các khoảng thời gian mở cửa bị trùng lặp nhau.',
  'Price per slot must be strictly positive': 'Giá mỗi khung giờ phải lớn hơn 0.',
  'Choose exactly one weekday or specific date':
    'Vui lòng chọn duy nhất một thứ trong tuần hoặc một ngày cụ thể.',
  'Rule has no applicable date inside its effective period':
    'Quy tắc giá không có ngày áp dụng hợp lệ trong khoảng thời gian hiệu lực.',
  'Price interval is outside every matching court operating calendar':
    'Khoảng giờ giá nằm ngoài lịch mở cửa của tất cả các sân phù hợp.',
  'Court must be enabled': 'Sân phải ở trạng thái đang bật (hoạt động).',
  'Price scope does not match the court classification':
    'Phạm vi giá không khớp với phân loại sân.',
  'If-Match must contain the current price version':
    'Phiên bản quy tắc giá không khớp; vui lòng tải lại.',
  'Price version changed; reload the rule before editing':
    'Phiên bản quy tắc giá đã thay đổi; vui lòng tải lại trước khi sửa.',
  'Create a new rule instead of editing a disabled price':
    'Vui lòng tạo quy tắc mới thay vì chỉnh sửa quy tắc giá đã ngừng hoạt động.',
  'Price version changed during update': 'Phiên bản quy tắc giá đã thay đổi trong lúc cập nhật.',
  'Price rules conflict at the same scope or precedence; change the scope or priority':
    'Quy tắc giá bị xung đột tại cùng phạm vi hoặc độ ưu tiên; vui lòng thay đổi phạm vi hoặc thứ tự ưu tiên.',
  'Invalid pricing fields': 'Thông tin bảng giá không hợp lệ.',
  'Price interval must end after it starts': 'Giờ kết thúc khoảng giá phải sau giờ bắt đầu.',
  'Effective period is invalid': 'Khoảng thời gian hiệu lực không hợp lệ.',
  'Opening time must precede closing time': 'Giờ mở cửa phải trước giờ đóng cửa.',
  'Special opening hours must have a valid interval':
    'Khung giờ mở cửa đặc biệt phải có khoảng thời gian hợp lệ.',
  'Special opening interval must contain whole slots':
    'Khung giờ mở cửa đặc biệt phải chứa các khung giờ trọn vẹn.',
  'Special opening interval has already ended': 'Khung giờ mở cửa đặc biệt đã kết thúc.',
  'Exceptions for the same scope overlap':
    'Các ngoại lệ trong cùng phạm vi bị trùng lặp thời gian.',
  'Past exceptions cannot be created': 'Không thể tạo ngày ngoại lệ trong quá khứ.',
  'Started or past exceptions cannot be changed':
    'Không thể thay đổi ngoại lệ đã bắt đầu hoặc trong quá khứ.',
  'CLOSED exceptions apply to the whole local date':
    'Ngoại lệ đóng cửa áp dụng cho trọn vẹn ngày đó.',
  'Suspended facilities cannot be specially opened':
    'Cơ sở đang tạm dừng hoạt động không thể mở cửa đặc biệt.',
  'Configured slots must have an unambiguous price':
    'Các khung giờ đã cấu hình phải có bảng giá rõ ràng và không mâu thuẫn.',
  'Choose between 1 and 200 courts': 'Vui lòng chọn từ 1 đến 200 sân.',
  'Signup cannot replace existing configuration':
    'Đăng ký ban đầu không thể thay thế cấu hình đã có sẵn.',
  'Schedule times must use whole minutes': 'Thời gian trong lịch phải tính theo phút tròn.',
  'SCHEDULE_READ requires an active Staff binding at this facility':
    'Xem lịch yêu cầu quyền Nhân viên đang hoạt động tại cơ sở này.',
  'Booking interval is invalid': 'Khoảng thời gian đặt sân không hợp lệ.',
  'Choose slots within one local date': 'Vui lòng chọn các khung giờ trong cùng một ngày.',
  'Interval does not align with operating slots':
    'Khoảng thời gian không khớp với các khung giờ khung giờ hoạt động.',
}
