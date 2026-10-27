import assert from 'node:assert/strict'
import { readFileSync, writeFileSync } from 'node:fs'
import path from 'node:path'

const home = 'tmp/peer-resale-performance'
const directory = process.argv[2] ?? path.join(home, 'system-performance-final-evidence')
const read = file => JSON.parse(readFileSync(file, 'utf8').replace(/^\uFEFF/, ''))
const ui = read(path.join(directory, 'integrated-ui-performance.json'))
const report = read(path.join(home, 'report-comparison.json'))
const business = read(path.join(directory, 'result.json'))
assert.equal(business.passed, true)
assert.equal(business.realServices, true)
assert.equal(business.realBankTransfers, false)
assert.equal(ui.conditions.production, true)
assert.equal(ui.conditions.runs, 5)
assert.equal(ui.conditions.phaseOrder, 'Alternating AB/BA by run')
assert.deepEqual(ui.errors, [])
assert.equal(ui.samples.length, 100)
assert.equal(report.conditions.warmups, 10)
assert.equal(report.conditions.samples, 30)
assert.ok(report.targets.some(row => row.target && row.met))
assert.ok(report.targets.every(row => row.met))
const quantile = (values, fraction) => [...values].sort((a, b) => a - b)[Math.ceil(values.length * fraction) - 1]
const routes = []
for (const route of ['search', 'schedule', 'booking', 'report', 'bank']) {
  for (const cache of ['cold', 'warm']) {
    const groups = ['before', 'after'].map(phase => ui.samples.filter(row => row.phase === phase && row.route === route && row.cache === cache))
    assert.ok(groups.every(group => group.length === 5 && new Set(group.map(row => row.run)).size === 5))
    const metrics = groups.map(group => ({
      contentReadyP50Ms: quantile(group.map(row => row.contentReady), .5),
      contentReadyP95Ms: quantile(group.map(row => row.contentReady), .95),
      lcpP50Ms: quantile(group.map(row => row.lcp), .5),
      initialJsBytesP50: quantile(group.map(row => row.initialJsBytes), .5),
      imageBytesP50: quantile(group.map(row => row.resources.filter(item => /\.(png|jpe?g|webp|avif|svg)$|^\/_next\/image$/.test(item.path)).reduce((total, item) => total + item.encoded, 0)), .5),
      fontBytesP50: quantile(group.map(row => row.fontBytes), .5),
      getRequestsP50: quantile(group.map(row => row.requests.filter(request => request.method === 'GET').length), .5),
    }))
    const [before, after] = metrics
    const allowedIncreaseMs = Math.max(before.contentReadyP50Ms * .1, 50)
    routes.push({ route, cache, before, after, allowedIncreaseMs, met: after.contentReadyP50Ms <= before.contentReadyP50Ms + allowedIncreaseMs })
  }
}
const result = {
  conditions: ui.conditions,
  mainBottleneck: report.metrics.large,
  reportTargets: report.targets,
  routes,
  met: routes.every(row => row.met),
  evidence: { integrated: directory, reports: path.join(home, 'report-comparison.json') },
  limitations: ['Provider fixtures do not measure real-bank latency', 'Five UI samples use medians for controls; p95 is the maximum of five and remains diagnostic', 'Shared-host CPU and memory snapshots are recorded separately; no isolated-hardware claim'],
}
writeFileSync(path.join(directory, 'acceptance-comparison.json'), JSON.stringify(result, null, 2))
console.log(JSON.stringify({ met: result.met, reportTargets: report.targets, controls: routes.map(row => ({ route: row.route, cache: row.cache, beforeMs: row.before.contentReadyP50Ms, afterMs: row.after.contentReadyP50Ms, met: row.met })) }, null, 2))
assert.ok(result.met, 'Production UI controls exceeded max(10%, 50ms); inspect the raw waterfall instead of marking performance complete')
