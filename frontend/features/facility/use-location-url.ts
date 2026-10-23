'use client'
import { useEffect, useRef, useState } from 'react'
import { parseCoordinatesLocally } from './location-coordinates'
import type { LocationPoint } from './location-types'

export function useLocationUrl(disabled: boolean, onApply: (point: LocationPoint) => void) {
  const [mapUrlInput, setMapUrlInput] = useState('')
  const [urlResolving, setUrlResolving] = useState(false)
  const [urlMessage, setUrlMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(
    null,
  )
  const request = useRef<AbortController | null>(null)
  const locked = useRef(disabled)
  useEffect(() => {
    locked.current = disabled
    if (disabled) request.current?.abort()
  }, [disabled])
  useEffect(() => () => request.current?.abort(), [])
  async function handleApplyUrl() {
    if (disabled || urlResolving) return
    const query = mapUrlInput.trim()
    if (!query) {
      setUrlMessage({ type: 'error', text: 'Vui lòng dán đường dẫn Google Maps hoặc nhập tọa độ.' })
      return
    }
    const controller = new AbortController()
    request.current?.abort()
    request.current = controller
    setUrlResolving(true)
    setUrlMessage(null)
    try {
      const local = parseCoordinatesLocally(query)
      if (local) {
        onApply(local)
        setUrlMessage({
          type: 'success',
          text: `Đã xác định tọa độ từ đường dẫn: Vĩ độ ${local.latitude} · Kinh độ ${local.longitude}`,
        })
      } else if (/^https?:\/\//.test(query)) {
        const response = await fetch(`/api/resolve-maps-url?url=${encodeURIComponent(query)}`, {
          signal: controller.signal,
        })
        const data = await response.json()
        if (controller.signal.aborted || locked.current) return
        if (
          response.ok &&
          typeof data.latitude === 'number' &&
          typeof data.longitude === 'number'
        ) {
          onApply({ latitude: data.latitude, longitude: data.longitude })
          setUrlMessage({
            type: 'success',
            text: `Đã xác định vị trí từ Google Maps: Vĩ độ ${data.latitude} · Kinh độ ${data.longitude}`,
          })
        } else
          setUrlMessage({
            type: 'error',
            text:
              data.error ||
              'Không tìm thấy tọa độ từ đường dẫn Google Maps này. Hãy kiểm tra lại đường dẫn.',
          })
      } else
        setUrlMessage({
          type: 'error',
          text: 'Định dạng đường dẫn không đúng. Hãy dán đường dẫn chia sẻ từ Google Maps (VD: https://maps.app.goo.gl/... hoặc https://www.google.com/maps/@...) hoặc nhập tọa độ "vĩ độ, kinh độ".',
        })
    } catch {
      if (!controller.signal.aborted)
        setUrlMessage({
          type: 'error',
          text: 'Không thể kết nối giải đường dẫn Google Maps. Bạn có thể bấm chọn trực tiếp trên bản đồ.',
        })
    } finally {
      if (request.current === controller) {
        request.current = null
        setUrlResolving(false)
      }
    }
  }
  return { mapUrlInput, setMapUrlInput, urlResolving, urlMessage, setUrlMessage, handleApplyUrl }
}
