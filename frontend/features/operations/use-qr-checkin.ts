'use client'
import { extractBookingIdFromToken, extractCheckinToken } from '@/features/groups/qr-parser'
import { api } from '@/lib/api'
import { useCallback, useEffect, useRef, useState } from 'react'

export function useQrCheckin(isOpen: boolean, onSuccess?: (bookingId: string) => void) {
  const [isDragging, setIsDragging] = useState(false)
  const [isScanning, setIsScanning] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [previewUrl, setPreviewUrl] = useState<string | null>(null)
  const [detectedToken, setDetectedToken] = useState<string | null>(null)
  const [detectedBookingId, setDetectedBookingId] = useState<string | null>(null)
  const [scanError, setScanError] = useState<string | null>(null)
  const [checkinSuccess, setCheckinSuccess] = useState(false)
  const [checkinMessage, setCheckinMessage] = useState<string | null>(null)
  const [isCameraOpen, setIsCameraOpen] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const active = useRef(false),
    attempt = useRef(0),
    posting = useRef(false),
    scanning = useRef(false)
  const decoder = useRef<AbortController | null>(null),
    preview = useRef<string | null>(null)
  const success = useRef(onSuccess)
  useEffect(() => {
    success.current = onSuccess
  }, [onSuccess])
  const revokePreview = useCallback(() => {
    if (preview.current) URL.revokeObjectURL(preview.current)
    preview.current = null
  }, [])
  const cancelImage = useCallback(() => {
    attempt.current++
    decoder.current?.abort()
    scanning.current = false
    revokePreview()
  }, [revokePreview])
  useEffect(() => {
    active.current = isOpen
    return () => {
      active.current = false
      cancelImage()
    }
  }, [isOpen, cancelImage])
  const resetState = useCallback(() => {
    if (posting.current) return
    cancelImage()
    setPreviewUrl(null)
    setDetectedToken(null)
    setDetectedBookingId(null)
    setScanError(null)
    setCheckinSuccess(false)
    setCheckinMessage(null)
    setIsScanning(false)
    setIsCameraOpen(false)
  }, [cancelImage])
  const executeCheckin = useCallback(async (bookingId: string, token: string) => {
    if (!active.current || posting.current) return
    posting.current = true
    const generation = attempt.current
    setIsSubmitting(true)
    setScanError(null)
    try {
      // Once submitted, a UI close cannot cancel or mislabel a committed server transition.
      await api(`/bookings/${bookingId}/check-in`, {
        method: 'POST',
        body: JSON.stringify({ token }),
      })
      if (active.current && generation === attempt.current) {
        setCheckinSuccess(true)
        setCheckinMessage(
          `Đã xác nhận vào sân thành công cho lượt đặt #${bookingId.slice(0, 8).toUpperCase()}!`,
        )
        success.current?.(bookingId)
      }
    } catch (error) {
      if (active.current && generation === attempt.current)
        setScanError(
          error instanceof Error
            ? error.message
            : 'Không thể xác nhận vào sân. Vui lòng kiểm tra trạng thái lượt đặt hoặc thời gian hiệu lực của mã.',
        )
    } finally {
      posting.current = false
      if (active.current) setIsSubmitting(false)
    }
  }, [])
  const processFile = useCallback(
    async (file: File) => {
      if (!active.current || scanning.current || posting.current || document.hidden) return
      if (!file.type.startsWith('image/') && !file.name.endsWith('.svg')) {
        setScanError('Vui lòng chọn tệp hình ảnh (PNG, JPG, WEBP, SVG).')
        return
      }
      const generation = ++attempt.current
      const controller = new AbortController()
      decoder.current?.abort()
      decoder.current = controller
      scanning.current = true
      setScanError(null)
      setCheckinMessage(null)
      setIsScanning(true)
      revokePreview()
      preview.current = URL.createObjectURL(file)
      setPreviewUrl(preview.current)
      try {
        const { decodeQrFromImageFile } = await import('@/features/groups/qr-image-decoder')
        const raw = await decodeQrFromImageFile(file, controller.signal)
        if (!active.current || generation !== attempt.current || controller.signal.aborted) return
        const token = extractCheckinToken(raw)
        if (!token) throw new Error('Không thể đọc mã xác nhận vào sân từ hình ảnh này.')
        const bookingId = extractBookingIdFromToken(token)
        setDetectedToken(token)
        setDetectedBookingId(bookingId)
        if (bookingId) await executeCheckin(bookingId, token)
      } catch (error) {
        if (active.current && generation === attempt.current && !controller.signal.aborted)
          setScanError(
            error instanceof Error
              ? error.message
              : 'Không tìm thấy mã QR trong ảnh. Vui lòng thử ảnh khác rõ nét hơn.',
          )
      } finally {
        if (decoder.current === controller) scanning.current = false
        if (active.current && generation === attempt.current) setIsScanning(false)
      }
    },
    [executeCheckin, revokePreview],
  )
  useEffect(() => {
    if (!isOpen) return
    const paste = (event: ClipboardEvent) => {
      for (const item of Array.from(event.clipboardData?.items ?? [])) {
        if (!item.type.startsWith('image/')) continue
        const file = item.getAsFile()
        if (file) void processFile(file)
        break
      }
    }
    window.addEventListener('paste', paste)
    return () => window.removeEventListener('paste', paste)
  }, [isOpen, processFile])
  return {
    isDragging,
    setIsDragging,
    isScanning,
    isSubmitting,
    previewUrl,
    detectedToken,
    setDetectedToken,
    detectedBookingId,
    setDetectedBookingId,
    scanError,
    setScanError,
    checkinSuccess,
    checkinMessage,
    isCameraOpen,
    setIsCameraOpen,
    fileInputRef,
    resetState,
    processFile,
    executeCheckin,
  }
}
export type QrCheckinModel = ReturnType<typeof useQrCheckin>
