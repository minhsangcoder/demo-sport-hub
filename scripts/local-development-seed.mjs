// Synthetic local demo only. Business states are created through actual APIs.
// The first Admin role is the sole explicit database bootstrap.
import fs from 'node:fs'
import path from 'node:path'
import assert from 'node:assert/strict'
import {createRequire} from 'node:module'
import {execFileSync} from 'node:child_process'
import {randomUUID} from 'node:crypto'
const root=path.resolve(import.meta.dirname,'..')
const config=JSON.parse(fs.readFileSync(path.join(root,'tmp/current-development/runtime.json'),'utf8'))
assert.equal(config.baseURL,'http://127.0.0.1:3000')
assert.equal(config.pg,'sporthub-current-6bd0-postgres')
assert.ok(Object.values(config.databases).every(value=>value.endsWith('_current_development')))
const require=createRequire(import.meta.url)
const {request,chromium}=require(process.env.SPORTHUB_DEV_PLAYWRIGHT||path.join(process.env.USERPROFILE,'.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright'))
const out=config.output, fixtureFile=path.join(out,'demo-accounts.json'), password='SportHubDemo123!',actors={},checks=[]
const wait=ms=>new Promise(resolve=>setTimeout(resolve,ms))
const headers=()=>({'Idempotency-Key':randomUUID()})
const guest=await request.newContext({baseURL:config.baseURL})
async function call(name,method,url,body,expected=200,extra={}){
 const actor=actors[name],options={method,headers:actor?.token?{Authorization:'Bearer '+actor.token}:{},...extra}
 if(body!==undefined)options.data=body
 const r=await (actor?.http||guest).fetch('/api/v1'+url,options)
 const data=await r.json().catch(()=>null)
 assert.equal(r.status(),expected,method+' '+url+' '+JSON.stringify(data))
 return data?.data??data
}
async function poll(fn,test,seconds=45){const deadline=Date.now()+seconds*1000;while(Date.now()<deadline){const data=await fn();if(test(data))return data;await wait(500)}throw Error('Polling timeout')}
async function login(name){const data=await call(name,'POST','/auth/login',{identifier:actors[name].email,password});actors[name].token=data.accessToken;return data}
async function register(name,fullName){
 const email=name+'@sporthub.demo.invalid'
 actors[name]={email,http:await request.newContext({baseURL:config.baseURL})}
 const account=await call(name,'POST','/auth/register',{fullName,email,password},201)
 const messages=await poll(async()=>fetch(`http://127.0.0.1:${config.ports.mail}/api/v1/search?query=`+encodeURIComponent('to:'+email)).then(r=>r.json()),data=>data.messages?.length>0)
 const message=await fetch(`http://127.0.0.1:${config.ports.mail}/api/v1/message/${messages.messages[0].ID}`).then(r=>r.json())
 const otp=(message.Text||message.HTML||'').match(/verification code is: ([0-9]{6})/)[1]
 await call(name,'POST','/auth/verify',{challengeId:account.verificationChallengeId,code:otp})
 actors[name].id=account.userId;await login(name)
}
let fixture
try {
 if(fs.existsSync(fixtureFile)){
  fixture=JSON.parse(fs.readFileSync(fixtureFile,'utf8'))
  for(const [name,account] of Object.entries(fixture.accounts)){actors[name]={...account,http:await request.newContext({baseURL:config.baseURL})};await login(name)}
  console.log('Existing demo data retained; all five demo accounts can log in.')
 }else{
  await register('admin','Quản trị viên Demo')
  await register('owner','Chủ cơ sở Demo')
  await register('staff','Nhân viên Demo')
  await register('customer','Khách hàng Demo')
  await register('buyer','Người nhận chuyển nhượng Demo')
  execFileSync('docker',['exec',config.pg,'psql','-U',config.pgUser,'-d',config.databases.identity,'-v','ON_ERROR_STOP=1','-c',`INSERT INTO user_roles(user_id,role) VALUES('${actors.admin.id}','ADMIN') ON CONFLICT DO NOTHING`],{windowsHide:true})
  await login('admin')
  const categories=[]
  for(const name of ['Tennis','Pickleball','Cầu lông','Bóng đá','Bóng rổ'])categories.push(await call('admin','POST','/admin/sport-categories',{name,active:true},201))
  const app=await call('owner','POST','/owner-applications',{
   legal:{representativeName:'Đại diện Demo',identityNumber:'DEMO-NOT-A-REAL-ID',businessName:'SportHub Development Demo',bankName:'Ngân hàng mô phỏng',bankAccountHolder:'Đại diện Demo',bankAccountNumber:'0000000000'},
   facility:{name:'SportHub Demo Riverside',phone:'+84901234567',addressLine:'Địa chỉ minh họa Riverside',provinceCode:'01',province:'Hà Nội',wardCode:'00004',ward:'Ba Đình',description:'Cơ sở DEMO trên dữ liệu development riêng. Dùng thử đặt sân, booking nhóm và chuyển nhượng; không phải địa điểm thương mại thật.',timezone:'Asia/Ho_Chi_Minh',latitude:21.04,longitude:105.84,amenities:['Bãi đỗ xe','Phòng thay đồ','Nước uống','Đèn chiếu sáng'],contactEmail:actors.owner.email}
  },201)
  const fid=app.facilityId
  const type=await call('owner','POST',`/owner/facilities/${fid}/court-types`,{name:'Ngoài trời',active:true},201)
  const courts=[]
  for(const [code,name,category] of [['T01','Sân Tennis 01',categories[0]],['T02','Sân Tennis 02',categories[0]],['P01','Sân Pickleball 01',categories[1]],['B01','Sân cầu lông 01',categories[2]]]){
   courts.push(await call('owner','POST',`/owner/facilities/${fid}/courts`,{code,name,sportCategoryId:category.id,courtTypeId:type.id,enabled:true},201))
  }
  const intervals=Array.from({length:7},(_,i)=>({dayOfWeek:i+1,opensAt:'06:00',closesAt:'23:00',slotMinutes:30}))
  await call('owner','PUT',`/schedules/facilities/${fid}/hours`,{intervals})
  for(let day=1;day<=7;day++){
   await call('owner','POST',`/schedules/facilities/${fid}/pricing`,{dayOfWeek:day,startsAt:'06:00',endsAt:'23:00',pricePerSlot:100000,priority:0,label:'Giá demo cơ bản mỗi 30 phút',effectiveFrom:'2026-01-01',currency:'VND'},201)
  }
  const buffer=fs.readFileSync(path.join(root,'frontend/public/images/court-aerial.jpg'))
  for(const name of ['demo-identity.jpg','demo-location.jpg'])await call('owner','POST',`/owner/facilities/${fid}/documents`,undefined,200,{multipart:{file:{name,mimeType:'image/jpeg',buffer}}})
  await call('owner','POST',`/owner/facilities/${fid}/images`,undefined,201,{multipart:{file:{name:'court-aerial.jpg',mimeType:'image/jpeg',buffer}}})
  await call('owner','POST',`/owner-applications/${app.id}/submit`)
  await poll(()=>call('admin','GET',`/admin/owner-applications/${app.id}`),d=>d.application.state==='PENDING_APPROVAL')
  await call('admin','POST',`/admin/owner-applications/${app.id}/decision`,{action:'APPROVE',reason:'Phê duyệt hồ sơ mô phỏng để trình diễn trong development; tài liệu không có giá trị pháp lý.',commissionPercent:5})
  await poll(()=>call('admin','GET',`/admin/owner-applications/${app.id}`),d=>d.application.state==='APPROVED')
  assert.ok((await login('owner')).user.roles.includes('OWNER'))
  const permissions=['BOOKING_READ','BOOKING_CREATE_COUNTER','BOOKING_CHECK_IN','BOOKING_COMPLETE','SCHEDULE_READ','BOOKING_CANCEL_PENDING','SCHEDULE_MANAGE','COURT_STATUS_MANAGE','COURT_MAINTENANCE_MANAGE']
  const profile=await call('owner','POST',`/staff-profiles/facilities/${fid}`,{fullName:'Nhân viên Demo',email:actors.staff.email,active:true})
  const invitation=await call('owner','POST',`/staff-profiles/facilities/${fid}/${profile.id}/invitations`,{identifier:actors.staff.email,permissions})
  await call('staff','POST',`/users/me/staff-invitations/${invitation.id}/accept`)
  await call('owner','POST',`/staff-bindings/facilities/${fid}`,{identifier:actors.staff.email,permissions})
  assert.ok((await login('staff')).user.roles.includes('STAFF'))
  fixture={createdAt:new Date().toISOString(),baseURL:config.baseURL,mailURL:`http://127.0.0.1:${config.ports.mail}`,password,facilityId:fid,applicationId:app.id,courts,accounts:Object.fromEntries(Object.entries(actors).map(([key,a])=>[key,{id:a.id,email:a.email}]))}
  fs.writeFileSync(fixtureFile,JSON.stringify(fixture,null,2))
  console.log('Created verified demo accounts, approved Owner/facility, four courts, 7-day hours/pricing and actual Staff consent.')
 }
 fixture.samples??={}
 const saveFixture=()=>fs.writeFileSync(fixtureFile,JSON.stringify(fixture,null,2))
 const tomorrow=new Date(Date.now()+7*3600000+86400000).toISOString().slice(0,10)
 const at=hour=>`${tomorrow}T${hour}:00:00+07:00`
 async function paidSample(courtId,hour){
  const hold=await call('customer','POST','/bookings/holds',{courtId,startsAt:at(hour),endsAt:at(String(Number(hour)+1)),expectedAmount:200000},201,{headers:{Authorization:'Bearer '+actors.customer.token,...headers()}})
  const booking=await call('customer','POST','/bookings',{holdId:hold.id},201,{headers:{Authorization:'Bearer '+actors.customer.token,...headers()}})
  const payment=await call('customer','POST','/payments',{bookingId:booking.id,provider:'DEMO'},201,{headers:{Authorization:'Bearer '+actors.customer.token,...headers()}})
  await call('customer','POST',`/payments/${payment.id}/demo/complete`,{outcome:'SUCCESS'})
  await poll(()=>call('customer','GET',`/bookings/${booking.id}`),data=>data.booking.status==='CONFIRMED')
  return {bookingId:booking.id,paymentId:payment.id}
 }
 if(!fixture.samples.personal){fixture.samples.personal=await paidSample(fixture.courts[0].id,'14');saveFixture()}
 if(!fixture.samples.group){
  const hold=await call('customer','POST','/bookings/holds',{courtId:fixture.courts[3].id,startsAt:at('16'),endsAt:at('17'),expectedAmount:200000},201,{headers:{Authorization:'Bearer '+actors.customer.token,...headers()}})
  const group=await call('customer','POST','/groups',{holdId:hold.id,name:'Nhóm chơi thử Demo',maxMembers:4},201,{headers:{Authorization:'Bearer '+actors.customer.token,...headers()}})
  const invitation=await call('customer','GET',`/groups/${group.id}/invite`)
  await call('buyer','POST','/groups/join',{code:invitation.code})
  const divided=await call('customer','PUT',`/groups/${group.id}/allocations`,{mode:'EQUAL'})
  assert.equal(divided.members.reduce((total,m)=>total+m.amountDue,0),200000)
  fixture.samples.group={groupId:group.id};saveFixture()
 }
 if(!fixture.samples.transferBooking){fixture.samples.transferBooking=await paidSample(fixture.courts[1].id,'18');saveFixture()}
 if(!fixture.samples.listing){
  // Explicit local eligibility example only; this does not enable settlement/payout.
  await call('owner','PUT',`/owner/facilities/${fixture.facilityId}/transfer-policy`,{enabled:true,minLeadSeconds:3600})
  const listing=await call('customer','POST','/transfers',{bookingId:fixture.samples.transferBooking.bookingId,price:150000,deadline:at('16')},201,{headers:{Authorization:'Bearer '+actors.customer.token,...headers()}})
  assert.equal(listing.state,'ACTIVE');fixture.samples.listing={listingId:listing.id};saveFixture()
 }
 checks.push('Backend quote/hold/create/DEMO callback/outbox/CONFIRMED actual booking','Group invite/join/exact equal allocation','Paid-booking transfer listing ACTIVE')
 assert.equal((await call('guest','GET',`/facilities/${fixture.facilityId}`)).status,'ACTIVE')
 assert.equal((await call('guest','GET',`/facilities/${fixture.facilityId}/courts`)).length,4)
 const result=await call('guest','GET','/bookings/search?q=SportHub')
 assert.ok(JSON.stringify(result).includes(fixture.facilityId))
 const denied=await actors.customer.http.get('/api/v1/admin/accounts',{headers:{Authorization:'Bearer '+actors.customer.token}});assert.equal(denied.status(),403)
 checks.push('Public facility/courts/search return actual approved development data','Customer denied Admin API')
 const browser=await chromium.launch({channel:'msedge',headless:true})
 const errors=[]
 try{
  for(const [name,route] of [['customer','/customer/bookings'],['owner','/owner'],['staff','/staff'],['admin','/admin']]){
   const ctx=await browser.newContext({viewport:{width:1440,height:1000},locale:'vi-VN'})
   const page=await ctx.newPage();page.on('pageerror',e=>errors.push(String(e)))
   await page.goto(config.baseURL+'/login');await page.locator('input[name=identifier]').fill(actors[name].email);await page.locator('input[name=password]').fill(password)
   await page.getByRole('button',{name:'Đăng nhập',exact:true}).click();await page.waitForURL(u=>!u.pathname.startsWith('/login'),{timeout:25000})
   await page.goto(config.baseURL+route);await page.locator('h1').first().waitFor();await wait(1200)
   await page.screenshot({path:path.join(out,name+'-dashboard.png'),fullPage:true})
   const alerts=await page.getByRole('alert').evaluateAll(nodes=>nodes.filter(n=>n.id!=='__next-route-announcer__'&&n.textContent.trim()).map(n=>n.textContent))
   assert.deepEqual(alerts,[],'Browser '+name+' errors: '+alerts.join('; '))
   checks.push('Browser '+name+' login/dashboard real APIs')
   await ctx.close()
  }
  const ctx=await browser.newContext({viewport:{width:1440,height:1000},locale:'vi-VN'}),page=await ctx.newPage()
  await page.goto(config.baseURL);await page.locator('h1').first().waitFor();await wait(1500);await page.screenshot({path:path.join(out,'home.png'),fullPage:true})
  await page.goto(config.baseURL+'/facilities/'+fixture.facilityId);await page.getByRole('heading',{name:'SportHub Demo Riverside'}).first().waitFor({timeout:20000});await page.screenshot({path:path.join(out,'facility.png'),fullPage:true})
  checks.push('Browser home/facility loaded');await ctx.close();assert.deepEqual(errors,[])
 }finally{await browser.close()}
 fs.writeFileSync(path.join(out,'smoke-results.json'),JSON.stringify({status:'PASS',testedAt:new Date().toISOString(),checks,browserErrors:errors},null,2))
 console.log('PASS: '+checks.length+' live smoke checks, 0 browser errors. Project remains running.')
} finally {
 for(const a of Object.values(actors))await a.http.dispose()
 await guest.dispose()
}
