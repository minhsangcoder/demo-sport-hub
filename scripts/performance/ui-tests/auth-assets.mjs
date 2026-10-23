import assert from 'node:assert/strict'

export async function authAndAssets(h) {
  const wardChunks = h.chunkFiles.filter(file => { const text = h.chunkSource(file); return text.includes('32248') && text.includes('31825') && text.includes('wards') })
  assert.ok(wardChunks.length, 'The full administrative dataset must exist for Owner address forms')
  for (const viewport of [{ width: 1440, height: 1000 }, { width: 390, height: 844 }]) {
    const { page, context } = await h.page('PUBLIC', { viewport })
    const requests = []
    page.on('request', request => requests.push(request.url()))
    await page.addInitScript(() => {
      window.__layoutShifts = []
      new PerformanceObserver(list => { for (const entry of list.getEntries()) if (!entry.hadRecentInput) window.__layoutShifts.push({ value: entry.value, image: entry.sources?.some(s => s.node?.tagName === 'IMG') }) }).observe({ type: 'layout-shift', buffered: true })
    })
    await page.goto(h.base + '/', { waitUntil: 'domcontentloaded' })
    const hero = page.locator('img').first(), before = await hero.boundingBox()
    await page.getByText('Sân đo 1', { exact: true }).first().waitFor()
    await page.waitForLoadState('networkidle')
    const after = await hero.boundingBox()
    const evidence = await page.evaluate(async () => {
      await document.fonts.ready
      const sample = 'Đặt sân thể thao – Nguyễn Thị Ánh, hoàn tiền, chuyển nhượng'
      await document.fonts.load('500 16px "Be Vietnam Pro"', sample)
      return {
        font: document.fonts.check('500 16px "Be Vietnam Pro"', sample),
        images: [...document.images].filter(img => img.currentSrc.includes('/images/')).map(img => ({ src: img.currentSrc, naturalWidth: img.naturalWidth, naturalHeight: img.naturalHeight, fit: getComputedStyle(img).objectFit })),
        imageShift: window.__layoutShifts.filter(s => s.image).reduce((n, s) => n + s.value, 0),
      }
    })
    assert.equal(evidence.font, true); assert.ok(evidence.images.length > 0)
    assert.ok(evidence.images.every(img => img.src.endsWith('.webp') && img.naturalWidth > 0))
    assert.ok(Math.abs(before.width - after.width) < 1 && Math.abs(before.height - after.height) < 1)
    assert.ok(evidence.imageShift < 0.01, 'Local image dimensions must not shift the page')
    assert.ok(!requests.some(url => /be-vietnam.*\.ttf/.test(url)))
    assert.ok(!requests.some(url => wardChunks.some(file => url.endsWith('/' + file))))
    await page.screenshot({ path: `${h.directory}/home-${viewport.width}.png`, fullPage: true })
    h.pass(`Production home ${viewport.width}px uses responsive WebP, Vietnamese WOFF2 and stable image boxes`, evidence)
    await context.close()
  }
  const { page, context, data } = await h.page('PUBLIC')
  const requests = []
  page.on('request', request => requests.push(request.url()))
  await page.goto(h.base + '/login'); await page.locator('input[name=identifier]').waitFor()
  assert.ok(!requests.some(url => wardChunks.some(file => url.endsWith('/' + file))))
  await page.screenshot({ path: `${h.directory}/login-desktop.png`, fullPage: true })
  await page.goto(h.base + '/register'); await page.locator('input[name=fullName]').waitFor()
  await page.getByRole('checkbox', { name: /Đăng ký trở thành chủ sân/ }).check()
  await page.locator('select[name=provinceCode]').waitFor()
  await page.waitForFunction(() => Boolean(document.querySelector('.leaflet-pane')), { timeout: 30000 })
  assert.ok(requests.some(url => wardChunks.some(file => url.endsWith('/' + file))))
  await page.getByPlaceholder(/maps.app.goo.gl/).fill('21.028, 105.78')
  await page.getByRole('button', { name: 'Xác định vị trí', exact: true }).click()
  await page.getByText(/Đã ghim: Vĩ độ 21.0280000/).waitFor()
  h.pass('Owner-only address and Leaflet provider load after selecting Owner registration; coordinate control still works')
  for (const [route, field] of [['/verify?challengeId=test', 'code'], ['/forgot-password', 'identifier'], ['/reset-password?challengeId=test', 'password']]) {
    await page.goto(h.base + route); await page.locator(`input[name=${field}]`).waitFor()
  }
  await page.goto(h.base + '/login?next=%2Fcustomer%2Fbookings')
  await page.route('**/api/v1/auth/oauth2/authorization/google', route => route.fulfill({ contentType: 'text/html', body: '<h1>Chuyển hướng Google thử nghiệm</h1>' }))
  const navigation = page.waitForRequest(request => request.url().includes('/api/v1/auth/oauth2/authorization/google') && request.isNavigationRequest())
  await page.getByRole('button', { name: /Google/ }).click(); await navigation
  assert.equal(await page.evaluate(() => sessionStorage.getItem('sporthub-google-next')), '/customer/bookings')
  assert.deepEqual(data.unknown, [])
  h.pass('Auth modes render Vietnamese and Google uses a full browser redirect with its saved continuation')
  await context.close()
  for (const role of ['OWNER', 'ADMIN', 'STAFF']) {
    const { page, context } = await h.page(role)
    await page.goto(h.base + '/')
    if (role === 'STAFF') { await page.waitForURL('**/staff/bookings'); await page.getByRole('heading', { name: 'Kiểm tra lượt đặt' }).waitFor() }
    else { await page.getByText('Sân đo 1', { exact: true }).first().waitFor(); assert.equal(new URL(page.url()).pathname, '/') }
    await context.close()
  }
  h.pass('Home role continuation remains Staff-only; Owner and Admin keep their previous home route')
}
