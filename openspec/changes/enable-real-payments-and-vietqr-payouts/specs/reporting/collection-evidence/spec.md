> **HISTORY ONLY — NOT AN ACTIVE REQUIREMENT SOURCE.**
> Read `openspec/CURRENT_CHANGE.md` at the project root for the current proposal.
> Do not apply, merge, or resume requirements/tasks from this document unless
> the user explicitly requests this historical change. Task counts are historical.

# Spec Delta

## Purpose

Cung cấp facts tài chính REAL có nguồn xác nhận rõ cho báo cáo và kiểm tra nghiệp vụ. Tiền thu ngân hàng, khoản áp dụng cho sân, phí handoff và chi thủ công là các loại dữ kiện riêng, không trộn với DEMO.

## ADDED Requirements

### Requirement: Real incoming collections require authenticated bank evidence

Khoản thu REAL SHALL có bằng chứng provider đã xác thực, binding cơ sở/Owner/kênh/đơn, amount/currency/reference/time và quyết định áp dụng rõ. Tên provider hoặc status SUCCESS tự khai SHALL không đủ để phân loại tiền thật.

#### Scenario: Real provider name has no valid evidence
- **WHEN** record ghi PAYOS/SUCCESS nhưng không có bằng chứng hợp lệ hoặc sai binding
- **THEN** facts đánh dấu cần kiểm tra/UNKNOWN, không tạo R/P hợp lệ hoặc quyền sử dụng từ tên provider

### Requirement: Unapplied and extra real collections remain visible

Khoản thu hợp lệ chưa áp dụng, thu thiếu, dư, trùng hoặc đến muộn SHALL giữ trong dòng tiền/đối soát cùng lý do và khoản cần hoàn. Chúng SHALL không tự biến thành tiền sân R hoặc phí F chỉ vì ngân hàng đã nhận tiền.

#### Scenario: Payment was received after expiration
- **WHEN**100.000đ đã vào Owner nhưng booking EXPIRED
- **THEN** thấy tiền vào100.000đ và nghĩa vụ hoàn kỹ thuật; R/F từ booking đó vẫn0

#### Scenario: Partial credits occur across reporting days
- **WHEN** một order nhận40.000đ ngày20/10 và60.000đ ngày21/10
- **THEN** dòng tiền ngân hàng giữ từng ngày/số tiền; áp dụng đủ100.000đ chỉ xảy ra một lần khi đủ, không ghi thêm tiền vào khi chuyển trạng thái

### Requirement: Stable financial identities prevent duplicate live totals

Native bank transaction trong cùng kênh và mỗi payment/effect/operation SHALL được tính một lần theo identity, không theo amount hoặc số lần nhận event. Hai giao dịch thật cùng số tiền SHALL vẫn là hai facts riêng.

#### Scenario: Webhook and query observe the same credit
- **WHEN** cùng reference được webhook retry và query trả lại nhiều lần
- **THEN** tổng tiền không tăng theo lần xử lý, sự kiện handoff/refund cũng không nhân lên

### Requirement: Live manual disbursement facts preserve their assurance method

N/H đã chi SHALL cần receipt của operator có quyền với bank reference, amount/payee/destination binding, actor và confirmedAt. Facts SHALL mang MANUALLY_RECORDED, không BANK_VERIFIED; QR/claim/note chưa hoàn tất SHALL không là tiền đã chi.

#### Scenario: QR exists without a transaction code
- **WHEN** Owner đã mở QR95.000đ nhưng chưa nhập reference và hoàn tất
- **THEN** N vẫn còn phải trả, không có fact chi95.000đ hoặc ngày chi giả

#### Scenario: Manual confirmation is committed
- **WHEN** operator hoàn tất bằng mã giao dịch hợp lệ
- **THEN** có đúng một fact chi theo confirmedAt và nhãn xác nhận thủ công; không tạo provider callback giả

### Requirement: Facility live revenue excludes resale principal and seller disbursement

R SHALL là court/contribution REAL áp dụng; F SHALL chỉ là phí Owner từ handoff COMPLETED. Doanh thu cơ sở SHALL là R+F, sau policy refund H đã ghi nhận là R+F−H. P dòng tiền mua lại và N trả A SHALL tách riêng; SportHub SHALL không hưởng phí này.

#### Scenario: Court payment resale payout and refund coexist
- **WHEN** R120.000/P100.000, x7% cho F7.000/N93.000 và policy H80.000 được cơ sở xác nhận chuyển
- **THEN** tổng kinh doanh127.000đ, sau hoàn47.000đ; P/N không cộng thêm, H có nhãn thủ công và không có doanh thu SportHub

### Requirement: Live technical returns and policy refunds are distinct effects

Hoàn phần thu không áp dụng SHALL nằm dòng tiền kỹ thuật, không tự trừ R sân gốc hoặc tạo phí. Policy H sau handoff SHALL giữ quyết định r/base/payee; trả A SHALL là N, không bị gọi là hoàn hủy cho B.

#### Scenario: Aborted resale follows an original court payment
- **WHEN** R120.000đ đã áp dụng, B trả100.000đ nhưng handoff ABORTED và cơ sở hoàn đủ B
- **THEN** R gốc vẫn120.000đ; resale F/N=0, thu/hoàn kỹ thuật100.000đ tách riêng với H policy

### Requirement: Live financial dates and outstanding balances remain auditable

Facts SHALL giữ thời điểm từng khoản thu ngân hàng, áp dụng đủ tiền, handoff và xác nhận chi thủ công riêng. Công nợ tại cutoff SHALL chỉ trừ xác nhận trước cutoff, gồm nghĩa vụ kỳ trước; sửa filter SHALL không đổi lịch sử hoặc kéo khoản thu cũ sang ngày hoàn.

#### Scenario: Confirmation occurs in a later period
- **WHEN** N93.000đ phát sinh20/10 nhưng operator ghi nhận chuyển23/10
- **THEN** report hết21/10 vẫn có nợ93.000đ; kỳ23/10 mới có chi thủ công, không dời F sang23/10

### Requirement: Live evidence projections separate money classes and protect bank data

REAL/DEMO/UNKNOWN SHALL tách rõ; dữ liệu không đầy đủ SHALL có coverage/reason thay vì tổng0 hoàn chỉnh. Facts/report SHALL chỉ trả thông tin cần thiết trong scope, không full STK/QR/credential hoặc tự biến receipt DEMO thành ngân hàng thật.

#### Scenario: Historical demo data is present during a live report
- **WHEN** cùng cơ sở có khoản REAL và DEMO trong kỳ
- **THEN** bộ REAL chỉ tính facts REAL; DEMO được xem riêng với nhãn, nguồn thiếu báo partial thay vì cộng gộp
