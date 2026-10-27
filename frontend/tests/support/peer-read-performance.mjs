import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import {installPeerContentTiming} from './peer-content-timing.mjs'

export async function peerReadPerformance({browser,config,actors,password,facility,court,quoteCourt,slot,booking,call,pass}) {
  const samples=[],metadata=[],errors=[]
  const day=new Date(Date.parse(slot.startsAt)+7*3600000).toISOString().slice(0,10)
  const backend={},comparisonBackend={},reads=[
    ['search','b','GET',`/bookings/search?date=${day}`],
    ['availability','b','GET',`/bookings/availability?courtId=${quoteCourt}&date=${day}`],
    ['quote','b','POST','/bookings/quote',{courtId:quoteCourt,startsAt:day+'T15:30:00+07:00',endsAt:day+'T16:30:00+07:00'}],
    ['bank','a','GET','/payments/bank-profile'],
    ['booking','b','GET',`/bookings/${booking}`],
    ['report','owner','GET',`/bookings/reports/facility/${facility}/dashboard?fromDate=${day}&toDate=${day}&mode=REAL&size=25`],
  ]
  for(const [name,actor,method,url,body] of reads) {
    const request=phase=>call(actor,method,url,body,200,{},phase==='before'?{origin:config.comparisonBaseURL}:{})
    for(let i=0;i<10;i++)for(const phase of ['before','after'])await request(phase)
    const times={before:[],after:[]}
    for(let i=0;i<30;i++) {
      for(const phase of i%2===0?['before','after']:['after','before']) {
        const started=performance.now(),value=await request(phase)
        times[phase].push(performance.now()-started)
        assert.ok(value)
        if(name==='quote')assert.equal(Number(value.amount),150000)
        if(name==='bank')assert.equal(value.principalId,actors.a.id)
        if(name==='booking')assert.equal(value.booking.currentHolderId,actors.b.id)
      }
    }
    for(const [phase,target] of [['before',comparisonBackend],['after',backend]]) {
      const sorted=[...times[phase]].sort((a,b)=>a-b)
      target[name]={samplesMs:times[phase],p50Ms:sorted[14],p95Ms:sorted[28]}
    }
  }
  fs.writeFileSync(path.join(config.output,'integrated-read-latency.json'),JSON.stringify({conditions:{warmups:10,samples:30,javaHeapMb:config.javaHeapMb,transport:'Genuine BFF/Gateway/native services',dataset:'Same small REAL/P2P business fixture; provider HTTP/HMAC bank credits and SePay image are synthetic, no real bank latency',comparison:'Both frontend BFFs receive the same alternating warmups and samples against one current backend; not a backend source comparison. Paired 1200-booking SQL optimization is separate.'},backend,comparisonBackend},null,2))
  const routes=[
    {name:'search',actor:'b',path:'/search',ready:page=>page.getByText('Sân kiểm thử main',{exact:true}).first()},
    {name:'schedule',actor:'b',path:`/facilities/${facility}/courts/${court}`,ready:page=>page.getByText('Còn trống',{exact:true}).first()},
    {name:'booking',actor:'b',path:`/customer/bookings/${booking}`,ready:page=>page.getByText('Lượt đặt đã chuyển nhượng một lần, không thể đăng bán tiếp.',{exact:true})},
    {name:'report',actor:'owner',path:`/owner/facilities/${facility}/reports`,ready:page=>page.getByRole('heading',{name:'Chuyển nhượng trực tiếp B → A',exact:true})},
    {name:'bank',actor:'a',path:'/customer/profile/bank',ready:page=>page.getByLabel('Chọn tài khoản đã khai',{exact:false})},
  ]
  for(const route of routes) for(let run=0;run<5;run++) for(const [phase,base] of (run%2===0?[['before',config.comparisonBaseURL],['after',config.baseURL]]:[['after',config.baseURL],['before',config.comparisonBaseURL]])) {
    const context=await browser.newContext({viewport:{width:1440,height:1000},locale:'vi-VN',timezoneId:'Asia/Ho_Chi_Minh'})
    try {
      const login=await context.request.post(base+'/api/v1/auth/login',{data:{identifier:actors[route.actor].email,password}})
      assert.equal(login.status(),200,await login.text())
      await installPeerContentTiming(context,route.name)
      await context.addInitScript(()=>{
        window.__perfLcp=0
        new PerformanceObserver(list=>{for(const item of list.getEntries()) window.__perfLcp=item.startTime}).observe({type:'largest-contentful-paint',buffered:true})
      })
      const page=await context.newPage(),cdp=await context.newCDPSession(page)
      page.on('pageerror',error=>errors.push({phase,route:route.name,message:error.message}))
      await cdp.send('Network.enable')
      await cdp.send('Network.emulateNetworkConditions',{offline:false,latency:40,downloadThroughput:1048576,uploadThroughput:1048576})
      for(const cache of ['cold','warm']) {
        await cdp.send('Network.setCacheDisabled',{cacheDisabled:cache==='cold'})
        if(cache==='cold') await cdp.send('Network.clearBrowserCache')
        else {await page.goto(base+route.path,{waitUntil:'networkidle'});await route.ready(page).waitFor()}
        const requests=[],responses=[]
        const track=request=>{if(request.url().includes('/api/v1/'))requests.push({method:request.method(),path:new URL(request.url()).pathname})}
        const receive=response=>{if(response.url().includes('/api/v1/')&&response.request().method()==='GET')responses.push({path:new URL(response.url()).pathname,status:response.status(),contentEncoding:response.headers()['content-encoding']??'identity'})}
        page.on('request',track)
        page.on('response',receive)
        await page.goto(base+route.path,{waitUntil:'domcontentloaded'})
        await route.ready(page).waitFor({timeout:30000})
        await page.waitForFunction(()=>window.__peerContentReady>0)
        const contentReady=await page.evaluate(()=>window.__peerContentReady)
        await page.waitForLoadState('networkidle')
        await page.evaluate(()=>document.fonts.ready)
        page.off('request',track)
        page.off('response',receive)
        const timing=await page.evaluate(()=>({lcp:window.__perfLcp,resources:performance.getEntriesByType('resource').map(item=>({path:new URL(item.name).pathname,encoded:item.encodedBodySize,transferred:item.transferSize,start:item.startTime,end:item.responseEnd,duration:item.duration}))}))
        const bytes=pattern=>timing.resources.filter(item=>pattern.test(item.path)).reduce((total,item)=>total+item.encoded,0)
        samples.push({phase,route:route.name,run,cache,contentReady,lcp:timing.lcp,initialJsBytes:bytes(/\.js$/),imageBytes:bytes(/\.(png|jpe?g|webp|avif|svg)$|^\/_next\/image$/),fontBytes:bytes(/\.(ttf|woff2?)$/),requests,responses,resources:timing.resources})
        fs.writeFileSync(path.join(config.output,'integrated-ui-progress.json'),JSON.stringify({samples,metadata,errors},null,2))
      }
      if(route.name==='bank') {
        const requests=[]
        page.on('request',request=>{if(request.method()==='GET'&&request.url().includes('/api/v1/payments/'))requests.push(new URL(request.url()).pathname)})
        for(let visit=0;visit<2;visit++) {
          await page.getByRole('link',{name:'Hồ sơ cá nhân',exact:true}).click()
          await page.getByRole('link',{name:'Ngân hàng',exact:true}).click()
          await route.ready(page).waitFor()
        }
        metadata.push({phase,run,requests})
      }
      if(route.name==='report') {
        const requests=[]
        page.on('request',request=>{if(request.url().includes('/api/v1/'))requests.push(new URL(request.url()).pathname)})
        const refreshed=page.waitForResponse(response=>response.url().includes('/dashboard?')&&response.request().method()==='GET')
        await page.evaluate(()=>window.dispatchEvent(new Event('focus')))
        assert.equal((await refreshed).status(),200)
        await route.ready(page).waitFor()
        metadata.push({phase,run,kind:'report-focus',requests})
      }
    } catch(error) {
      const page=context.pages().at(-1)
      if(page) {
        await page.screenshot({path:path.join(config.output,`performance-${phase}-${route.name}-failed.png`),fullPage:true}).catch(()=>{})
        fs.writeFileSync(path.join(config.output,`performance-${phase}-${route.name}-failed.txt`),await page.locator('body').innerText().catch(()=>''))
      }
      throw error
    } finally {await context.close()}
    console.log(`Measured ${phase} ${route.name} ${run+1}/5 cold+warm`)
  }
  assert.deepEqual(errors,[])
  assert.equal(samples.length,100)
  const median=values=>values.sort((a,b)=>a-b)[Math.floor(values.length/2)],comparison=[]
  for(const route of routes) for(const cache of ['cold','warm']) {
    const rows=['before','after'].map(phase=>samples.filter(row=>row.phase===phase&&row.route===route.name&&row.cache===cache))
    assert.ok(rows.every(group=>group.length===5))
    const before=median(rows[0].map(row=>row.contentReady)),after=median(rows[1].map(row=>row.contentReady))
    comparison.push({route:route.name,cache,beforeMs:before,afterMs:after,withinControl:after<=before+Math.max(before*.1,50)})
  }
  fs.writeFileSync(path.join(config.output,'integrated-ui-performance.json'),JSON.stringify({conditions:{browser:browser.version(),production:true,api:'Same genuine service graph and unchanged REAL/peer facts for both frontend snapshots',javaHeapMb:config.javaHeapMb,bffWarmup:'Both frontend BFFs receive identical 10 warmups and 30 alternating samples per native read before browser measurement',latencyMs:40,bytesPerSecond:1048576,runs:5,contentTiming:'MutationObserver and first visible animation frame; excludes Playwright polling/IPC delay',phaseOrder:'Alternating AB/BA by run',phases:'Preserved candidate-peer-policy versus current production snapshot; equivalent feature protocol, not original missing-feature build',caveat:'Small integrated dataset; backend 1200-booking latency is measured separately. Shared host memory and median controls are reported, never assumed.'},samples,metadata,comparison,errors},null,2))
  pass('Five cold/warm production browser runs for search, schedule, paid booking, REAL report and private bank profile; raw bytes, request waterfall and control outcomes saved')
}
