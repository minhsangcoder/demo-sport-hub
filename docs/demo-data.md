# Dữ liệu demo SportHub

Sau khi chạy toàn bộ ứng dụng bằng Docker Compose với profile `local` hoặc `demo`, chạy từ repository root:

```sh
python3 scripts/demo/seed-showcase.py
```

Trên Windows có thể dùng `python` thay cho `python3`. Script dùng Python 3.9 trở lên, không cần cài thư viện. `.env` phải có `DEMO_SEED_ENABLED=true` và `DEMO_PASSWORD` của các tài khoản seed. Gateway, PostgreSQL và các application service phải đang chạy.

Script bổ sung dữ liệu vào hệ thống đang chạy:

- 8 cơ sở hoạt động với 24 sân tại Hà Nội, Đà Nẵng và TP. Hồ Chí Minh, đủ Pickleball, Cầu lông, Tennis và Bóng đá.
- Lịch 06:00–23:00, slot 30/60 phút, giá giờ thường/cao điểm/cuối tuần, ảnh minh họa, giấy tờ mẫu và quyền Staff.
- Booking trả tiền thành công, booking hủy, thanh toán thất bại và booking tại quầy.
- 3 nhóm chơi: 2 nhóm có 4 thành viên đã đóng đủ tiền, 1 nhóm đã hủy.
- 4 tin chuyển nhượng: 3 tin đang bán, 1 tin đã mua và bàn giao booking.
- 4 hồ sơ cơ sở bổ sung: 2 chờ duyệt, 1 nháp, 1 bị từ chối; 2 hồ sơ đăng ký Owner: 1 chờ duyệt và 1 nháp.
- 6 tài khoản Customer mới, dùng chung mật khẩu demo. Nếu còn slot hôm nay trong cửa sổ check-in, script thêm một booking đã check-in cho Staff.

Có tổng cộng 14 cơ sở và 37 sân mới, bao gồm hồ sơ chưa công khai. Các cơ sở, thông tin liên hệ, pháp lý và giấy tờ đều là dữ liệu mô phỏng. Ảnh thể thao lấy từ tài nguyên minh họa có sẵn trong frontend.

## Trải nghiệm demo

Mở frontend tại địa chỉ trong `APP_PUBLIC_URL` hoặc `http://localhost:13000` theo cấu hình demo mặc định. Mật khẩu của các tài khoản bên dưới là `DEMO_PASSWORD` trong `.env`.

| Tài khoản | Màn hình và kịch bản |
| --- | --- |
| `customer@sporthub.local` | Booking, nhóm chơi, tin chuyển nhượng đang bán |
| `customer2@sporthub.local` | Booking, nhóm chơi, lượt mua chuyển nhượng |
| `owner@sporthub.local` | Cơ sở, lịch giá, Staff, hồ sơ cơ sở bổ sung |
| `staff@sporthub.local` | Lịch, booking tại quầy, check-in tại 8 cơ sở mới |
| `admin@sporthub.local` | Tài khoản, duyệt hồ sơ, booking, giao dịch, báo cáo |
| `demo.anh@sporthub.local`, `demo.huy@sporthub.local` | Thành viên hai nhóm đã thanh toán |
| `demo.linh@sporthub.local` | Hồ sơ đăng ký Owner đang chờ duyệt |
| `demo.trang@sporthub.local` | Hồ sơ đăng ký Owner đang là nháp |
| `demo.quang@sporthub.local`, `demo.mai@sporthub.local` | Customer mẫu |

Booking được tạo từ ngày chạy đến 9 ngày tiếp theo. Tìm sân với từ khóa `Demo` và ngày mai để thấy các sân mới có giá và lịch trống.

## Chạy lại và xác minh

Script chỉ gọi Gateway trên localhost. Các luồng cơ sở, lịch, booking, payment, nhóm và chuyển nhượng dùng API thật; payment dùng provider Demo. Tài khoản Customer mới được thêm qua fixture SQL vào `identity_db`, sao chép hash mật khẩu của Customer seed hiện có. Fixture SQL chỉ chèn tài khoản mới, giữ nguyên tài khoản và profile đã có.

Manifest lưu tại `tmp/demo/showcase-manifest.json`; giữ file này để lần chạy tiếp theo dùng lại booking, payment, nhóm và tin đã tạo. Thư mục `tmp/` được gitignore. Script không xóa dữ liệu hiện có. Không chạy hai bản script đồng thời trên cùng bộ dữ liệu.

```sh
# Xác minh bộ dữ liệu đã nạp, không tạo thêm kịch bản
python3 scripts/demo/seed-showcase.py --verify-only

# Tạo một đợt booking/nhóm/chuyển nhượng theo ngày mới; tái sử dụng cơ sở
python3 scripts/demo/seed-showcase.py --date YYYY-MM-DD
```

Kết quả kiểm tra và các đường dẫn màn hình được ghi vào `tmp/demo/showcase-summary.md`. Script kiểm tra cơ sở công khai, sân tìm kiếm có giá/lịch trống, ảnh truy cập được, booking/payment và tổng đóng góp của nhóm đã xác nhận.
