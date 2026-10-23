import { readFile, writeFile, readdir } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import prettier from 'prettier'

const root = fileURLToPath(new URL('../', import.meta.url))
const scope = JSON.parse(await readFile(path.join(root, 'refactor-scope.json'), 'utf8'))
async function collect(folder) {
  const files = []
  for (const entry of await readdir(path.join(root, folder), { withFileTypes: true })) {
    const relative = path.join(folder, entry.name)
    if (entry.isDirectory()) files.push(...(await collect(relative)))
    else if (/\.[cm]?[jt]sx?$/.test(entry.name)) files.push(relative)
  }
  return files
}
const files = new Set(scope.files)
for (const folder of scope.directories) for (const file of await collect(folder)) files.add(file)
let failed = false
for (const file of [...files].sort()) {
  const absolute = path.resolve(root, file)
  if (!absolute.startsWith(root) || file.startsWith('src/'))
    throw new Error('Formatter escaped approved scope')
  const source = await readFile(absolute, 'utf8')
  const options = { ...(await prettier.resolveConfig(absolute)), filepath: absolute }
  const formatted = await prettier.format(source, options)
  if (source !== formatted) {
    if (process.argv.includes('--write')) await writeFile(absolute, formatted)
    else {
      console.error(`Needs formatting: ${file}`)
      failed = true
    }
  }
}
console.log(`Prettier ${prettier.version}: ${files.size} scoped modules`)
if (failed) process.exitCode = 1
