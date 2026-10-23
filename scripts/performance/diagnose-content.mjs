// Supplemental timing only: one sample cannot replace the five-run acceptance medians.
import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { createRequire } from 'node:module'
import { writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const root = fileURLToPath(new URL('../../', import.meta.url))
const require = createRequire(import.meta.url)
const { chromium } = require(process.env.SPORTHUB_E2E_PLAYWRIGHT || 'playwright')
const result = { purpose: 'One-sample diagnostic of the client GET/render path; not acceptance statistics', samples: [] }
for (const phase of ['before', 'candidate-final']) {
  const server = spawn(process.execPath, [path.join(root, 'scripts/performance/ui-server.mjs'), path.join(root, 'tmp/loading-performance', phase, 'frontend')], { windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'], env: { ...process.env, NODE_ENV: 'production' } })
  let browser
  try {
    const base = await new Promise((resolve, reject) => {
      let output = ''
      const timer = setTimeout(() => reject(Error('Diagnostic fixture readiness timeout')), 60000)
      server.once('error', error => { clearTimeout(timer); reject(error) })
      server.once('exit', code => { clearTimeout(timer); reject(Error('Diagnostic listener exited: ' + code)) })
      server.stdout.on('data', value => {
        output += value
        for (const line of output.split('\n')) try {
          const item = JSON.parse(line)
          if (item.url) { clearTimeout(timer); resolve(item.url) }
        } catch { /* Next startup. */ }
      })
    })
    browser = await chromium.launch({ channel: 'chrome', headless: true })
    for (const route of ['/', '/search']) {
      const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: 'vi-VN', timezoneId: 'Asia/Ho_Chi_Minh' })
      await context.addInitScript(() => {
        window.__firstCourt = null
        const observer = new MutationObserver(() => {
          if ([...document.querySelectorAll('article h2,article h3')].some(node => node.textContent.trim() === 'Sân đo 1')) {
            window.__firstCourt = performance.now()
            observer.disconnect()
          }
        })
        observer.observe(document, { subtree: true, childList: true, characterData: true })
      })
      const page = await context.newPage(), cdp = await context.newCDPSession(page)
      await cdp.send('Network.enable')
      await cdp.send('Network.emulateNetworkConditions', { offline: false, latency: 40, downloadThroughput: 1048576, uploadThroughput: 1048576 })
      await cdp.send('Network.setCacheDisabled', { cacheDisabled: true })
      await cdp.send('Network.clearBrowserCache')
      await page.goto(base + route, { waitUntil: 'domcontentloaded' })
      await page.waitForFunction(() => window.__firstCourt !== null)
      await page.waitForLoadState('networkidle')
      const timing = await page.evaluate(() => ({
        firstCourt: window.__firstCourt,
        document: performance.getEntriesByType('navigation')[0].responseEnd,
        resources: performance.getEntriesByType('resource').filter(entry => entry.name.includes('/api/v1/') || /\.js(?:\?|$)/.test(entry.name)).map(entry => ({ path: new URL(entry.name).pathname, start: entry.startTime, end: entry.responseEnd, duration: entry.duration })),
      }))
      const search = timing.resources.find(entry => entry.path === '/api/v1/bookings/search')
      assert.ok(search)
      result.samples.push({ phase, route, ...timing, searchFetchStarts: search.start, searchFetchEnds: search.end, renderAfterFetchMs: timing.firstCourt - search.end })
      await context.close()
    }
  } finally { await browser?.close(); server.kill('SIGTERM') }
}
writeFileSync(path.join(root, 'tmp/loading-performance/content-diagnostic.json'), JSON.stringify(result, null, 2))
console.log(JSON.stringify(result.samples.map(({ phase, route, firstCourt, document, searchFetchStarts, searchFetchEnds, renderAfterFetchMs }) => ({ phase, route, firstCourt, document, searchFetchStarts, searchFetchEnds, renderAfterFetchMs })), null, 2))
