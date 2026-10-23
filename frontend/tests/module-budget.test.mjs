import assert from 'node:assert/strict'
import test from 'node:test'
import { measureModule } from '../../scripts/refactor/module-metrics.mjs'

test('module guard uses formatted LOC and detects compressed executable code', async () => {
  const source = 'export function f(){' + Array.from({ length: 250 }, (_, i) => `if(x===${i}){return ${i};}`).join('') + '}'
  const result = await measureModule(source, 'example.ts')
  assert.ok(result.loc > 500)
  assert.ok(result.violations.some(reason => reason.includes('Compressed')))
})
test('pure dictionaries are inventoried separately and cannot conceal a hook or JSX', async () => {
  const data = 'export const labels = {' + Array.from({ length: 600 }, (_, i) => `key${i}: 'Nhãn ${i}',`).join('\n') + '}'
  assert.equal((await measureModule(data, 'example.messages.ts')).category, 'dictionary')
  const bad = await measureModule('export function x(){ return useState(0) }', 'example.messages.ts')
  assert.ok(bad.violations.some(reason => reason.includes('executable')))
  assert.equal((await measureModule('export function a() { return 1 }', 'feature.ts')).violations.length, 0)
  assert.equal((await measureModule('{}', 'seed.json')).category, 'data')
})
