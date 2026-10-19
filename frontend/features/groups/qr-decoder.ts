import jsQR from 'jsqr'

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
      if (parsed.checkinToken && typeof parsed.checkinToken === 'string') return parsed.checkinToken.trim()
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
          .join('')
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


/**
 * Decodes a QR code from a File or Blob (supports PNG, JPG, WEBP, SVG).
 */
export async function decodeQrFromImageFile(file: File | Blob): Promise<string> {
  // If native BarcodeDetector is supported, try it first
  if (typeof window !== 'undefined' && 'BarcodeDetector' in window) {
    try {
      // @ts-expect-error Native BarcodeDetector API in modern Chromium
      const detector = new window.BarcodeDetector({ formats: ['qr_code'] })
      const bitmap = await createImageBitmap(file)
      const barcodes = await detector.detect(bitmap)
      if (barcodes && barcodes.length > 0 && barcodes[0].rawValue) {
        return barcodes[0].rawValue
      }
    } catch {
      // Fallback to jsQR
    }
  }

  return new Promise((resolve, reject) => {
    const objectUrl = URL.createObjectURL(file)
    const img = new Image()

    img.onload = () => {
      try {
        const result = decodeQrFromImageElement(img)
        URL.revokeObjectURL(objectUrl)
        if (result) {
          resolve(result)
        } else {
          reject(new Error('Không tìm thấy mã QR trong ảnh. Hãy thử ảnh rõ nét hơn hoặc cắt bớt khoảng trống.'))
        }
      } catch (err) {
        URL.revokeObjectURL(objectUrl)
        reject(err instanceof Error ? err : new Error('Không thể phân tích ảnh mã QR.'))
      }
    }

    img.onerror = () => {
      URL.revokeObjectURL(objectUrl)
      reject(new Error('Không thể tải hoặc hiển thị file ảnh này.'))
    }

    img.src = objectUrl
  })
}

/**
 * Scans an HTMLImageElement using canvas and jsQR.
 * Handles both standard resolution and downscaled high-resolution photos.
 */
export function decodeQrFromImageElement(img: HTMLImageElement): string | null {
  const canvas = document.createElement('canvas')
  const ctx = canvas.getContext('2d', { willReadFrequently: true })
  if (!ctx) return null

  const naturalWidth = img.naturalWidth || img.width
  const naturalHeight = img.naturalHeight || img.height
  if (!naturalWidth || !naturalHeight) return null

  // Pass 1: Try natural dimensions
  canvas.width = naturalWidth
  canvas.height = naturalHeight
  ctx.drawImage(img, 0, 0, naturalWidth, naturalHeight)
  const imageData1 = ctx.getImageData(0, 0, naturalWidth, naturalHeight)
  const code1 = jsQR(imageData1.data, imageData1.width, imageData1.height, {
    inversionAttempts: 'attemptBoth',
  })
  if (code1 && code1.data) {
    return code1.data
  }

  // Pass 2: If image is high-resolution (e.g. > 1000px), resize down for better pattern recognition
  if (naturalWidth > 1000 || naturalHeight > 1000) {
    const maxDim = 800
    const scale = Math.min(maxDim / naturalWidth, maxDim / naturalHeight)
    const targetW = Math.round(naturalWidth * scale)
    const targetH = Math.round(naturalHeight * scale)

    canvas.width = targetW
    canvas.height = targetH
    ctx.drawImage(img, 0, 0, targetW, targetH)
    const imageData2 = ctx.getImageData(0, 0, targetW, targetH)
    const code2 = jsQR(imageData2.data, imageData2.width, imageData2.height, {
      inversionAttempts: 'attemptBoth',
    })
    if (code2 && code2.data) {
      return code2.data
    }
  }

  return null
}

/**
 * Scans a single frame from a live HTMLVideoElement into a canvas.
 */
export function scanVideoFrame(
  video: HTMLVideoElement,
  canvas: HTMLCanvasElement
): string | null {
  if (video.readyState < HTMLMediaElement.HAVE_CURRENT_DATA) {
    return null
  }

  const width = video.videoWidth
  const height = video.videoHeight
  if (!width || !height) return null

  if (canvas.width !== width || canvas.height !== height) {
    canvas.width = width
    canvas.height = height
  }

  const ctx = canvas.getContext('2d', { willReadFrequently: true })
  if (!ctx) return null

  ctx.drawImage(video, 0, 0, width, height)
  const imageData = ctx.getImageData(0, 0, width, height)
  const code = jsQR(imageData.data, imageData.width, imageData.height, {
    inversionAttempts: 'attemptBoth',
  })

  return code?.data || null
}
