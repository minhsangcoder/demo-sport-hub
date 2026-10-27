export type PeerAttempt = {
  id: string
  listingId: string
  bookingId: string
  sellerId: string
  buyerId: string
  protocol: 'PEER_RESALE_V1'
  phase: string
  version: number
  amount: number | string
  currency: string
  payBy: string
  usageDecisionBy: string
  conversationId: string
  termsHash: string
  confirmationMethod: string
  decision: { decision: string; reason: string; decidedAt: string } | null
}
export const peerPhases: Record<string, string> = {
  WAITING_PAYMENT: 'Chờ người mua chuyển tiền',
  PAYMENT_DECLARED: 'Người mua đã khai chuyển tiền',
  DISPUTED: 'Đang tranh chấp quyền sân',
  HANDOFF_PENDING: 'Đang bàn giao',
  COMPLETED: 'Đã bàn giao cho người mua',
  EXPIRED: 'Lượt giữ đã kết thúc',
  ABORTED_A_RETAINS: 'Người bán giữ quyền sân',
  TIMED_OUT_A_RETAINS: 'Hết hạn phân xử; người bán giữ sân',
}
export const finalPeerPhase = (phase: string) =>
  ['COMPLETED', 'EXPIRED', 'ABORTED_A_RETAINS', 'TIMED_OUT_A_RETAINS'].includes(phase)
export type PeerEvidence = {
  id: string
  acquisitionId: string
  uploaderId: string
  contentType: string
  size: number
  state: string
  createdAt: string
}
export type PeerMessage = {
  id: string
  cursor: number
  senderId: string
  text: string
  createdAt: string
}
export type PeerDeclaration = {
  reference: string
  evidenceIds: string[]
  claimedAt: string
  confirmationMethod: string
}
export type PeerMoney = {
  acquisitionId: string
  termsHash: string
  state: string
  amount: string
  authority: string
  noClaims: string[]
  returns: {
    id: string
    amount: string
    state: string
    version: number
    reference: string
    evidenceIds: string[]
    confirmationMethod: string
  }[]
  returnPage: { more: boolean; nextAfter: string | null }
  totals: {
    declared: string
    received: string
    awaitingBuyer: string
    records: number
    pending: number
  }
  reports: {
    id: string
    reference: string
    evidenceIds: string[]
    reporterId: string
    reportedAt: string
  }[]
}
export type PeerCase = {
  attempt: PeerAttempt
  authority: 'USAGE_ONLY'
  booking: {
    id: string
    status: string
    currentHolderId: string
    nativeAmount: number
    startsAt: string
    endsAt: string
  }
  declaration: PeerDeclaration | null
  moneyCase: { state: string; reason: string }
  money: PeerMoney
  presence: { party: string; reason: string; observedAt: string }[]
  history: { id: string; action: string; createdAt: string }[]
}
