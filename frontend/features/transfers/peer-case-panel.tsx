'use client'
import { useState } from 'react'
import { useApi } from '@/lib/use-api'
import { useAuth } from '@/lib/auth-context'
import { Button } from '@/components/ui/button'
import { EvidencePreview } from './private-image'
import { PeerEvidencePicker } from './peer-evidence'
import { usePeerCommand } from './use-peer-command'
import { peerPhases, type PeerCase, type PeerEvidence } from './peer-types'

export default function PeerCasePanel({
  acquisitionId,
  facilityId,
}: {
  acquisitionId: string
  facilityId?: string
}) {
  const { user } = useAuth()
  return (
    <CaseContent
      key={`${user?.id}:${facilityId}:${acquisitionId}`}
      acquisitionId={acquisitionId}
      facilityId={facilityId}
      userId={user?.id ?? ''}
    />
  )
}
function CaseContent({
  acquisitionId,
  facilityId,
  userId,
}: {
  acquisitionId: string
  facilityId?: string
  userId: string
}) {
  const path = facilityId
    ? `/transfers/facilities/${facilityId}/cases/${acquisitionId}`
    : `/transfers/acquisitions/${acquisitionId}/case`
  const read = useApi<PeerCase>(path)
  const evidence = useApi<PeerEvidence[]>(
    read.data ? `/transfers/acquisitions/${acquisitionId}/evidence` : null,
  )
  const [selected, setSelected] = useState<string[]>([])
  const [outcome, setOutcome] = useState('SELLER_RETAINS')
  const [reason, setReason] = useState('')
  const [ownPhotos, setOwnPhotos] = useState<string[]>([])
  const command = usePeerCommand()
  const data = read.data
  const recipient = useApi<{ bankName: string; maskedAccountNumber: string; accountName: string }>(
    facilityId && data ? `/payments/peer-acquisitions/${acquisitionId}/case-recipient` : null,
  )
  return (
    <section className="space-y-4 rounded-xl border p-5">
      <h2 className="text-lg font-bold">Hồ sơ quyền sử dụng sân</h2>
      {read.loading && <p role="status">Đang đọc hồ sơ…</p>}
      {read.error && (
        <p role="alert" className="text-red-700">
          {read.error}
        </p>
      )}
      {data && (
        <>
          <p className="font-semibold">{peerPhases[data.attempt.phase]}</p>
          <p className="text-sm">{data.moneyCase.reason}</p>
          <p className="rounded-lg bg-amber-50 p-3 text-sm">
            Owner chỉ quyết định A hay B được dùng sân. Tiền chuyển B → A do hai bên tự đối chiếu;
            thao tác phân xử không hoàn tiền hoặc xác nhận tiền thay hai bên.
          </p>
          <p>
            Giờ chơi: {new Date(data.booking.startsAt).toLocaleString('vi-VN')} →{' '}
            {new Date(data.booking.endsAt).toLocaleString('vi-VN')}. Không bù thêm giờ.
          </p>
          <p className="text-sm">
            Cả hai vắng khi đến giờ chơi: A giữ quyền. Có bên được nhân sự sân ghi nhận đúng giờ:
            Owner có thể bàn giao phần còn lại trước giờ kết thúc, nếu sân chưa được sử dụng và chưa
            có quyết định cuối.
          </p>
          {recipient.data && (
            <p className="text-sm">
              Tài khoản người bán đã chốt: {recipient.data.bankName} ·{' '}
              {recipient.data.maskedAccountNumber} · {recipient.data.accountName} (tự khai)
            </p>
          )}
          {recipient.error && <p role="alert">{recipient.error}</p>}
          {data.declaration && (
            <p className="text-sm">
              Người mua khai mã: {data.declaration.reference} ·{' '}
              {new Date(data.declaration.claimedAt).toLocaleString('vi-VN')}
            </p>
          )}
          <h3 className="font-semibold">Có mặt tại cơ sở</h3>
          {data.presence.length ? (
            <ul className="space-y-2">
              {data.presence.map((row) => (
                <li key={row.party} className="text-sm">
                  {row.party === 'SELLER' ? 'Người bán' : 'Người mua'} ·{' '}
                  {new Date(row.observedAt).toLocaleString('vi-VN')} · {row.reason}
                </li>
              ))}
            </ul>
          ) : (
            <p className="text-sm text-slate-500">Chưa ghi nhận bên nào có mặt.</p>
          )}
          <h3 className="font-semibold">Chứng cứ riêng tư</h3>
          <div className="grid gap-3 sm:grid-cols-3">
            {evidence.data?.map((item) => (
              <div key={item.id} className="space-y-2 rounded-lg border p-3 text-sm">
                <p>
                  {item.uploaderId === data.attempt.sellerId
                    ? 'Người bán cung cấp'
                    : 'Người mua cung cấp'}{' '}
                  · {new Date(item.createdAt).toLocaleString('vi-VN')}
                </p>
                <EvidencePreview id={item.id} />
                {facilityId && (
                  <label className="flex gap-2">
                    <input
                      type="checkbox"
                      checked={selected.includes(item.id)}
                      disabled={
                        command.busy || (!selected.includes(item.id) && selected.length >= 20)
                      }
                      onChange={(event) =>
                        setSelected(
                          event.target.checked
                            ? [...selected, item.id]
                            : selected.filter((id) => id !== item.id),
                        )
                      }
                    />
                    Dùng làm căn cứ
                  </label>
                )}
              </div>
            ))}
          </div>
          {evidence.error && <p role="alert">{evidence.error}</p>}
          {!facilityId && (
            <PeerEvidencePicker
              acquisitionId={acquisitionId}
              userId={userId}
              value={ownPhotos}
              onChange={setOwnPhotos}
            />
          )}
          {!facilityId && (
            <p className="text-xs text-slate-600">
              Ảnh tải thành công đã sẵn sàng cho Owner xem. Khai khoản trả lại hoặc bổ sung mã giao
              dịch trong mục đối chiếu tiền.
            </p>
          )}
          {facilityId && data.attempt.phase === 'DISPUTED' && !data.attempt.decision && (
            <form
              className="space-y-3"
              onSubmit={(event) => {
                event.preventDefault()
                if (
                  window.confirm(
                    'Chốt quyết định quyền sân? Quyết định cuối không được đảo lại; không xác nhận tiền đã hoàn.',
                  )
                )
                  void command.run(
                    `${path}/resolve`,
                    {
                      version: data.attempt.version,
                      outcome,
                      reason: reason.trim(),
                      evidenceIds: selected,
                    },
                    () => {
                      void read.reload()
                      void evidence.reload()
                    },
                  )
              }}
            >
              <label className="grid gap-2 text-sm">
                Kết luận quyền sân
                <select
                  value={outcome}
                  onChange={(event) => setOutcome(event.target.value)}
                  className="rounded-lg border p-3"
                  disabled={command.busy || command.blocked}
                >
                  <option value="SELLER_RETAINS">
                    Người bán giữ quyền, không kết luận gian dối
                  </option>
                  <option value="AWARD_BUYER">Bàn giao quyền sân cho người mua</option>
                  <option value="BUYER_FRAUD">
                    Người mua cố ý khai chuyển tiền giả (có chứng cứ)
                  </option>
                </select>
              </label>
              <label className="grid gap-2 text-sm">
                Lý do và căn cứ
                <textarea
                  rows={3}
                  maxLength={1000}
                  value={reason}
                  onChange={(event) => setReason(event.target.value)}
                  className="rounded-lg border p-3"
                  required
                  disabled={command.busy || command.blocked}
                />
              </label>
              {outcome === 'BUYER_FRAUD' && (
                <p className="text-sm text-red-800">
                  Chỉ dùng khi có chứng cứ cố ý gian dối. Mỗi lần cấm chợ 30 ngày, đủ 3 lần cấm vĩnh
                  viễn; thiếu bằng chứng hoặc vắng mặt không tự bị phạt.
                </p>
              )}
              <Button
                type="submit"
                disabled={
                  command.busy ||
                  command.blocked ||
                  !reason.trim() ||
                  (outcome === 'BUYER_FRAUD' && !selected.length)
                }
              >
                Chốt quyền sử dụng sân
              </Button>
            </form>
          )}
          {data.attempt.decision && (
            <p className="rounded bg-emerald-50 p-4">
              Đã có quyết định cuối: {data.attempt.decision.reason}. Khóa ngân hàng do tranh chấp đã
              gỡ; lịch sân và khoản trả/hoàn chưa xong vẫn khóa riêng.
            </p>
          )}
          <p className="text-sm">
            Lịch sử tiền:{' '}
            {data.money.state === 'OPEN'
              ? 'Hai bên còn đối chiếu'
              : data.money.state === 'CLOSED_RETURNED'
                ? 'Người mua xác nhận đã nhận đủ khoản trả lại'
                : data.money.state === 'CLOSED_MUTUAL'
                  ? 'Hai bên xác nhận hết yêu cầu'
                  : 'Chưa có yêu cầu tiền'}
            . Owner không đóng hồ sơ tiền thay hai bên.
          </p>
          <details>
            <summary className="cursor-pointer text-sm underline">
              Lịch sử thao tác với hồ sơ
            </summary>
            <ol className="space-y-2 text-sm">
              {data.history.map((row) => (
                <li key={row.id}>
                  {new Date(row.createdAt).toLocaleString('vi-VN')} ·{' '}
                  {row.action === 'PEER_ATTENDANCE_RECORDED'
                    ? 'Ghi nhận có mặt'
                    : row.action === 'PEER_OWNER_ADJUDICATED'
                      ? 'Owner phân xử quyền sân'
                      : row.action === 'PEER_CASE_EVIDENCE_ACCESS'
                        ? 'Owner xem chứng cứ'
                        : 'Cập nhật hồ sơ chuyển nhượng'}
                </li>
              ))}
            </ol>
          </details>
        </>
      )}
      {command.error && (
        <p role="alert" className="text-red-700">
          {command.error}
        </p>
      )}
      <Button
        variant="outline"
        onClick={() => {
          void read.reload()
          void evidence.reload()
        }}
      >
        Tải lại hồ sơ
      </Button>
    </section>
  )
}
