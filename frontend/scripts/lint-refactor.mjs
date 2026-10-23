import { execFileSync } from 'node:child_process'
import { readFileSync, globSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('../', import.meta.url))
const scope = JSON.parse(readFileSync(path.join(root, 'refactor-scope.json'), 'utf8'))
const files = [
  ...new Set([
    ...scope.files,
    ...scope.directories.flatMap((folder) =>
      [...globSync('**/*.{ts,tsx,mjs}', { cwd: path.join(root, folder) })].map((file) =>
        path.join(folder, file),
      ),
    ),
  ]),
]
execFileSync(
  process.execPath,
  [path.join(root, 'node_modules/eslint/bin/eslint.js'), '--max-warnings=0', ...files],
  { cwd: root, stdio: 'inherit' },
)
