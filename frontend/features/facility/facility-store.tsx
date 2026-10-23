'use client'
import { createContext, useContext } from 'react'
import type { FacilityProfile } from './types'
export const FacilityContext = createContext<{
  facility: FacilityProfile
  reload: () => void
  basePath: string
  editable: boolean
  applicationId?: string
} | null>(null)
export function FacilityWorkspace({
  facility,
  reload,
  basePath,
  children,
}: {
  facility: FacilityProfile
  reload: () => void
  basePath: string
  children: React.ReactNode
}) {
  return (
    <FacilityContext.Provider value={{ facility, reload, basePath, editable: true }}>
      {children}
    </FacilityContext.Provider>
  )
}
export function useFacility() {
  const value = useContext(FacilityContext)
  if (!value) throw new Error('Facility context is required')
  return value
}
