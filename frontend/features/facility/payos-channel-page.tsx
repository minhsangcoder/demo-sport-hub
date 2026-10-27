'use client'
import { useState } from 'react'
import { api } from '@/lib/api'
import { useApi } from '@/lib/use-api'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { BankSelect } from '@/features/payments/bank-select'
import type { PayosChannel } from '@/features/payments/types'
import { useFacility } from './facility-store'

const labels: Record<string, string> = {
  PREPARING: 'Chờ xác minh',
  READY: 'Sẵn sàng nhận tiền',
  DISABLED: 'Đã tắt',
}
export function PayosChannelPage() {
  const { facility } = useFacility()
  const path = `/payments/facilities/${facility.id}/channel`
  const resource = useApi<PayosChannel | null>(path)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  async function configure(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy) return
    const form = event.currentTarget
    const fields = new FormData(form)
    setBusy(true)
    setError('')
    setMessage('')
    try {
      await api(path, {
        method: 'PUT',
        headers: { 'Idempotency-Key': crypto.randomUUID() },
        body: JSON.stringify({
          version: resource.data?.version ?? 0,
          bankBin: fields.get('bankBin'),
          clientId: fields.get('clientId'),
          apiKey: fields.get('apiKey'),
          checksumKey: fields.get('checksumKey'),
        }),
      })
      setMessage(
        'Đã lưu khóa ở máy chủ. Bấm Xác minh kênh để xác nhận tài khoản nhận và địa chỉ thông báo.',
      )
      await resource.reload()
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : 'Không thể lưu kênh nhận tiền.')
      void resource.reload()
    } finally {
      form.reset()
      setBusy(false)
    }
  }
  async function action(kind: 'verify' | 'disable') {
    if (!resource.data || busy) return
    setBusy(true)
    setError('')
    setMessage('')
    try {
      await api(`${path}/${kind}`, {
        method: 'POST',
        headers: { 'Idempotency-Key': crypto.randomUUID() },
        body: JSON.stringify({ version: resource.data.version }),
      })
      setMessage(
        kind === 'verify'
          ? 'Kênh đã được xác minh. Khi tạo QR, hệ thống sẽ kiểm tra ngân hàng và số tài khoản nhận.'
          : 'Đã tắt tạo khoản thu qua kênh này; các giao dịch đã mở vẫn được đối soát.',
      )
      await resource.reload()
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : 'Không thể cập nhật kênh.')
      void resource.reload()
    } finally {
      setBusy(false)
    }
  }
  return (
    <section className="max-w-2xl space-y-5">
      <h1 className="text-2xl font-bold">Kênh nhận tiền payOS</h1>
      <p className="text-sm text-slate-600">
        Tiền khách chuyển vào tài khoản của cơ sở. Chủ cơ sở cấu hình kênh riêng; SportHub không
        hưởng khoản thu hoặc phí chuyển nhượng này.
      </p>
      {resource.loading && <p role="status">Đang tải kênh nhận tiền…</p>}
      {(error || resource.error) && (
        <p role="alert" className="rounded-xl bg-red-50 p-4 text-red-700">
          {error || resource.error}
        </p>
      )}
      {message && (
        <p role="status" className="rounded-xl bg-emerald-50 p-4 text-emerald-800">
          {message}
        </p>
      )}
      {resource.data && (
        <div className="space-y-2 rounded-xl border p-4 text-sm">
          <p>
            Trạng thái: <strong>{labels[resource.data.status] ?? 'Cần kiểm tra'}</strong>
          </p>
          <p>
            Khóa:{' '}
            {resource.data.credentialsConfigured ? 'Đã lưu an toàn trên máy chủ' : 'Chưa cấu hình'}
          </p>
          <p>
            Ngân hàng: {resource.data.bankBin} · Số tài khoản:{' '}
            {resource.data.maskedAccountNumber || 'Chưa xác minh'}
          </p>
          <p>Người nhận: {resource.data.accountName || 'Chưa xác minh'}</p>
          <p className="break-all">Địa chỉ nhận thông báo: {resource.data.webhookUrl}</p>
          <div className="flex flex-wrap gap-3">
            <Button onClick={() => action('verify')} disabled={busy}>
              Xác minh kênh
            </Button>
            <Button
              variant="outline"
              onClick={() => action('disable')}
              disabled={busy || resource.data.status === 'DISABLED'}
            >
              Tắt kênh
            </Button>
          </div>
        </div>
      )}
      <form onSubmit={configure} className="space-y-4 rounded-xl border p-5" autoComplete="off">
        <h2 className="font-semibold">
          {resource.data ? 'Thay khóa kết nối' : 'Thiết lập kết nối'}
        </h2>
        <BankSelect
          key={resource.data?.version ?? 0}
          defaultValue={resource.data?.bankBin}
          disabled={busy || resource.loading || Boolean(resource.error)}
        />
        <label className="grid gap-2 text-sm">
          Mã kênh (Client ID)
          <Input
            name="clientId"
            type="password"
            autoComplete="new-password"
            required
            maxLength={200}
            disabled={busy}
          />
        </label>
        <label className="grid gap-2 text-sm">
          Khóa API
          <Input
            name="apiKey"
            type="password"
            autoComplete="new-password"
            required
            maxLength={200}
            disabled={busy}
          />
        </label>
        <label className="grid gap-2 text-sm">
          Khóa kiểm tra chữ ký
          <Input
            name="checksumKey"
            type="password"
            autoComplete="new-password"
            required
            maxLength={200}
            disabled={busy}
          />
        </label>
        <p className="text-xs text-slate-600">
          Không gửi khóa trong đường dẫn hoặc lưu trên trình duyệt. Chọn ngân hàng đúng với tài
          khoản gắn kênh payOS; số tài khoản được máy chủ lấy từ payOS.
        </p>
        <Button type="submit" disabled={busy || resource.loading || Boolean(resource.error)}>
          {busy ? 'Đang xử lý…' : 'Lưu cấu hình'}
        </Button>
      </form>
    </section>
  )
}
