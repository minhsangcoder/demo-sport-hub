import assert from 'node:assert/strict'
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const root = fileURLToPath(new URL('../../', import.meta.url))
const before = process.argv[2] ?? 'tmp/backend-report-before/booking-service/target/report-compatibility.json'
const after = process.argv[3] ?? 'tmp/backend-loading-refactor/booking-service/target/report-compatibility.json'
function normalize(value, key = '') {
  if (typeof value === 'string') return value.replace(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/gi, '<fixture-id>')
  if (Array.isArray(value)) {
    const items = value.map(item => normalize(item))
    // UUIDs are random in the shared business fixture. Compare equal-name courts and all pages as multisets.
    return ['courts', 'items'].includes(key) ? items.sort((a, b) => JSON.stringify(a).localeCompare(JSON.stringify(b))) : items
  }
  if (value && typeof value === 'object') return Object.fromEntries(Object.entries(value).map(([name, child]) => [name, normalize(child, name)]))
  return value
}
const original = normalize(JSON.parse(readFileSync(path.join(root, before), 'utf8')))
const current = normalize(JSON.parse(readFileSync(path.join(root, after), 'utf8')))
assert.deepEqual(current, original, 'Report DTOs, exact decimal strings, availability, usage, obligations and all transaction pages must stay equal')
const evidence = { cases: Object.keys(original), equal: true, normalized: ['random fixture UUIDs', 'transaction item order after flattening all individually disjoint pages', 'equal-name court order after removing random UUIDs'], timestampsRemoved: [], before, after }
mkdirSync(path.join(root, 'tmp/loading-performance'), { recursive: true })
writeFileSync(path.join(root, 'tmp/loading-performance/report-compatibility.json'), JSON.stringify({ ...evidence, payload: current }, null, 2))
console.log(JSON.stringify(evidence))
