import assert from 'node:assert/strict'
import {readFileSync,writeFileSync} from 'node:fs'
import {createHash} from 'node:crypto'

const home='tmp/peer-resale-performance/'
const before=JSON.parse(readFileSync(home+'report-before.json','utf8'))
const after=JSON.parse(readFileSync(home+'report-after.json','utf8'))
assert.deepEqual(after.conditions,before.conditions)
const uuid=/[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}/gi
const normalize=value=>JSON.parse(JSON.stringify(value).replace(uuid,'<fixture-id>'))
function comparable(payload) {
  const result=structuredClone(payload)
  result.transactions.items=[]
  return normalize(result)
}
function courtId(index) {
  const bytes=createHash('md5').update('peer-perf-court-'+index).digest()
  bytes[6]=(bytes[6]&15)|48;bytes[8]=(bytes[8]&63)|128
  const hex=bytes.toString('hex')
  return `${hex.slice(0,8)}-${hex.slice(8,12)}-${hex.slice(12,16)}-${hex.slice(16,20)}-${hex.slice(20)}`
}
function checkSample(payload) {
  const items=payload.transactions.items
  assert.equal(items.length,25)
  const ordered=[...items].sort((a,b)=>b.effectiveAt.localeCompare(a.effectiveAt)||b.id.localeCompare(a.id))
  assert.deepEqual(items,ordered)
  for(const item of items) {
    assert.equal(item.amount,'100000');assert.equal(item.currency,'VND')
    assert.equal(item.moneyClass,'REAL');assert.equal(item.source,'ONLINE')
    assert.equal(item.purpose,'BOOKING');assert.equal(item.confirmationMethod,'BANK_VERIFIED')
    assert.equal(item.courtId,courtId(Number(item.courtName.replace('Sân ',''))))
  }
}
const result={conditions:before.conditions,comparison:'Full totals/courts/days/coverage/cutoff/page metadata; random UUID tie-break page samples validated independently',metrics:{},targets:[],limitations:['Controlled money-source adapters, not a browser/network or real-bank latency measurement','Host free memory varied; recorded in original files','First-page UUID tie-break samples differ because native service fixtures create fresh IDs']}
for(const size of ['small','large']) {
  assert.deepEqual(after[size].rows,before[size].rows)
  result.metrics[size]={}
  for(const route of ['healthy','partial','bank']) {
    const a=before[size][route],b=after[size][route]
    assert.equal(a.samplesMs.length,30);assert.equal(b.samplesMs.length,30)
    assert.deepEqual(b.queryCountPerSample,a.queryCountPerSample)
    if(route==='bank')assert.deepEqual(b.payload,a.payload)
    else {assert.deepEqual(comparable(b.payload),comparable(a.payload));checkSample(a.payload);checkSample(b.payload)}
    const metric={beforeP95Ms:a.p95Ms,afterP95Ms:b.p95Ms,improvementPercent:100*(a.p95Ms-b.p95Ms)/a.p95Ms,beforeP50Ms:a.p50Ms,afterP50Ms:b.p50Ms,queriesPerRequest:[...new Set(b.queryCountPerSample)]}
    result.metrics[size][route]=metric
    const target=size==='large'&&route!=='bank'
    result.targets.push({size,route,target,met:target?metric.improvementPercent>=20:b.p95Ms-a.p95Ms<=Math.max(a.p95Ms*.1,50)})
  }
}
assert.ok(result.targets.every(target=>target.met))
writeFileSync(home+'report-comparison.json',JSON.stringify(result,null,2))
console.log(JSON.stringify({targets:result.targets,large:result.metrics.large},null,2))
