import assert from 'node:assert/strict'

export async function chunkRecovery(h) {
  const chunks = h.chunkFiles.filter(file => h.chunkSource(file).includes('/facilities/favorites?page='))
  assert.ok(chunks.length, 'Identify the real dynamically imported Favorites module')
  const { page, context } = await h.page('CUSTOMER')
  h.expectChunkFailure(page)
  let block = true, failures = 0
  await page.route('**/_next/static/chunks/**', route => {
    if (block && chunks.some(file => route.request().url().endsWith('/' + file))) {
      failures++
      return route.abort('failed')
    }
    return route.continue()
  })
  await page.goto(h.base + '/customer/favorites')
  await page.getByRole('alert').filter({ hasText: 'Không thể mở màn hình.' }).waitFor()
  assert.ok(failures > 0)
  block = false
  await page.getByRole('button', { name: 'Tải lại màn hình', exact: true }).click()
  await page.getByRole('heading', { name: 'Cơ sở yêu thích', exact: true }).waitFor()
  assert.equal(await page.getByRole('alert').filter({ hasText: 'Không thể mở màn hình.' }).count(), 0)
  await context.close()
  h.pass('A genuine lazy chunk download failure shows Vietnamese recovery UI; reloading recovers without bypassing authentication')
}
