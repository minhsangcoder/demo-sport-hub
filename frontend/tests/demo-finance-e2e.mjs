// Genuine isolated BFF -> Gateway -> services -> PostgreSQL/RabbitMQ; no API response mocks.
// SQL bootstraps synthetic Owner/Admin roles + publishes a fixture facility, bypassing unrelated onboarding only.
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import {createRequire} from 'node:module'
import {execFileSync} from 'node:child_process'
import {browserBookingFlows} from './demo-finance-browser-flows.mjs'
const config=JSON.parse(fs.readFileSync(process.argv[2],'utf8'))
assert.match(config.pg,/^sporthub-finance-test-pg-[a-f0-9]+$/)
assert.ok(Object.values(config.databases).every(n=>/^\w+_system_phase12_[a-f0-9]+$/.test(n)))
const require=createRequire(import.meta.url),{chromium,request}=require(process.env.SPORTHUB_E2E_PLAYWRIGHT||'playwright')
const actors={},contexts=[],checks=[],errors=[],password='FinanceDemoSynthetic123!',output=config.output
const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms)),key=()=>crypto.randomUUID()
const sql=(service,statement)=>execFileSync('docker',['exec',config.pg,'psql','-U',config.pgUser,'-d',config.databases[service],'-v','ON_ERROR_STOP=1','-At','-c',statement],{windowsHide:true,encoding:'utf8'}).trim()
const pass=message=>{checks.push(message);console.log('PASS: '+message)}
const guest=await request.newContext({baseURL:config.baseURL})
async function poll(fn,predicate,seconds=40){const deadline=Date.now()+seconds*1000;let value;while(Date.now()<deadline){value=await fn();if(predicate(value))return value;await sleep(300)}throw Error('Condition not reached: '+JSON.stringify(value))}
async function call(actor,method,url,body,expected=200,headers={}){
 const a=actors[actor],options={method,headers:{...headers,...(a?.token?{Authorization:'Bearer '+a.token}:{})},...(body===undefined?{}:{data:body})}
 const response=await(a?.http||guest).fetch('/api/v1'+url,options);let payload;try{payload=await response.json()}catch{payload=await response.text()}
 assert.equal(response.status(),expected,`${actor} ${method} ${url}: ${JSON.stringify(payload)}`);return payload?.data??payload
}
async function register(name){
 const email=`finance-${name}-${config.run}@phase12.invalid`;actors[name]={email,http:await request.newContext({baseURL:config.baseURL})}
 const result=await call(name,'POST','/auth/register',{fullName:'Demo '+name,email,password},201)
 const otp=await poll(async()=>JSON.parse(fs.readFileSync(path.join(output,'smtp-codes.json'),'utf8'))[email],Boolean)
 await call(name,'POST','/auth/verify',{challengeId:result.verificationChallengeId,code:otp});actors[name].id=result.userId;await login(name)
}
async function login(name){const session=await call(name,'POST','/auth/login',{identifier:actors[name].email,password});actors[name].token=session.accessToken;return session}
let browser,fid,slot,courts={},originalQr
async function loginPage(name){const context=await browser.newContext({locale:'vi-VN',timezoneId:'America/Los_Angeles',viewport:{width:1440,height:1050}});contexts.push(context);const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));await page.goto(config.baseURL+'/login');await page.locator('input[name=identifier]').fill(actors[name].email);await page.locator('input[name=password]').fill(password);await page.getByRole('button',{name:'Đăng nhập ngay',exact:true}).click();await page.waitForURL(url=>!url.pathname.startsWith('/login'));return page}
async function hold(actor,name){return call(actor,'POST','/bookings/holds',{courtId:courts[name].id,...slot,expectedAmount:120000},201,{'Idempotency-Key':key()})}
async function book(actor,name){const h=await hold(actor,name);return call(actor,'POST','/bookings',{holdId:h.id},201,{'Idempotency-Key':key()})}
async function payment(actor,bookingId,extra={}){return call(actor,'POST','/payments',{bookingId,...extra},201,{'Idempotency-Key':key()})}
async function collect(actor,bookingId,extra={}){const p=await payment(actor,bookingId,extra);await call(actor,'POST',`/payments/${p.id}/demo/complete`,{outcome:'SUCCESS'});return p}
async function confirmed(actor,id){return poll(()=>call(actor,'GET',`/bookings/${id}`),d=>d.booking.status==='CONFIRMED')}
async function transfer(bookingId,{pay=true,viaBrowser=false}={}){
 const input={bookingId,price:100000,deadline:new Date(new Date(slot.startsAt).getTime()-60000).toISOString()}
 const quote=await call('a','POST','/transfers/quote',input);assert.equal(quote.mode,'DEMO');assert.equal(quote.feeBps,500);assert.equal(quote.quotedFeeAmount,'5000');assert.equal(quote.receiverFacilityId,fid);assert.equal('beneficiaryRevisionId' in quote,false)
 const listing=await call('a','POST','/transfers',{...input,acceptedTermsHash:quote.termsHash},201,{'Idempotency-Key':key()})
 await poll(()=>call('b','GET',`/transfers/${listing.id}`),d=>d.available)
 const acquisition=await call('b','POST',`/transfers/${listing.id}/acquisitions`,{acceptedTermsHash:quote.termsHash},201,{'Idempotency-Key':key()})
 const p=await payment('b',bookingId,{acquisitionId:acquisition.id})
 if(pay){
  if(viaBrowser){const page=await loginPage('b');await page.goto(config.baseURL+`/customer/payments/${p.id}`);await page.getByRole('heading',{name:'Điều khoản chuyển nhượng mô phỏng'}).waitFor();await page.getByRole('button',{name:'Thanh toán mô phỏng',exact:true}).click();await page.waitForURL(`**/customer/transfers/acquisitions/${acquisition.id}`);await page.getByRole('link',{name:'Mở lượt đặt và mã xác nhận vào sân của bạn'}).waitFor();await page.screenshot({path:path.join(output,'buyer-rights.png'),fullPage:true})}
  else await call('b','POST',`/payments/${p.id}/demo/complete`,{outcome:'SUCCESS'})
  await poll(()=>call('b','GET',`/transfers/acquisitions/${acquisition.id}`),d=>d.state==='SUCCESS')
 }
 return{listing,acquisition,payment:p}
}
async function cancel(actor,id){const quote=await call(actor,'POST',`/bookings/${id}/cancellation-quote`);return call(actor,'POST',`/bookings/${id}/cancel-with-policy`,{quoteId:quote.id,reason:'Synthetic DEMO cancellation'},200,{'Idempotency-Key':key()})}
try{
 for(const name of ['admin','owner','staff','a','b','c'])await register(name)
 sql('identity',`INSERT INTO user_roles(user_id,role) VALUES('${actors.admin.id}','ADMIN'),('${actors.owner.id}','OWNER')`);await login('admin');await login('owner')
 const category=await call('admin','POST','/admin/sport-categories',{name:'Finance DEMO '+config.run,active:true},201)
 const facility=await call('owner','POST','/owner/facilities',{name:'Finance DEMO '+config.run,phone:'+84901234567',addressLine:'Synthetic address',provinceCode:'01',province:'Hà Nội',wardCode:'00004',ward:'Ba Đình',timezone:'Asia/Ho_Chi_Minh',amenities:[]},201);fid=facility.id
 for(const name of ['example','rights','guest','counter','group','technical','cancelGuest','partial','uiOnline','uiGuest','uiLinked','uiGroup'])courts[name]=await call('owner','POST',`/owner/facilities/${fid}/courts`,{code:name,name:'Sân DEMO '+name,sportCategoryId:category.id,enabled:true},201)
 await call('owner','PUT',`/schedules/facilities/${fid}/hours`,{intervals:Array.from({length:7},(_,i)=>({dayOfWeek:i+1,opensAt:'00:00',closesAt:'23:55',slotMinutes:5}))})
 const today=new Date(Date.now()+7*3600000).toISOString().slice(0,10)
 for(let i=1;i<=7;i++)await call('owner','POST',`/schedules/facilities/${fid}/pricing`,{dayOfWeek:i,startsAt:'00:00',endsAt:'23:55',pricePerSlot:120000,priority:0,label:'DEMO court receipt',effectiveFrom:today,currency:'VND'},201)
 sql('facility',`UPDATE facilities SET status='ACTIVE',version=version+1 WHERE id='${fid}'`)
 const start=Math.ceil((Date.now()+20*60000)/300000)*300000;slot={startsAt:new Date(start).toISOString(),endsAt:new Date(start+300000).toISOString()}
 await call('a','POST','/bookings/holds',{courtId:courts.example.id,...slot,expectedAmount:120000},409,{'Idempotency-Key':key()})
 const refundPolicy=await call('owner','PUT',`/owner/facilities/${fid}/refund-policy`,{rateBps:8000,version:0})
 await call('owner','PUT',`/owner/facilities/${fid}/transfer-policy`,{enabled:true,minLeadSeconds:0})
 const b=await book('a','example');await collect('a',b.id);await confirmed('a',b.id)
 await call('a','POST','/transfers/quote',{bookingId:b.id,price:100000,deadline:new Date(start-60000).toISOString()},409)
 await call('owner','PUT',`/owner/facilities/${fid}/transfer-fee-policy`,{feeBps:500,version:0})
 const permissions=['BOOKING_READ','BOOKING_CREATE_COUNTER','BOOKING_PAYMENT_COUNTER','BOOKING_CHECK_IN','TRANSFER_PAYOUT','BOOKING_REFUND']
 const profile=await call('owner','POST',`/staff-profiles/facilities/${fid}`,{fullName:'Finance operator',email:actors.staff.email,active:true})
 const invitation=await call('owner','POST',`/staff-profiles/facilities/${fid}/${profile.id}/invitations`,{identifier:actors.staff.email,permissions})
 await call('staff','POST',`/users/me/staff-invitations/${invitation.id}/accept`)
 const binding=await call('owner','POST',`/staff-bindings/facilities/${fid}`,{identifier:actors.staff.email,permissions});await login('staff')
 browser=await chromium.launch({channel:process.env.SPORTHUB_E2E_BROWSER||'chrome',headless:true})
 const deal=await transfer(b.id,{viaBrowser:true});const buyer=await confirmed('b',b.id);assert.equal(buyer.booking.currentHolderId,actors.b.id)
 const panel=await call('owner','GET',`/bookings/demo-finance/facility/${fid}`);assert.equal(panel.settlements[0].payoutState,'UNPAID')
 const ownerPage=await loginPage('owner');await ownerPage.goto(config.baseURL+`/owner/bookings?facilityId=${fid}`);await ownerPage.getByRole('button',{name:'Trả tiền mô phỏng cho A',exact:true}).click();await ownerPage.getByText('Đã trả mô phỏng',{exact:true}).waitFor()
 const cancelled=await cancel('b',b.id);assert.equal(cancelled.refund.targetAmount,'80000');await ownerPage.getByRole('button',{name:'Tải lại nghĩa vụ',exact:true}).click();await ownerPage.getByRole('button',{name:'Hoàn tiền mô phỏng',exact:true}).click();await ownerPage.getByText('Đã hoàn mô phỏng',{exact:true}).waitFor()
 const range=`fromDate=${today}&toDate=${today}`,report=await call('owner','GET',`/bookings/reports/facility/${fid}/dashboard?${range}`)
 assert.equal(report.moneyAvailability,'COMPLETE');assert.deepEqual([report.summary.money.courtCollected,report.summary.money.transferCollected,report.summary.money.transferFees,report.summary.money.sellerPaid,report.summary.money.policyRefunded,report.summary.money.operatingRevenue,report.summary.money.afterRefund],['120000','100000','5000','95000','80000','125000','45000'])
 await ownerPage.goto(config.baseURL+`/owner/facilities/${fid}/reports`);await ownerPage.getByText('125.000 ₫',{exact:true}).waitFor();await ownerPage.screenshot({path:path.join(output,'actual-report-125000-45000.png'),fullPage:true})
 pass('Real DEMO payment B→facility automatically hands over without A payout; Owner UI executes 95k/80k; DB-backed report 125k/45k without bank setup')
 const rights=await book('a','rights');await collect('a',rights.id);const old=await confirmed('a',rights.id);originalQr=old.checkinToken;const rightsDeal=await transfer(rights.id)
 await call('staff','POST',`/bookings/demo-finance/settlements/${rightsDeal.acquisition.id}/pay-seller`,{outcome:'FAILED'},200,{'Idempotency-Key':key()})
 await call('owner','POST',`/bookings/${rights.id}/check-in`,{token:originalQr},403)
 const right=await confirmed('b',rights.id);assert.equal((await call('owner','POST',`/bookings/${rights.id}/check-in`,{token:right.checkinToken})).status,'CHECKED_IN')
 await call('staff','POST',`/bookings/demo-finance/settlements/${rightsDeal.acquisition.id}/pay-seller`,{outcome:'SUCCESS'},200,{'Idempotency-Key':key()})
 pass('B check-in uses rotated credential while N FAILED; old A QR is denied; scoped Staff retries payout successfully')
 const linkedPhone='+84977777778';await call('a','PATCH','/users/me',{phone:linkedPhone})
 const phoneChallenge=(await call('a','GET','/users/me/contact-challenges')).find(c=>c.channel==='PHONE');assert.ok(phoneChallenge)
 const phoneCode=await poll(async()=>JSON.parse(fs.readFileSync(path.join(output,'smtp-codes.json'),'utf8'))[linkedPhone.slice(1)+'@sms.sporthub.local'],Boolean)
 await call('a','POST','/auth/verify',{challengeId:phoneChallenge.id,code:phoneCode})
 for(const name of ['guest','counter','cancelGuest']){
  const counter=await call('staff','POST','/bookings/counter',{courtId:courts[name].id,...slot,expectedAmount:120000,guestName:'Guest '+name,guestPhone:name==='counter'?linkedPhone:'+84977777777',paymentMethod:'DEMO',refundPolicyHash:refundPolicy.termsHash},201,{'Idempotency-Key':key()});assert.equal(counter.currentHolderId,name==='counter'?actors.a.id:null);assert.equal(counter.customerId,name==='counter'?actors.a.id:null);assert.equal(counter.createdBy,actors.staff.id)
  await call('staff','POST',`/bookings/${counter.id}/check-in`,{token:'NO_PAYMENT'},409)
  await collect('staff',counter.id);await confirmed('staff',counter.id)
  if(name==='cancelGuest'){const request=await cancel('staff',counter.id);await call('staff','POST',`/bookings/demo-finance/refunds/${request.refund.id}/complete`,{outcome:'SUCCESS'},200,{'Idempotency-Key':key()});assert.equal(sql('booking',`SELECT (payee_id IS NULL)::text||':'||(guest_booking_id='${counter.id}')::text FROM demo_money_events WHERE subject_id='${request.refund.id}' AND outcome='SUCCESS'`),'true:true')}
  else{const current=(await call('staff','GET',`/bookings/${counter.id}`)).booking;assert.equal((await call('staff','POST',`/bookings/facility/${fid}/check-in-by-code`,{bookingCode:current.bookingCode})).status,'CHECKED_IN')}
 }
 pass('Counter Guest records retain NULL ownership; insufficient payment blocks check-in; full DEMO enables manual check-in; Guest refund never pays Staff')
 const groupHold=await hold('a','group');let group=await call('a','POST','/groups',{holdId:groupHold.id,name:'DEMO shared court',maxMembers:5},201,{'Idempotency-Key':key()})
 const invite=await call('a','GET',`/groups/${group.id}/invite`);await call('c','POST','/groups/join',{code:invite.code});group=await call('a','PUT',`/groups/${group.id}/allocations`,{mode:'EQUAL'})
 for(const name of ['a','c']){const member=group.members.find(m=>m.userId===actors[name].id);await collect(name,group.id,{memberId:member.id})}
 const groupBooking=await confirmed('a',group.id);assert.equal((await call('staff','POST',`/bookings/${group.id}/check-in`,{token:groupBooking.checkinToken})).status,'CHECKED_IN')
 pass('Group actual contributions sum to full court price before signed QR check-in')
 await browserBookingFlows({actors,fid,courts,slot,call,loginPage,confirmed,pass,baseURL:config.baseURL,linkedPhone,password,output})
 const technical=await book('a','technical');await collect('a',technical.id);await confirmed('a',technical.id);const failedDeal=await transfer(technical.id,{pay:false})
 const rabbit=config.containers.find(n=>n.startsWith('sporthub-phase12-rabbit-'));assert.ok(rabbit)
 execFileSync('docker',['pause',rabbit],{windowsHide:true})
 try{await call('b','POST',`/payments/${failedDeal.payment.id}/demo/complete`,{outcome:'SUCCESS'});console.log('Waiting for isolated acquisition expiry with Rabbit deliberately paused');await sleep(50000)}finally{execFileSync('docker',['unpause',rabbit],{windowsHide:true})}
 const technicalRequest=await poll(()=>call('owner','GET',`/bookings/demo-finance/facility/${fid}`),d=>d.refunds.some(r=>r.acquisitionId===failedDeal.acquisition.id&&r.reason==='TECHNICAL_TRANSFER_FAILURE'),70)
 const full=technicalRequest.refunds.find(r=>r.acquisitionId===failedDeal.acquisition.id);assert.equal(full.targetAmount,'100000')
 assert.equal(technicalRequest.settlements.some(s=>s.acquisitionId===failedDeal.acquisition.id),false)
 await call('b','GET',`/bookings/${technical.id}`,undefined,403)
 await call('staff','POST',`/bookings/demo-finance/refunds/${full.id}/complete`,{outcome:'SUCCESS'},200,{'Idempotency-Key':key()})
 assert.equal(sql('booking',`SELECT decision FROM transfer_acquisition_decisions WHERE acquisition_id='${failedDeal.acquisition.id}'`),'ABORTED')
 pass('Collected P after authoritative aborted handoff yields full 100k technical refund, F/N=0, no B ownership; no bank or implicit policy deduction')
 const grants=await call('owner','GET',`/staff-bindings/facilities/${fid}/${binding.id}/permissions`);await call('owner','PUT',`/staff-bindings/facilities/${fid}/${binding.id}/permissions`,{permissions:['BOOKING_READ'],expectedVersion:grants.version})
 await call('staff','POST',`/bookings/demo-finance/refunds/${full.id}/complete`,{outcome:'SUCCESS'},403,{'Idempotency-Key':key()})
 await call('staff','GET',`/bookings/reports/facility/${fid}/dashboard?${range}`,undefined,403)
 await call('admin','POST',`/bookings/demo-finance/refunds/${full.id}/complete`,{outcome:'SUCCESS'},403,{'Idempotency-Key':key()});await call('admin','GET','/admin/bookings/demo-finance')
 const final=await call('owner','GET',`/bookings/reports/facility/${fid}/dashboard?${range}`);assert.equal(final.moneyAvailability,'COMPLETE');assert.equal(final.summary.money.technicalRefunded,'100000')
 assert.equal(sql('payment','SELECT count(*) FROM seller_payout_account_revisions'),'0')
 assert.equal(sql('payment','SELECT count(*) FROM transfer_settlements'),'0')
 assert.equal(sql('booking',"SELECT count(*) FROM (SELECT kind,subject_id,payment_id FROM demo_money_events WHERE outcome='SUCCESS' GROUP BY kind,subject_id,payment_id HAVING count(*)>1) duplicated"),'0')
 pass('Live grant revocation and Admin read-only scope; final DEMO report has technical refunds separate; zero bank-beneficiary or legacy settlement setup')
 assert.deepEqual(errors,[])
 fs.writeFileSync(path.join(output,'result.json'),JSON.stringify({checks,fixture:{facilityId:fid,bookingId:b.id,deal,rightsBookingId:rights.id,technicalBookingId:technical.id},initialReport:report,finalReport:final,browserErrors:errors},null,2))
}catch(error){let n=0;for(const context of contexts)for(const page of context.pages()){fs.writeFileSync(path.join(output,`failure-${n}.txt`),await page.locator('body').innerText().catch(()=>''));await page.screenshot({path:path.join(output,`failure-${n++}.png`),fullPage:true}).catch(()=>{})}throw error
}finally{for(const context of contexts)await context.close();if(browser)await browser.close();for(const actor of Object.values(actors))await actor.http.dispose();await guest.dispose()}
