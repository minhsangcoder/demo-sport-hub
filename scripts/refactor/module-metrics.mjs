import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
const require = createRequire(fileURLToPath(new URL('../../frontend/package.json', import.meta.url)))
const ts = require('typescript')
const prettier = require('prettier')

function onlyData(source, file) {
  const ast = ts.createSourceFile(file, source, ts.ScriptTarget.Latest, true)
  let valid = true
  function visit(node) {
    if (ts.isFunctionLike(node) || ts.isCallExpression(node) || ts.isNewExpression(node)
      || ts.isJsxElement(node) || ts.isJsxSelfClosingElement(node) || ts.isClassDeclaration(node)) valid = false
    ts.forEachChild(node, visit)
  }
  visit(ast)
  return valid
}

export async function measureModule(source, file) {
  if (/\.(json|sql)$/.test(file) || /(?:^|\/)generated\//.test(file)) return { category: /\.sql$/.test(file) ? 'migration' : 'data', loc: source.split(/\r?\n/).length, violations: [] }
  const dictionary = /(?:\/messages\/|\.messages\.ts$|\.data\.ts$)/.test(file)
  if (dictionary && onlyData(source, file)) return { category: 'dictionary', loc: source.split(/\r?\n/).length, violations: [] }
  const formatted = /\.[jt]sx?$/.test(file)
    ? await prettier.format(source, { parser: 'typescript', filepath: file, semi: false, singleQuote: true, printWidth: 100 })
    : source
  let comment = false
  const loc = formatted.split(/\r?\n/).filter(line => {
    const text = line.trim()
    if (comment) { if (text.includes('*/')) comment = false; return false }
    if (text.startsWith('/*')) { comment = !text.includes('*/'); return false }
    return text && !text.startsWith('//')
  }).length
  const packed = source.split(/\r?\n/).some(line => {
    const stripped = line.replace(/(['"`])(?:\\.|(?!\1).)*?\1/g, 'value')
    return line.length > 240 && (stripped.match(/[;{}]/g) ?? []).length >= 8
  })
  const violations = []
  if (dictionary) violations.push('A dictionary contains executable logic')
  if (loc > 500) violations.push(`Logic module exceeds 500 formatted LOC (${loc})`)
  if (packed) violations.push('Compressed logic line exceeds 240 characters; format before splitting')
  return { category: 'logic', loc, warning: loc > 350 ? 'Above the 350 LOC review target' : undefined, violations }
}
