import assert from 'node:assert/strict'
import {execFileSync} from 'node:child_process'
import {createServer} from 'node:http'

export async function peerSystemFaults({config,call,actors,bank,paidBook,publish,acquire,attempt,declare,evidence,poll,key,slot,facility,main,buyerDetail,pass}) {
  assert.equal(config.fixtureClockEnabled,true)
  async function advance(instant) {
    const response=await fetch(config.faultControlURL+'/clock',{method:'POST',headers:{Authorization:'Bearer '+config.faultControlToken,'Content-Type':'application/json'},body:JSON.stringify({instant:new Date(instant).toISOString()})})
    assert.equal(response.status,200,await response.text())
  }
  const seller='clockSeller'
  await bank(seller);await bank('timeoutB','000888999444');await bank('noshowB','000888999555');await bank('presentB','000888999666')
  const absent=await paidBook(seller,'guest'),timely=await paidBook(seller,'group'),expiring=await paidBook(seller,'broker'),probe=await paidBook(seller,'fault')
  const absentSale=await publish(seller,absent.booking),timelySale=await publish(seller,timely.booking),expiringSale=await publish(seller,expiring.booking)
  const probeSale=await publish(seller,probe.booking)
  const absentPurchase=await acquire('noshowB',absentSale),timelyPurchase=await acquire('presentB',timelySale),old=await acquire('timeoutB',expiringSale)
  const absentDeclaration=await declare('noshowB',absentPurchase.id),timelyDeclaration=await declare('presentB',timelyPurchase.id)
  for(const [id,declaration] of [[absentPurchase.id,absentDeclaration],[timelyPurchase.id,timelyDeclaration]])
    await call(seller,'POST',`/transfers/acquisitions/${id}/deny-receipt`,{version:declaration.value.version,termsHash:declaration.value.termsHash},200,key())
  await call('staff','POST',`/transfers/facilities/${facility}/cases/${timelyPurchase.id}/presence`,{party:'BUYER',reason:'Buyer physically arrived before cutoff in isolated fixture'},200,key())
  await call('timeoutB','POST',`/transfers/conversations/${old.conversationId}/messages`,{text:'Chat alone must not extend payBy'},200,key())
  const waiting=await attempt('timeoutB',old.id)
  for(const service of ['booking','transfer','payment']) {
    const restarted=await fetch(config.faultControlURL+'/restart-'+service,{method:'POST',headers:{Authorization:'Bearer '+config.faultControlToken}})
    assert.equal(restarted.status,200,await restarted.text())
  }
  const recovered=await attempt('timeoutB',old.id)
  assert.equal(recovered.payBy,waiting.payBy)
  assert.equal(recovered.phase,'WAITING_PAYMENT')
  assert.equal((await attempt('presentB',timelyPurchase.id)).phase,'DISPUTED')
  pass('Restart Booking, Transfer and Payment with live waiting and disputed attempts; the original deadline and canonical claim survive')
  await advance(Date.parse(waiting.payBy)+1)
  assert.equal((await call('timeoutB','GET','/payments/bank-profile')).canChange,true)
  await poll(()=>call('b','GET',`/transfers/${expiringSale.listing.id}`),value=>value.available)
  assert.equal((await attempt('timeoutB',old.id)).phase,'EXPIRED')
  assert.ok((await call('timeoutB','GET',`/transfers/conversations/${old.conversationId}/messages?after=0&size=50`)).length)
  const replacement=await acquire('b',expiringSale)
  const latePhoto=await evidence('timeoutB',old.id)
  await call('timeoutB','POST',`/transfers/acquisitions/${old.id}/report-late-payment`,{termsHash:waiting.termsHash,bankTransactionReference:'LATE-'+config.run,evidenceIds:[latePhoto.id]},200,key())
  assert.equal((await attempt('b',replacement.id)).phase,'WAITING_PAYMENT')
  assert.equal((await call('timeoutB','GET','/payments/bank-profile')).canChange,true)
  pass('Five-minute chat-only expiry reopens exactly its listing; old chat/proof remain while late money report cannot freeze replacement or keep seller dispute lock')
  const replacementDeclaration=await declare('b',replacement.id)
  await call(seller,'POST',`/transfers/acquisitions/${replacement.id}/deny-receipt`,{version:replacementDeclaration.value.version,termsHash:replacementDeclaration.value.termsHash},200,key())
  const disputed=await attempt('b',replacement.id)
  const rabbit=config.containers.find(name=>name.startsWith('sporthub-phase12-rabbit-'))
  assert.equal(rabbit,'sporthub-phase12-rabbit-'+config.run)
  execFileSync('docker',['pause',rabbit],{windowsHide:true})
  try {
    await call('owner','POST',`/transfers/facilities/${facility}/cases/${replacement.id}/resolve`,{version:disputed.version,outcome:'BUYER_FRAUD',reason:'Synthetic explicit intentional fraud finding for authority race',evidenceIds:[replacementDeclaration.photo.id]},200,key())
    const denied=await call('b','POST',`/transfers/${probeSale.listing.id}/acquisitions`,{acceptedTermsHash:probeSale.terms.termsHash},403,key())
    assert.equal(denied.code,'TRANSFER_MARKET_SUSPENDED')
    assert.equal((await call('b','GET','/users/me/market-restriction')).state,'NOT_RECORDED')
  } finally {execFileSync('docker',['unpause',rabbit],{windowsHide:true})}
  await poll(()=>call('b','GET','/users/me/market-restriction'),value=>value.strikeCount===1)
  const correction={reason:'Additional synthetic evidence retracts only the fraud finding',evidenceIds:[replacementDeclaration.photo.id]},correctionKey=key()
  await call('owner','POST',`/transfers/facilities/${facility}/cases/${replacement.id}/fraud-correction`,correction,200,correctionKey)
  await call('owner','POST',`/transfers/facilities/${facility}/cases/${replacement.id}/fraud-correction`,correction,200,correctionKey)
  await poll(()=>call('b','GET','/users/me/market-restriction'),value=>value.strikeCount===0)
  assert.equal((await attempt('b',replacement.id)).phase,'ABORTED_A_RETAINS')
  pass('With broker paused, canonical fraud decision blocks new purchase before Identity projection; audited correction clears restriction once without rewriting usage')
  const lostPurchase=await acquire('c',probeSale),lostDeclaration=await declare('c',lostPurchase.id)
  const lostBody={version:lostDeclaration.value.version,termsHash:lostDeclaration.value.termsHash},lostKey=key()
  let forwarded=false,forwardError
  const drop=createServer(async(request,response)=>{
    try {
      const result=await fetch(config.baseURL+`/api/v1/transfers/acquisitions/${lostPurchase.id}/confirm-receipt`,{method:'POST',headers:{...lostKey,Authorization:'Bearer '+actors[seller].token,'Content-Type':'application/json'},body:JSON.stringify(lostBody),signal:AbortSignal.timeout(20000)})
      assert.equal(result.status,200)
      assert.equal((await result.json()).data.phase,'COMPLETED')
      forwarded=true
      request.socket.destroy()
    } catch(error) {forwardError=error;response.writeHead(500).end()}
  })
  await new Promise(resolve=>drop.listen(0,'127.0.0.1',resolve))
  try {
    let lost=false
    try {await fetch(`http://127.0.0.1:${drop.address().port}`,{method:'POST',signal:AbortSignal.timeout(25000)})}
    catch {lost=true}
    if(forwardError)throw forwardError
    assert.ok(lost&&forwarded,'The receipt reply is lost only after a genuine committed handoff')
  } finally {await new Promise(resolve=>drop.close(resolve))}
  const committed=await call('c','GET',`/bookings/${probe.booking.id}`)
  await call(seller,'POST',`/transfers/acquisitions/${lostPurchase.id}/confirm-receipt`,lostBody,200,lostKey)
  const replayed=await call('c','GET',`/bookings/${probe.booking.id}`)
  assert.equal(replayed.booking.currentHolderId,actors.c.id)
  assert.equal(replayed.booking.version,committed.booking.version)
  assert.equal(replayed.booking.bookingCode,committed.booking.bookingCode)
  assert.equal(replayed.booking.checkinTokenVersion,committed.booking.checkinTokenVersion)
  pass('A real receipt acknowledgment commits through BFF/Gateway/services before its HTTP reply is dropped; replay preserves one holder, code and token version')
  await advance(slot.startsAt)
  assert.equal((await call('noshowB','GET','/payments/bank-profile')).canChange,true)
  assert.equal((await attempt('noshowB',absentPurchase.id)).phase,'TIMED_OUT_A_RETAINS')
  assert.equal((await call(seller,'GET',`/bookings/${absent.booking.id}`)).booking.currentHolderId,actors[seller].id)
  assert.equal((await attempt('presentB',timelyPurchase.id)).phase,'DISPUTED')
  await advance(Date.parse(slot.startsAt)+15*60000)
  const current=await attempt('presentB',timelyPurchase.id)
  await call('owner','POST',`/transfers/facilities/${facility}/cases/${timelyPurchase.id}/resolve`,{version:current.version,outcome:'AWARD_BUYER',reason:'Timely attendance, award only remaining scheduled minutes',evidenceIds:[timelyDeclaration.photo.id]},200,key())
  const winner=await call('presentB','GET',`/bookings/${timely.booking.id}`)
  assert.equal(winner.booking.currentHolderId,actors.presentB.id)
  assert.equal(Date.parse(winner.booking.endsAt),Date.parse(slot.endsAt))
  await advance(Date.parse(slot.startsAt)+20*60000)
  for (const code of [winner.booking.bookingCode,buyerDetail.booking.bookingCode])
    assert.equal((await call('staff','POST',`/bookings/facility/${facility}/check-in-by-code`,{bookingCode:code})).status,'CHECKED_IN')
  await advance(Date.parse(slot.startsAt)+30*60000)
  const day=new Date(Date.parse(slot.startsAt)+7*3600000).toISOString().slice(0,10)
  const report=await call('owner','GET',`/bookings/reports/facility/${facility}/dashboard?fromDate=${day}&toDate=${day}&mode=REAL&size=25`)
  assert.equal(Number(report.courts.find(row=>row.courtId===main.booking.courtId).usedMinutes),10)
  assert.equal(Number(report.courts.find(row=>row.courtId===timely.booking.courtId).usedMinutes),10)
  await advance(Date.parse(slot.endsAt)+1)
  assert.equal((await call('staff','POST',`/bookings/${main.booking.id}/complete`)).status,'COMPLETED')
  assert.equal((await call(seller,'GET','/payments/bank-profile')).canChange,true)
  pass('Both absent default to A; timely attendance permits late Owner handoff with unchanged end; real HTTP check-in records CHECKED_IN and only actual minutes then COMPLETED')
}
