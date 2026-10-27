'use client'
import { useEffect, useRef, useState } from 'react'
import { api, ApiError } from '@/lib/api'
import { usePolling } from '@/lib/use-polling'
import { Button } from '@/components/ui/button'
import { usePeerCommand } from './use-peer-command'
import type { PeerMessage } from './peer-types'

export function PeerChat({ id, userId }: { id: string; userId: string }) {
  const [messages, setMessages] = useState<PeerMessage[]>([])
  const cursor = useRef<number | null>(null)
  const reading = useRef(false)
  const manual = useRef<AbortController | null>(null)
  const [text, setText] = useState('')
  const [error, setError] = useState('')
  const [stopped, setStopped] = useState(false)
  const [denied, setDenied] = useState(false)
  const [older, setOlder] = useState(false)
  const [more, setMore] = useState(false)
  const [loadingOlder, setLoadingOlder] = useState(false)
  const [until] = useState(() => Date.now() + 30 * 60_000)
  const command = usePeerCommand()
  const path = `/transfers/conversations/${id}/messages`
  useEffect(() => () => manual.current?.abort(), [])
  async function read(signal: AbortSignal, before?: number) {
    if (reading.current || denied) return
    reading.current = true
    const initial = cursor.current === null && before === undefined
    if (before !== undefined) setLoadingOlder(true)
    try {
      const query =
        before !== undefined
          ? `before=${before}&size=50`
          : initial
            ? 'size=50'
            : `after=${cursor.current}&size=50`
      const rows = await api<PeerMessage[]>(`${path}?${query}`, { signal })
      if (signal.aborted) return
      if (before === undefined) {
        cursor.current = Math.max(cursor.current ?? 0, ...rows.map((row) => row.cursor))
        setMore(!initial && rows.length === 50)
      }
      if (initial || before !== undefined) setOlder(rows.length === 50)
      if (rows.length) {
        setMessages((old) =>
          [...new Map([...old, ...rows].map((row) => [row.id, row])).values()]
            .sort((a, b) => a.cursor - b.cursor)
            .slice(-500),
        )
      }
      setError('')
    } catch (failure) {
      if (signal.aborted) return
      setError(failure instanceof Error ? failure.message : 'Chưa tải được tin nhắn.')
      if (failure instanceof ApiError && [401, 403].includes(failure.status)) {
        setStopped(true)
        setDenied(true)
        return false
      }
    } finally {
      reading.current = false
      if (!signal.aborted) setLoadingOlder(false)
    }
  }
  function loadManually(before?: number) {
    if (reading.current || denied) return
    manual.current?.abort()
    manual.current = new AbortController()
    void read(manual.current.signal, before)
  }
  usePolling(read, {
    enabled: !stopped,
    scope: `${userId}:${id}`,
    immediate: true,
    interval: 5000,
    deadline: until,
    onTimeout: () => setStopped(true),
  })
  return (
    <section className="space-y-3 rounded-xl border p-4">
      <h2 className="text-lg font-bold">
        Trao đổi riêng với{' '}
        {messages.some((row) => row.senderId !== userId) ? 'đối phương' : 'bên còn lại'}
      </h2>
      <p className="text-xs text-slate-600">
        Phòng chat còn sau khi lượt giữ kết thúc. Nhắn tin không kéo dài thời gian giữ sân. Chỉ hiển
        thị tối đa 500 tin gần nhất đã tải.
      </p>
      <ol aria-label="Tin nhắn" aria-live="polite" className="max-h-80 space-y-3 overflow-y-auto">
        {messages.map((row) => (
          <li
            key={row.id}
            className={`rounded-lg p-3 ${row.senderId === userId ? 'bg-emerald-50' : 'bg-slate-100'}`}
          >
            <strong className="text-xs">{row.senderId === userId ? 'Bạn' : 'Đối phương'}</strong>
            <p className="whitespace-pre-wrap break-words">{row.text}</p>
            <time className="text-xs text-slate-500">
              {new Date(row.createdAt).toLocaleString('vi-VN')}
            </time>
          </li>
        ))}
      </ol>
      {messages.length === 0 && <p className="text-sm text-slate-500">Chưa có tin nhắn.</p>}
      {older && messages.length > 0 && messages.length < 500 && (
        <Button
          variant="outline"
          type="button"
          onClick={() => loadManually(messages[0].cursor)}
          disabled={command.blocked || denied || loadingOlder}
        >
          {loadingOlder ? 'Đang tải tin cũ…' : 'Tải tin nhắn cũ hơn'}
        </Button>
      )}
      {(more || stopped) && (
        <Button
          variant="outline"
          type="button"
          onClick={() => loadManually()}
          disabled={command.blocked || denied}
        >
          Tải tin nhắn mới
        </Button>
      )}
      <form
        className="space-y-2"
        onSubmit={(event) => {
          event.preventDefault()
          void command.run<PeerMessage>(path, { text }, (row) => {
            setText('')
            setMessages((old) =>
              [...new Map([...old, row].map((value) => [value.id, value])).values()]
                .sort((a, b) => a.cursor - b.cursor)
                .slice(-500),
            )
          })
        }}
      >
        <label className="grid gap-2 text-sm">
          Tin nhắn (tối đa 10 tin/phút)
          <textarea
            value={text}
            onChange={(event) => setText(event.target.value)}
            required
            maxLength={2000}
            rows={3}
            className="rounded-lg border p-3"
            disabled={command.busy || command.blocked || denied}
          />
        </label>
        <Button type="submit" disabled={command.busy || command.blocked || denied || !text.trim()}>
          Gửi tin nhắn
        </Button>
      </form>
      {(error || command.error) && (
        <p role="alert" className="text-sm text-red-700">
          {error || command.error}
        </p>
      )}
    </section>
  )
}
