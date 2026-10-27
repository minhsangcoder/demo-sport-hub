import assert from 'node:assert/strict'
import {readFileSync,writeFileSync,mkdirSync} from 'node:fs'
import {createHash} from 'node:crypto'
import path from 'node:path'
import {fileURLToPath} from 'node:url'
import {moduleLoader,hookDriver,flush} from './support/load-module.mjs'

const root=fileURLToPath(new URL('../../',import.meta.url))
const before=path.resolve(process.argv[2]||path.join(root,'tmp/peer-resale-performance/before/frontend'))
const after=path.join(root,'frontend')
const id='79000000-0000-0000-0000-000000000309'
const reads=[`/transfers/acquisitions/${id}`,`/transfers/acquisitions/${id}/peer`,
  `/payments/peer-acquisitions/${id}/instruction`,`/transfers/acquisitions/${id}/evidence`,
  `/transfers/conversations/${id}/messages?after=0&size=50`]
async function run(directory,command) {
  const driver=hookDriver(),calls=[]
  const load=moduleLoader({fetch:async(url,options)=>{
    calls.push({method:options?.method||'GET',url})
    return Response.json({data:{synthetic:true},message:'Dữ liệu đo thử'})
  }},{react:driver.react},directory)
  const useApi=load('lib/use-api.ts').useApi
  const render=()=>driver.render(()=>reads.map(url=>useApi(url)))
  render();await flush();render();await flush()
  const initial=calls.filter(call=>call.method==='GET').length
  assert.equal(initial,reads.length)
  calls.length=0
  await load('lib/api.ts').api(command,{method:'POST',body:'{}'})
  render();await flush();render();await flush()
  driver.unmount()
  return {initial,mutationGets:calls.filter(call=>call.method==='GET'),commands:calls.filter(call=>call.method==='POST').length}
}
const result={conditions:{transport:'Actual api/useApi/request-store code in hook lifetime driver; synthetic HTTP; no browser latency claim',activeReads:reads},beforeRoot:before,afterRoot:after,scenarios:{},sources:{}}
for(const [name,url] of Object.entries({chat:`/transfers/conversations/${id}/messages`,evidence:`/transfers/acquisitions/${id}/evidence`,unknown:'/unknown-test-command'})) {
  result.scenarios[name]={before:await run(before,url),after:await run(after,url)}
}
assert.equal(result.scenarios.chat.before.mutationGets.length,5)
assert.equal(result.scenarios.chat.after.mutationGets.length,1)
assert.equal(result.scenarios.evidence.after.mutationGets.length,1)
assert.equal(result.scenarios.unknown.after.mutationGets.length,5)
for(const [name,directory] of Object.entries({before,after}))result.sources[name]=Object.fromEntries(['lib/api.ts','lib/read-state.ts','lib/use-api.ts'].map(file=>[file,createHash('sha256').update(readFileSync(path.join(directory,file))).digest('hex')]))
mkdirSync(path.join(root,'tmp/peer-resale-performance'),{recursive:true})
writeFileSync(path.join(root,'tmp/peer-resale-performance/peer-request-counts.json'),JSON.stringify(result,null,2))
console.log(JSON.stringify({chatGets:[5,1],evidenceGets:[result.scenarios.evidence.before.mutationGets.length,1],unknownGets:[5,5],realApiAndHooks:true,syntheticHttp:true}))
