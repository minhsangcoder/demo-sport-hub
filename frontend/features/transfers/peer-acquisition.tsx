'use client'
import dynamic from 'next/dynamic'
import { useState } from 'react'
import Link from 'next/link'
import { useAuth } from '@/lib/auth-context'
import { useApi } from '@/lib/use-api'
import { usePolling } from '@/lib/use-polling'
import { Countdown, useRemaining } from '@/features/booking/countdown'
import { Button } from '@/components/ui/button'
import { PeerPayment } from './peer-payment'
import { finalPeerPhase, peerPhases, type PeerAttempt } from './peer-types'

const Chat = dynamic(() => import('./peer-chat').then((module) => module.PeerChat), {
  loading: () => <p role="status">Đang mở phòng trao đổi…</p>,
})
const MoneyPanel = dynamic(() => import('./peer-money'), {
  loading: () => <p role="status">Đang mở hồ sơ tiền…</p>,
})
const CasePanel = dynamic(() => import('./peer-case-panel'), {
  loading: () => <p role="status">Đang mở hồ sơ quyền sân…</p>,
})
export default function PeerAcquisitionPage({ id }: { id: string }) {
  const { user } = useAuth()
  return user ? <Loaded key={`${user.id}:${id}`} id={id} userId={user.id} /> : null
}
function Loaded({ id, userId }: { id: string; userId: string }) {
  const read = useApi<PeerAttempt>(`/transfers/acquisitions/${id}/peer`)
  const [showChat, setShowChat] = useState(true)
  const [showMoney, setShowMoney] = useState(false)
  const [showCase, setShowCase] = useState(false)
  const [until] = useState(() => Date.now() + 30 * 60_000)
  const [timedOut, setTimedOut] = useState(false)
  const remaining = useRemaining(read.data?.payBy)
  const blocked = Boolean(read.failure && [401, 403].includes(read.failure.status))
  usePolling(read.poll, {
    enabled: Boolean(read.data && !finalPeerPhase(read.data.phase) && !blocked && !timedOut),
    scope: `${userId}:${id}`,
    interval: 4000,
    deadline: until,
    onTimeout: () => setTimedOut(true),
  })
  const a = read.data
  return (
    <main className="mx-auto max-w-3xl space-y-5 px-5 py-8">
      <Link href="/customer/transfers" className="text-sm text-emerald-700 underline">
        Giao dịch tham gia
      </Link>
      <h1 className="text-2xl font-bold">Chuyển nhượng trực tiếp giữa hai khách</h1>
      {read.loading && !a && <p role="status">Đang đối chiếu quyền sân…</p>}
      {read.error && (
        <p role="alert" className="text-red-700">
          {read.error}
        </p>
      )}
      {a && (
        <>
          <p role="status" className="rounded-lg bg-emerald-50 p-4 font-semibold">
            {peerPhases[a.phase] ?? 'Đang đối chiếu trạng thái'}
          </p>
          <p className="text-sm">
            Bạn là {userId === a.sellerId ? 'người bán (A)' : 'người mua (B)'}. Tiền chuyển trực
            tiếp B → A; cơ sở và SportHub không thu phí chuyển nhượng.
          </p>
          {a.phase === 'WAITING_PAYMENT' && <Countdown expiresAt={a.payBy} />}
          {a.phase === 'WAITING_PAYMENT' && remaining === 0 && (
            <p className="text-amber-900">
              Đã hết hạn khai chuyển tiền. Đang đối chiếu để mở lại tin; không chuyển từ QR cũ. Nếu
              đã chuyển, mở hồ sơ tiền để báo bổ sung.
            </p>
          )}
          {!blocked && (
            <PeerPayment
              key={`${id}:${a.termsHash}`}
              attempt={a}
              userId={userId}
              reload={read.reload}
              remaining={remaining ?? 0}
            />
          )}
          {a.decision && (
            <p className="rounded border p-4">
              Quyết định quyền sân:{' '}
              {a.decision.decision === 'BUYER_RECEIVES'
                ? 'Người mua nhận quyền'
                : a.phase === 'COMPLETED'
                  ? 'Đã bàn giao cho người mua'
                  : 'Người bán giữ quyền'}{' '}
              · {a.decision.reason}. Quyết định này không xác nhận tiền đã hoàn.
            </p>
          )}
          {a.phase === 'COMPLETED' && userId === a.buyerId && (
            <Link
              href={`/customer/bookings/${a.bookingId}`}
              className="block rounded-lg bg-emerald-600 p-3 text-center text-white"
            >
              Mở lượt đặt và mã vào sân của bạn
            </Link>
          )}
          <div className="flex flex-wrap gap-3">
            <Button variant="outline" onClick={() => setShowChat((value) => !value)}>
              {showChat ? 'Thu gọn trao đổi' : 'Mở trao đổi'}
            </Button>
            <Button variant="outline" onClick={() => setShowMoney((value) => !value)}>
              Đối chiếu tiền / báo tiền muộn
            </Button>
            <Button variant="outline" onClick={() => setShowCase((value) => !value)}>
              Xem hồ sơ quyền sân
            </Button>
          </div>
          {!blocked && showChat && <Chat id={a.conversationId} userId={userId} />}
          {!blocked && showMoney && <MoneyPanel attempt={a} userId={userId} />}
          {!blocked && showCase && <CasePanel acquisitionId={id} />}
        </>
      )}
      {timedOut && (
        <p>
          Đã dừng tự theo dõi tại màn hình. Tải lại để xem trạng thái mới; không chuyển tiền lần nữa
          khi chưa rõ kết quả.
        </p>
      )}
      <Button variant="outline" onClick={read.reload}>
        Tải lại trạng thái quyền sân
      </Button>
    </main>
  )
}
