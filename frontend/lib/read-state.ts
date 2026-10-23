// Browser-memory generations only. Responses, tokens and profiles are never persisted here.
let identity = ''
let session = 0
let revision = 0
const paths = new Map<string, number>()
const listeners = new Set<() => void>()
const publicCatalogs = new Map([['/sport-categories', 60_000]])

export function catalogTtl(path: string) {
  return publicCatalogs.get(path) ?? 0
}
export function readSession(path: string | null) {
  return path && catalogTtl(path) ? 'public' : String(session)
}
export function readVersion(path: string | null) {
  if (!path) return ''
  return `${readSession(path)}:${revision}:${paths.get(path) ?? 0}`
}
export function subscribeReads(listener: () => void) {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}
function notify() {
  for (const listener of listeners) listener()
}
export function setReadIdentity(next: string) {
  if (next === identity) return
  identity = next
  session++
  notify()
}
export function invalidateReads(path?: string) {
  if (path) paths.set(path, (paths.get(path) ?? 0) + 1)
  else {
    revision++
    paths.clear()
  }
  notify()
}
