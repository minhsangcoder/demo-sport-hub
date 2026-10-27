import assert from 'node:assert/strict'
import {createRequire} from 'node:module'
import {installPeerContentTiming} from '../../frontend/tests/support/peer-content-timing.mjs'

const require=createRequire(import.meta.url)
const {chromium}=require(process.env.SPORTHUB_E2E_PLAYWRIGHT||'playwright')
const browser=await chromium.launch({headless:true,channel:process.env.SPORTHUB_E2E_BROWSER_CHANNEL||'chrome'})
try {
  const context=await browser.newContext()
  await installPeerContentTiming(context,'bank')
  const page=await context.newPage()
  await page.goto('data:text/html,<main></main>')
  assert.equal(await page.evaluate(()=>window.__peerContentReady),0)
  const started=await page.evaluate(()=>{
    setTimeout(()=>document.querySelector('main').innerHTML='<label>Chọn tài khoản đã khai<select><option>Current masked account</option></select></label>',120)
    return performance.now()
  })
  await page.waitForTimeout(1000)
  const result=await page.evaluate(()=>({ready:window.__peerContentReady,driverObserved:performance.now()}))
  assert.ok(result.ready>=started+120&&result.ready<started+600,JSON.stringify(result))
  assert.ok(result.driverObserved-result.ready>300,'Delayed driver polling must not inflate content-ready')
  await page.evaluate(()=>document.querySelector('main').append(document.createElement('p')))
  assert.equal(await page.evaluate(()=>window.__peerContentReady),result.ready)
  console.log(JSON.stringify({passed:true,contentReadyMs:result.ready-started,driverDelayExcludedMs:result.driverObserved-result.ready}))
  await context.close()
} finally {await browser.close()}
