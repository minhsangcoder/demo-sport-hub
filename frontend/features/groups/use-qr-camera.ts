'use client'
import { useCallback, useEffect, useRef, useState, type RefObject } from 'react'
import { createQrCameraSession } from './qr-camera-session'

export function useQrCamera(
  isOpen: boolean,
  onScanSuccess: (value: string) => void,
  video: RefObject<HTMLVideoElement | null>,
  canvas: RefObject<HTMLCanvasElement | null>,
) {
  const callback = useRef(onScanSuccess)
  const session = useRef<ReturnType<typeof createQrCameraSession> | null>(null)
  const [state, setState] = useState({
    hasCamera: null as boolean | null,
    isInitializing: false,
    errorMessage: null as string | null,
  })
  useEffect(() => {
    callback.current = onScanSuccess
  }, [onScanSuccess])
  useEffect(() => {
    if (!isOpen || !video.current || !canvas.current) return
    const camera = createQrCameraSession(
      video.current,
      canvas.current,
      (value) => callback.current(value),
      setState,
    )
    session.current = camera
    return () => {
      camera.stop()
      if (session.current === camera) session.current = null
    }
  }, [isOpen, video, canvas])
  const startCamera = useCallback(() => session.current?.restart(), [])
  return { ...state, startCamera }
}
