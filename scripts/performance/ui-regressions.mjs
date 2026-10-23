import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { createRequire } from 'node:module'
import { mkdirSync, writeFileSync, readdirSync, readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import { fixture } from './ui-fixture.mjs'
import { authAndAssets } from './ui-tests/auth-assets.mjs'
import { workspaces } from './ui-tests/workspaces.mjs'
import { qrLifetime } from './ui-tests/qr.mjs'
import { chunkRecovery } from './ui-tests/chunk-recovery.mjs'

const root = fileURLToPath(new URL('../../', import.meta.url))
const phase = process.argv[2]
assert.match(phase ?? '', /^(after|candidate(?:-[a-z0-9-]+)?)$/)
const directory = path.join(root, 'tmp/loading-performance', phase)
const require = createRequire(import.meta.url)
const { chromium } = require(process.env.SPORTHUB_E2E_PLAYWRIGHT || 'playwright')
const server = spawn(process.execPath, [path.join(root, 'scripts/performance/ui-server.mjs'), path.join(directory, 'frontend')], { windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] })
let browser
const checks = [], errors = [], expectedChunkErrors = [], chunkFailurePages = new WeakSet()
try {
  const base = await new Promise((resolve, reject) => {
    let output = ''
    const timer = setTimeout(() => reject(Error('UI fixture readiness timed out')), 60000)
    server.once('error', error => { clearTimeout(timer); reject(error) })
    server.once('exit', code => { clearTimeout(timer); reject(Error('UI fixture exited: ' + code + output)) })
    server.stderr.on('data', value => { output += value })
    server.stdout.on('data', value => {
      output += value
      for (const line of output.split('\n')) try { const result = JSON.parse(line); if (result.url) { clearTimeout(timer); resolve(result.url) } } catch { /* Next startup output. */ }
    })
  })
  browser = await chromium.launch({ channel: 'chrome', headless: true, args: ['--use-fake-device-for-media-stream', '--use-fake-ui-for-media-stream'] })
  const ctx = {
    root, directory, base, browser, checks,
    expectChunkFailure(page) { chunkFailurePages.add(page) },
    pass(label, evidence = {}) { checks.push({ label, ...evidence }); console.log('PASS: ' + label) },
    chunkFiles: readdirSync(path.join(directory, 'frontend/.next/static/chunks')).filter(file => file.endsWith('.js')),
    chunkSource(file) { return readFileSync(path.join(directory, 'frontend/.next/static/chunks', file), 'utf8') },
    async page(role = 'PUBLIC', options = {}) {
      const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: 'vi-VN', timezoneId: 'Asia/Ho_Chi_Minh', ...options })
      const data = fixture(role)
      await context.route('**/api/v1/**', data.route)
      await context.route('https://tile.openstreetmap.org/**', route => route.fulfill({ contentType: 'image/png', body: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j4U8AAAAASUVORK5CYII=', 'base64') }))
      const page = await context.newPage()
      page.on('pageerror', error => {
        if (chunkFailurePages.has(page) && /ChunkLoadError|Loading chunk|Failed to fetch dynamically imported/i.test(error.message)) expectedChunkErrors.push(error.message)
        else errors.push(error.message)
      })
      return { page, context, data }
    },
  }
  await authAndAssets(ctx)
  await workspaces(ctx)
  await qrLifetime(ctx)
  await chunkRecovery(ctx)
  assert.deepEqual(errors, [], 'Unexpected production render/hydration errors')
  mkdirSync(directory, { recursive: true })
  writeFileSync(path.join(directory, 'ui-regressions.json'), JSON.stringify({ source: phase, production: true, api: 'Synthetic HTTP responses; actual built UI/controllers/lazy chunks, camera uses Chrome fake device', checks, errors, expectedChunkErrors }, null, 2))
} catch (error) {
  let index = 0
  for (const context of browser?.contexts() ?? []) for (const page of context.pages()) {
    writeFileSync(path.join(directory, `ui-failure-${index}.txt`), await page.locator('body').innerText().catch(() => ''))
    await page.screenshot({ path: path.join(directory, `ui-failure-${index++}.png`), fullPage: true }).catch(() => {})
  }
  writeFileSync(path.join(directory, 'ui-failure.json'), JSON.stringify({ error: error.message, errors, checks }, null, 2))
  throw error
} finally { await browser?.close(); server.kill('SIGTERM') }
