'use client'

export type AdminAccount = {
  id: string
  fullName: string
  email: string | null
  phone: string | null
  emailVerified: boolean
  phoneVerified: boolean
  roles: string[]
  status: string
  createdAt: string
  lastLoginAt: string | null
  lockedAt: string | null
  lockedUntil: string | null
  lockReason: string | null
}
