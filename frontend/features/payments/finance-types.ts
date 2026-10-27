export type Obligation = {
  id: string
  bookingId: string
  facilityId: string
  kind: string
  amount: string
  state: string
  version: number
  payeeId?: string | null
  guestBookingId?: string | null
  destinationRevisionId?: string | null
  operationId?: string | null
  operationVersion?: number
  claimedBy?: string | null
  confirmationMethod?: string | null
  confirmedAt?: string | null
  bankReference?: string | null
}
export type Operation = {
  id: string
  operationId: string
  obligationId: string
  kind: string
  version: number
  claimedBy: string
  state: string
  amount: string
  description: string
  destinationRevisionId: string
  bankBin?: string
  bankName?: string
  accountNumber?: string
  accountName?: string
  qrImageDataUrl?: string
  confirmationMethod?: string
  confirmationLabel?: string
  bankTransactionReference?: string
}
export type Finance = {
  mode: 'REAL'
  canPaySeller?: boolean
  canRefund?: boolean
  obligations: Obligation[]
  settlements: {
    acquisitionId: string
    bookingId: string
    sellerId: string
    buyerId: string
    amount: string
    feeAmount: string
    sellerNetAmount: string
    payoutState: string | null
  }[]
}
export const obligationLabels: Record<string, string> = {
  AWAITING_BANK_DETAILS: 'Chờ người nhận bổ sung tài khoản',
  UNPAID: 'Chưa chuyển tiền',
  PREPARED: 'Đã có người nhận xử lý',
  REVIEW: 'Cần đối soát trước khi thực hiện tiếp',
  MANUALLY_RECORDED: 'Cơ sở xác nhận đã chuyển',
  RELEASED: 'Đã xác nhận chưa chuyển và giải phóng lệnh',
}
export const obligationKinds: Record<string, string> = {
  SELLER_PAYOUT: 'Trả người chuyển nhượng',
  POLICY_REFUND: 'Hoàn theo chính sách',
  TECHNICAL_REFUND: 'Hoàn đủ tiền không áp dụng được',
}
