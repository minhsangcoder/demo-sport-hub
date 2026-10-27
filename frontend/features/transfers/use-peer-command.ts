'use client'
import { useRef, useState } from 'react'
import { api, ApiError } from '@/lib/api'

export function usePeerCommand() {
  const intent = useRef(new Map<string, { payload: string; key: string }>())
  const running = useRef(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [blocked, setBlocked] = useState(false)
  const [failure, setFailure] = useState<ApiError | null>(null)
  async function run<T>(path: string, body: unknown, done?: (value: T) => void) {
    if (running.current || blocked) return
    const payload = JSON.stringify(body)
    if (intent.current.get(path)?.payload !== payload)
      intent.current.set(path, { payload, key: crypto.randomUUID() })
    running.current = true
    setBusy(true)
    setError('')
    setFailure(null)
    try {
      const value = await api<T>(path, {
        method: 'POST',
        headers: { 'Idempotency-Key': intent.current.get(path)!.key },
        body: payload,
      })
      done?.(value)
      return value
    } catch (failure) {
      setFailure(failure instanceof ApiError ? failure : null)
      if (failure instanceof ApiError && [401, 403].includes(failure.status)) setBlocked(true)
      setError(
        failure instanceof Error
          ? failure.message
          : 'Chưa hoàn tất thao tác. Kiểm tra trạng thái trước khi thử lại.',
      )
    } finally {
      running.current = false
      setBusy(false)
    }
  }
  return { run, busy, error, blocked, failure }
}
