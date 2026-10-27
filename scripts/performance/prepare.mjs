import { createHash } from 'node:crypto'
import { cpSync, existsSync, mkdirSync, readdirSync, readFileSync, symlinkSync, writeFileSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('../../', import.meta.url))
const phase = process.argv[2]
if (!/^(before|after|candidate(?:-[a-z0-9-]+)?)$/.test(phase ?? '')) throw new Error('Usage: node scripts/performance/prepare.mjs before|after|candidate[-name]')
const target = path.join(path.resolve(root, process.env.SPORTHUB_PERFORMANCE_HOME || 'tmp/loading-performance'), phase)
if (existsSync(target)) throw new Error(`Snapshot already exists: ${target}. Preserve it and choose a fresh run directory.`)
const files = []
function inventory(folder, relative = '') {
  for (const entry of readdirSync(folder, { withFileTypes: true }).sort((a, b) => a.name.localeCompare(b.name))) {
    if (['node_modules', 'target', '.next', '.git'].includes(entry.name) || entry.name.startsWith('.env')) continue
    const name = path.join(relative, entry.name)
    if (entry.isDirectory()) inventory(path.join(folder, entry.name), name)
    else {
      const contents = readFileSync(path.join(folder, entry.name))
      files.push({ path: name.replaceAll('\\', '/'), bytes: contents.length, sha256: createHash('sha256').update(contents).digest('hex') })
    }
  }
}
inventory(path.join(root, 'frontend'), 'frontend')
inventory(path.join(root, 'backend'), 'backend')
mkdirSync(target, { recursive: true })
const ui = path.join(target, 'frontend')
mkdirSync(ui)
for (const entry of ['app', 'components', 'features', 'lib', 'public', 'styles', 'package.json', 'tsconfig.json', 'next-env.d.ts', 'next.config.mjs', 'components.json']) {
  const source = path.join(root, 'frontend', entry)
  if (existsSync(source)) cpSync(source, path.join(ui, entry), { recursive: true })
}
symlinkSync(path.join(root, 'frontend', 'node_modules'), path.join(ui, 'node_modules'), 'junction')
writeFileSync(path.join(ui, 'postcss.config.mjs'), "import {fileURLToPath} from 'node:url'\nexport default {plugins:{'@tailwindcss/postcss':{base:fileURLToPath(new URL('.',import.meta.url))}}}\n")
cpSync(path.join(root, 'backend'), path.join(target, 'backend'), {
  recursive: true,
  filter: source => !['node_modules', 'target', '.git'].includes(path.basename(source)) && !path.basename(source).startsWith('.env'),
})
const fingerprint = createHash('sha256').update(JSON.stringify(files)).digest('hex')
const manifest = {
  phase, fingerprint, capturedAt: new Date().toISOString(), files,
  source: 'Full working tree, including uncommitted Vietnamese UI and untracked files; no .env',
  runtime: { node: process.version, platform: process.platform, release: os.release(), arch: process.arch, cpu: os.cpus()[0]?.model, logicalCpus: os.cpus().length, memoryBytes: os.totalmem() },
  git: execFileSync('git', ['status', '--short'], { cwd: root, encoding: 'utf8' }),
}
writeFileSync(path.join(target, 'source.json'), JSON.stringify(manifest, null, 2))
console.log(JSON.stringify({ target, fingerprint, files: files.length, privateConfigurationCopied: false }))
