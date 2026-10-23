'use client'

import { useCallback, useSyncExternalStore } from 'react'

const STORAGE_PREFIX = 'sporthub_hidden_groups_'

function getStorageKey(userId?: string): string {
  return `${STORAGE_PREFIX}${userId || 'anonymous'}`
}

/**
 * Returns list of hidden group IDs for the current user from localStorage.
 */
export function getHiddenGroupIds(userId?: string): string[] {
  if (typeof window === 'undefined') return []
  try {
    const raw = localStorage.getItem(getStorageKey(userId))
    if (!raw) return []
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

/**
 * Hides/dismisses a group from the personal view.
 */
export function hideGroup(groupId: string, userId?: string): void {
  if (typeof window === 'undefined') return
  try {
    const key = getStorageKey(userId)
    const current = getHiddenGroupIds(userId)
    if (!current.includes(groupId)) {
      const updated = [...current, groupId]
      localStorage.setItem(key, JSON.stringify(updated))
      window.dispatchEvent(new Event('sporthub_hidden_groups_changed'))
    }
  } catch (err) {
    console.error('Failed to hide group:', err)
  }
}

/**
 * Restores a previously hidden group back to the personal view.
 */
export function unhideGroup(groupId: string, userId?: string): void {
  if (typeof window === 'undefined') return
  try {
    const key = getStorageKey(userId)
    const current = getHiddenGroupIds(userId)
    const updated = current.filter((id) => id !== groupId)
    localStorage.setItem(key, JSON.stringify(updated))
    window.dispatchEvent(new Event('sporthub_hidden_groups_changed'))
  } catch (err) {
    console.error('Failed to unhide group:', err)
  }
}

/**
 * Checks if a group is hidden.
 */
export function isGroupHidden(groupId: string, userId?: string): boolean {
  return getHiddenGroupIds(userId).includes(groupId)
}

/**
 * React hook to access and manage hidden groups state with real-time sync.
 */
function subscribe(listener: () => void) {
  window.addEventListener('storage', listener)
  window.addEventListener('sporthub_hidden_groups_changed', listener)
  return () => {
    window.removeEventListener('storage', listener)
    window.removeEventListener('sporthub_hidden_groups_changed', listener)
  }
}
const serverSnapshot = () => ''
export function useHiddenGroups(userId?: string) {
  const snapshot = useCallback(() => {
    try {
      return localStorage.getItem(getStorageKey(userId)) ?? ''
    } catch {
      return ''
    }
  }, [userId])
  const raw = useSyncExternalStore(subscribe, snapshot, serverSnapshot)
  let hiddenIds: string[] = []
  try {
    const parsed = JSON.parse(raw || '[]')
    if (Array.isArray(parsed)) hiddenIds = parsed
  } catch {
    /* Invalid local preference behaves as an empty list. */
  }

  const hide = useCallback(
    (groupId: string) => {
      hideGroup(groupId, userId)
    },
    [userId],
  )

  const unhide = useCallback(
    (groupId: string) => {
      unhideGroup(groupId, userId)
    },
    [userId],
  )

  return {
    hiddenIds,
    hide,
    unhide,
    isGroupHidden: (groupId: string) => hiddenIds.includes(groupId),
  }
}
