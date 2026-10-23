'use client'
import { useMutation } from '@/features/integration/shared'
import { useAuth } from '@/lib/auth-context'
import { type ChatMessage, type PlayGroup } from '@/lib/business-api'
import { useApi } from '@/lib/use-api'
import { usePolling } from '@/lib/use-polling'
import { useRouter } from 'next/navigation'
import { useState } from 'react'
export function usePlayWorkspace({
  root: p,
  reload,
  completed,
  terminal,
}: {
  root: PlayGroup
  reload: () => void
  completed: boolean
  terminal: boolean
}) {
  const { user } = useAuth(),
    owner = p.owner_id === user?.id,
    r = useApi<ChatMessage[]>(`/groups/play-groups/${p.id}/messages?size=50`),
    a = useMutation(),
    [text, setText] = useState(''),
    [messageKey, setMessageKey] = useState(() => crypto.randomUUID()),
    [reuseKey, setReuseKey] = useState(() => crypto.randomUUID()),
    [older, setOlder] = useState<ChatMessage[]>([]),
    router = useRouter()
  usePolling(r.reload, { interval: 10000, scope: p.id })
  const messages = [...(r.data ?? []), ...older]
    .filter((m, i, all) => all.findIndex((x) => x.id === m.id) === i)
    .sort((x, y) => x.id - y.id)

  return {
    p,
    reload,
    completed,
    terminal,
    user,
    owner,
    r,
    a,
    text,
    setText,
    messageKey,
    setMessageKey,
    reuseKey,
    setReuseKey,
    older,
    setOlder,
    router,
    messages,
  }
}
