// Production-only test listener; no .env, gateway or real user database is read.
import { createServer } from 'node:http'
import { createRequire } from 'node:module'
import path from 'node:path'
import { responseFor } from './ui-data.mjs'

const dir = path.resolve(process.argv[2])
const require = createRequire(path.join(dir, 'package.json'))
const next = require('next')
const app = next({ dev: false, dir, hostname: '127.0.0.1' })
await app.prepare()
const handle = app.getRequestHandler()
const server = createServer(async (request, response) => {
  if (!request.url.startsWith('/api/v1/')) return handle(request, response)
  const role = /perf-role=(OWNER|STAFF|PUBLIC)/.exec(request.headers.cookie ?? '')?.[1] ?? 'PUBLIC'
  try {
    const result = responseFor(new URL(request.url, 'http://fixture.invalid'), role)
    await new Promise(resolve => setTimeout(resolve, 100))
    response.writeHead(result.status ?? 200, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' })
    response.end(JSON.stringify({ status: result.status ?? 200, message: 'Dữ liệu thử', data: result.data }))
  } catch (error) { response.writeHead(500); response.end(JSON.stringify({ message: error.message })) }
})
server.listen(0, '127.0.0.1', () => console.log(JSON.stringify({ url: `http://127.0.0.1:${server.address().port}`, production: true })))
async function stop() { server.close(); await app.close(); process.exit(0) }
process.on('SIGINT', stop)
process.on('SIGTERM', stop)
