'use client'
import { Challenge, Channel, labels } from '@/features/profile/use-contact-shared'
import { ApiError, api, authApi, type UserProfile } from '@/lib/api'
import { useAuth } from '@/lib/auth-context'
import { useApi } from '@/lib/use-api'
import { useEffect, useState } from 'react'
export function useContact() {
  const { user, ready } = useAuth()
  const resource = useApi<Challenge[]>(ready && user ? '/users/me/contact-challenges' : null)
  const challenges = resource.data ?? []
  const [retrying, setLoading] = useState(false)
  const loading = resource.loading || retrying
  const [busy, setBusy] = useState<Channel | null>(null)
  const [actionError, setError] = useState('')
  const error = actionError || resource.error
  const [message, setMessage] = useState('')
  const [now, setNow] = useState(0)

  const load = resource.reload
  const [previousUser, setPreviousUser] = useState(user?.id)
  if (previousUser !== user?.id) {
    setPreviousUser(user?.id)
    setError('')
    setMessage('')
    setBusy(null)
  }

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(timer)
  }, [])

  async function perform(channel: Channel, operation: () => Promise<void>) {
    if (busy) return
    setBusy(channel)
    setError('')
    setMessage('')
    try {
      await operation()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Không thể cập nhật thông tin liên hệ.')
      await load().catch(() => {})
    } finally {
      setBusy(null)
    }
  }

  function requestChange(event: React.FormEvent<HTMLFormElement>, channel: Channel) {
    event.preventDefault()
    const recipient = String(new FormData(event.currentTarget).get('recipient') ?? '').trim()
    if (recipient === (channel === 'EMAIL' ? user?.email : user?.phone)) {
      setError('Thông tin mới phải khác thông tin đang sử dụng.')
      return
    }
    void perform(channel, async () => {
      await api('/users/me', {
        method: 'PATCH',
        body: JSON.stringify({ [channel === 'EMAIL' ? 'email' : 'phone']: recipient }),
      })
      await load()
      setMessage(
        `Đã gửi mã xác minh đến ${recipient}. Thông tin hiện tại vẫn hoạt động bình thường đến khi bạn hoàn tất xác thực OTP.`,
      )
    })
  }

  function verify(event: React.FormEvent<HTMLFormElement>, challenge: Challenge) {
    event.preventDefault()
    const code = String(new FormData(event.currentTarget).get('code') ?? '')
    void perform(challenge.channel, async () => {
      await authApi.verify({ challengeId: challenge.id, code })
      const updated = await api<UserProfile>('/users/me')
      window.dispatchEvent(new CustomEvent('sporthub-session', { detail: updated }))
      await load()
      setMessage(`${labels[challenge.channel]} của bạn đã được xác minh và cập nhật thành công!`)
    })
  }

  function resend(challenge: Challenge) {
    void perform(challenge.channel, async () => {
      await api(`/users/me/contact-challenges/${challenge.channel}/resend`, { method: 'POST' })
      await load()
      setMessage('Đã gửi mã OTP mới. Vui lòng kiểm tra hộp thư hoặc tin nhắn.')
    })
  }

  return {
    user,
    ready,
    challenges,
    loading,
    setLoading,
    busy,
    setBusy,
    error,
    setError,
    message,
    setMessage,
    now,
    setNow,
    load,
    perform,
    requestChange,
    verify,
    resend,
  }
}
