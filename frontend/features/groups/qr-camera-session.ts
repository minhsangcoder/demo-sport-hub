type CameraState = {
  hasCamera: boolean | null
  isInitializing: boolean
  errorMessage: string | null
}
type Decoder = {
  scanVideoFrame: (video: HTMLVideoElement, canvas: HTMLCanvasElement) => string | null
}
type CameraEnvironment = {
  media?: Pick<MediaDevices, 'getUserMedia'>
  visibility: Pick<Document, 'hidden' | 'addEventListener' | 'removeEventListener'>
  requestFrame: typeof requestAnimationFrame
  cancelFrame: typeof cancelAnimationFrame
  loadDecoder: () => Promise<Decoder>
  vibrate?: () => void
}
const cameraError = (error: unknown) => {
  const name = (error as { name?: string })?.name
  if (name === 'NotAllowedError' || name === 'PermissionDeniedError')
    return 'Quyền truy cập camera bị từ chối. Vui lòng cấp quyền trong cài đặt trình duyệt hoặc chọn tải ảnh QR lên.'
  if (name === 'NotFoundError' || name === 'DevicesNotFoundError')
    return 'Không tìm thấy thiết bị camera trên máy của bạn.'
  return 'Không thể khởi động camera. Vui lòng thử tải ảnh mã QR lên.'
}

/** Owns every acquired track and RAF, including a getUserMedia that completes after close/retry. */
export function createQrCameraSession(
  video: HTMLVideoElement,
  canvas: HTMLCanvasElement,
  onScan: (value: string) => void,
  publish: (state: CameraState) => void,
  environment: CameraEnvironment = {
    media: navigator.mediaDevices,
    visibility: document,
    requestFrame: requestAnimationFrame,
    cancelFrame: cancelAnimationFrame,
    loadDecoder: () => import('./qr-image-decoder'),
    vibrate: () => navigator.vibrate?.(100),
  },
) {
  let active = true,
    generation = 0,
    initializing = false,
    resume = false
  let stream: MediaStream | null = null
  let frame: number | undefined
  const { visibility } = environment
  function release() {
    if (frame !== undefined) environment.cancelFrame(frame)
    frame = undefined
    stream?.getTracks().forEach((track) => track.stop())
    stream = null
    video.srcObject = null
  }
  function current(token: number) {
    return active && generation === token && !visibility.hidden
  }
  async function restart() {
    const token = ++generation
    release()
    if (!active) return
    if (visibility.hidden) {
      resume = true
      return
    }
    if (!environment.media?.getUserMedia) {
      publish({
        hasCamera: false,
        isInitializing: false,
        errorMessage:
          'Trình duyệt này không hỗ trợ truy cập camera hoặc đang ở kết nối không bảo mật (yêu cầu HTTPS/localhost).',
      })
      return
    }
    initializing = true
    publish({ hasCamera: null, isInitializing: true, errorMessage: null })
    const decoderFlight = environment.loadDecoder().then(
      (value) => ({ value, error: null }),
      (error) => ({ value: null, error }),
    )
    let acquired: MediaStream | undefined
    try {
      try {
        acquired = await environment.media.getUserMedia({
          video: { facingMode: { ideal: 'environment' } },
          audio: false,
        })
      } catch (error) {
        if (
          !current(token) ||
          ['NotAllowedError', 'PermissionDeniedError'].includes((error as { name: string }).name)
        )
          throw error
        acquired = await environment.media.getUserMedia({ video: true, audio: false })
      }
      if (!current(token)) {
        acquired.getTracks().forEach((track) => track.stop())
        return
      }
      stream = acquired
      const decoder = await decoderFlight
      if (decoder.error || !decoder.value) throw decoder.error
      if (!current(token)) return
      video.srcObject = acquired
      await video.play().catch(() => {})
      if (!current(token)) return
      initializing = false
      publish({ hasCamera: true, isInitializing: false, errorMessage: null })
      function scan() {
        if (!current(token) || stream !== acquired) return
        try {
          const detected = decoder.value!.scanVideoFrame(video, canvas)
          if (detected) {
            generation++
            release()
            environment.vibrate?.()
            onScan(detected)
            return
          }
          frame = environment.requestFrame(scan)
        } catch (error) {
          release()
          publish({ hasCamera: false, isInitializing: false, errorMessage: cameraError(error) })
        }
      }
      frame = environment.requestFrame(scan)
    } catch (error) {
      acquired?.getTracks().forEach((track) => track.stop())
      if (current(token)) {
        release()
        initializing = false
        publish({ hasCamera: false, isInitializing: false, errorMessage: cameraError(error) })
      }
    } finally {
      if (!current(token)) {
        acquired?.getTracks().forEach((track) => track.stop())
        if (stream === acquired) {
          stream = null
          video.srcObject = null
        }
      }
    }
  }
  function changed() {
    if (!active) return
    if (visibility.hidden) {
      resume = Boolean(stream || initializing)
      generation++
      initializing = false
      release()
    } else if (resume) {
      resume = false
      void restart()
    }
  }
  visibility.addEventListener('visibilitychange', changed)
  void restart()
  return {
    restart,
    stop() {
      active = false
      generation++
      release()
      visibility.removeEventListener('visibilitychange', changed)
    },
  }
}
