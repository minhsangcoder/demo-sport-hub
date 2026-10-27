'use client'
import Image from 'next/image'
import { useEffect, useRef, useState } from 'react'
import { api, ApiError } from '@/lib/api'
import { useAuth } from '@/lib/auth-context'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { money } from '@/features/schedule/types'
import { obligationLabels, type Operation } from './finance-types'

export function ManualOperationPanel({
  operation,
  onChanged,
  onClose,
}: {
  operation: Operation
  onChanged: (next: Operation) => void
  onClose: () => void
}) {
  const { user } = useAuth()
  const dialog = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    const element = dialog.current
    element?.showModal()
    return () => element?.close()
  }, [])
  const [reference, setReference] = useState('')
  const [reason, setReason] = useState('')
  const [unsent, setUnsent] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const intent = useRef<{ body: string; key: string } | null>(null)
  const ended = ['MANUALLY_RECORDED', 'RELEASED'].includes(operation.state)
  async function complete(event: React.FormEvent) {
    event.preventDefault()
    if (busy || ended || !reference.trim()) return
    const body = JSON.stringify({
      version: operation.version,
      bankTransactionReference: reference.trim(),
    })
    if (intent.current?.body !== body) intent.current = { body, key: crypto.randomUUID() }
    setBusy(true)
    setError('')
    try {
      onChanged(
        await api<Operation>(`/bookings/facility-finance/operations/${operation.id}/complete`, {
          method: 'POST',
          headers: { 'Idempotency-Key': intent.current.key },
          body,
        }),
      )
    } catch (failure) {
      if (failure instanceof ApiError && [401, 403].includes(failure.status)) onClose()
      setError(
        failure instanceof Error
          ? failure.message
          : 'Chưa rõ kết quả ghi nhận. Dùng cùng mã giao dịch để kiểm tra; không chuyển tiền lần nữa.',
      )
    } finally {
      setBusy(false)
    }
  }
  async function review(action: 'REVIEW' | 'RELEASE') {
    if (busy || ended || !reason.trim()) return
    setBusy(true)
    setError('')
    try {
      await api(`/bookings/facility-finance/operations/${operation.id}/review`, {
        method: 'POST',
        headers: { 'Idempotency-Key': crypto.randomUUID() },
        body: JSON.stringify({
          version: operation.version,
          action,
          reason: reason.trim(),
          confirmedNotTransferred: unsent,
        }),
      })
      if (action === 'RELEASE') onClose()
      else onChanged(await api<Operation>(`/bookings/facility-finance/operations/${operation.id}`))
    } catch (failure) {
      if (failure instanceof ApiError && [401, 403].includes(failure.status)) onClose()
      setError(
        failure instanceof Error ? failure.message : 'Không cập nhật được trạng thái đối soát.',
      )
    } finally {
      setBusy(false)
    }
  }
  return (
    <dialog
      ref={dialog}
      aria-labelledby="manual-operation-title"
      onCancel={(event) => {
        event.preventDefault()
        if (!busy) onClose()
      }}
      className="m-auto w-[calc(100%-1.5rem)] max-w-xl rounded-2xl bg-white p-0 backdrop:bg-black/50"
    >
      <section className="max-h-[92vh] w-full space-y-4 overflow-y-auto rounded-2xl bg-white p-5 text-slate-900">
        <div className="flex justify-between gap-4">
          <h2 id="manual-operation-title" className="text-lg font-bold">
            Chuyển khoản đã chốt
          </h2>
          <Button variant="outline" onClick={onClose} disabled={busy}>
            Đóng
          </Button>
        </div>
        <p role="status" className="font-semibold">
          {obligationLabels[operation.state] ?? 'Cần kiểm tra trạng thái'}
        </p>
        <p className="rounded-xl bg-amber-50 p-4 text-sm text-amber-900">
          Nếu đã chuyển tiền, hãy nhập mã của giao dịch đó. Đóng màn hình không giải phóng lệnh; mất
          phản hồi không có nghĩa là phải chuyển lần nữa.
        </p>
        <dl className="grid grid-cols-2 gap-3 text-sm">
          <dt>Số tiền</dt>
          <dd className="text-right font-bold">{money(Number(operation.amount))}</dd>
          <dt>Nội dung</dt>
          <dd className="break-all text-right font-mono font-bold">{operation.description}</dd>
          {operation.accountNumber && (
            <>
              <dt>Ngân hàng</dt>
              <dd className="text-right">
                {operation.bankName} ({operation.bankBin})
              </dd>
              <dt>Số tài khoản người nhận</dt>
              <dd className="break-all text-right font-semibold">{operation.accountNumber}</dd>
              <dt>Tên người nhận tự khai</dt>
              <dd className="text-right">{operation.accountName || 'Chưa cung cấp'}</dd>
            </>
          )}
        </dl>
        {operation.state === 'PREPARED' && operation.qrImageDataUrl && (
          <Image
            unoptimized
            src={operation.qrImageDataUrl}
            width={320}
            height={320}
            alt="VietQR trả hoặc hoàn vào tài khoản người nhận đã chốt"
            className="mx-auto h-auto max-w-full"
          />
        )}
        {operation.state === 'REVIEW' && (
          <p className="text-sm text-amber-800">
            QR được ẩn khi đang đối soát. Owner kiểm tra giao dịch đã chuyển và ghi mã; chỉ giải
            phóng khi chắc chắn chưa chuyển.
          </p>
        )}
        {!ended && (
          <form onSubmit={complete} className="space-y-3">
            <label className="grid gap-2 text-sm">
              Mã giao dịch ngân hàng sau khi chuyển
              <Input
                value={reference}
                onChange={(event) => setReference(event.target.value)}
                autoComplete="off"
                maxLength={120}
                pattern="[A-Za-z0-9][A-Za-z0-9 ._:\/\-]{0,119}"
                required
                disabled={busy}
              />
            </label>
            <Button
              type="submit"
              disabled={
                busy ||
                !reference.trim() ||
                (operation.state === 'REVIEW' && !user?.roles.includes('OWNER'))
              }
            >
              {busy ? 'Đang ghi nhận…' : 'Hoàn tất chuyển khoản'}
            </Button>
          </form>
        )}
        {ended && operation.state === 'MANUALLY_RECORDED' && (
          <p className="text-sm text-emerald-800">
            Cơ sở xác nhận đã chuyển · mã {operation.bankTransactionReference}. Đây là xác nhận thủ
            công, không phải xác thực ngân hàng.
          </p>
        )}
        {!ended && (
          <details>
            <summary className="cursor-pointer text-sm font-semibold">
              Đối soát hoặc xác nhận chưa chuyển
            </summary>
            <div className="mt-3 space-y-3">
              <label className="grid gap-2 text-sm">
                Lý do
                <Input
                  value={reason}
                  onChange={(event) => setReason(event.target.value)}
                  maxLength={500}
                  disabled={busy}
                />
              </label>
              <Button
                variant="outline"
                onClick={() => review('REVIEW')}
                disabled={busy || !reason.trim()}
              >
                Báo chưa rõ kết quả giao dịch
              </Button>
              {user?.roles.includes('OWNER') && (
                <>
                  <label className="flex gap-2 text-sm">
                    <input
                      type="checkbox"
                      checked={unsent}
                      onChange={(event) => setUnsent(event.target.checked)}
                      disabled={busy}
                    />
                    Tôi đã kiểm tra chắc chắn chưa chuyển khoản. QR cũ đã lưu ngoài hệ thống không
                    thể thu hồi.
                  </label>
                  <Button
                    variant="outline"
                    onClick={() => review('RELEASE')}
                    disabled={busy || !reason.trim() || !unsent}
                  >
                    Xác nhận chưa chuyển và giải phóng lệnh
                  </Button>
                </>
              )}
            </div>
          </details>
        )}
        {error && (
          <p role="alert" className="text-red-700">
            {error}
          </p>
        )}
      </section>
    </dialog>
  )
}
