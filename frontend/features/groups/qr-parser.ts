/**
 * Extracts the invite code whether the input is a full URL, a relative path,
 * or the raw code string itself.
 *
 * Examples:
 * - "http://localhost:3001/customer/groups/join?code=ABC123XYZ" -> "ABC123XYZ"
 * - "/customer/groups/join?code=ABC123XYZ" -> "ABC123XYZ"
 * - "sporthub.vn/customer/groups/join?code=ABC123XYZ" -> "ABC123XYZ"
 * - "ABC123XYZ" -> "ABC123XYZ"
 */
export function extractInviteCode(rawContent: string): string {
  const trimmed = rawContent.trim()
  if (!trimmed) return ''

  // 1. Try URL parsing if it looks like an absolute or relative URL
  try {
    const urlString = trimmed.startsWith('/')
      ? `http://sporthub.internal${trimmed}`
      : trimmed.startsWith('http://') || trimmed.startsWith('https://')
        ? trimmed
        : `http://${trimmed}`

    const parsed = new URL(urlString)
    const codeParam = parsed.searchParams.get('code')
    if (codeParam && codeParam.trim()) {
      return codeParam.trim()
    }
  } catch {
    // Ignore and fall through to regex
  }

  // 2. Regex fallback for URL parameters
  const match = trimmed.match(/[?&]code=([^&#\s]+)/)
  if (match && match[1]) {
    try {
      return decodeURIComponent(match[1]).trim()
    } catch {
      return match[1].trim()
    }
  }

  // 3. Raw invite code string
  return trimmed
}

/**
 * Extracts a check-in token whether the QR content is a direct token,
 * a full or relative URL with `token` query param, or a JSON payload.
 */
export function extractCheckinToken(rawContent: string): string {
  const trimmed = rawContent.trim()
  if (!trimmed) return ''

  // 1. JSON string fallback
  if (trimmed.startsWith('{') && trimmed.endsWith('}')) {
    try {
      const parsed = JSON.parse(trimmed)
      if (parsed.token && typeof parsed.token === 'string') return parsed.token.trim()
      if (parsed.checkinToken && typeof parsed.checkinToken === 'string')
        return parsed.checkinToken.trim()
    } catch {
      // not JSON
    }
  }

  // 2. Try URL parsing if it looks like an absolute or relative URL
  try {
    const urlString = trimmed.startsWith('/')
      ? `http://sporthub.internal${trimmed}`
      : trimmed.startsWith('http://') || trimmed.startsWith('https://')
        ? trimmed
        : `http://${trimmed}`

    const parsed = new URL(urlString)
    const tokenParam = parsed.searchParams.get('token')
    if (tokenParam && tokenParam.trim()) {
      return tokenParam.trim()
    }
  } catch {
    // Ignore and fall through to regex
  }

  // 3. Regex fallback for URL parameters
  const match = trimmed.match(/[?&]token=([^&#\s]+)/)
  if (match && match[1]) {
    try {
      return decodeURIComponent(match[1]).trim()
    } catch {
      return match[1].trim()
    }
  }

  // 4. Raw token string
  return trimmed
}

/**
 * Extracts the booking ID (UUID) from a check-in token if present.
 * Booking tokens have the structure: Base64Url(bookingId|currentHolderId|tokenVersion|expires).signature
 */
export function extractBookingIdFromToken(token: string): string | null {
  try {
    const cleanToken = extractCheckinToken(token)
    const parts = cleanToken.split('.')
    if (!parts[0]) return null

    let base64 = parts[0].replace(/-/g, '+').replace(/_/g, '/')
    while (base64.length % 4) {
      base64 += '='
    }

    let decoded = ''
    if (typeof Buffer !== 'undefined') {
      decoded = Buffer.from(base64, 'base64').toString('utf-8')
    } else if (typeof atob !== 'undefined') {
      decoded = decodeURIComponent(
        atob(base64)
          .split('')
          .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
          .join(''),
      )
    }

    const fields = decoded.split('|')
    if (fields[0] && /^[0-9a-fA-F-]{36}$/.test(fields[0])) {
      return fields[0].toLowerCase()
    }
    return null
  } catch {
    return null
  }
}
