# SportHub — Nền Tảng Đặt Sân Thể Thao Trực Tuyến

Hệ thống đặt sân thể thao đa cơ sở (Sport Court Booking System) xây dựng theo kiến trúc Microservices (Spring Boot 3, PostgreSQL, Redis, RabbitMQ, MinIO) kết hợp giao diện Web hiện đại (Next.js 15, TailwindCSS, TypeScript).

---

## 🚀 Hướng Dẫn Khởi Động Nhanh Cho Developer / Thành Viên Mới

Khi clone source code từ GitHub về, thực hiện theo các bước sau để chạy và có sẵn toàn bộ tài khoản test:

### 1. Chuẩn bị môi trường (`.env`)
Mở PowerShell tại thư mục gốc của dự án và chạy script khởi tạo:
```powershell
pwsh scripts/demo/init-env.ps1
```
*(Hoặc sao chép `cp .env.example .env`). Mật khẩu demo mặc định là **`SportHub123@`**.*

### 2. Khởi động hạ tầng Docker
Đảm bảo **Docker Desktop** đang chạy, sau đó bật database và các dịch vụ middleware:
```powershell
docker compose up -d
```
*(PostgreSQL, Redis, RabbitMQ, MinIO sẽ khởi động và sẵn sàng trên các port cục bộ).*

### 3. Nạp dữ liệu tài khoản test (Tất cả Role)
Chạy script seed một chạm để nạp toàn bộ 11 tài khoản kèm phân quyền:
```powershell
pwsh scripts/seed-accounts.ps1
```

### 4. Khởi động Backend & Frontend

#### Khởi động Backend Services:
```powershell
pwsh scripts/demo/start.ps1
```
*(Script sẽ tự động build và chạy: `identity-service`, `facility-service`, `schedule-service`, `booking-service`, `payment-service`, `transfer-service` và `api-gateway` tại `http://localhost:18080`).*

#### Khởi động Frontend:
```powershell
cd frontend
pnpm install
pnpm dev -p 3001
```
Mở trình duyệt truy cập: **[http://localhost:3001](http://localhost:3001)** hoặc trang đăng nhập **[http://localhost:3001/login](http://localhost:3001/login)**.

---

## 👥 Danh Sách Tài Khoản Test (Tất Cả Các Role)

> **Mật khẩu dùng chung cho tất cả tài khoản bên dưới:**  
> 🔑 **`SportHub123@`**

| Vai trò (Role) | Email đăng nhập | Tên người dùng | Quyền hạn & Chức năng | Trang kiểm tra |
|:---|:---|:---|:---|:---|
| **ADMIN** | `admin@sporthub.local` | Quản trị demo | Phê duyệt hồ sơ chủ sân, quản lý tài khoản & danh mục | `/admin` |
| **OWNER** | `owner@sporthub.local` | Chủ cơ sở demo | Quản lý sân, cài đặt giờ mở cửa, bảng giá & nhân viên | `/owner` |
| **STAFF** | `staff@sporthub.local` | Nhân viên demo | Xem lịch đặt, tạo đặt sân tại quầy, check-in, bảo trì | `/staff` |
| **CUSTOMER** *(Chính)* | `customer@sporthub.local` | Khách hàng demo | Đặt sân, giữ chỗ, thanh toán demo, tạo nhóm chia tiền | `/customer/bookings` |
| **CUSTOMER** *(Phụ)* | `customer2@sporthub.local` | Khách hàng thứ hai | Tham gia nhóm chia tiền, nhận chuyển nhượng booking | `/customer/bookings` |
| **CUSTOMER** | `demo.anh@sporthub.local` | Nguyễn Minh Anh | Thành viên đặt sân / nhóm | `/customer/bookings` |
| **CUSTOMER** | `demo.huy@sporthub.local` | Trần Quốc Huy | Thành viên đặt sân / nhóm | `/customer/bookings` |
| **CUSTOMER** | `demo.linh@sporthub.local` | Lê Thùy Linh | Thành viên đặt sân / nhóm | `/customer/bookings` |
| **CUSTOMER** | `demo.trang@sporthub.local` | Phạm Thu Trang | Thành viên đặt sân / nhóm | `/customer/bookings` |
| **CUSTOMER** | `demo.quang@sporthub.local` | Vũ Minh Quang | Thành viên đặt sân / nhóm | `/customer/bookings` |
| **CUSTOMER** | `demo.mai@sporthub.local` | Đỗ Ngọc Mai | Thành viên đặt sân / nhóm | `/customer/bookings` |

---

## 🛠 Cổng Kết Nối Mặc Định (Port Mapping)

- **Frontend App:** [http://localhost:3001](http://localhost:3001)
- **API Gateway:** [http://localhost:18080](http://localhost:18080)
- **PostgreSQL:** `localhost:15432` (User: `sporthub_demo`, Database: `identity_db`, `facility_db`, `booking_db`, ...)
- **RabbitMQ Management:** [http://localhost:25672](http://localhost:25672)
- **MinIO Console:** [http://localhost:19001](http://localhost:19001)
