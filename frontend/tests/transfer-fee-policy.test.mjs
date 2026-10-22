import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import test from 'node:test'
import ts from 'typescript'

const filename = new URL('../features/facility/transfer-fee-policy.ts', import.meta.url)
const source = readFileSync(filename, 'utf8')
const exports = {}
vm.runInNewContext(ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText,
  { exports, module: { exports } })
const { transferFeePercentToBps, transferFeeBpsToPercent } = exports

test('percent values preserve whole basis points without floating point rounding', () => {
  for (const [input, expected] of [['0', 0], ['50', 5000], ['80', 8000], ['100', 10000], ['80.01', 8001], ['0.01', 1], ['2,55', 255]]) {
    assert.equal(transferFeePercentToBps(input), expected)
    assert.equal(transferFeePercentToBps(transferFeeBpsToPercent(expected)), expected)
  }
  assert.equal(transferFeeBpsToPercent(null), '')
})

test('invalid percentages are rejected rather than clamped or rounded', () => {
  for (const input of ['', '-1', '100.01', '80.001', 'NaN', 'Infinity', '1e2', '1,2,3']) {
    assert.throws(() => transferFeePercentToBps(input), input)
  }
  for (const value of [-1, 10001, 8000.5]) assert.throws(() => transferFeeBpsToPercent(value))
})
