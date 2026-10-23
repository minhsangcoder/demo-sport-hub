'use client'
import { useAuth } from '@/lib/auth-context'
import { useRouter } from 'next/navigation'
import { useEffect } from 'react'
export function HomeRoleRedirect() {
  const router = useRouter()
  const { user, ready } = useAuth()

  useEffect(() => {
    if (
      ready &&
      user &&
      user.roles.includes('STAFF') &&
      !user.roles.includes('OWNER') &&
      !user.roles.includes('ADMIN')
    ) {
      router.replace('/staff/bookings')
    }
  }, [user, ready, router])

  return null
}
