const nextKey = 'sporthub-google-next'
const fallbackPath = '/'

export function postLoginPath(value: string | null): string {
  if (!value?.startsWith('/')) return fallbackPath
  const base = 'https://sporthub.local'
  try {
    const target = new URL(value, base)
    if (target.origin !== base || target.pathname.startsWith('//')) return fallbackPath
    return `${target.pathname}${target.search}${target.hash}`
  } catch {
    return fallbackPath
  }
}

export function rememberGoogleNext(value: string | null) {
  try {
    window.sessionStorage.setItem(nextKey, postLoginPath(value))
  } catch {
    // Google sign-in can continue when browser storage is unavailable.
  }
}

export function consumeGoogleNext(): string {
  try {
    const next = window.sessionStorage.getItem(nextKey)
    window.sessionStorage.removeItem(nextKey)
    return postLoginPath(next)
  } catch {
    return fallbackPath
  }
}
