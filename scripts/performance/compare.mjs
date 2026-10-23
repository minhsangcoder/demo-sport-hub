import assert from 'node:assert/strict'
import { readFileSync, writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const root = fileURLToPath(new URL('../../', import.meta.url))
const read = file => JSON.parse(readFileSync(path.join(root, file), 'utf8'))
const before = read('tmp/loading-performance/before/browser.json')
const phase = process.argv[2] ?? 'after'
assert.match(phase, /^(after|candidate(?:-[a-z0-9-]+)?)$/)
const after = read(`tmp/loading-performance/${phase}/browser.json`)
assert.deepEqual(after.conditions, before.conditions)
assert.equal(after.browser, before.browser)
assert.equal(before.samples.length, 50)
assert.equal(after.samples.length, 50)
assert.equal(before.navigations.length, 20)
assert.equal(after.navigations.length, 20)

const median = values => [...values].sort((a, b) => a - b)[Math.floor(values.length / 2)]
const improved = (a, b) => a ? 100 * (a - b) / a : null
const browsers = {}
const targets = []
for (const route of ['home', 'login', 'search', 'staff', 'report']) {
  browsers[route] = {}
  for (const cache of ['cold', 'warm']) {
    const rows = [before, after].map(data => data.samples.filter(row => row.route === route && row.cache === cache))
    assert.ok(rows.every(list => list.length === 5 && list.every(row => Number.isFinite(row.contentReady))))
    const metrics = {}
    for (const field of ['contentReady', 'lcp', 'initialJsBytes', 'imageBytes', 'fontBytes']) {
      const a = median(rows[0].map(row => row[field])), b = median(rows[1].map(row => row[field]))
      metrics[field] = { before: a, after: b, improvementPercent: improved(a, b) }
    }
    metrics.duplicateApiRequests = rows.map(list => median(list.map(row => {
      const counts = new Map()
      for (const url of row.apiRequests) counts.set(url, (counts.get(url) ?? 0) + 1)
      return [...counts.values()].reduce((total, count) => total + Math.max(0, count - 1), 0)
    })))
    browsers[route][cache] = metrics
  }
}
function goal(name, metric, required) {
  targets.push({ name, ...metric, requiredImprovementPercent: required, met: metric.improvementPercent >= required })
}
for (const route of ['login', 'staff']) goal(`${route} cold JS`, browsers[route].cold.initialJsBytes, 20)
const a = browsers.home.cold
const resources = { before: a.imageBytes.before + a.fontBytes.before, after: a.imageBytes.after + a.fontBytes.after }
goal('home cold images + fonts', { ...resources, improvementPercent: improved(resources.before, resources.after) }, 30)
for (const route of ['home', 'search']) goal(`${route} cold main content`, browsers[route].cold.contentReady, 20)
for (const route of ['login', 'staff', 'report']) for (const cache of ['cold', 'warm']) {
  const metric = browsers[route][cache].contentReady
  const allowanceMs = Math.max(metric.before * 0.1, 50)
  targets.push({ name: `${route} ${cache} regression control`, ...metric, allowanceMs, met: metric.after - metric.before <= allowanceMs })
}

const originalApi = read('tmp/loading-performance/before/backend/api-before.json')
const currentApi = read('tmp/backend-loading-refactor/api-after.json')
assert.deepEqual(currentApi.runtime, originalApi.runtime)
const api = {}
for (const dataset of ['small', 'large']) {
  api[dataset] = {}
  for (const route of ['search', 'report']) {
    const a = originalApi[dataset][route], b = currentApi[dataset][route]
    assert.equal(a.samplesMs.length, 30)
    assert.equal(b.samplesMs.length, 30)
    assert.deepEqual(b.payload, a.payload, `${dataset} ${route} HTTP payload`)
    api[dataset][route] = Object.fromEntries(['p50Ms', 'p95Ms'].map(field => [field, { before: a[field], after: b[field], improvementPercent: improved(a[field], b[field]) }]))
    if (dataset === 'large') goal(`large ${route} API p95`, api[dataset][route].p95Ms, 20)
  }
  const data = currentApi[dataset]
  api[dataset].rows = { reservationBefore: data.reservationRowsWithoutRange, reservationAfter: data.reservationRowsWithRange, report: data.reportSnapshotRows }
  api[dataset].executor = { sequentialP95Ms: data.executorSequential.p95Ms, twoFacilityP95Ms: data.executorParallel.p95Ms, improvementPercent: improved(data.executorSequential.p95Ms, data.executorParallel.p95Ms) }
}
const navigations = {}
for (const to of ['login', 'search']) for (const cache of ['cold', 'warm']) {
  const [a, b] = [before, after].map(data => median(data.navigations.filter(row => row.to === to && row.cache === cache).map(row => row.elapsedMs)))
  navigations[`${to}-${cache}`] = { before: a, after: b, improvementPercent: improved(a, b) }
}
const result = { phase, browser: before.browser, conditions: before.conditions, browsers, api, navigations, targets, payloadsEqual: true, timestampsRemoved: [] }
writeFileSync(path.join(root, 'tmp/loading-performance/comparison.json'), JSON.stringify(result, null, 2))
console.log(JSON.stringify({ targets, api, navigations }, null, 2))
