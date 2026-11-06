#!/usr/bin/env node
import { readFileSync, readdirSync } from 'node:fs'
import { join, resolve } from 'node:path'
const args = process.argv.slice(2)
const value = key => args.includes(key) ? args[args.indexOf(key) + 1] : null
const docs = value('--docs') || (process.env.RYNEW_OFFLINE_HOME && join(process.env.RYNEW_OFFLINE_HOME, 'official-docs'))
const query = value('--query')
if (!docs || !query) throw new Error('Use --docs OFFICIAL_DOCS_DIRECTORY --query SEARCH_TEXT')
const directory = resolve(docs)
const manifest = JSON.parse(readFileSync(join(directory, 'manifest.json'), 'utf8'))
const results = []
for (const file of readdirSync(join(directory, 'text')).filter(file => file.endsWith('.txt'))) {
  const content = readFileSync(join(directory, 'text', file), 'utf8')
  const at = content.toLowerCase().indexOf(query.toLowerCase())
  if (at < 0) continue
  results.push({ source: content.split('\n')[0].slice('Source: '.length),
    file: join(directory, 'text', file), line: content.slice(0, at).split('\n').length,
    preview: content.slice(Math.max(0, at - 50), at + query.length + 100).replaceAll(/\s+/g, ' ') })
}
console.log(JSON.stringify({ retrievedAt: manifest.retrieved_at, query, matches: results.length, results: results.slice(0, 10) }, null, 2))
