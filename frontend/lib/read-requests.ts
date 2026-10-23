import { api } from './api'
import { catalogTtl, readVersion } from './read-state'

type Entry = {
  controller: AbortController
  promise: Promise<unknown>
  consumers: number
  settled: boolean
  expires: number
}
function aborted() {
  const error = new Error('Request no longer has a consumer')
  error.name = 'AbortError'
  return error
}
export function createReadRequests(
  load: <T>(path: string, options: RequestInit) => Promise<T> = api,
  now: () => number = Date.now,
) {
  const entries = new Map<string, Entry>()
  function acquire<T>(path: string) {
    const version = readVersion(path)
    const key = `${version}|${path}`
    let entry = entries.get(key)
    if (entry && (entry.controller.signal.aborted || (entry.settled && entry.expires <= now()))) {
      entries.delete(key)
      entry = undefined
    }
    if (!entry) {
      const controller = new AbortController()
      const created: Entry = {
        controller,
        promise: Promise.resolve(),
        consumers: 0,
        settled: false,
        expires: 0,
      }
      created.promise = Promise.resolve()
        .then(() => {
          if (controller.signal.aborted) throw aborted()
          return load<T>(path, { signal: controller.signal })
        })
        .then((data) => {
          if (controller.signal.aborted || version !== readVersion(path)) throw aborted()
          created.expires = now() + catalogTtl(path)
          return data
        })
        .finally(() => {
          created.settled = true
          if (!created.expires || !catalogTtl(path) || version !== readVersion(path)) {
            if (entries.get(key) === created) entries.delete(key)
          } else {
            for (const [oldKey, old] of entries) {
              if (old !== created && old.settled && oldKey.endsWith(`|${path}`))
                entries.delete(oldKey)
            }
          }
        })
      entry = created
      entries.set(key, entry)
    }
    entry.consumers++
    let released = false
    const selected = entry
    return {
      promise: selected.promise as Promise<T>,
      release() {
        if (released) return
        released = true
        selected.consumers--
        // Strict Mode can retain this request during cleanup/re-subscribe in the same turn.
        queueMicrotask(() => {
          if (!selected.consumers && !selected.settled) {
            selected.controller.abort()
            if (entries.get(key) === selected) entries.delete(key)
          }
        })
      },
    }
  }
  return { acquire }
}
export const readRequests = createReadRequests()
