// Browser-memory generations only. Responses, tokens and profiles are never persisted here.
let identity = ''
let session = 0
let revision = 0
const paths = new Map<string, number>()
const resources = new Map<string, number>()
const listeners = new Set<() => void>()
const publicCatalogs = new Map([['/sport-categories', 60_000]])
const sessionCatalogs = new Map([
  ['/payments/banks', 60_000],
  ['/payments/runtime', 5_000],
])
// These GET handlers take no caller and return the same public data before/after login.
function publicRead(path: string) {
  return (
    publicCatalogs.has(path) ||
    /^\/bookings\/(?:search|availability)(?:\?|$)/.test(path) ||
    /^\/facilities\/[0-9a-f-]{36}(?:\/(?:courts|court-types|images|refund-policy))?(?:\?|$)/i.test(
      path,
    )
  )
}

export function catalogTtl(path: string) {
  return publicCatalogs.get(path) ?? sessionCatalogs.get(path) ?? 0
}
export function readSession(path: string | null) {
  return path && publicRead(path) ? 'public' : String(session)
}
export function readVersion(path: string | null) {
  if (!path) return ''
  const related = [...resources]
    .filter(
      ([prefix]) =>
        path === prefix || path.startsWith(prefix + '/') || path.startsWith(prefix + '?'),
    )
    .map(([prefix, value]) => `${prefix}=${value}`)
    .join('|')
  return `${readSession(path)}:${revision}:${paths.get(path) ?? 0}:${related}`
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
  resources.clear()
  paths.clear()
  notify()
}
export function invalidateReads(path?: string) {
  if (path) paths.set(path, (paths.get(path) ?? 0) + 1)
  else {
    revision++
    paths.clear()
    resources.clear()
  }
  notify()
}
export function invalidateResources(prefixes: string[] | null) {
  if (prefixes === null || resources.size + prefixes.length > 256) {
    invalidateReads()
    return
  }
  for (const prefix of prefixes) resources.set(prefix, (resources.get(prefix) ?? 0) + 1)
  if (prefixes.length) notify()
}
