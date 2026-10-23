'use client'
import { useCallback, useEffect, useRef, useState } from 'react'
import type { FacilityLocation, LocationMap, LocationPoint } from './location-types'

export function useLocationMap(
  value: FacilityLocation,
  focusLocation: FacilityLocation,
  disabled: boolean,
  onChange: (point: LocationPoint) => void,
) {
  const container = useRef<HTMLDivElement>(null)
  const map = useRef<LocationMap | null>(null)
  const latest = useRef({ value, focusLocation, disabled, onChange })
  const [ready, setReady] = useState(false)
  const [provider, setProvider] = useState<'google' | 'leaflet'>('leaflet')
  const [error, setError] = useState('')
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    latest.current = { value, focusLocation, disabled, onChange }
  }, [value, focusLocation, disabled, onChange])
  useEffect(() => {
    let active = true
    const controller = new AbortController()
    let instance: LocationMap | null = null
    async function initialize() {
      if (!container.current) return
      const options = {
        container: container.current,
        signal: controller.signal,
        selected: latest.current.focusLocation ?? latest.current.value,
        disabled: () => latest.current.disabled,
        onChange: (point: LocationPoint) => latest.current.onChange(point),
      }
      let selected: 'google' | 'leaflet' = 'leaflet'
      try {
        if (process.env.NEXT_PUBLIC_GOOGLE_MAPS_API_KEY) {
          try {
            instance = await (
              await import('./google-location-map')
            ).createGoogleLocationMap(options)
            selected = 'google'
          } catch {
            /* Preserve the existing OpenStreetMap fallback when Google is unavailable. */
          }
        }
        if (!active) {
          instance?.destroy()
          return
        }
        if (!instance)
          instance = await (
            await import('./leaflet-location-map')
          ).createLeafletLocationMap(options)
        if (!active) {
          instance.destroy()
          return
        }
        map.current = instance
        instance.sync(latest.current.value, latest.current.disabled)
        if (latest.current.focusLocation) instance.focus(latest.current.focusLocation)
        setProvider(selected)
        setReady(true)
        setError('')
      } catch {
        if (active) setError('Không thể tải bản đồ. Vui lòng thử lại.')
      }
    }
    void initialize()
    return () => {
      active = false
      controller.abort()
      instance?.destroy()
      map.current = null
    }
  }, [attempt])
  useEffect(() => {
    if (ready) map.current?.sync(value, disabled)
  }, [value, disabled, ready])
  useEffect(() => {
    if (ready && focusLocation) map.current?.focus(focusLocation)
  }, [focusLocation, ready])
  const applyPosition = useCallback((point: LocationPoint) => {
    if (latest.current.disabled) return
    latest.current.onChange(point)
    map.current?.setPosition(point)
  }, [])
  const retry = useCallback(() => {
    setReady(false)
    setError('')
    setAttempt((value) => value + 1)
  }, [])
  return { container, ready, provider, error, retry, applyPosition }
}
