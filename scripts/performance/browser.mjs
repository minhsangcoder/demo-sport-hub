import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { createRequire } from 'node:module'
import { mkdirSync, writeFileSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { facility } from './ui-data.mjs'

const root = fileURLToPath(new URL('../../', import.meta.url))
const phase = process.argv[2]
assert.match(phase ?? '', /^(before|after|candidate(?:-[a-z0-9-]+)?)$/, 'Use before|after|candidate[-name]')
const require = createRequire(import.meta.url)
const { chromium } = require(process.env.SPORTHUB_E2E_PLAYWRIGHT || 'playwright')
const performanceHome = path.resolve(root, process.env.SPORTHUB_PERFORMANCE_HOME || 'tmp/loading-performance')
const directory = path.join(performanceHome, phase)
const routes = [
  { name: 'home', path: '/', role: 'PUBLIC', ready: 'text=Sân đo 1' },
  { name: 'login', path: '/login', role: 'PUBLIC', ready: 'input[name=identifier]' },
  { name: 'search', path: '/search', role: 'PUBLIC', ready: 'text=Sân đo 1' },
  { name: 'staff', path: '/staff/bookings', role: 'STAFF', ready: 'text=Chưa có lượt đặt phù hợp.' },
  { name: 'report', path: `/owner/facilities/${facility.id}/reports`, role: 'OWNER', ready: 'text=125.000 ₫' },
]
const navigationOnly = process.argv.includes('--navigation-only')
const samples = navigationOnly ? JSON.parse(readFileSync(path.join(directory, 'browser.json'), 'utf8')).samples : []
const navigations = []
const selected = process.argv.find(value => value.startsWith('--routes='))?.slice(9).split(',') ?? routes.map(route => route.name)
assert.ok(selected.every(name => routes.some(route => route.name === name)), 'Unknown measurement route')
const fixtureDay = new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' })
if (phase !== 'before') {
  const baseline = JSON.parse(readFileSync(path.join(performanceHome, 'before/browser.json'), 'utf8'))
  if (baseline.conditions.fixtureDay !== fixtureDay) throw new Error('Re-run the preserved before snapshot on the same calendar day before comparing phases')
}
const server = spawn(process.execPath, [path.join(root, 'scripts/performance/ui-server.mjs'), path.join(directory, 'frontend')], { windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'], env: { ...process.env, NODE_ENV: 'production' } })
const base = await new Promise((resolve, reject) => {
  let output = ''
  const timeout = setTimeout(() => reject(new Error('Production fixture readiness timed out')), 60000)
  server.on('exit', code => { clearTimeout(timeout); reject(new Error(`Test listener exited ${code}: ${output}`)) })
  server.on('error', error => { clearTimeout(timeout); reject(error) })
  server.stderr.on('data', data => { output += data })
  server.stdout.on('data', data => {
    output += data
    for (const line of output.split('\n')) {
      try { const value = JSON.parse(line); if (value.production && value.url) { clearTimeout(timeout); resolve(value.url) } } catch { /* Next also logs startup messages. */ }
    }
  })
}).catch(error => { server.kill('SIGTERM'); throw error })
let browser
const errors = []
try {
  browser = await chromium.launch({ headless: true, channel: process.env.SPORTHUB_E2E_BROWSER_CHANNEL || 'chrome' })
  for (const route of navigationOnly ? [] : routes.filter(route => selected.includes(route.name))) for (let run = 0; run < 5; run++) {
    const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: 'vi-VN', timezoneId: 'Asia/Ho_Chi_Minh' })
    await context.addCookies([{ name: 'perf-role', value: route.role, url: base }])
    await context.addInitScript(() => {
      window.__perfLcp = 0
      new PerformanceObserver(list => { for (const entry of list.getEntries()) window.__perfLcp = entry.startTime }).observe({ type: 'largest-contentful-paint', buffered: true })
    })
    const page = await context.newPage()
    page.on('pageerror', error => errors.push({ route: route.name, message: error.message }))
    const cdp = await context.newCDPSession(page)
    await cdp.send('Network.enable')
    await cdp.send('Network.emulateNetworkConditions', { offline: false, latency: 40, downloadThroughput: 1024 * 1024, uploadThroughput: 1024 * 1024 })
    for (const cache of ['cold', 'warm']) {
      await cdp.send('Network.setCacheDisabled', { cacheDisabled: cache === 'cold' })
      if (cache === 'cold') await cdp.send('Network.clearBrowserCache')
      else {
        // A cache-disabled cold load need not populate Chrome's HTTP cache.
        await page.goto(base + route.path, { waitUntil: 'networkidle' })
        await page.locator(route.ready).first().waitFor({ timeout: 30000 })
      }
      const requests = []
      const onRequest = request => { if (request.url().includes('/api/v1/')) requests.push(new URL(request.url()).pathname + new URL(request.url()).search) }
      page.on('request', onRequest)
      await page.goto(base + route.path, { waitUntil: 'domcontentloaded' })
      await page.locator(route.ready).first().waitFor({ timeout: 30000 })
      const contentReady = await page.evaluate(() => performance.now())
      await page.waitForLoadState('networkidle')
      await page.evaluate(() => document.fonts.ready)
      const sample = await page.evaluate(() => ({ lcp: window.__perfLcp, resources: performance.getEntriesByType('resource').map(entry => ({ name: entry.name, encoded: entry.encodedBodySize, transferred: entry.transferSize, duration: entry.duration })) }))
      page.off('request', onRequest)
      const unique = [...new Map(sample.resources.map(entry => [entry.name, entry])).values()]
      const sum = pattern => unique.filter(entry => pattern.test(new URL(entry.name).pathname)).reduce((total, entry) => total + entry.encoded, 0)
      samples.push({ route: route.name, role: route.role, cache, run, contentReady, lcp: sample.lcp, initialJsBytes: sum(/\.js$/), imageBytes: sum(/\.(png|jpe?g|webp|avif|svg)$/), fontBytes: sum(/\.(ttf|woff2?)$/), apiRequests: requests, resources: unique })
      console.log(`${phase} ${route.name} ${cache} #${run + 1}: ${Math.round(contentReady)}ms`)
    }
    await context.close()
  }
  for (const route of process.argv.includes('--no-navigation') ? [] : routes.filter(route => ['search', 'login'].includes(route.name) && selected.includes(route.name))) for (const cache of ['cold', 'warm']) for (let run = 0; run < 5; run++) {
    const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: 'vi-VN', timezoneId: 'Asia/Ho_Chi_Minh' })
    const page = await context.newPage(), cdp = await context.newCDPSession(page)
    await cdp.send('Network.enable')
    await cdp.send('Network.emulateNetworkConditions', { offline: false, latency: 40, downloadThroughput: 1048576, uploadThroughput: 1048576 })
    await cdp.send('Network.setCacheDisabled', { cacheDisabled: cache === 'cold' })
    if (cache === 'cold') await cdp.send('Network.clearBrowserCache')
    await page.goto(base + '/', { waitUntil: 'networkidle' })
    if (cache === 'warm') {
      await page.goto(base + route.path, { waitUntil: 'networkidle' })
      await page.goto(base + '/', { waitUntil: 'networkidle' })
    }
    const started = await page.evaluate(() => performance.now())
    await page.locator(`a[href="${route.path}"]`).first().click()
    await page.waitForURL(url => url.pathname === route.path)
    await page.locator(route.ready).first().waitFor()
    const elapsedMs = await page.evaluate(start => performance.now() - start, started)
    navigations.push({ from: 'home', to: route.name, cache, run, elapsedMs })
    await context.close()
  }
  assert.deepEqual(errors, [], 'Production hydration must be error-free')
  mkdirSync(directory, { recursive: true })
  const result = { phase, browser: browser.version(), conditions: { fixtureDay, viewport: '1440x1000', latencyMs: 40, bytesPerSecond: 1048576, apiFixtureDelayMs: 100, runsPerCacheRoute: 5, isolated: true, production: true, api: 'Deterministic HTTP synthetic fixture, not backend latency', navigation: 'Full-page samples separate from home → login/search Link navigations' }, samples, navigations }
  writeFileSync(path.join(directory, 'browser.json'), JSON.stringify(result, null, 2))
} finally { await browser?.close(); server.kill('SIGTERM') }
