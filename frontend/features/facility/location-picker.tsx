'use client'

import { useEffect, useRef, useState } from 'react'
import styles from '@/features/discovery/search-map.module.css'
import { loadGoogleMaps } from './google-maps'

export type FacilityLocation = { latitude: number; longitude: number } | null
export function LocationPicker({ value, focusLocation, onChange, disabled = false }: {
  value: FacilityLocation
  focusLocation: FacilityLocation
  onChange: (value: FacilityLocation) => void
  disabled?: boolean
}) {
  const container = useRef<HTMLDivElement>(null)
  const map = useRef<google.maps.Map | null>(null)
  const marker = useRef<google.maps.Marker | null>(null)
  const valueRef = useRef(value)
  const focusLocationRef = useRef(focusLocation)
  const locked = useRef(disabled)
  const changeLocation = useRef(onChange)
  const [ready, setReady] = useState(false)
  const [mapError, setMapError] = useState('')

  useEffect(() => {
    valueRef.current = value
    focusLocationRef.current = focusLocation
    locked.current = disabled
    changeLocation.current = onChange
  }, [value, focusLocation, disabled, onChange])

  useEffect(() => {
    let active = true
    const listeners: google.maps.MapsEventListener[] = []
    let observer: ResizeObserver | undefined

    async function initialize() {
      try {
        const { maps, marker: markerLibrary } = await loadGoogleMaps()
        if (!active || !container.current) return

        const selectedInitially = focusLocationRef.current ?? valueRef.current
        const initial = selectedInitially
          ? { lat: selectedInitially.latitude, lng: selectedInitially.longitude }
          : { lat: 21.028, lng: 105.78 }
        const instance = new maps.Map(container.current, {
          center: initial,
          zoom: selectedInitially ? 16 : 12,
          mapTypeControl: false,
          streetViewControl: false,
          fullscreenControl: false,
          clickableIcons: false,
          gestureHandling: 'cooperative',
        })
        map.current = instance

        const pin = new markerLibrary.Marker({
          map: selectedInitially ? instance : null,
          position: selectedInitially ? initial : null,
          draggable: !locked.current,
          title: 'Vị trí cơ sở đã chọn',
        })
        marker.current = pin

        function selectPosition(position: google.maps.LatLng | google.maps.LatLngLiteral) {
          if (locked.current) return
          const point = typeof (position as google.maps.LatLng).lat === 'function'
            ? position as google.maps.LatLng
            : new google.maps.LatLng(position as google.maps.LatLngLiteral)
          pin.setPosition(point)
          pin.setMap(instance)
          instance.panTo(point)
          changeLocation.current({
            latitude: Number(point.lat().toFixed(7)),
            longitude: Number(point.lng().toFixed(7)),
          })
        }

        listeners.push(instance.addListener('click', (event: google.maps.MapMouseEvent) => {
          if (event.latLng) selectPosition(event.latLng)
        }))
        listeners.push(pin.addListener('dragend', () => {
          const position = pin.getPosition()
          if (position) selectPosition(position)
        }))

        observer = new ResizeObserver(() => google.maps.event.trigger(instance, 'resize'))
        observer.observe(container.current)
        setReady(true)
      } catch (error) {
        if (active) {
          setMapError(error instanceof Error ? error.message : 'Không thể tải Google Maps.')
          setReady(true)
        }
      }
    }

    void initialize()
    return () => {
      active = false
      observer?.disconnect()
      for (const listener of listeners) listener.remove()
      marker.current?.setMap(null)
      map.current = null
      marker.current = null
    }
  }, [])

  useEffect(() => {
    if (!map.current || !marker.current) return
    if (value) {
      marker.current.setPosition({ lat: value.latitude, lng: value.longitude })
      marker.current.setMap(map.current)
    } else {
      marker.current.setMap(null)
    }
    marker.current.setDraggable(!disabled)
  }, [value, disabled])

  useEffect(() => {
    if (!map.current || !focusLocation) return
    map.current.setCenter({ lat: focusLocation.latitude, lng: focusLocation.longitude })
    map.current.setZoom(17)
  }, [focusLocation])

  return <section className="min-w-0 space-y-3 md:col-span-2" aria-label="Vị trí cơ sở">
    <h2 className="text-lg font-semibold">Vị trí cơ sở</h2>
    <p id="location-instructions" className="text-sm text-slate-600">
      Tìm địa chỉ ở ô phía trên và chọn một gợi ý để đặt điểm, hoặc bấm lên bản đồ. Kéo marker để điều chỉnh vị trí.
      Chọn địa chỉ sẽ cập nhật tọa độ, các trường hành chính có sẵn và mức phóng bản đồ.
    </p>
    <div ref={container} className={styles.canvas} role="region" aria-label="Chọn vị trí cơ sở trên Google Maps" aria-describedby="location-instructions" />
    {disabled && <p className="text-sm text-slate-500">Chọn vị trí đang tạm khóa.</p>}
    {!ready && <p role="status" className="text-sm text-slate-500">Đang tải Google Maps…</p>}
    {mapError && <p role="alert" className="rounded-xl bg-amber-50 p-3 text-sm text-amber-900">{mapError}</p>}
    <p role="status" className="text-sm text-slate-600">{value ? `Vĩ độ: ${value.latitude.toFixed(7)} · Kinh độ: ${value.longitude.toFixed(7)}` : 'Chưa chọn vị trí. Tâm bản đồ mặc định không được lưu.'}</p>
    <p className="text-xs text-slate-500">Địa chỉ và điểm bản đồ được chỉnh riêng. Hãy kiểm tra cả hai trước khi lưu.</p>
  </section>
}
