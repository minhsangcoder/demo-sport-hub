'use client'
import { createContext, useContext, useEffect, useState } from 'react'
import { isCurrentSessionUser, refreshSession, type UserProfile } from './api'
import { setReadIdentity } from './read-state'
const AuthContext = createContext<{ user: UserProfile | null; ready: boolean }>({
  user: null,
  ready: false,
})
export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<UserProfile | null>(null)
  const [ready, setReady] = useState(false)
  useEffect(() => {
    let mounted = true
    const onSession = (event: Event) => {
      const updated = (event as CustomEvent<UserProfile | null>).detail
      if (mounted && isCurrentSessionUser(updated?.id ?? null)) {
        setReadIdentity(
          updated ? JSON.stringify({ ...updated, roles: [...updated.roles].sort() }) : '',
        )
        setUser(updated)
      }
    }
    window.addEventListener('sporthub-session', onSession)
    void refreshSession()
      .then((session) => {
        if (mounted) setUser(session.user)
      })
      .catch(() => {})
      .finally(() => {
        if (mounted) setReady(true)
      })
    return () => {
      mounted = false
      window.removeEventListener('sporthub-session', onSession)
    }
  }, [])
  return <AuthContext.Provider value={{ user, ready }}>{children}</AuthContext.Provider>
}
export function useAuth() {
  return useContext(AuthContext)
}
