export type Channel = 'EMAIL' | 'PHONE'
export type Challenge = {
  id: string
  channel: Channel
  recipient: string
  expiresAt: string
  resendAfter: string
  usable: boolean
}
export const channels: Channel[] = ['EMAIL', 'PHONE']
export const labels = { EMAIL: 'Địa chỉ thư điện tử', PHONE: 'Số điện thoại' }
