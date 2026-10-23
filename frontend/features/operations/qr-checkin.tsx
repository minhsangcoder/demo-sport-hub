'use client'
import { FeatureBoundary } from '@/components/feature-boundary'
import dynamic from 'next/dynamic'
export { QrUploadButton } from './qr-upload-button'
const Dialog = dynamic(
  () => import('./qr-checkin-dialog').then((module) => module.QuickQrCheckinModal),
  { ssr: false, loading: () => <p role="status">Đang mở công cụ đọc QR…</p> },
)
export function QuickQrCheckinModal(props: {
  isOpen: boolean
  onClose: () => void
  onSuccess?: (bookingId: string) => void
}) {
  return props.isOpen ? (
    <FeatureBoundary>
      <Dialog {...props} />
    </FeatureBoundary>
  ) : null
}
