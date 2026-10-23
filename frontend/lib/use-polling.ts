'use client'
import { useEffect, useRef } from 'react'
import { startPolling, type PollOptions } from './polling'

export function usePolling(
  task: (signal: AbortSignal) => Promise<unknown> | unknown,
  options: PollOptions & { enabled?: boolean; scope?: string },
) {
  const callbacks = useRef({ task, onTimeout: options.onTimeout, onError: options.onError })
  useEffect(() => {
    callbacks.current = { task, onTimeout: options.onTimeout, onError: options.onError }
  }, [task, options.onTimeout, options.onError])
  const { enabled = true, scope, interval, immediate, deadline, maxAttempts } = options
  useEffect(() => {
    if (!enabled) return
    return startPolling((signal) => callbacks.current.task(signal), {
      interval,
      immediate,
      deadline,
      maxAttempts,
      onTimeout: () => callbacks.current.onTimeout?.(),
      onError: (error) => callbacks.current.onError?.(error),
    })
  }, [enabled, scope, interval, immediate, deadline, maxAttempts])
}
