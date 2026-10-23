'use client'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { ApiError,api } from '@/lib/api'
import { useApi } from '@/lib/use-api'
import { useState } from 'react'
import { transferFeeBpsToPercent,transferFeePercentToBps,type TransferFeePolicy } from './transfer-fee-policy'

export function TransferFeePolicyEditor({ facilityId }: { facilityId: string }) {
  const path = `/owner/facilities/${facilityId}/transfer-fee-policy`
  const policy = useApi<TransferFeePolicy>(path)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [conflict, setConflict] = useState(false)
  const [saved, setSaved] = useState(false)

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!policy.data || busy || conflict) return
    setError(''); setSaved(false)
    try {
      const feeBps = transferFeePercentToBps(String(new FormData(event.currentTarget).get('feePercent') ?? ''))
      setBusy(true)
      await api<TransferFeePolicy>(path, { method: 'PUT', body: JSON.stringify({ feeBps, version: policy.data.version }) })
      setSaved(true); policy.reload()
    } catch (failure) {
      const changed = failure instanceof ApiError && failure.status === 409
      setConflict(changed)
      setError(changed ? 'Phí đã được cập nhật. Tải phiên bản mới trước khi lưu.' : failure instanceof Error ? failure.message : 'Không thể lưu phí chuyển nhượng.')
    } finally { setBusy(false) }
  }

  if (policy.loading) return <p role="status" className="mt-8">Đang tải phí chuyển nhượng…</p>
  if (!policy.data) return <div role="alert" className="mt-8 space-y-3 text-red-700"><p>{policy.error || 'Chưa tải được phí chuyển nhượng.'}</p><Button onClick={policy.reload}>Thử lại</Button></div>
  const current = policy.data
  return <section className="mt-8 max-w-2xl space-y-4 border-t pt-6">
    <h3 className="text-xl font-bold">Phí chuyển nhượng của cơ sở</h3>
    <p className="text-sm text-slate-600">Cơ sở nhận thanh toán mô phỏng của người mua, hưởng khoản phí bạn cấu hình và có nghĩa vụ trả phần còn lại cho người bán. Người mua nhận quyền sau thanh toán mô phỏng thành công, không phải chờ trả người bán.</p>
    <p className="text-sm text-slate-600">SportHub không hưởng khoản phí này. Tỷ lệ hoàn khi hủy lượt đặt được cấu hình riêng tại Chính sách hoàn tiền.</p>
    {!current.configured && <p className="rounded-xl bg-amber-50 p-4 text-sm text-amber-900">Chưa cấu hình phí. chủ cơ sở cần lưu tỷ lệ trước khi có chuyển nhượng mới. Bạn có thể chọn 0%; không cần cấu hình tài khoản ngân hàng để mô phỏng.</p>}
    {current.configured && <p className="text-sm">Phí hiện tại: <strong>{transferFeeBpsToPercent(current.feeBps)}%</strong> · Phiên bản {current.version}</p>}
    <form key={`${current.version}:${current.feeBps}`} onSubmit={submit} className="space-y-4 rounded-2xl border p-5">
      <label className="grid gap-2 text-sm font-medium">Phí chuyển nhượng (%)
        <Input name="feePercent" required inputMode="decimal" autoComplete="off" placeholder="Nhập tỷ lệ của cơ sở" defaultValue={transferFeeBpsToPercent(current.feeBps)} disabled={busy} aria-describedby="transfer-fee-help" />
      </label>
      <p id="transfer-fee-help" className="text-sm text-slate-500">Từ 0 đến 100%, tối đa 2 chữ số thập phân. Giao dịch đã được chấp nhận giữ phí đã chốt; chỉ chủ cơ sở thay đổi cấu hình.</p>
      {error && <p role="alert" className="text-red-700">{error}</p>}
      {conflict && <Button type="button" onClick={() => { setConflict(false); setError(''); policy.reload() }}>Tải phí mới</Button>}
      {saved && <p role="status" className="text-emerald-700">Đã lưu phí chuyển nhượng của cơ sở.</p>}
      <Button type="submit" disabled={busy || conflict} className="bg-emerald-600 text-white">{busy ? 'Đang lưu…' : 'Lưu phí chuyển nhượng'}</Button>
    </form>
  </section>
}
