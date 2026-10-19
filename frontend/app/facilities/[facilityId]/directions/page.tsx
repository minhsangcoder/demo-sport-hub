import { Metadata } from 'next'
import { Suspense } from 'react'
import { FacilityDirectionsPage } from '@/features/discovery/facility-directions-page'

export const metadata: Metadata = {
  title: 'Chỉ đường & Vị trí sân · SportHub',
  description: 'Xem vị trí bản đồ và nhận chỉ đường chính xác đến cơ sở thể thao.',
}

export default async function Page({
  params,
  searchParams,
}: {
  params: Promise<{ facilityId: string }>
  searchParams: Promise<{ [key: string]: string | string[] | undefined }>
}) {
  const { facilityId } = await params
  const sp = await searchParams
  const bookingId = typeof sp?.bookingId === 'string' ? sp.bookingId : undefined

  return (
    <Suspense
      fallback={
        <div className="mx-auto max-w-5xl px-4 py-16 text-center">
          <div className="inline-block h-8 w-8 animate-spin rounded-full border-2 border-slate-300 border-t-emerald-600" />
          <p className="mt-3 text-sm text-slate-500">Đang chuẩn bị bản đồ…</p>
        </div>
      }
    >
      <FacilityDirectionsPage
        facilityId={facilityId}
        initialBookingId={bookingId}
      />
    </Suspense>
  )
}
