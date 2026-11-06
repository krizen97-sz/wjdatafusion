#!/usr/bin/env node
// Audit locked tarballs by SRI; optionally populate a dedicated npm cache.
import { createHash } from 'node:crypto'
import { copyFileSync, existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs'
import { resolve, join, dirname, relative } from 'node:path'
import { spawnSync } from 'node:child_process'

const args = process.argv.slice(2)
const value = name => args.includes(name) ? args[args.indexOf(name) + 1] : null
const lockPath = value('--lock')
const cacheArg = value('--cache')
if (!lockPath || !cacheArg) throw new Error('Use --lock package-lock.json --cache DIRECTORY [--fetch] [--report FILE] [--normalize-registry]')
const lock = JSON.parse(readFileSync(lockPath, 'utf8'))
if (!lock.packages) throw new Error('A package-lock v2/v3 packages map is required')
if (args.includes('--normalize-registry')) {
  for (const pkg of Object.values(lock.packages)) {
    if (pkg.resolved?.startsWith('https://registry.npmmirror.com/')) {
      pkg.resolved = pkg.resolved.replace('https://registry.npmmirror.com/', 'https://registry.npmjs.org/')
    }
  }
  writeFileSync(lockPath, JSON.stringify(lock, null, 2) + '\n')
}
const cache = resolve(cacheArg)
const entries = [...new Map(Object.entries(lock.packages).filter(([key]) => key).map(([path, pkg]) => [pkg.integrity || path, { path, ...pkg }])).values()]
function check(pkg) {
  if (!pkg.integrity || !pkg.resolved?.startsWith('https://')) return { ok: false, reason: 'unsupported or missing integrity/resolved' }
  for (const sri of pkg.integrity.split(/\s+/)) {
    const match = /^(sha512|sha384|sha256|sha1)-([A-Za-z0-9+/=]+)$/.exec(sri)
    if (!match) continue
    const hex = Buffer.from(match[2], 'base64').toString('hex')
    const path = join(cache, '_cacache', 'content-v2', match[1], hex.slice(0, 2), hex.slice(2, 4), hex.slice(4))
    if (existsSync(path) && createHash(match[1]).update(readFileSync(path)).digest('base64') === match[2]) return { ok: true, contentPath: path }
  }
  return { ok: false, reason: 'missing or invalid cached content' }
}
const initialMissing = entries.filter(pkg => !check(pkg).ok)
if (args.includes('--fetch') && initialMissing.length) {
  mkdirSync(cache, { recursive: true })
  const emptyConfig = join(cache, 'empty-user.npmrc')
  const emptyGlobal = join(cache, 'empty-global.npmrc')
  if (!existsSync(emptyConfig)) writeFileSync(emptyConfig, '# Offline bundle preparation; no credentials\n')
  if (!existsSync(emptyGlobal)) writeFileSync(emptyGlobal, '# No global credentials\n')
  for (let index = 0; index < initialMissing.length; index += 16) {
    const group = initialMissing.slice(index, index + 16)
    const command = process.platform === 'win32' ? 'npm.cmd' : 'npm'
    const result = spawnSync(command, ['cache', 'add', ...group.map(pkg => pkg.resolved), '--cache', cache,
      '--userconfig', emptyConfig, '--globalconfig', emptyGlobal, '--ignore-scripts', '--no-audit', '--no-fund', '--fetch-retries=2'], { encoding: 'utf8' })
    if (result.status !== 0) throw new Error('npm cache add failed: ' + result.stderr?.slice(-1200))
    console.error(JSON.stringify({ fetched: Math.min(index + 16, initialMissing.length), totalMissing: initialMissing.length }))
  }
}
const missing = entries.filter(pkg => !check(pkg).ok).map(pkg => ({ path: pkg.path, version: pkg.version, resolved: pkg.resolved, reason: check(pkg).reason }))
if (value('--export-cache')) {
  if (missing.length) throw new Error('Cannot export an incomplete locked cache')
  const target = resolve(value('--export-cache'))
  if (existsSync(target) && readdirSync(target).length) throw new Error('Export cache directory must be empty; never overwrite another cache')
  for (const pkg of entries) {
    const source = check(pkg).contentPath
    const destination = join(target, relative(cache, source))
    mkdirSync(dirname(destination), { recursive: true })
    copyFileSync(source, destination)
  }
  mkdirSync(target, { recursive: true })
  for (const name of ['empty-user.npmrc', 'empty-global.npmrc']) writeFileSync(join(target, name), '# Dedicated locked cache; no credentials\n')
}
const nativeWindows = entries.filter(pkg => pkg.os?.includes('win32') && (!pkg.cpu || pkg.cpu.includes('x64')))
const report = { lockVersion: lock.lockfileVersion, lockedEntries: Object.keys(lock.packages).length - 1, uniqueTarballs: entries.length,
  initialMissing: initialMissing.length, missingCount: missing.length, missing,
  windowsX64NativePackages: nativeWindows.map(pkg => ({ path: pkg.path, version: pkg.version, cached: check(pkg).ok })) }
if (value('--report')) writeFileSync(value('--report'), JSON.stringify(report, null, 2) + '\n')
console.log(JSON.stringify({ ...report, missing: missing.slice(0, 5) }, null, 2))
if (missing.length) process.exitCode = 1
