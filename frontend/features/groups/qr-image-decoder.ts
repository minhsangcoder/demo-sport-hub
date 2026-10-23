import jsQR from 'jsqr'

/**
 * Decodes a QR code from a File or Blob (supports PNG, JPG, WEBP, SVG).
 */
export async function decodeQrFromImageFile(
  file: File | Blob,
  signal?: AbortSignal,
): Promise<string> {
  signal?.throwIfAborted()
  type Detector = new (options: { formats: string[] }) => {
    detect(bitmap: ImageBitmap): Promise<{ rawValue?: string }[]>
  }
  const NativeDetector =
    typeof window === 'undefined'
      ? undefined
      : (window as unknown as { BarcodeDetector?: Detector }).BarcodeDetector
  if (NativeDetector) {
    try {
      const bitmap = await createImageBitmap(file)
      try {
        signal?.throwIfAborted()
        const codes = await new NativeDetector({ formats: ['qr_code'] }).detect(bitmap)
        signal?.throwIfAborted()
        if (codes[0]?.rawValue) return codes[0].rawValue
      } finally {
        bitmap.close()
      }
    } catch (error) {
      if (signal?.aborted) throw error
    }
  }
  signal?.throwIfAborted()
  return new Promise((resolve, reject) => {
    const objectUrl = URL.createObjectURL(file)
    const image = new Image()
    function cleanup() {
      image.onload = null
      image.onerror = null
      signal?.removeEventListener('abort', abort)
      URL.revokeObjectURL(objectUrl)
    }
    function abort() {
      cleanup()
      image.src = ''
      reject(signal?.reason ?? new DOMException('Đã hủy đọc ảnh.', 'AbortError'))
    }
    signal?.addEventListener('abort', abort, { once: true })
    image.onload = () => {
      try {
        signal?.throwIfAborted()
        const value = decodeQrFromImageElement(image)
        if (value) resolve(value)
        else
          reject(
            new Error(
              'Không tìm thấy mã QR trong ảnh. Hãy thử ảnh rõ nét hơn hoặc cắt bớt khoảng trống.',
            ),
          )
      } catch (error) {
        reject(error instanceof Error ? error : new Error('Không thể phân tích ảnh mã QR.'))
      } finally {
        cleanup()
      }
    }
    image.onerror = () => {
      cleanup()
      reject(new Error('Không thể tải hoặc hiển thị tệp ảnh này.'))
    }
    if (signal?.aborted) abort()
    else image.src = objectUrl
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
export function scanVideoFrame(video: HTMLVideoElement, canvas: HTMLCanvasElement): string | null {
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
