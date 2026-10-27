'use client'
import { useState } from 'react'
import { useApi } from '@/lib/use-api'
import { Button } from '@/components/ui/button'
import { money } from '@/features/schedule/types'
import { EvidencePreview, PrivateImage } from './private-image'
import { PeerEvidencePicker } from './peer-evidence'
import { usePeerCommand } from './use-peer-command'
import type { PeerAttempt, PeerDeclaration } from './peer-types'

type Instruction = {
  amount: string
  description: string
  bankName: string
  maskedAccountNumber: string
  accountName: string
  expiresAt: string
}
export function PeerPayment({
  attempt: a,
  userId,
  reload,
  remaining,
}: {
  attempt: PeerAttempt
  userId: string
  reload: () => void
  remaining: number
}) {
  const buyer = userId === a.buyerId
  const instruction = useApi<Instruction>(
    a.phase === 'WAITING_PAYMENT' && remaining > 0
      ? `/payments/peer-acquisitions/${a.id}/instruction`
      : null,
  )
  const declaration = useApi<PeerDeclaration>(
    ['PAYMENT_DECLARED', 'DISPUTED', 'COMPLETED'].includes(a.phase)
      ? `/transfers/acquisitions/${a.id}/declaration`
      : null,
  )
  const [evidence, setEvidence] = useState<string[]>([])
  const [reference, setReference] = useState('')
  const command = usePeerCommand()
  const input = { version: a.version, termsHash: a.termsHash }
  const run = (action: string) =>
    void command.run(`/transfers/acquisitions/${a.id}/${action}`, input, reload)
  return (
    <section className="space-y-4 rounded-xl border p-5">
      <h2 className="text-lg font-bold">Thanh toán trực tiếp vào tài khoản người bán</h2>
      {a.phase === 'WAITING_PAYMENT' && remaining > 0 && (
        <>
          {instruction.data && (
            <>
              <p>
                {instruction.data.bankName} · {instruction.data.maskedAccountNumber} ·{' '}
                {instruction.data.accountName} (tên tự khai)
              </p>
              <p className="font-bold">
                {money(Number(instruction.data.amount), 'VND')} · Nội dung:{' '}
                {instruction.data.description}
              </p>
              <div className="mx-auto max-w-72">
                <PrivateImage
                  path={`/payments/peer-acquisitions/${a.id}/qr`}
                  alt="QR SePay chuyển tiền đúng giá cho người bán"
                />
              </div>
            </>
          )}
          {(instruction.loading || instruction.error) && (
            <p role={instruction.error ? 'alert' : 'status'}>
              {instruction.error || 'Đang lấy hướng dẫn chuyển tiền…'}{' '}
              <button type="button" onClick={instruction.reload} className="underline">
                Thử lại
              </button>
            </p>
          )}
          <p className="text-sm text-amber-900">
            Chỉ chuyển khi phiên còn hạn. QR đã lưu ngoài SportHub không thể thu hồi tại ngân hàng;
            không dùng lại mã cũ sau khi hết hạn.
          </p>
          {buyer && (
            <form
              className="space-y-4"
              onSubmit={(event) => {
                event.preventDefault()
                void command.run(
                  `/transfers/acquisitions/${a.id}/declare-payment`,
                  { ...input, bankTransactionReference: reference.trim(), evidenceIds: evidence },
                  reload,
                )
              }}
            >
              <PeerEvidencePicker
                acquisitionId={a.id}
                userId={userId}
                value={evidence}
                onChange={setEvidence}
                disabled={command.busy || command.blocked}
              />
              <label className="grid gap-2 text-sm">
                Mã giao dịch ngân hàng
                <input
                  className="rounded-lg border p-3"
                  value={reference}
                  onChange={(event) => setReference(event.target.value)}
                  required
                  maxLength={120}
                  disabled={command.busy || command.blocked}
                />
              </label>
              <Button
                type="submit"
                disabled={
                  command.busy || command.blocked || !reference.trim() || evidence.length < 1
                }
              >
                Tôi đã chuyển tiền, gửi chứng từ
              </Button>
              <Button
                type="button"
                variant="outline"
                disabled={command.busy || command.blocked}
                onClick={() => {
                  if (
                    window.confirm(
                      'Chỉ hủy nếu chưa chuyển tiền. Bạn muốn giải phóng lượt giữ này?',
                    )
                  )
                    run('release')
                }}
              >
                Hủy lượt giữ chưa thanh toán
              </Button>
            </form>
          )}
        </>
      )}
      {declaration.data && (
        <div className="space-y-2 text-sm">
          <p>
            Người mua khai mã giao dịch: <strong>{declaration.data.reference}</strong>. Đây là lời
            khai, chưa phải xác minh ngân hàng.
          </p>
          <div className="grid gap-3 sm:grid-cols-3">
            {declaration.data.evidenceIds.map((id) => (
              <EvidencePreview key={id} id={id} />
            ))}
          </div>
        </div>
      )}
      {declaration.error && <p role="alert">{declaration.error}</p>}
      {a.phase === 'PAYMENT_DECLARED' &&
        (buyer ? (
          <>
            <p>
              Đang chờ người bán kiểm tra tiền thực nhận. Lượt giữ không tự hết sau 5 phút khi đã
              khai đúng hạn.
            </p>
            <Button
              variant="outline"
              disabled={command.busy || command.blocked}
              onClick={() => run('open-dispute')}
            >
              Khiếu nại chưa được bàn giao
            </Button>
          </>
        ) : (
          <div className="space-y-3">
            <p>
              Kiểm tra đủ {money(Number(a.amount), a.currency)} trong tài khoản của bạn trước khi
              xác nhận.
            </p>
            <Button
              disabled={command.busy || command.blocked}
              onClick={() => {
                if (
                  window.confirm(
                    'Bạn đã nhận đủ tiền? Xác nhận sẽ bàn giao quyền sân và đổi mã vào sân cho người mua.',
                  )
                )
                  run('confirm-receipt')
              }}
            >
              Đã nhận đủ tiền, bàn giao sân
            </Button>
            <Button
              variant="outline"
              disabled={command.busy || command.blocked}
              onClick={() => run('deny-receipt')}
            >
              Chưa nhận đủ tiền, gửi Owner phân xử quyền sân
            </Button>
          </div>
        ))}
      {a.phase === 'DISPUTED' && (
        <p className="rounded bg-amber-50 p-3">
          Quyền sân đang đóng băng. Đến cơ sở để Owner hoặc nhân viên được phép ghi nhận có mặt;
          nhắn tin không tính có mặt. Owner chỉ quyết định quyền dùng sân.
        </p>
      )}
      {command.error && (
        <p role="alert" className="text-red-700">
          {command.error}
        </p>
      )}
    </section>
  )
}
