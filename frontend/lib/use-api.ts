'use client'
import { useCallback, useEffect, useRef, useState, useSyncExternalStore } from 'react'
import { readRequests } from './read-requests'
import { ApiError } from './api'
import { invalidateReads, readSession, readVersion, subscribeReads } from './read-state'
export function useApi<T>(path: string | null) {
  const version = useSyncExternalStore(
    subscribeReads,
    () => readVersion(path),
    () => '',
  )
  const [result, setResult] = useState<{
    path: string
    version: string
    session: string
    data: T | null
    error: string
    failure: ApiError | null
  } | null>(null)
  const lifetime = useRef(0)
  const mounted = useRef(false)
  const completed = useRef('')
  const refreshFence = useRef<{ path: string; version: string } | null>(null)
  const leases = useRef(new Set<() => void>())
  const start = useCallback((url: string, generation: string, signal?: AbortSignal) => {
    if (signal?.aborted) return Promise.resolve()
    const session = readSession(url)
    const token = lifetime.current
    const request = readRequests.acquire<T>(url)
    leases.current.add(request.release)
    signal?.addEventListener('abort', request.release, { once: true })
    const current = () =>
      !signal?.aborted &&
      mounted.current &&
      token === lifetime.current &&
      generation === readVersion(url)
    return request.promise
      .then((data) => {
        if (current()) {
          completed.current = `${generation}|${url}`
          refreshFence.current = { path: url, version: generation }
          setResult({ path: url, version: generation, session, data, error: '', failure: null })
        }
      })
      .catch((error) => {
        if (current() && error?.name !== 'AbortError') {
          completed.current = `${generation}|${url}`
          refreshFence.current = { path: url, version: generation }
          setResult({
            path: url,
            version: generation,
            session,
            data: null,
            error: error instanceof Error ? error.message : 'Không thể tải dữ liệu.',
            failure: error instanceof ApiError ? error : null,
          })
        }
      })
      .finally(() => {
        signal?.removeEventListener('abort', request.release)
        request.release()
        leases.current.delete(request.release)
      })
  }, [])
  useEffect(() => {
    mounted.current = true
    const token = ++lifetime.current
    const activeLeases = leases.current
    if (path && refreshFence.current?.path !== path) refreshFence.current = { path, version }
    if (path && completed.current !== `${version}|${path}`) void start(path, version)
    return () => {
      mounted.current = false
      lifetime.current = token + 1
      for (const release of activeLeases) release()
      activeLeases.clear()
    }
  }, [path, version, start])
  const current = result?.path === path && result.version === version
  const sameSession = result?.path === path && result.session === readSession(path)
  const reload = useCallback(() => {
    if (!path || !mounted.current || refreshFence.current?.path !== path) return Promise.resolve()
    // A successful mutation may already have invalidated and started this GET.
    // Join that generation; only a settled/current read needs a new explicit fence.
    if (refreshFence.current.version === readVersion(path)) invalidateReads(path)
    return start(path, readVersion(path))
  }, [path, start])
  const poll = useCallback(
    (signal: AbortSignal) => {
      if (!path || !mounted.current || refreshFence.current?.path !== path) return Promise.resolve()
      // Polling joins an active read without creating an uncancellable subscription refresh.
      return start(path, readVersion(path), signal)
    },
    [path, start],
  )
  return {
    data: sameSession ? result.data : null,
    error: current ? result.error : '',
    loading: Boolean(path) && !current,
    failure: current ? result.failure : null,
    reload,
    poll,
  }
}
