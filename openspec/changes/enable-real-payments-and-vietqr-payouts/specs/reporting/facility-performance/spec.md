> **HISTORY ONLY — NOT AN ACTIVE REQUIREMENT SOURCE.**
> Read `openspec/CURRENT_CHANGE.md` at the project root for the current proposal.
> Do not apply, merge, or resume requirements/tasks from this document unless
> the user explicitly requests this historical change. Task counts are historical.

# Spec Delta

## Purpose

Bổ sung báo cáo hoạt động và tiền của cơ sở cho cohort REAL trên cùng luồng báo cáo hiện hữu. Các chỉ số phải giải thích được bằng facts thu tiền, handoff, nghĩa vụ và xác nhận chuyển khoản thủ công.

## ADDED Requirements

### Requirement: Live facility reports explicitly select a money cohort

Dashboard/chart/bảng sân/giao dịch SHALL dùng cùng mode REAL hoặc DEMO và cùng bộ lọc ngày/timezone/cutoff đã công bố. Triển khai LIVE SHALL mặc định REAL; UNKNOWN không tự gộp vào tổng, dữ liệu DEMO vẫn có thể xem riêng.

#### Scenario: Owner switches from real to demo history
- **WHEN** Owner đổi mode hoặc cơ sở/kỳ đang xem
- **THEN** mọi panel dùng filter mới nhất, reset phân trang phù hợp và không cộng tiền DEMO vào KPI REAL hoặc áp response cũ cho filter mới

### Requirement: Live facility KPIs distinguish revenue cash and obligations

Owner SHALL thấy R/F, tổng R+F, policy H đã xác nhận và R+F−H; P/N, tiền chưa áp dụng, phần dư/hoàn kỹ thuật và nợ chi SHALL hiển thị riêng. Các số SHALL không được gọi là doanh thu SportHub hoặc toàn bộ lợi nhuận/tiền đã đối soát ngân hàng.

#### Scenario: Seller payout is pending while buyer has usage
- **WHEN** B đã nhận quyền, F7.000đ đã phát sinh và N93.000đ chưa trả
- **THEN** report ghi F7.000đ, N còn nợ93.000đ; B đủ tiền và P100.000đ không cộng thành doanh thu

#### Scenario: Policy refund is prepared but not recorded
- **WHEN** QR hoàn80.000đ đã tạo nhưng chưa nhập mã giao dịch
- **THEN** H chưa được tính đã hoàn, tiền sau hoàn chưa giảm80.000đ và phần phải hoàn vẫn có

### Requirement: Live report labels disclose manual disbursement confirmation

N/H hoàn tất thủ công SHALL có nhãn cơ sở xác nhận đã chuyển, nguồn xác nhận và confirmedAt. Report SHALL phân biệt nó với tiền vào BANK_VERIFIED; full STK/QR/credential SHALL không có trong bảng báo cáo tổng.

#### Scenario: Owner examines completed outgoing transactions
- **WHEN** một payout/refund được hoàn tất bằng bank reference do operator nhập
- **THEN** bảng ghi phương thức thủ công và thời điểm xác nhận, không hiển thị như payOS đã xác thực tiền ra

### Requirement: Live report dates preserve receipts handoffs and manual confirmations

R SHALL theo lúc đủ tiền được áp dụng; dòng tiền P và tiền chưa áp dụng SHALL giữ thời điểm từng bank credit; F theo handoffAt, N/H theo manual confirmedAt. Mọi chỉ số SHALL công bố date basis, công nợ tính tới cutoff và không ghi nhận lại dòng tiền khi đổi trạng thái.

#### Scenario: Cash and application fall on different dates
- **WHEN** order nhận40.000đ20/10,60.000đ21/10 rồi đủ tiền và trả A23/10
- **THEN** dòng tiền giữ40.000/60.000 ở từng ngày, khoản áp dụng ghi một lần21/10; chi thủ công23/10 không dời ngày thu hoặc handoff

#### Scenario: Prior debt is settled after the selected period
- **WHEN** nợ phát sinh trước kỳ nhưng chỉ được xác nhận trả sau cutoff
- **THEN** số còn phải trả tại cutoff vẫn gồm nợ đó; không lấy trạng thái hiện tại để xóa nợ quá khứ

### Requirement: Live operational counts and usage stay independent of money event counts

Booking count SHALL đếm booking theo lịch, không theo số bank credit/payer/refund. Giờ sử dụng SHALL chỉ từ check-in đủ tiền, cắt theo lịch/report/asOf; đọc report SHALL không tự chuyển trạng thái hoặc tạo tỷ lệ lấp đầy thiếu dữ liệu lịch sử.

#### Scenario: Several bank transfers fund one booking
- **WHEN** hai khoản40.000đ và60.000đ trả đủ một booking100.000đ
- **THEN** report chỉ đếm một booking, không hai; chưa check-in thì không suy đã sử dụng sân

### Requirement: Live facility reports remain private read only and recoverable

Server SHALL yêu cầu Owner sở hữu cơ sở cho report/drill-down; grant hoàn/trả A không cấp dashboard Owner. GET/filter/retry view SHALL không thực hiện tiền; thiếu nguồn trả PARTIAL/UNAVAILABLE có retry, không số0 hoàn chỉnh hoặc dữ liệu mẫu.

#### Scenario: Operator tries to read Owner revenue
- **WHEN** Staff có TRANSFER_PAYOUT/BOOKING_REFUND hoặc Owner đổi sang facilityId người khác
- **THEN** server từ chối trước khi trả số tiền/dòng giao dịch ngoài quyền

#### Scenario: One evidence source fails
- **WHEN** nguồn cần thiết timeout lúc tải report
- **THEN** UI báo dữ liệu một phần/lỗi và phạm vi thiếu, không vẽ tổng đầy đủ bằng0 hoặc ghi thêm receipt để làm khớp
