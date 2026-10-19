'use client'

import React, { useEffect, useRef, useState } from 'react'
import { Camera, X, AlertCircle, RefreshCw } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { scanVideoFrame } from './qr-decoder'

interface CameraQrScannerModalProps {
  isOpen: boolean
  onClose: () => void
  onScanSuccess: (rawResult: string) => void
}

export function CameraQrScannerModal({
  isOpen,
  onClose,
  onScanSuccess,
}: CameraQrScannerModalProps) {
  const videoRef = useRef<HTMLVideoElement>(null)
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const animationFrameRef = useRef<number | null>(null)

  const [hasCamera, setHasCamera] = useState<boolean | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [isInitializing, setIsInitializing] = useState(false)

  useEffect(() => {
    if (!isOpen) {
      stopCamera()
      return
    }

    startCamera()

    return () => {
      stopCamera()
    }
  }, [isOpen])

  function stopCamera() {
    if (animationFrameRef.current) {
      cancelAnimationFrame(animationFrameRef.current)
      animationFrameRef.current = null
    }
    if (streamRef.current) {
      streamRef.current.getTracks().forEach((track) => track.stop())
      streamRef.current = null
    }
    if (videoRef.current) {
      videoRef.current.srcObject = null
    }
  }

  async function startCamera() {
    setIsInitializing(true)
    setErrorMessage(null)

    if (
      typeof navigator === 'undefined' ||
      !navigator.mediaDevices ||
      !navigator.mediaDevices.getUserMedia
    ) {
      setHasCamera(false)
      setErrorMessage(
        'Trình duyệt này không hỗ trợ truy cập camera hoặc đang ở kết nối không bảo mật (yêu cầu HTTPS/localhost).'
      )
      setIsInitializing(false)
      return
    }

    try {
      // Try back camera first on mobile devices, or fallback to any camera
      let stream: MediaStream
      try {
        stream = await navigator.mediaDevices.getUserMedia({
          video: { facingMode: { ideal: 'environment' } },
          audio: false,
        })
      } catch {
        stream = await navigator.mediaDevices.getUserMedia({
          video: true,
          audio: false,
        })
      }

      streamRef.current = stream
      setHasCamera(true)

      if (videoRef.current) {
        videoRef.current.srcObject = stream
        // Play video
        await videoRef.current.play().catch(() => {})
      }

      setIsInitializing(false)
      startScanningLoop()
    } catch (err: unknown) {
      setIsInitializing(false)
      setHasCamera(false)
      const error = err as { name?: string; message?: string }
      if (
        error.name === 'NotAllowedError' ||
        error.name === 'PermissionDeniedError'
      ) {
        setErrorMessage(
          'Quyền truy cập camera bị từ chối. Vui lòng cấp quyền trong cài đặt trình duyệt hoặc chọn tải ảnh QR lên.'
        )
      } else if (
        error.name === 'NotFoundError' ||
        error.name === 'DevicesNotFoundError'
      ) {
        setErrorMessage('Không tìm thấy thiết bị camera trên máy của bạn.')
      } else {
        setErrorMessage(
          'Không thể khởi động camera. Vui lòng thử tải ảnh mã QR lên.'
        )
      }
    }
  }

  function startScanningLoop() {
    const scan = () => {
      if (!videoRef.current || !canvasRef.current || !streamRef.current) {
        return
      }

      const video = videoRef.current
      const canvas = canvasRef.current

      if (video.readyState >= HTMLMediaElement.HAVE_CURRENT_DATA) {
        const detected = scanVideoFrame(video, canvas)
        if (detected) {
          // Success feedback
          if (typeof navigator !== 'undefined' && navigator.vibrate) {
            navigator.vibrate(100)
          }
          stopCamera()
          onScanSuccess(detected)
          return
        }
      }

      animationFrameRef.current = requestAnimationFrame(scan)
    }

    animationFrameRef.current = requestAnimationFrame(scan)
  }

  if (!isOpen) return null

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="camera-modal-title"
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/80 p-4 backdrop-blur-xs animate-in fade-in duration-200"
    >
      <div className="relative w-full max-w-md overflow-hidden rounded-3xl bg-white shadow-2xl ring-1 ring-slate-200">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-slate-100 px-5 py-4">
          <div className="flex items-center gap-2.5">
            <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-emerald-50 text-emerald-700">
              <Camera className="h-5 w-5" />
            </div>
            <div>
              <h2
                id="camera-modal-title"
                className="font-bold text-slate-900 text-base"
              >
                Quét mã QR bằng Camera
              </h2>
              <p className="text-xs text-slate-500">
                Hướng camera về phía mã QR mời
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

        {/* Viewfinder area */}
        <div className="relative aspect-square w-full overflow-hidden bg-black">
          {/* Hidden offscreen canvas for frame processing */}
          <canvas ref={canvasRef} className="hidden" />

          {/* Live video */}
          <video
            ref={videoRef}
            playsInline
            muted
            className="h-full w-full object-cover"
          />

          {/* Scanner Overlay UI */}
          {hasCamera && !errorMessage && !isInitializing && (
            <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
              {/* Darkened vignette around target */}
              <div className="relative h-64 w-64 rounded-2xl ring-2 ring-emerald-500/80 shadow-[0_0_0_9999px_rgba(0,0,0,0.5)]">
                {/* 4 Corner accents */}
                <div className="absolute -top-1 -left-1 h-6 w-6 border-t-4 border-l-4 border-emerald-400 rounded-tl-lg" />
                <div className="absolute -top-1 -right-1 h-6 w-6 border-t-4 border-r-4 border-emerald-400 rounded-tr-lg" />
                <div className="absolute -bottom-1 -left-1 h-6 w-6 border-b-4 border-l-4 border-emerald-400 rounded-bl-lg" />
                <div className="absolute -bottom-1 -right-1 h-6 w-6 border-b-4 border-r-4 border-emerald-400 rounded-br-lg" />

                {/* Animated laser line */}
                <div className="absolute inset-x-2 top-0 h-0.5 bg-gradient-to-r from-transparent via-emerald-400 to-transparent shadow-[0_0_8px_#10b981] animate-pulse" />
              </div>
            </div>
          )}

          {/* Initializing loader */}
          {isInitializing && (
            <div className="absolute inset-0 flex flex-col items-center justify-center gap-3 bg-slate-900 text-white">
              <div className="h-8 w-8 animate-spin rounded-full border-2 border-slate-600 border-t-emerald-500" />
              <p className="text-xs text-slate-300">Đang bật camera…</p>
            </div>
          )}

          {/* Error display */}
          {errorMessage && (
            <div className="absolute inset-0 flex flex-col items-center justify-center p-6 text-center bg-slate-900 text-white">
              <div className="flex h-12 w-12 items-center justify-center rounded-full bg-red-950/60 text-red-400 ring-1 ring-red-500/30">
                <AlertCircle className="h-6 w-6" />
              </div>
              <p className="mt-3 text-sm font-medium text-slate-200">
                {errorMessage}
              </p>
              <Button
                type="button"
                variant="outline"
                onClick={startCamera}
                className="mt-4 gap-2 border-slate-700 bg-slate-800 text-white hover:bg-slate-700"
              >
                <RefreshCw className="h-4 w-4" />
                Thử lại
              </Button>
            </div>
          )}
        </div>

        {/* Footer tips */}
        <div className="border-t border-slate-100 bg-slate-50 px-5 py-3 text-center">
          <p className="text-xs text-slate-500">
            Mẹo: Giữ ảnh QR thẳng và đủ ánh sáng để hệ thống nhận diện nhanh
            nhất.
          </p>
        </div>
      </div>
    </div>
  )
}
