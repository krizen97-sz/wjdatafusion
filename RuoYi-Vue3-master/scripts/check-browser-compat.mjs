import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { parse } from '@babel/parser'

const distDirectory = path.resolve(process.cwd(), 'dist')

function collectJavaScriptFiles(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const target = path.join(directory, entry.name)
    if (entry.isDirectory()) return collectJavaScriptFiles(target)
    return entry.isFile() && entry.name.endsWith('.js') ? [target] : []
  })
}

function findUnsupportedSyntax(file) {
  const source = fs.readFileSync(file, 'utf8')
  const ast = parse(source, { sourceType: 'unambiguous' })
  const findings = []
  const seen = new Set()

  function visit(node) {
    if (!node || typeof node !== 'object' || seen.has(node)) return
    seen.add(node)
    if (node.type === 'OptionalMemberExpression' || node.type === 'OptionalCallExpression') findings.push('optional chaining')
    if (node.type === 'LogicalExpression' && node.operator === '??') findings.push('nullish coalescing')
    if (node.type === 'AssignmentExpression' && ['??=', '&&=', '||='].includes(node.operator)) findings.push('logical assignment')
    if (['BigIntLiteral', 'ClassPrivateProperty', 'ClassPrivateMethod', 'PrivateName', 'StaticBlock'].includes(node.type)) findings.push(node.type)
    for (const value of Object.values(node)) Array.isArray(value) ? value.forEach(visit) : visit(value)
  }

  visit(ast)
  return [...new Set(findings)]
}

if (!fs.existsSync(distDirectory)) throw new Error('Missing dist directory. Run the production build first.')

const files = collectJavaScriptFiles(distDirectory)
const failures = files.flatMap((file) => {
  const findings = findUnsupportedSyntax(file)
  return findings.length ? [{ file: path.relative(distDirectory, file), findings }] : []
})

if (failures.length) {
  for (const failure of failures) console.error(`${failure.file}: ${failure.findings.join(', ')}`)
  throw new Error(`Browser compatibility check failed for ${failures.length} JavaScript file(s).`)
}

console.log(`Browser compatibility check passed: ${files.length} JavaScript files target Chrome 64+.`)
