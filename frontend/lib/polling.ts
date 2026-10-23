export type PollOptions = {
  interval: number
  immediate?: boolean
  deadline?: number
  maxAttempts?: number
  onTimeout?: () => void
  onError?: (error: unknown) => void
}
type Environment = {
  visibility: Pick<Document, 'hidden' | 'addEventListener' | 'removeEventListener'>
  now: () => number
  setTimer: (callback: () => void, delay: number) => ReturnType<typeof setTimeout>
  clearTimer: (timer: ReturnType<typeof setTimeout>) => void
}

/** A single awaited loop. A hidden tab never starts another read or extends a deadline. */
export function startPolling(
  task: (signal: AbortSignal) => Promise<unknown> | unknown,
  options: PollOptions,
  environment: Environment = {
    visibility: document,
    now: Date.now,
    setTimer: setTimeout,
    clearTimer: clearTimeout,
  },
) {
  const { visibility, now, setTimer, clearTimer } = environment
  let timer: ReturnType<typeof setTimeout> | undefined
  let controller: AbortController | undefined
  let stopped = false,
    running = false,
    resume = false,
    attempts = 0
  const expired = () =>
    (options.deadline !== undefined && now() >= options.deadline) ||
    attempts >= (options.maxAttempts ?? Infinity)
  function clear() {
    if (timer !== undefined) clearTimer(timer)
    timer = undefined
  }
  function stop() {
    stopped = true
    clear()
    controller?.abort()
    visibility.removeEventListener('visibilitychange', changed)
  }
  function timeout() {
    if (!stopped) {
      stop()
      options.onTimeout?.()
    }
  }
  function schedule() {
    clear()
    if (stopped) return
    if (expired()) {
      timeout()
      return
    }
    const remaining = options.deadline === undefined ? Infinity : options.deadline - now()
    if (visibility.hidden && !Number.isFinite(remaining)) return
    timer = setTimer(
      () => {
        timer = undefined
        if (expired()) timeout()
        else if (!visibility.hidden) void run()
      },
      visibility.hidden ? remaining : Math.min(options.interval, remaining),
    )
  }
  async function run() {
    if (stopped || visibility.hidden) return
    if (expired()) {
      timeout()
      return
    }
    if (running) {
      resume = true
      return
    }
    clear()
    running = true
    attempts++
    controller = new AbortController()
    try {
      if ((await task(controller.signal)) === false) {
        stop()
        return
      }
    } catch (error) {
      if (!controller.signal.aborted) options.onError?.(error)
    } finally {
      running = false
      if (!stopped) {
        if (resume && !visibility.hidden) {
          resume = false
          void run()
        } else schedule()
      }
    }
  }
  function changed() {
    if (stopped) return
    clear()
    if (expired()) {
      timeout()
      return
    }
    if (visibility.hidden) {
      controller?.abort()
      schedule()
    } else if (running) resume = true
    else void run()
  }
  visibility.addEventListener('visibilitychange', changed)
  if (options.immediate && !visibility.hidden) void run()
  else schedule()
  return stop
}
