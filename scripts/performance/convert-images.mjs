import { createRequire } from 'node:module'
import { statSync, writeFileSync, mkdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const root = fileURLToPath(new URL('../../', import.meta.url))
const require = createRequire(path.join(root, 'frontend/package.json'))
const nextRequire = createRequire(require.resolve('next/package.json'))
const sharp = nextRequire('sharp')
const files = ['hero-pickleball.png', 'hero-paddle.png', 'hero-tennis.png', 'court-aerial.jpg']
const results = []
for (const file of files) {
  const source = path.join(root, 'frontend/public/images', file)
  const metadata = await sharp(source).metadata()
  for (const width of [640, 1280]) {
    const target = path.join(path.dirname(source), file.replace(/\.(png|jpg)$/, `-${width}.webp`))
    const result = await sharp(source).resize({ width, withoutEnlargement: true }).webp({ quality: 80, effort: 5 }).toFile(target)
    results.push({ source: file, originalBytes: statSync(source).size, originalWidth: metadata.width, originalHeight: metadata.height, target: path.basename(target), ...result })
  }
}
mkdirSync(path.join(root, 'tmp/loading-performance'), { recursive: true })
writeFileSync(path.join(root, 'tmp/loading-performance/images.json'), JSON.stringify(results, null, 2))
console.log(results.map(item => `${item.target}: ${item.size} bytes`).join('\n'))
