'use client'
import { FeatureBoundary } from '@/components/feature-boundary'
import dynamic from 'next/dynamic'

const Camera = dynamic(
  () => import('./camera-qr-scanner-modal').then((module) => module.CameraQrScannerModal),
  {
    ssr: false,
    loading: () => <p role="status">Đang mở camera…</p>,
  },
)
export function CameraQrScannerModal(props: {
  isOpen: boolean
  onClose: () => void
  onScanSuccess: (rawResult: string) => void
}) {
  return props.isOpen ? (
    <FeatureBoundary>
      <Camera {...props} />
    </FeatureBoundary>
  ) : null
}
