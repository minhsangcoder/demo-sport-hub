import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import test from 'node:test'
import ts from 'typescript'
const exports = {}
vm.runInNewContext(ts.transpileModule(readFileSync(new URL('../features/facility/demo-report.ts', import.meta.url), 'utf8'), { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText, { exports, module: { exports } })
const { facilityCalendarDate, reportPreset, validateReportRange, demoVnd } = exports
test('facility calendar and presets do not use the browser calendar or fixed DST hours', () => {
  const now = new Date('2026-10-21T18:30:00Z')
  assert.equal(facilityCalendarDate('Asia/Ho_Chi_Minh', now), '2026-10-22')
  assert.equal(facilityCalendarDate('America/Los_Angeles', now), '2026-10-21')
  assert.deepEqual({ ...reportPreset('Asia/Ho_Chi_Minh', '7', now) }, { fromDate: '2026-10-16', toDate: '2026-10-22' })
  assert.deepEqual({ ...reportPreset('Asia/Ho_Chi_Minh', '30', now) }, { fromDate: '2026-09-23', toDate: '2026-10-22' })
  assert.deepEqual({ ...reportPreset('America/New_York', 'month', new Date('2026-11-03T12:00:00Z')) }, { fromDate: '2026-11-01', toDate: '2026-11-03' })
})
test('inclusive calendar ranges reject incomplete invalid reversed or excessive input', () => {
  for (const range of [{ fromDate: '', toDate: '2026-10-22' }, { fromDate: '2026-02-30', toDate: '2026-03-01' }, { fromDate: '2026-10-23', toDate: '2026-10-22' }, { fromDate: '2026-01-01', toDate: '2027-01-02' }]) assert.notEqual(validateReportRange(range), '')
  assert.equal(validateReportRange({ fromDate: '2026-01-01', toDate: '2027-01-01' }), '')
  assert.equal(validateReportRange({ fromDate: '2026-10-22', toDate: '2026-10-22' }), '')
})
test('financial labels preserve exact decimal strings and negative periods', () => {
  assert.equal(demoVnd('125000'), '125.000 ₫')
  assert.equal(demoVnd('-80000'), '-80.000 ₫')
  assert.equal(demoVnd('12345678901234567890'), '12.345.678.901.234.567.890 ₫')
  assert.equal(demoVnd('120000.50'), '120.000,5 ₫')
})
