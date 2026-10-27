'use client'
import { useState } from 'react'
import { useApi } from '@/lib/use-api'
import { Button } from '@/components/ui/button'
import { money } from '@/features/schedule/types'
import { PeerEvidencePicker } from './peer-evidence'
import { EvidencePreview } from './private-image'
import { usePeerCommand } from './use-peer-command'
import type { PeerAttempt, PeerMoney } from './peer-types'

const states: Record<string, string> = {
  NONE: 'Chưa có hồ sơ tiền',
  OPEN: 'Còn cần hai bên đối chiếu',
  CLOSED_RETURNED: 'Người mua đã xác nhận nhận đủ khoản trả lại',
  CLOSED_MUTUAL: 'Hai bên xác nhận hết yêu cầu',
  AWAITING_BUYER: 'Người bán khai đã trả; chờ người mua xác nhận',
  BUYER_ACKNOWLEDGED: 'Người mua xác nhận đã nhận',
  WAIVED_BY_BOTH: 'Hai bên xác nhận hết yêu cầu, không ghi nhận đã trả tiền',
}
export default function PeerMoneyPanel({
  attempt: a,
  userId,
}: {
  attempt: PeerAttempt
  userId: string
}) {
  const [after, setAfter] = useState('')
  const read = useApi<PeerMoney>(
    `/transfers/acquisitions/${a.id}/money?size=20${after ? `&afterReturn=${after}` : ''}`,
  )
  const [evidence, setEvidence] = useState<string[]>([])
  const [reference, setReference] = useState('')
  const [amount, setAmount] = useState('')
  const [action, setAction] = useState('report-late-payment')
  const command = usePeerCommand()
  const current = read.data
  const seller = userId === a.sellerId
  const refresh = () => {
    setEvidence([])
    setReference('')
    setAmount('')
    void read.reload()
  }
  return (
    <section className="space-y-4 rounded-xl border p-5">
      <h2 className="text-lg font-bold">Đối chiếu tiền riêng giữa hai khách</h2>
      <p className="text-sm">
        Owner chỉ phân xử quyền sân. Khoản trả lại ở đây do A chuyển trực tiếp B, không áp tỷ lệ
        hoàn của cơ sở và không xác minh tự động từ ngân hàng.
      </p>
      {current && (
        <>
          <p role="status" className="font-semibold">
            {states[current.state] ?? 'Đang đối chiếu'}
          </p>
          <p>
            Người mua xác nhận nhận: {money(Number(current.totals.received), 'VND')} · Chờ xác nhận:{' '}
            {money(Number(current.totals.awaitingBuyer), 'VND')}
          </p>
          <ul className="space-y-3">
            {current.returns.map((row) => (
              <li key={row.id} className="space-y-2 rounded-lg bg-slate-50 p-3">
                <p>
                  {money(Number(row.amount), 'VND')} · {states[row.state]}
                </p>
                <p className="break-words text-sm">Mã giao dịch do A khai: {row.reference}</p>
                <div className="grid gap-2 sm:grid-cols-3">
                  {row.evidenceIds.map((id) => (
                    <EvidencePreview key={id} id={id} />
                  ))}
                </div>
                {!seller && row.state === 'AWAITING_BUYER' && (
                  <Button
                    disabled={command.busy || command.blocked}
                    onClick={() => {
                      if (window.confirm('Bạn đã thực nhận khoản tiền trả lại này?'))
                        void command.run(
                          `/transfers/acquisitions/${a.id}/returns/${row.id}/acknowledge`,
                          { version: row.version, termsHash: a.termsHash },
                          refresh,
                        )
                    }}
                  >
                    Đã nhận khoản trả lại này
                  </Button>
                )}
              </li>
            ))}
          </ul>
          <div className="flex gap-3">
            {after && (
              <Button variant="outline" onClick={() => setAfter('')}>
                Trang đầu khoản trả
              </Button>
            )}
            {current.returnPage.more && (
              <Button
                variant="outline"
                onClick={() => setAfter(current.returnPage.nextAfter ?? '')}
              >
                Khoản trả tiếp theo
              </Button>
            )}
          </div>
          {current.reports.map((report) => (
            <details key={report.id} className="rounded border p-3">
              <summary className="cursor-pointer text-sm">
                Báo tiền/chứng từ bổ sung: {report.reference}
              </summary>
              <div className="grid gap-2 sm:grid-cols-3">
                {report.evidenceIds.map((id) => (
                  <EvidencePreview key={id} id={id} />
                ))}
              </div>
            </details>
          ))}
          {a.phase !== 'WAITING_PAYMENT' && (
            <form
              className="space-y-3"
              onSubmit={(event) => {
                event.preventDefault()
                void command.run(
                  `/transfers/acquisitions/${a.id}/${action}`,
                  {
                    termsHash: a.termsHash,
                    bankTransactionReference: reference.trim(),
                    evidenceIds: evidence,
                    ...(action === 'returns' ? { amount: Number(amount) } : {}),
                  },
                  refresh,
                )
              }}
            >
              <label className="grid gap-2 text-sm">
                Thao tác với tiền
                <select
                  className="rounded-lg border p-3"
                  value={action}
                  onChange={(event) => setAction(event.target.value)}
                  disabled={command.busy || command.blocked}
                >
                  <option value="report-late-payment">Bổ sung chứng từ / báo tiền đến muộn</option>
                  {seller && current.state === 'OPEN' && (
                    <option value="returns">Tôi đã chuyển trả tiền cho người mua</option>
                  )}
                </select>
              </label>
              {action === 'returns' && (
                <label className="grid gap-2 text-sm">
                  Số tiền A đã chuyển trả (VND)
                  <input
                    className="rounded-lg border p-3"
                    type="number"
                    min={1}
                    max={Number(a.amount) - Number(current.totals.declared)}
                    step={1}
                    value={amount}
                    onChange={(event) => setAmount(event.target.value)}
                    required
                    disabled={command.busy || command.blocked}
                  />
                </label>
              )}
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
                disabled={command.busy || command.blocked || !reference.trim() || !evidence.length}
              >
                Gửi lời khai và chứng từ
              </Button>
            </form>
          )}
          {current.state === 'OPEN' && (
            <div className="rounded-lg bg-amber-50 p-4 text-sm">
              <p>
                Chỉ xác nhận hết yêu cầu khi hai bên đã tự giải quyết xong. Một bên xác nhận không
                đóng hồ sơ; đủ cả hai sẽ ghi nhận hết yêu cầu và miễn các khoản đang chờ, không tạo
                chứng từ đã trả tiền.
              </p>
              <Button
                variant="outline"
                disabled={command.busy || command.blocked || current.noClaims.includes(userId)}
                onClick={() => {
                  if (
                    window.confirm(
                      'Bạn xác nhận không còn yêu cầu tiền với bên kia trong phiên này?',
                    )
                  )
                    void command.run(
                      `/transfers/acquisitions/${a.id}/money-no-claim`,
                      { termsHash: a.termsHash },
                      refresh,
                    )
                }}
              >
                Tôi xác nhận hết yêu cầu tiền
              </Button>
            </div>
          )}
        </>
      )}
      {read.loading && <p role="status">Đang đọc lịch sử tiền…</p>}
      {(read.error || command.error) && (
        <p role="alert" className="text-red-700">
          {read.error || command.error}
        </p>
      )}
      <Button variant="outline" onClick={read.reload}>
        Tải lại hồ sơ tiền
      </Button>
    </section>
  )
}
