import test from 'node:test'
import assert from 'node:assert/strict'
import {
  getHiddenGroupIds,
  hideGroup,
  unhideGroup,
  isGroupHidden,
} from '../features/groups/hidden-groups.ts'

// Mock localStorage for node test runner
const mockStore = new Map()
global.localStorage = {
  getItem: (key) => mockStore.get(key) || null,
  setItem: (key, val) => mockStore.set(key, String(val)),
  removeItem: (key) => mockStore.delete(key),
  clear: () => mockStore.clear(),
}
global.window = {
  dispatchEvent: () => true,
}

test('hidden-groups management works correctly', () => {
  mockStore.clear()
  const userId = 'user-123'
  const groupA = 'group-aaa'
  const groupB = 'group-bbb'

  // Initially empty
  assert.deepEqual(getHiddenGroupIds(userId), [])
  assert.equal(isGroupHidden(groupA, userId), false)

  // Hide group A
  hideGroup(groupA, userId)
  assert.deepEqual(getHiddenGroupIds(userId), [groupA])
  assert.equal(isGroupHidden(groupA, userId), true)

  // Hide group B
  hideGroup(groupB, userId)
  assert.deepEqual(getHiddenGroupIds(userId), [groupA, groupB])
  assert.equal(isGroupHidden(groupB, userId), true)

  // Duplicate hide is idempotent
  hideGroup(groupA, userId)
  assert.deepEqual(getHiddenGroupIds(userId), [groupA, groupB])

  // Different user has isolated storage
  const userOther = 'user-999'
  assert.deepEqual(getHiddenGroupIds(userOther), [])
  assert.equal(isGroupHidden(groupA, userOther), false)

  // Unhide group A
  unhideGroup(groupA, userId)
  assert.deepEqual(getHiddenGroupIds(userId), [groupB])
  assert.equal(isGroupHidden(groupA, userId), false)
  assert.equal(isGroupHidden(groupB, userId), true)

  // Unhide group B
  unhideGroup(groupB, userId)
  assert.deepEqual(getHiddenGroupIds(userId), [])
  assert.equal(isGroupHidden(groupB, userId), false)
})
