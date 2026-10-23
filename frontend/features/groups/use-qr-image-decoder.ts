'use client'
import { useCallback, useEffect, useRef } from 'react'

export function useQrImageDecoder() {
  const mounted = useRef(false)
  const controller = useRef<AbortController | null>(null)
  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
      controller.current?.abort()
    }
  }, [])
  const cancelDecode = useCallback(() => controller.current?.abort(), [])
  const decode = useCallback((file: File) => {
    const current = new AbortController()
    controller.current?.abort()
    controller.current = current
    const active = () =>
      mounted.current && controller.current === current && !current.signal.aborted
    const promise = import('./qr-image-decoder').then(async (module) => {
      current.signal.throwIfAborted()
      const value = await module.decodeQrFromImageFile(file, current.signal)
      current.signal.throwIfAborted()
      if (!active()) throw new DOMException('Đã hủy đọc ảnh.', 'AbortError')
      return value
    })
    return { active, promise }
  }, [])
  return { decode, cancelDecode }
}
