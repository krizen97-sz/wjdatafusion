#!/usr/bin/env node
// Source metadata only: contracts, imports, exports, entrypoints and table names.
import { createHash } from 'node:crypto'
import { existsSync, readFileSync, writeFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { dirname, extname, relative, resolve } from 'node:path'
import { execFileSync } from 'node:child_process'

const args = process.argv.slice(2)
const option = (key, fallback) => args.includes(key) ? args[args.indexOf(key) + 1] : fallback
const root = resolve(option('--root', '.'))
const output = option('--output')
if (!output) throw new Error('Use --root RYNEW_CHECKOUT --output FILE [--deps-dir FRONTEND_WITH_NODE_MODULES]')
const frontend = resolve(root, 'RuoYi-Vue3-master')
const require = createRequire(resolve(option('--deps-dir', frontend), 'package.json'))
const babel = require('@babel/parser')
const sfc = require('@vue/compiler-sfc')
const tracked = execFileSync('git', ['-C', root, '-c', 'core.quotepath=false', 'ls-files', '-z'], { encoding: 'utf8' }).split('\0').filter(Boolean)
const schema = readFileSync(resolve(root, 'WDF100.0/sql/rynew_schema_latest.sql'), 'utf8')
const tableNames = new Set([...schema.matchAll(/^CREATE TABLE IF NOT EXISTS `([^`]+)`/gm)].map(match => match[1]))
const sha = content => createHash('sha256').update(content).digest('hex')
const cleanPath = path => relative(root, path).replaceAll('\\', '/')
function resolveImport(file, name) {
  if (!name.startsWith('.') && !name.startsWith('@/')) return null
  const base = name.startsWith('@/') ? resolve(frontend, 'src', name.slice(2)) : resolve(dirname(file), name)
  for (const suffix of ['', '.js', '.mjs', '.vue', '.ts', '/index.js', '/index.vue']) {
    const candidate = base + suffix
    if (existsSync(candidate) && tracked.includes(cleanPath(candidate))) return cleanPath(candidate)
  }
  return null
}
const rows = [], errors = []
function walk(node, visit) {
  if (!node || typeof node !== 'object') return
  if (Array.isArray(node)) { node.forEach(child => walk(child, visit)); return }
  if (node.type) visit(node)
  for (const [key, value] of Object.entries(node)) if (!['loc', 'start', 'end', 'extra', 'comments', 'tokens'].includes(key)) walk(value, visit)
}
const nameOf = node => node?.name || node?.value
for (const path of tracked) {
  const ui = path.startsWith('RuoYi-Vue3-master/src/') && ['.vue', '.js', '.mjs', '.ts'].includes(extname(path)) && !path.includes('/__tests__/')
  const java = path.startsWith('WDF100.0/') && path.includes('/src/main/java/') && path.endsWith('.java')
  const mapper = path.startsWith('WDF100.0/') && path.includes('/src/main/resources/mapper/') && path.endsWith('.xml')
  if (!ui && !java && !mapper) continue
  const full = resolve(root, path), text = readFileSync(full, 'utf8')
  const row = { path, sha256: sha(text), lines: text.split('\n').length }
  if (ui) {
    row.kind = path.endsWith('.vue') ? 'vue-component' : 'frontend-module'
    let script = text
    row.imports = []; row.exports = []; row.props = []; row.emits = []; row.apiPaths = []
    if (path.endsWith('.vue')) {
      const parsed = sfc.parse(text, { filename: full })
      script = [parsed.descriptor.script?.content, parsed.descriptor.scriptSetup?.content].filter(Boolean).join('\n')
      row.templateTags = [...new Set([...(parsed.descriptor.template?.content || '').matchAll(/<([A-Za-z][\w-]*)\b/g)].map(match => match[1]))].sort()
    }
    try {
      const ast = babel.parse(script, { sourceType: 'unambiguous', plugins: ['typescript', 'jsx', 'decorators-legacy'], errorRecovery: true })
      walk(ast, node => {
        if (node.type === 'ImportDeclaration') row.imports.push({ module: node.source.value, resolved: resolveImport(full, node.source.value) })
        if (node.type === 'ExportNamedDeclaration') {
          if (node.declaration?.id) row.exports.push(node.declaration.id.name)
          for (const item of node.declaration?.declarations || []) if (item.id.type === 'Identifier') row.exports.push(item.id.name)
          for (const item of node.specifiers || []) row.exports.push(nameOf(item.exported))
        }
        if (node.type === 'CallExpression' && ['defineProps', 'defineEmits'].includes(node.callee.name)) {
          const target = node.callee.name === 'defineProps' ? row.props : row.emits
          const value = node.arguments[0]
          if (value?.type === 'ObjectExpression') target.push(...value.properties.map(item => nameOf(item.key)).filter(Boolean))
          if (value?.type === 'ArrayExpression') target.push(...value.elements.map(nameOf).filter(Boolean))
        }
        if (node.type === 'ObjectProperty' && nameOf(node.key) === 'url') {
          const value = node.value.type === 'StringLiteral' ? node.value.value : node.value.type === 'TemplateLiteral' ? node.value.quasis.map(part => part.value.cooked).join('${dynamic}') : ''
          if (value.startsWith('/')) row.apiPaths.push(value)
        }
      })
      for (const field of ['exports', 'props', 'emits', 'apiPaths']) row[field] = [...new Set(row[field])].sort()
    } catch (error) { errors.push({ path, parser: 'babel', errorType: error.name }); row.parseFailed = true }
  } else if (java) {
    row.kind = 'java-source'
    row.package = /\bpackage\s+([\w.]+)\s*;/.exec(text)?.[1]
    row.types = [...text.matchAll(/\b(?:class|interface|record|enum)\s+(\w+)/g)].map(match => match[1])
    row.methods = [...text.matchAll(/\b(?:public|protected|private)\s+(?:static\s+|final\s+|synchronized\s+)*(?:[\w<>?,.[\] ]+)\s+(\w+)\s*\(/g)].map(match => match[1])
    row.mappingPaths = [...text.matchAll(/@(?:Request|Get|Post|Put|Delete|Patch)Mapping\s*\(\s*(?:value\s*=\s*)?"(\/[^"\n]*)"/g)].map(match => match[1])
    row.permissions = [...new Set([...text.matchAll(/(?:hasPermi|hasRole)\('([^']+)'\)/g)].map(match => match[1]))]
  } else {
    row.kind = 'mybatis-mapper'
    row.namespace = /<mapper\s+namespace="([^"]+)"/.exec(text)?.[1]
    row.statements = [...text.matchAll(/<(select|insert|update|delete)\s+id="([^"]+)"/g)].map(match => ({ kind: match[1], id: match[2] }))
    row.tables = [...new Set([...text.matchAll(/\b(?:from|join|into|update)\s+`?([A-Za-z_][\w]*)`?/gi)].map(match => match[1]).filter(name => tableNames.has(name)))].sort()
  }
  rows.push(row)
}
const result = { sourceCommit: execFileSync('git', ['-C', root, 'rev-parse', 'HEAD'], { encoding: 'utf8' }).trim(),
  note: 'Static source index, not runtime validation. No configuration values, method bodies or business data are emitted.',
  counts: Object.fromEntries([...new Set(rows.map(row => row.kind))].map(kind => [kind, rows.filter(row => row.kind === kind).length])),
  parseErrors: errors, files: rows }
writeFileSync(resolve(output), JSON.stringify(result, null, 2) + '\n')
console.log(JSON.stringify({ counts: result.counts, parseErrors: errors, output: resolve(output) }, null, 2))
if (errors.length) process.exitCode = 1
