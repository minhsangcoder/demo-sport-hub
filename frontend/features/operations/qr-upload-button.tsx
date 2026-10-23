'use client'
import { Button } from '@/components/ui/button'
import { CameraQrScannerModal } from '@/features/groups/lazy-camera-qr-scanner'
import { extractBookingIdFromToken, extractCheckinToken } from '@/features/groups/qr-parser'
import { useQrImageDecoder } from '@/features/groups/use-qr-image-decoder'
import { AlertCircle, Camera, Loader2, UploadCloud, X } from 'lucide-react'
import { useRef, useState } from 'react'
interface QrUploadButtonProps {
  onTokenDetected: (token: string, bookingId: string | null) => void
  disabled?: boolean
  label?: string
  size?: 'default' | 'sm' | 'lg' | 'icon'
  variant?: 'outline' | 'default' | 'secondary' | 'ghost'
  className?: string
  showCamera?: boolean
}
export function QrUploadButton({
  onTokenDetected,
  disabled = false,
  label = 'Tải ảnh QR',
  size = 'sm',
  variant = 'outline',
  className = '',
  showCamera = true,
}: QrUploadButtonProps) {
  const { decode } = useQrImageDecoder()
  const [isScanning, setIsScanning] = useState(false)
  const [isCameraOpen, setIsCameraOpen] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const handleFile = async (file: File) => {
    if (!file.type.startsWith('image/') && !file.name.endsWith('.svg')) {
      setErrorMessage('Vui lòng chọn tệp hình ảnh (PNG, JPG, WEBP, SVG).')
      return
    }

    setIsScanning(true)
    setErrorMessage(null)

    const operation = decode(file)
    try {
      const raw = await operation.promise
      const token = extractCheckinToken(raw)
      if (!token) {
        throw new Error('Không trích xuất được mã xác nhận vào sân từ ảnh QR.')
      }
      const bookingId = extractBookingIdFromToken(token)
      onTokenDetected(token, bookingId)
    } catch (err: unknown) {
      if (!operation.active()) return
      const error = err as { message?: string }
      setErrorMessage(
        error.message || 'Không tìm thấy mã QR trong ảnh. Vui lòng chọn ảnh rõ nét hơn.',
      )
    } finally {
      if (operation.active()) setIsScanning(false)
    }
  }

  const handleCameraScan = (rawResult: string) => {
    setIsCameraOpen(false)
    const token = extractCheckinToken(rawResult)
    if (token) {
      const bookingId = extractBookingIdFromToken(token)
      onTokenDetected(token, bookingId)
    } else {
      setErrorMessage('Mã QR từ camera không hợp lệ.')
    }
  }

  return (
    <div className="relative inline-flex items-center gap-1.5">
      <input
        ref={fileInputRef}
        type="file"
        accept="image/*,.svg"
        className="hidden"
        disabled={disabled || isScanning}
        onChange={(e) => {
          const file = e.target.files?.[0]
          if (file) handleFile(file)
          e.target.value = ''
        }}
      />

      <Button
        type="button"
        variant={variant}
        size={size}
        disabled={disabled || isScanning}
        onClick={() => fileInputRef.current?.click()}
        title="Tải ảnh chụp màn hình hoặc tệp mã QR của khách"
        className={`gap-1.5 border-emerald-200 bg-emerald-50/60 text-emerald-800 hover:bg-emerald-100 hover:text-emerald-900 ${className}`}
      >
        {isScanning ? (
          <Loader2 className="h-3.5 w-3.5 animate-spin text-emerald-700" />
        ) : (
          <UploadCloud className="h-3.5 w-3.5 text-emerald-700" />
        )}
        <span>{isScanning ? 'Đang đọc…' : label}</span>
      </Button>

      {showCamera && (
        <Button
          type="button"
          variant="outline"
          size="icon"
          disabled={disabled || isScanning}
          onClick={() => setIsCameraOpen(true)}
          title="Quét mã QR bằng Camera"
          className="h-8 w-8 rounded-lg border-emerald-200 bg-emerald-50/60 text-emerald-800 hover:bg-emerald-100"
        >
          <Camera className="h-3.5 w-3.5 text-emerald-700" />
          <span className="sr-only">Quét bằng Camera</span>
        </Button>
      )}

      {isCameraOpen && (
        <CameraQrScannerModal
          isOpen={isCameraOpen}
          onClose={() => setIsCameraOpen(false)}
          onScanSuccess={handleCameraScan}
        />
      )}

      {errorMessage && (
        <div
          role="alert"
          className="absolute left-0 top-full z-20 mt-1 max-w-xs rounded-lg border border-red-200 bg-red-50 p-2 text-xs text-red-700 shadow-md animate-in fade-in"
        >
          <div className="flex items-start gap-1.5">
            <AlertCircle className="h-3.5 w-3.5 shrink-0 mt-0.5 text-red-600" />
            <p className="flex-1">{errorMessage}</p>
            <button
              type="button"
              onClick={() => setErrorMessage(null)}
              className="text-red-500 hover:text-red-800"
            >
              <X className="h-3 w-3" />
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
