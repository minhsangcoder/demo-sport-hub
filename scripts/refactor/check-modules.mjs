import { createHash } from 'node:crypto'
import { globSync, readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { measureModule } from './module-metrics.mjs'

const root = fileURLToPath(new URL('../../', import.meta.url))
const baselineFile = path.join(root, 'scripts/refactor/module-baseline.json')
const directories = ['frontend/app', 'frontend/components', 'frontend/features', 'frontend/lib', 'backend/booking-service/src/main/java', 'backend/schedule-service/src/main/java', 'backend/booking-service/src/main/resources/db/migration', 'backend/schedule-service/src/main/resources/db/migration']
const files = directories.flatMap(folder => [...globSync('**/*.{ts,tsx,java,json,sql}', { cwd: path.join(root, folder) })].map(file => path.join(folder, file).replaceAll('\\', '/')))
const baseline = JSON.parse(readFileSync(baselineFile, 'utf8')).files
const inventory = [], failures = []
for (const file of files.sort()) {
  const source = readFileSync(path.join(root, file), 'utf8')
  const sha256 = createHash('sha256').update(source).digest('hex')
  const changed = baseline[file] !== sha256
  const metrics = await measureModule(source, file)
  inventory.push({ file, changed, ...metrics })
  if (changed && metrics.violations.length) failures.push({ file, violations: metrics.violations })
  if (changed && metrics.warning) console.log(`REVIEW ${file}: ${metrics.loc} LOC`)
}
{
  const report = { inventory, failures, allowlist: 'Only exact baseline hashes; no size exemption for modified or new logic' }
  if (process.argv.includes('--report')) {
    mkdirSync(path.join(root, 'tmp/loading-performance'), { recursive: true })
    writeFileSync(path.join(root, 'tmp/loading-performance/module-check.json'), JSON.stringify(report, null, 2))
  }
  for (const failure of failures) console.error(`${failure.file}: ${failure.violations.join('; ')}`)
  console.log(`${inventory.filter(i => i.changed).length} changed/new modules checked; ${failures.length} violations`)
  if (failures.length) process.exitCode = 1
}
