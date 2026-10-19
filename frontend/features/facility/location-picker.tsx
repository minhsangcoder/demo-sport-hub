'use client'

import { useEffect, useRef, useState } from 'react'
import 'leaflet/dist/leaflet.css'
import { Link2, ExternalLink, CheckCircle2, AlertCircle } from 'lucide-react'
import styles from '@/features/discovery/search-map.module.css'
import { loadGoogleMaps } from './google-maps'

export type FacilityLocation = { latitude: number; longitude: number } | null

function parseCoordinatesLocally(text: string): { latitude: number; longitude: number } | null {
  const trimmed = text.trim()
  if (!trimmed) return null

  // 1. Raw coordinates "10.776889, 106.700806" or "10.776889 106.700806"
  const rawMatch = trimmed.match(/^(-?\d{1,2}(?:\.\d+)?)[,\s]+(-?\d{1,3}(?:\.\d+)?)$/)
  if (rawMatch) {
    const lat = parseFloat(rawMatch[1])
    const lng = parseFloat(rawMatch[2])
    if (lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180) {
      return { latitude: Number(lat.toFixed(7)), longitude: Number(lng.toFixed(7)) }
    }
  }

  // 2. Google Maps @lat,lng
  const atMatch = trimmed.match(/@(-?\d+\.\d+),(-?\d+\.\d+)/)
  if (atMatch) {
    return {
      latitude: Number(parseFloat(atMatch[1]).toFixed(7)),
      longitude: Number(parseFloat(atMatch[2]).toFixed(7)),
    }
  }

  // 3. Google Maps data=!3dlat!4dlng
  const dataMatch = trimmed.match(/!3d(-?\d+\.\d+)!4d(-?\d+\.\d+)/)
  if (dataMatch) {
    return {
      latitude: Number(parseFloat(dataMatch[1]).toFixed(7)),
      longitude: Number(parseFloat(dataMatch[2]).toFixed(7)),
    }
  }

  // 4. Query param ?q=lat,lng or &query=lat,lng or &ll=lat,lng or &destination=lat,lng
  const qMatch = trimmed.match(/[?&](?:q|query|ll|destination|center)=(-?\d+\.\d+),(-?\d+\.\d+)/)
  if (qMatch) {
    return {
      latitude: Number(parseFloat(qMatch[1]).toFixed(7)),
      longitude: Number(parseFloat(qMatch[2]).toFixed(7)),
    }
  }

  return null
}

export function LocationPicker({
  value,
  focusLocation,
  onChange,
  disabled = false,
}: {
  value: FacilityLocation
  focusLocation: FacilityLocation
  onChange: (value: FacilityLocation) => void
  disabled?: boolean
}) {
  const container = useRef<HTMLDivElement>(null)
  const mapRef = useRef<any>(null)
  const markerRef = useRef<any>(null)
  const setPositionRef = useRef<(lat: number, lng: number) => void>(() => {})
  const valueRef = useRef(value)
  const focusLocationRef = useRef(focusLocation)
  const locked = useRef(disabled)
  const changeLocation = useRef(onChange)
  const [ready, setReady] = useState(false)
  const [provider, setProvider] = useState<'google' | 'leaflet'>('leaflet')

  // URL parse input state
  const [mapUrlInput, setMapUrlInput] = useState('')
  const [urlResolving, setUrlResolving] = useState(false)
  const [urlMessage, setUrlMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null)

  useEffect(() => {
    valueRef.current = value
    focusLocationRef.current = focusLocation
    locked.current = disabled
    changeLocation.current = onChange
  }, [value, focusLocation, disabled, onChange])

  function applyPosition(lat: number, lng: number) {
    const point = {
      latitude: Number(lat.toFixed(7)),
      longitude: Number(lng.toFixed(7)),
    }
    changeLocation.current(point)
    setPositionRef.current(point.latitude, point.longitude)
  }

  async function handleApplyUrl() {
    const query = mapUrlInput.trim()
    if (!query) {
      setUrlMessage({ type: 'error', text: 'Vui lòng dán link Google Maps hoặc nhập tọa độ.' })
      return
    }

    setUrlResolving(true)
    setUrlMessage(null)

    // 1. Kiểm tra phân tích trực tiếp từ link/tọa độ
    const local = parseCoordinatesLocally(query)
    if (local) {
      applyPosition(local.latitude, local.longitude)
      setUrlResolving(false)
      setUrlMessage({
        type: 'success',
        text: `Đã xác định tọa độ từ link: Vĩ độ ${local.latitude} · Kinh độ ${local.longitude}`,
      })
      return
    }

    // 2. Nếu là link rút gọn (maps.app.goo.gl...) thì gọi API server để giải link
    if (query.startsWith('http://') || query.startsWith('https://')) {
      try {
        const res = await fetch(`/api/resolve-maps-url?url=${encodeURIComponent(query)}`)
        const data = await res.json()
        if (res.ok && data.latitude && data.longitude) {
          applyPosition(data.latitude, data.longitude)
          setUrlMessage({
            type: 'success',
            text: `Đã xác định vị trí từ Google Maps: Vĩ độ ${data.latitude} · Kinh độ ${data.longitude}`,
          })
        } else {
          setUrlMessage({
            type: 'error',
            text: data.error || 'Không tìm thấy tọa độ từ link Google Maps này. Hãy kiểm tra lại link.',
          })
        }
      } catch {
        setUrlMessage({
          type: 'error',
          text: 'Không thể kết nối giải link Google Maps. Bạn có thể bấm chọn trực tiếp trên bản đồ.',
        })
      } finally {
        setUrlResolving(false)
      }
      return
    }

    setUrlResolving(false)
    setUrlMessage({
      type: 'error',
      text: 'Định dạng link không đúng. Hãy dán link chia sẻ từ Google Maps (VD: https://maps.app.goo.gl/... hoặc https://www.google.com/maps/@...) hoặc nhập tọa độ "vĩ độ, kinh độ".',
    })
  }

  useEffect(() => {
    let active = true
    let observer: ResizeObserver | undefined
    let cleanupFn: (() => void) | undefined

    async function initMap() {
      if (!container.current) return

      const selectedInitially = focusLocationRef.current ?? valueRef.current
      const initial = selectedInitially
        ? { lat: selectedInitially.latitude, lng: selectedInitially.longitude }
        : { lat: 21.028, lng: 105.78 }

      // Try Google Maps if key is present
      const hasGoogleKey = Boolean(process.env.NEXT_PUBLIC_GOOGLE_MAPS_API_KEY)
      if (hasGoogleKey) {
        try {
          const { maps, marker: markerLibrary } = await loadGoogleMaps()
          if (!active || !container.current) return

          setProvider('google')
          const instance = new maps.Map(container.current, {
            center: initial,
            zoom: selectedInitially ? 16 : 13,
            mapTypeControl: false,
            streetViewControl: false,
            fullscreenControl: false,
            clickableIcons: false,
            gestureHandling: 'cooperative',
          })
          mapRef.current = instance

          const pin = new markerLibrary.Marker({
            map: selectedInitially ? instance : null,
            position: selectedInitially ? initial : null,
            draggable: !locked.current,
            title: 'Vị trí cơ sở đã chọn',
          })
          markerRef.current = pin

          function selectPosition(position: google.maps.LatLng | google.maps.LatLngLiteral) {
            if (locked.current) return
            const point =
              typeof (position as google.maps.LatLng).lat === 'function'
                ? (position as google.maps.LatLng)
                : new google.maps.LatLng(position as google.maps.LatLngLiteral)
            pin.setPosition(point)
            pin.setMap(instance)
            instance.panTo(point)
            changeLocation.current({
              latitude: Number(point.lat().toFixed(7)),
              longitude: Number(point.lng().toFixed(7)),
            })
          }

          setPositionRef.current = (lat: number, lng: number) => {
            const pt = new google.maps.LatLng(lat, lng)
            pin.setPosition(pt)
            pin.setMap(instance)
            instance.panTo(pt)
            instance.setZoom(16)
          }

          const clickListener = instance.addListener('click', (e: google.maps.MapMouseEvent) => {
            if (e.latLng) selectPosition(e.latLng)
          })
          const dragListener = pin.addListener('dragend', () => {
            const pos = pin.getPosition()
            if (pos) selectPosition(pos)
          })

          observer = new ResizeObserver(() => google.maps.event.trigger(instance, 'resize'))
          observer.observe(container.current)

          cleanupFn = () => {
            clickListener.remove()
            dragListener.remove()
            pin.setMap(null)
            mapRef.current = null
            markerRef.current = null
          }

          setReady(true)
          return
        } catch {
          // Fall through to Leaflet OpenStreetMap
        }
      }

      // Free fallback: Leaflet + OpenStreetMap (100% free, no API key required)
      try {
        const L = (await import('leaflet')).default
        if (!active || !container.current) return

        setProvider('leaflet')
        const leafletMap = L.map(container.current, {
          scrollWheelZoom: false,
        }).setView([initial.lat, initial.lng], selectedInitially ? 16 : 13)
        mapRef.current = leafletMap

        L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
          attribution: '© OpenStreetMap contributors',
          referrerPolicy: 'origin',
        }).addTo(leafletMap)

        const pinIcon = L.divIcon({
          className: 'leaflet-custom-marker',
          html: `<div style="display:flex;align-items:center;justify-content:center;width:34px;height:34px;background:#059669;color:white;border-radius:50%;box-shadow:0 10px 15px -3px rgba(0,0,0,0.3);border:3px solid white;transform:translate(-50%,-100%);">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0Z"/><circle cx="12" cy="10" r="3"/></svg>
          </div>`,
          iconSize: [0, 0],
        })

        let leafletMarker: import('leaflet').Marker | undefined
        if (selectedInitially) {
          leafletMarker = L.marker([initial.lat, initial.lng], {
            icon: pinIcon,
            draggable: !locked.current,
          }).addTo(leafletMap)
          markerRef.current = leafletMarker
        }

        function selectLeafletPos(latlng: import('leaflet').LatLng) {
          if (locked.current) return
          if (!markerRef.current) {
            leafletMarker = L.marker(latlng, {
              icon: pinIcon,
              draggable: !locked.current,
            }).addTo(leafletMap)
            markerRef.current = leafletMarker
            leafletMarker.on('dragend', () => {
              selectLeafletPos(leafletMarker!.getLatLng())
            })
          } else {
            markerRef.current.setLatLng(latlng)
          }
          leafletMap.panTo(latlng)
          changeLocation.current({
            latitude: Number(latlng.lat.toFixed(7)),
            longitude: Number(latlng.lng.toFixed(7)),
          })
        }

        setPositionRef.current = (lat: number, lng: number) => {
          if (!markerRef.current) {
            leafletMarker = L.marker([lat, lng], {
              icon: pinIcon,
              draggable: !locked.current,
            }).addTo(leafletMap)
            markerRef.current = leafletMarker
            leafletMarker.on('dragend', () => {
              selectLeafletPos(leafletMarker!.getLatLng())
            })
          } else {
            markerRef.current.setLatLng([lat, lng])
          }
          leafletMap.setView([lat, lng], 16)
        }

        leafletMap.on('click', (e: import('leaflet').LeafletMouseEvent) => {
          selectLeafletPos(e.latlng)
        })

        if (leafletMarker) {
          leafletMarker.on('dragend', () => {
            selectLeafletPos(leafletMarker!.getLatLng())
          })
        }

        observer = new ResizeObserver(() => {
          leafletMap.invalidateSize()
        })
        observer.observe(container.current)

        cleanupFn = () => {
          leafletMap.remove()
          mapRef.current = null
          markerRef.current = null
        }

        setReady(true)
      } catch (err) {
        console.error('Không thể tải Leaflet OpenStreetMap:', err)
      }
    }

    void initMap()

    return () => {
      active = false
      observer?.disconnect()
      cleanupFn?.()
    }
  }, [])

  // Sync external value
  useEffect(() => {
    if (!mapRef.current || !ready) return

    if (provider === 'google') {
      if (value) {
        markerRef.current?.setPosition({ lat: value.latitude, lng: value.longitude })
        markerRef.current?.setMap(mapRef.current)
      } else {
        markerRef.current?.setMap(null)
      }
      markerRef.current?.setDraggable(!disabled)
    } else if (provider === 'leaflet') {
      if (value) {
        setPositionRef.current(value.latitude, value.longitude)
      }
    }
  }, [value, disabled, ready, provider])

  // Sync focus location
  useEffect(() => {
    if (!mapRef.current || !focusLocation || !ready) return

    if (provider === 'google') {
      mapRef.current.setCenter({ lat: focusLocation.latitude, lng: focusLocation.longitude })
      mapRef.current.setZoom(17)
    } else if (provider === 'leaflet') {
      mapRef.current.setView([focusLocation.latitude, focusLocation.longitude], 16)
    }
  }, [focusLocation, ready, provider])

  return (
    <section className="min-w-0 space-y-3 md:col-span-2" aria-label="Vị trí cơ sở">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold">
          Vị trí cơ sở <span className="ml-1 font-bold text-red-500">*</span>
        </h2>
        <span className="rounded-full bg-emerald-50 px-3 py-1 text-xs font-semibold text-emerald-800">
          {provider === 'google' ? 'Google Maps' : 'Bản đồ OpenStreetMap (Miễn phí)'}
        </span>
      </div>

      <p id="location-instructions" className="text-sm text-slate-600">
        Bạn có thể <strong>dán link Google Maps</strong> vào ô dưới đây để hệ thống tự động ghim vị trí, hoặc bấm trực tiếp lên bản đồ / kéo ghim để điều chỉnh.
      </p>

      {/* Hộp dán link Google Maps tiện lợi */}
      <div className="rounded-xl border border-emerald-200 bg-emerald-50/60 p-3.5 space-y-2.5">
        <div className="flex flex-wrap items-center justify-between gap-1.5">
          <span className="text-xs font-bold text-emerald-950 flex items-center gap-1.5">
            <Link2 className="h-4 w-4 text-emerald-700" />
            Dán link Google Maps hoặc tọa độ
          </span>
          {value && (
            <a
              href={`https://www.google.com/maps/search/?api=1&query=${value.latitude},${value.longitude}`}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-1 text-[11px] font-semibold text-emerald-800 hover:text-emerald-950 hover:underline"
              title="Mở Google Maps kiểm tra vị trí này"
            >
              <span>Mở Google Maps kiểm tra</span>
              <ExternalLink className="h-3 w-3" />
            </a>
          )}
        </div>

        <p className="text-[11px] text-slate-600 leading-relaxed">
          Mở Google Maps trên điện thoại hoặc máy tính → Tìm cơ sở của bạn → Bấm nút <strong>&quot;Chia sẻ&quot; (Share)</strong> rồi sao chép link dán vào đây (hoặc dán đường link trên thanh địa chỉ trình duyệt).
        </p>

        <div className="flex flex-col sm:flex-row gap-2">
          <input
            type="text"
            value={mapUrlInput}
            onChange={e => {
              setMapUrlInput(e.target.value)
              if (urlMessage) setUrlMessage(null)
            }}
            onKeyDown={e => {
              if (e.key === 'Enter') {
                e.preventDefault()
                void handleApplyUrl()
              }
            }}
            disabled={disabled || urlResolving}
            placeholder="VD: https://maps.app.goo.gl/... hoặc https://www.google.com/maps/@... hoặc 10.7768, 106.7008"
            className="h-9 flex-1 rounded-lg border border-slate-300 bg-white px-3 text-xs text-slate-800 placeholder:text-slate-400 focus:border-emerald-600 focus:outline-hidden"
          />
          <button
            type="button"
            onClick={() => void handleApplyUrl()}
            disabled={disabled || urlResolving || !mapUrlInput.trim()}
            className="inline-flex h-9 items-center justify-center rounded-lg bg-emerald-600 px-4 text-xs font-bold text-white shadow-xs hover:bg-emerald-700 disabled:opacity-50 transition-colors shrink-0"
          >
            {urlResolving ? 'Đang đọc link…' : 'Xác định vị trí'}
          </button>
        </div>

        {urlMessage && (
          <div
            className={`flex items-start gap-1.5 text-xs ${
              urlMessage.type === 'success' ? 'font-medium text-emerald-800' : 'text-red-600'
            }`}
          >
            {urlMessage.type === 'success' ? (
              <CheckCircle2 className="h-4 w-4 shrink-0 text-emerald-600 mt-0.5" />
            ) : (
              <AlertCircle className="h-4 w-4 shrink-0 text-red-500 mt-0.5" />
            )}
            <span>{urlMessage.text}</span>
          </div>
        )}
      </div>

      <div
        ref={container}
        className={styles.canvas}
        role="region"
        aria-label="Chọn vị trí cơ sở trên bản đồ"
        aria-describedby="location-instructions"
      />

      {disabled && <p className="text-sm text-slate-500">Chọn vị trí đang tạm khóa.</p>}
      {!ready && <p role="status" className="text-sm text-slate-500">Đang khởi tạo bản đồ…</p>}

      <div className="flex flex-wrap items-center justify-between gap-2 text-xs">
        <p role="status" className="text-slate-700 font-medium">
          {value
            ? `Đã ghim: Vĩ độ ${value.latitude.toFixed(7)} · Kinh độ ${value.longitude.toFixed(7)}`
            : 'Chưa chọn vị trí. Vui lòng dán link Google Maps hoặc bấm lên bản đồ.'}
        </p>
        <p className="text-slate-400">
          Bản đồ tích hợp OpenStreetMap không phát sinh chi phí API.
        </p>
      </div>
    </section>
  )
}
