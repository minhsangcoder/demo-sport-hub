import assert from 'node:assert/strict'
import {execFileSync} from 'node:child_process'

// Policy-clock tests use Booking's authenticated public HTTP API; Gateway's real-time budget is checked separately.
export async function peerSystemAdmission({config,call,actors,quote,key,slot,courts,pass}) {
  const base=Date.parse(slot.endsAt)+1,origin=`http://127.0.0.1:${config.ports.booking}`
  const range={startsAt:new Date(base+2*3600000-1).toISOString(),endsAt:new Date(base+3*3600000-1).toISOString()}
  const priced=await quote('hoard','rights',range)
  const input={courtId:courts.rights.id,...range,expectedAmount:Number(priced.amount),quoteHash:priced.quoteHash}
  const direct=(actor,method,url,body,expected=200,headers={})=>call(actor,method,url,body,expected,headers,{origin})
  async function advance(seconds) {
    const response=await fetch(config.faultControlURL+'/clock',{method:'POST',headers:{Authorization:'Bearer '+config.faultControlToken,'Content-Type':'application/json'},body:JSON.stringify({instant:new Date(base+seconds*1000).toISOString()})})
    assert.equal(response.status,200,await response.text())
  }
  const redis='sporthub-real-test-redis-'+config.run
  assert.match(redis,/^sporthub-real-test-redis-[a-f0-9]+$/)
  for(const [index,seconds] of [0,901,1802,3603,5404].entries()) {
    await advance(seconds)
    const held=await direct('hoard','POST','/bookings/holds',input,201,key())
    await direct('hoard','DELETE',`/bookings/holds/${held.id}`,undefined,204)
    if(index===2) {
      const denied=await direct('hoard','POST','/bookings/holds',input,409,key())
      assert.equal(denied.code,'HOLD_COOLDOWN')
      assert.equal(Date.parse(denied.details.retryAt),base+(1802+1800)*1000)
      execFileSync('docker',['exec',redis,'redis-cli','DEL',`booking:hold-policy:{${actors.hoard.id}}:revision`],{windowsHide:true})
      assert.equal((await direct('hoard','POST','/bookings/holds',input,409,key())).code,'HOLD_COOLDOWN')
    }
  }
  const suspended=await direct('hoard','POST','/bookings/holds',input,409,key())
  assert.equal(suspended.code,'ONLINE_BOOKING_SUSPENDED')
  assert.equal(Date.parse(suspended.details.retryAt),base+(5404+86400)*1000)
  const status=await call('hoard','GET','/bookings/admission')
  assert.equal(status.policyAvailability,'COMPLETE')
  assert.equal(Date.parse(status.onlineBlockedUntil),Date.parse(suspended.details.retryAt))
  pass('Five native HTTP abandonments enforce third/30-minute and fifth/24-hour deadlines; deleting only this test user Redis revision rebuilds the same committed ban')

  const first=await direct('regrab','POST','/bookings/holds',input,201,key())
  await direct('regrab','DELETE',`/bookings/holds/${first.id}`,undefined,204)
  const overlap={startsAt:new Date(Date.parse(range.startsAt)+30*60000).toISOString(),endsAt:new Date(Date.parse(range.endsAt)+30*60000).toISOString()}
  const overlapQuote=await quote('regrab','rights',overlap)
  const blocked=await direct('regrab','POST','/bookings/holds',{...input,...overlap,quoteHash:overlapQuote.quoteHash},409,key())
  assert.equal(blocked.code,'HOLD_REGRAB_COOLDOWN')
  const another=await direct('c','POST','/bookings/holds',input,201,key())
  await direct('c','DELETE',`/bookings/holds/${another.id}`,undefined,204)
  const adjacent={startsAt:range.endsAt,endsAt:new Date(Date.parse(range.endsAt)+3600000).toISOString()}
  const adjacentQuote=await quote('regrab','rights',adjacent)
  const allowed=await direct('regrab','POST','/bookings/holds',{...input,...adjacent,quoteHash:adjacentQuote.quoteHash},201,key())
  await direct('regrab','DELETE',`/bookings/holds/${allowed.id}`,undefined,204)
  pass('Anti-regrab rejects overlapping minutes for the same user while allowing another user and a half-open adjacent interval')

  execFileSync('docker',['pause',redis],{windowsHide:true})
  try {
    const started=Date.now()
    const unavailable=await direct('rate','POST','/bookings/holds',input,503,key())
    assert.equal(unavailable.code,'HOLD_POLICY_UNAVAILABLE')
    assert.ok(Date.now()-started<15000,'Native admission must fail closed within a bounded request')
  } finally {execFileSync('docker',['unpause',redis],{windowsHide:true})}
  const held=await call('rate','POST','/bookings/holds',input,201,key())
  // Repeating the same interval can fail its overlap check before the cross-court quota check.
  for(let i=0;i<4;i++) await call('rate','POST','/bookings/holds',input,409,key())
  assert.equal((await call('rate','POST','/bookings/holds',input,429,key())).code,'HOLD_RATE_LIMITED')
  await call('rate','DELETE',`/bookings/holds/${held.id}`,undefined,204)
  await call('guest','POST','/bookings/holds',input,401,key())
  pass('Redis outage fails closed without a claim; recovery permits admission, Gateway rejects request six in its real rolling minute, anonymous admission is denied')
}
