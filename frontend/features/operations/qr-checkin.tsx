'use client'

import React, { useState, useRef, useEffect, useCallback } from 'react'
import {
  QrCode,
  UploadCloud,
  Camera,
  CheckCircle2,
  AlertCircle,
  Loader2,
  X,
  RefreshCw,
  Image as ImageIcon,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { api } from '@/lib/api'
import {
  decodeQrFromImageFile,
  extractCheckinToken,
  extractBookingIdFromToken,
} from '@/features/groups/qr-decoder'
import { CameraQrScannerModal } from '@/features/groups/camera-qr-scanner-modal'

interface QrUploadButtonProps {
  onTokenDetected: (token: string, bookingId: string | null) => void
  disabled?: boolean
  label?: string
  size?: 'default' | 'sm' | 'lg' | 'icon'
  variant?: 'outline' | 'default' | 'secondary' | 'ghost'
  className?: string
  showCamera?: boolean
}

/**
 * Compact button to upload a QR code image or scan via camera.
 * Automatically decodes the QR code and calls `onTokenDetected`.
 */
export function QrUploadButton({
  onTokenDetected,
  disabled = false,
  label = 'Tải ảnh QR',
  size = 'sm',
  variant = 'outline',
  className = '',
  showCamera = true,
}: QrUploadButtonProps) {
  const [isScanning, setIsScanning] = useState(false)
  const [isCameraOpen, setIsCameraOpen] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const handleFile = async (file: File) => {
    if (!file.type.startsWith('image/') && !file.name.endsWith('.svg')) {
      setErrorMessage('Vui lòng chọn file hình ảnh (PNG, JPG, WEBP, SVG).')
      return
    }

    setIsScanning(true)
    setErrorMessage(null)

    try {
      const raw = await decodeQrFromImageFile(file)
      const token = extractCheckinToken(raw)
      if (!token) {
        throw new Error('Không trích xuất được mã check-in từ ảnh QR.')
      }
      const bookingId = extractBookingIdFromToken(token)
      onTokenDetected(token, bookingId)
    } catch (err: unknown) {
      const error = err as { message?: string }
      setErrorMessage(
        error.message ||
          'Không tìm thấy mã QR trong ảnh. Vui lòng chọn ảnh rõ nét hơn.'
      )
    } finally {
      setIsScanning(false)
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
        title="Tải ảnh chụp màn hình hoặc file mã QR của khách"
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

interface QuickQrCheckinModalProps {
  isOpen: boolean
  onClose: () => void
  onSuccess?: (bookingId: string) => void
}

/**
 * Dedicated Modal for receptionist / facility owner:
 * Allows dropping an image, pasting via Ctrl+V, picking a file, or using camera.
 * Automatically resolves the booking ID and performs one-click check-in.
 */
export function QuickQrCheckinModal({
  isOpen,
  onClose,
  onSuccess,
}: QuickQrCheckinModalProps) {
  const [isDragging, setIsDragging] = useState(false)
  const [isScanning, setIsScanning] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [previewUrl, setPreviewUrl] = useState<string | null>(null)
  const [detectedToken, setDetectedToken] = useState<string | null>(null)
  const [detectedBookingId, setDetectedBookingId] = useState<string | null>(null)
  const [scanError, setScanError] = useState<string | null>(null)
  const [checkinSuccess, setCheckinSuccess] = useState<boolean>(false)
  const [checkinMessage, setCheckinMessage] = useState<string | null>(null)
  const [isCameraOpen, setIsCameraOpen] = useState(false)

  const fileInputRef = useRef<HTMLInputElement>(null)

  const resetState = useCallback(() => {
    if (previewUrl) {
      URL.revokeObjectURL(previewUrl)
    }
    setPreviewUrl(null)
    setDetectedToken(null)
    setDetectedBookingId(null)
    setScanError(null)
    setCheckinSuccess(false)
    setCheckinMessage(null)
    setIsScanning(false)
    setIsSubmitting(false)
  }, [previewUrl])

  // Reset when opened
  useEffect(() => {
    if (isOpen) {
      resetState()
    }
  }, [isOpen])

  const processFile = useCallback(
    async (file: File) => {
      if (!file.type.startsWith('image/') && !file.name.endsWith('.svg')) {
        setScanError('Vui lòng chọn file hình ảnh (PNG, JPG, WEBP, SVG).')
        return
      }

      setScanError(null)
      setCheckinMessage(null)
      setIsScanning(true)

      const objectUrl = URL.createObjectURL(file)
      setPreviewUrl(objectUrl)

      try {
        const raw = await decodeQrFromImageFile(file)
        const token = extractCheckinToken(raw)
        if (!token) {
          throw new Error('Không thể đọc mã check-in từ hình ảnh này.')
        }

        const bId = extractBookingIdFromToken(token)
        setDetectedToken(token)
        setDetectedBookingId(bId)

        // Automatically trigger check-in if bookingId was resolved
        if (bId) {
          await executeCheckin(bId, token)
        }
      } catch (err: unknown) {
        const error = err as { message?: string }
        setScanError(
          error.message ||
            'Không tìm thấy mã QR trong ảnh. Vui lòng thử ảnh khác rõ nét hơn.'
        )
      } finally {
        setIsScanning(false)
      }
    },
    []
  )

  const executeCheckin = async (bookingId: string, token: string) => {
    setIsSubmitting(true)
    setScanError(null)
    try {
      await api(`/bookings/${bookingId}/check-in`, {
        method: 'POST',
        body: JSON.stringify({ token }),
      })
      setCheckinSuccess(true)
      setCheckinMessage(`Đã check-in thành công cho booking #${bookingId.slice(0, 8).toUpperCase()}!`)
      if (onSuccess) {
        onSuccess(bookingId)
      }
    } catch (err: unknown) {
      const error = err as { message?: string }
      setScanError(
        error.message ||
          'Không thể check-in. Vui lòng kiểm tra trạng thái booking hoặc thời gian hiệu lực của mã.'
      )
    } finally {
      setIsSubmitting(false)
    }
  }

  // Paste image from clipboard (Ctrl + V)
  useEffect(() => {
    if (!isOpen) return

    function handlePaste(e: ClipboardEvent) {
      const items = e.clipboardData?.items
      if (!items) return

      for (let i = 0; i < items.length; i++) {
        if (items[i].type.indexOf('image') !== -1) {
          const file = items[i].getAsFile()
          if (file) {
            processFile(file)
            break
          }
        }
      }
    }

    window.addEventListener('paste', handlePaste)
    return () => window.removeEventListener('paste', handlePaste)
  }, [isOpen, processFile])

  if (!isOpen) return null

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="qr-modal-title"
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 p-4 backdrop-blur-xs animate-in fade-in duration-200"
    >
      <div className="relative w-full max-w-lg overflow-hidden rounded-3xl bg-white shadow-2xl ring-1 ring-slate-200">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-slate-100 px-6 py-4">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-emerald-50 text-emerald-700">
              <QrCode className="h-5 w-5" />
            </div>
            <div>
              <h2 id="qr-modal-title" className="text-lg font-black text-slate-900">
                Check-in bằng mã QR
              </h2>
              <p className="text-xs text-slate-500">
                Tải ảnh mã QR của khách hoặc quét trực tiếp
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="flex h-8 w-8 items-center justify-center rounded-full text-slate-400 hover:bg-slate-100 hover:text-slate-700"
            aria-label="Đóng"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Modal Body */}
        <div className="p-6 space-y-4">
          {checkinSuccess ? (
            /* Success State */
            <div className="flex flex-col items-center justify-center py-6 text-center animate-in zoom-in-95">
              <div className="flex h-16 w-16 items-center justify-center rounded-full bg-emerald-100 text-emerald-600 shadow-inner">
                <CheckCircle2 className="h-10 w-10" />
              </div>
              <h3 className="mt-4 text-xl font-bold text-slate-900">
                Check-in thành công!
              </h3>
              <p className="mt-2 text-sm text-emerald-700 font-medium">
                {checkinMessage}
              </p>
              {detectedBookingId && (
                <p className="mt-1 text-xs text-slate-500 font-mono">
                  Mã booking: {detectedBookingId}
                </p>
              )}

              <div className="mt-6 flex flex-wrap gap-3">
                <Button
                  type="button"
                  onClick={resetState}
                  className="gap-2 bg-emerald-600 hover:bg-emerald-700 text-white"
                >
                  <RefreshCw className="h-4 w-4" /> Quét tiếp khách khác
                </Button>
                <Button type="button" variant="outline" onClick={onClose}>
                  Đóng
                </Button>
              </div>
            </div>
          ) : (
            /* Upload / Scan Area */
            <>
              {/* Quick Camera Bar */}
              <div className="flex items-center justify-between rounded-xl bg-slate-50 p-3">
                <div className="flex items-center gap-2 text-xs text-slate-600">
                  <Camera className="h-4 w-4 text-emerald-600" />
                  <span>Hoặc quét trực tiếp từ thiết bị</span>
                </div>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => setIsCameraOpen(true)}
                  disabled={isScanning || isSubmitting}
                  className="gap-1.5 rounded-full border-emerald-200 bg-white text-emerald-800 hover:bg-emerald-50 text-xs font-semibold"
                >
                  <Camera className="h-3.5 w-3.5 text-emerald-600" />
                  <span>Mở Camera</span>
                </Button>
              </div>

              {/* File upload hidden */}
              <input
                ref={fileInputRef}
                type="file"
                accept="image/*,.svg"
                className="hidden"
                disabled={isScanning || isSubmitting}
                onChange={(e) => {
                  const file = e.target.files?.[0]
                  if (file) processFile(file)
                  e.target.value = ''
                }}
              />

              {/* Dropzone */}
              <div
                onDragOver={(e) => {
                  e.preventDefault()
                  e.stopPropagation()
                  setIsDragging(true)
                }}
                onDragLeave={(e) => {
                  e.preventDefault()
                  e.stopPropagation()
                  setIsDragging(false)
                }}
                onDrop={(e) => {
                  e.preventDefault()
                  e.stopPropagation()
                  setIsDragging(false)
                  const file = e.dataTransfer.files?.[0]
                  if (file) processFile(file)
                }}
                onClick={() => fileInputRef.current?.click()}
                role="button"
                tabIndex={0}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') {
                    fileInputRef.current?.click()
                  }
                }}
                className={`relative flex min-h-[170px] cursor-pointer flex-col items-center justify-center rounded-2xl border-2 border-dashed p-6 text-center transition ${
                  isDragging
                    ? 'border-emerald-500 bg-emerald-50/70 scale-[0.99]'
                    : 'border-slate-200 bg-slate-50/50 hover:border-emerald-400 hover:bg-emerald-50/20'
                } ${isScanning || isSubmitting ? 'pointer-events-none opacity-70' : ''}`}
              >
                {isScanning ? (
                  <div className="flex flex-col items-center justify-center gap-2 py-4 text-emerald-700">
                    <Loader2 className="h-8 w-8 animate-spin" />
                    <p className="text-sm font-semibold">Đang đọc mã QR từ hình ảnh…</p>
                  </div>
                ) : isSubmitting ? (
                  <div className="flex flex-col items-center justify-center gap-2 py-4 text-emerald-700">
                    <Loader2 className="h-8 w-8 animate-spin" />
                    <p className="text-sm font-semibold">Đang thực hiện check-in…</p>
                  </div>
                ) : previewUrl ? (
                  <div
                    className="flex flex-col items-center gap-3"
                    onClick={(e) => e.stopPropagation()}
                  >
                    <div className="relative h-24 w-24 shrink-0 overflow-hidden rounded-xl border border-slate-200 bg-white p-2 shadow-xs">
                      <img
                        src={previewUrl}
                        alt="Ảnh QR đã chọn"
                        className="h-full w-full object-contain"
                      />
                    </div>
                    <div className="text-center">
                      <p className="text-xs font-semibold text-slate-700">
                        Đã chọn ảnh mã QR
                      </p>
                      <div className="mt-1 flex items-center justify-center gap-3">
                        <button
                          type="button"
                          onClick={() => fileInputRef.current?.click()}
                          className="text-xs font-semibold text-emerald-700 hover:underline"
                        >
                          Chọn ảnh khác
                        </button>
                        <span className="text-slate-300">·</span>
                        <button
                          type="button"
                          onClick={resetState}
                          className="text-xs text-slate-500 hover:text-red-600 inline-flex items-center gap-0.5"
                        >
                          <X className="h-3 w-3" /> Hủy
                        </button>
                      </div>
                    </div>
                  </div>
                ) : (
                  <div className="flex flex-col items-center gap-2">
                    <div className="flex h-12 w-12 items-center justify-center rounded-full bg-white text-emerald-600 shadow-xs ring-1 ring-slate-200">
                      <UploadCloud className="h-6 w-6" />
                    </div>
                    <div>
                      <p className="text-sm font-bold text-slate-800">
                        <span className="text-emerald-700 underline">Bấm để tải ảnh QR</span> hoặc kéo thả vào đây
                      </p>
                      <p className="mt-1 text-xs text-slate-500">
                        Hỗ trợ PNG, JPG, WEBP, SVG hoặc nhấn <kbd className="rounded border bg-white px-1.5 py-0.5 text-[11px] font-mono text-slate-600 shadow-xs">Ctrl + V</kbd> để dán ảnh
                      </p>
                    </div>
                  </div>
                )}
              </div>

              {/* Error Alert */}
              {scanError && (
                <div className="flex items-start gap-2.5 rounded-xl border border-rose-200 bg-rose-50 p-3.5 text-xs text-rose-800 animate-in fade-in">
                  <AlertCircle className="h-4 w-4 shrink-0 text-rose-600 mt-0.5" />
                  <div className="flex-1">
                    <p className="font-semibold">Không thể check-in</p>
                    <p className="mt-0.5">{scanError}</p>
                  </div>
                  <button
                    type="button"
                    onClick={() => setScanError(null)}
                    className="text-rose-500 hover:text-rose-800"
                  >
                    <X className="h-3.5 w-3.5" />
                  </button>
                </div>
              )}

              {/* Manual Confirmation if bookingId was resolved but needs click */}
              {detectedToken && detectedBookingId && !isSubmitting && !checkinSuccess && (
                <div className="rounded-xl border border-emerald-200 bg-emerald-50/50 p-4">
                  <div className="flex items-center justify-between">
                    <div>
                      <p className="text-xs font-semibold text-emerald-800">
                        Đã nhận diện Booking #{detectedBookingId.slice(0, 8).toUpperCase()}
                      </p>
                      <p className="text-[11px] text-slate-500 font-mono truncate max-w-xs">
                        {detectedBookingId}
                      </p>
                    </div>
                    <Button
                      type="button"
                      onClick={() => executeCheckin(detectedBookingId, detectedToken)}
                      className="bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-bold"
                    >
                      Xác nhận Check-in
                    </Button>
                  </div>
                </div>
              )}

              {/* Fallback if token was read but bookingId could not be extracted automatically */}
              {detectedToken && !detectedBookingId && !isSubmitting && !checkinSuccess && (
                <div className="rounded-xl border border-amber-200 bg-amber-50 p-4 space-y-2">
                  <p className="text-xs font-semibold text-amber-900">
                    Đã đọc được mã QR, vui lòng nhập mã booking cần check-in:
                  </p>
                  <form
                    className="flex gap-2"
                    onSubmit={(e) => {
                      e.preventDefault()
                      const bId = new FormData(e.currentTarget).get('manualBookingId') as string
                      if (bId && bId.trim()) {
                        executeCheckin(bId.trim(), detectedToken)
                      }
                    }}
                  >
                    <Input
                      name="manualBookingId"
                      placeholder="Nhập ID booking (UUID)"
                      required
                      className="text-xs"
                    />
                    <Button type="submit" size="sm" className="bg-emerald-600 text-white shrink-0">
                      Check-in
                    </Button>
                  </form>
                </div>
              )}
            </>
          )}
        </div>

        {/* Footer */}
        <div className="border-t border-slate-100 bg-slate-50 px-6 py-3 flex items-center justify-between text-xs text-slate-500">
          <span>Hệ thống tự động kích hoạt giờ chơi khi check-in</span>
          <Button variant="ghost" size="sm" onClick={onClose}>
            Đóng
          </Button>
        </div>
      </div>

      {/* Camera scanner modal */}
      {isCameraOpen && (
        <CameraQrScannerModal
          isOpen={isCameraOpen}
          onClose={() => setIsCameraOpen(false)}
          onScanSuccess={(raw) => {
            setIsCameraOpen(false)
            const token = extractCheckinToken(raw)
            if (token) {
              const bId = extractBookingIdFromToken(token)
              setDetectedToken(token)
              setDetectedBookingId(bId)
              if (bId) {
                executeCheckin(bId, token)
              }
            } else {
              setScanError('Mã QR từ camera không hợp lệ.')
            }
          }}
        />
      )}
    </div>
  )
}
