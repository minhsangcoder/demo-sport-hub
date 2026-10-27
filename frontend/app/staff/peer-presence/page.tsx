import { RequireAuth } from '@/features/auth/require-auth'
import { PeerPresence } from '@/features/transfers/peer-presence'
export default async function Page({
  searchParams,
}: {
  searchParams: Promise<{ facilityId?: string }>
}) {
  const { facilityId } = await searchParams
  return (
    <RequireAuth roles={['STAFF']}>
      <main className="mx-auto max-w-3xl space-y-5 p-6">
        <h1 className="text-2xl font-bold">Có mặt khi tranh chấp quyền sân</h1>
        {facilityId && /^[a-f0-9-]{36}$/i.test(facilityId) ? (
          <PeerPresence key={facilityId} facilityId={facilityId} />
        ) : (
          <p>Chọn cơ sở từ màn hình kiểm tra lượt đặt.</p>
        )}
      </main>
    </RequireAuth>
  )
}
