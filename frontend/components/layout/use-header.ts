'use client'
import { authApi } from '@/lib/api'
import { useAuth } from '@/lib/auth-context'
import { usePathname, useRouter } from 'next/navigation'
import { useEffect, useRef, useState } from 'react'
export function useHeader() {
  const { user, ready } = useAuth()
  const router = useRouter()
  const pathname = usePathname()
  const isStaffOnly = Boolean(
    user &&
    user.roles.includes('STAFF') &&
    !user.roles.includes('OWNER') &&
    !user.roles.includes('ADMIN'),
  )
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [userMenuOpen, setUserMenuOpen] = useState(false)
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)
  const userMenuRef = useRef<HTMLDivElement>(null)

  // Close menus on path change or click outside
  const [menuPath, setMenuPath] = useState(pathname)
  if (menuPath !== pathname) {
    setMenuPath(pathname)
    setUserMenuOpen(false)
    setMobileMenuOpen(false)
  }

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (userMenuRef.current && !userMenuRef.current.contains(event.target as Node)) {
        setUserMenuOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  async function logout() {
    setBusy(true)
    setError('')
    try {
      await authApi.logout()
      router.push('/login')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không thể đăng xuất')
    } finally {
      setBusy(false)
    }
  }

  // Active check helper
  const isActive = (path: string) => {
    if (path === '/') return pathname === '/'
    return pathname.startsWith(path)
  }

  return {
    pathname,
    user,
    ready,
    isStaffOnly,
    busy,
    error,
    userMenuOpen,
    setUserMenuOpen,
    mobileMenuOpen,
    setMobileMenuOpen,
    userMenuRef,
    logout,
    isActive,
  }
}
export type HeaderModel = ReturnType<typeof useHeader>
