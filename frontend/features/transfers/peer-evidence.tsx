'use client'
import { useRef, useState } from 'react'
import { api, ApiError } from '@/lib/api'
import { useApi } from '@/lib/use-api'
import type { PeerEvidence } from './peer-types'
import { EvidencePreview } from './private-image'

export function PeerEvidencePicker({
  acquisitionId,
  userId,
  value,
  onChange,
  disabled,
}: {
  acquisitionId: string
  userId: string
  value: string[]
  onChange: (ids: string[]) => void
  disabled?: boolean
}) {
  const path = `/transfers/acquisitions/${acquisitionId}/evidence`
  const read = useApi<PeerEvidence[]>(path)
  const keys = useRef(new Map<string, string>())
  const running = useRef(false)
  const [busy, setBusy] = useState(false)
  const [blocked, setBlocked] = useState(false)
  const [error, setError] = useState('')
  async function upload(file: File) {
    if (running.current || disabled || blocked) return
    running.current = true
    setBusy(true)
    setError('')
    try {
      if (
        !['image/jpeg', 'image/png'].includes(file.type) ||
        !file.size ||
        file.size > 5 * 1024 * 1024
      )
        throw new Error('Chỉ nhận ảnh JPEG/PNG, tối đa 5 MiB mỗi ảnh.')
      const digest = Array.from(
        new Uint8Array(await crypto.subtle.digest('SHA-256', await file.arrayBuffer())),
        (n) => n.toString(16).padStart(2, '0'),
      ).join('')
      if (!keys.current.has(digest)) keys.current.set(digest, crypto.randomUUID())
      const body = new FormData()
      body.set('file', file)
      const uploaded = await api<PeerEvidence>(path, {
        method: 'POST',
        headers: { 'Idempotency-Key': keys.current.get(digest)! },
        body,
      })
      if (uploaded.state !== 'READY')
        throw new Error('Ảnh chưa sẵn sàng, hãy thử tải lại đúng ảnh.')
      if (value.length < 3 && !value.includes(uploaded.id)) onChange([...value, uploaded.id])
      void read.reload()
    } catch (failure) {
      if (failure instanceof ApiError && [401, 403].includes(failure.status)) setBlocked(true)
      setError(
        failure instanceof Error
          ? failure.message
          : 'Chưa tải được ảnh. Thử lại cùng ảnh để đối chiếu.',
      )
    } finally {
      running.current = false
      setBusy(false)
    }
  }
  return (
    <fieldset disabled={busy || disabled || blocked} className="space-y-3 rounded-lg border p-3">
      <legend className="px-1 text-sm font-semibold">Chọn 1–3 ảnh chứng từ của bạn</legend>
      <label className="grid gap-2 text-sm">
        Tải ảnh mới (tối đa 10 ảnh/giờ)
        <input
          type="file"
          accept="image/png,image/jpeg"
          onChange={(event) => {
            const file = event.target.files?.[0]
            if (file) void upload(file)
            event.target.value = ''
          }}
        />
      </label>
      <p className="text-xs text-slate-600">
        Ảnh chỉ cho hai bên và Owner xử lý hồ sơ xem; không đăng công khai. Ảnh đã nộp được giữ
        nguyên trong lịch sử.
      </p>
      <div className="grid gap-3 sm:grid-cols-3">
        {read.data
          ?.filter((item) => item.uploaderId === userId && item.state === 'READY')
          .map((item) => (
            <label key={item.id} className="space-y-2 rounded border p-2 text-xs">
              <input
                type="checkbox"
                checked={value.includes(item.id)}
                disabled={!value.includes(item.id) && value.length >= 3}
                onChange={(event) =>
                  onChange(
                    event.target.checked
                      ? [...value, item.id]
                      : value.filter((id) => id !== item.id),
                  )
                }
              />{' '}
              Chọn ảnh {item.id.slice(0, 6)}
              <EvidencePreview id={item.id} />
            </label>
          ))}
      </div>
      {busy && <p role="status">Đang kiểm tra và tải ảnh…</p>}
      {(error || read.error) && (
        <p role="alert" className="text-sm text-red-700">
          {error || read.error}
        </p>
      )}
      <button type="button" onClick={read.reload} className="text-sm underline">
        Tải lại danh sách ảnh
      </button>
    </fieldset>
  )
}
