// Separate Next cache and loopback listener for mocked UI browser tests.
import next from 'next'
import { createServer } from 'node:http'
import { cpSync, existsSync, lstatSync, mkdirSync, symlinkSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const port = Number(process.env.SPORTHUB_UI_PORT || 3017)
const frontend = fileURLToPath(new URL('..', import.meta.url))
const fixture = path.resolve(frontend, '../tmp/refund-ui-fixture')
if (existsSync(fixture) && lstatSync(fixture).isSymbolicLink()) throw new Error('Fixture directory must be a normal directory')
mkdirSync(fixture, { recursive: true })
for (const entry of ['app', 'components', 'features', 'lib', 'public', 'styles', 'package.json', 'tsconfig.json', 'next-env.d.ts', 'next.config.mjs', 'postcss.config.mjs']) {
  const source = path.join(frontend, entry)
  if (existsSync(source)) cpSync(source, path.join(fixture, entry), { recursive: true })
}
const modules = path.join(fixture, 'node_modules')
if (!existsSync(modules)) symlinkSync(path.join(frontend, 'node_modules'), modules, 'junction')
const app = next({ dev: true, dir: fixture, hostname: '127.0.0.1', port, webpack: true })
await app.prepare()
const handle = app.getRequestHandler()
const server = createServer((request, response) => handle(request, response))
server.on('upgrade', app.getUpgradeHandler())
server.listen(port, '127.0.0.1', () => console.log(JSON.stringify({ fixtureServer: true, pid: process.pid, url: `http://127.0.0.1:${port}` })))
async function stop() { server.close(); await app.close(); process.exit(0) }
process.on('SIGINT', stop)
process.on('SIGTERM', stop)
