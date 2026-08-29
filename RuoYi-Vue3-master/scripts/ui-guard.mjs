#!/usr/bin/env node

import { execFileSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath, pathToFileURL } from 'node:url'

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url))
const DEFAULT_FRONTEND_ROOT = path.resolve(SCRIPT_DIR, '..')
const DEFAULT_ALLOWLIST = path.join(SCRIPT_DIR, 'ui-guard-allowlist.json')

const KNOWN_UI_FRAMEWORKS = [
  'ant-design-vue',
  'naive-ui',
  'vuetify',
  'quasar',
  'primevue',
  '@arco-design/web-vue',
  'tdesign-vue-next',
  'bootstrap-vue',
  'bootstrap-vue-next',
  'view-ui-plus',
  'vant',
  '@headlessui/vue',
  'radix-vue',
  'reka-ui',
  'floating-vue',
  'element-ui',
  '@varlet/ui',
  '@nutui/nutui',
  'vue-devui'
]

const KNOWN_ICON_LIBRARIES = [
  'lucide-vue-next',
  '@fortawesome',
  '@mdi',
  '@heroicons',
  '@iconify',
  'vue-feather-icons',
  'bootstrap-icons',
  'material-icons'
]

const KNOWN_DESIGN_SYSTEMS = [
  'tailwindcss',
  'bootstrap',
  'bulma',
  'daisyui',
  'unocss',
  '@chakra-ui',
  '@mui',
  'semantic-ui',
  'foundation-sites'
]

const DUPLICATE_COMPONENT_PATTERN = /(?:status[-_.]?(?:badge|pill|tag)|custom[-_.]?(?:tabs?|switch|dialog|pagination|table)|loading[-_.]?spinner|empty[-_.]?state|success[-_.]?state|error[-_.]?state)/i
const HARD_CODED_COLOR_PATTERN = /#[0-9a-f]{3,8}\b|(?:rgb|hsl)a?\([^)]*\)|oklch\([^)]*\)/gi
const HARD_CODED_METRIC_PATTERN = /\b(?:margin(?:-[a-z]+)?|padding(?:-[a-z]+)?|gap|row-gap|column-gap|border-radius|box-shadow|font-size)\s*:\s*[^;]*(?:-?\d*\.?\d+)(?:px|rem|em)\b/gi
const ABSOLUTE_PATTERN = /\bposition\s*:\s*absolute\b/i
const DATA_SVG_PATTERN = /data:image\/svg\+xml/i
const EMOJI_PATTERN = /\p{Extended_Pictographic}/u
const SCREENSHOT_PATTERN = /(?:screenshot|screen-shot|prototype|mockup|wireframe|page[-_]?capture|界面截图|原型图)[^\s"')]*(?:\.png|\.jpe?g|\.webp)/i

function normalizePath(value) {
  return value.split(path.sep).join('/')
}

function runGit(args, cwd, { allowFailure = false, trim = true } = {}) {
  try {
    const output = execFileSync('git', args, { cwd, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] })
    return trim ? output.trimEnd() : output
  } catch (error) {
    if (allowFailure) return ''
    const stderr = error?.stderr?.toString().trim()
    throw new Error(stderr || `git ${args.join(' ')} failed`)
  }
}

function gitSucceeds(args, cwd) {
  try {
    execFileSync('git', args, { cwd, stdio: 'ignore' })
    return true
  } catch {
    return false
  }
}

function parseArgs(argv) {
  const options = {
    all: false,
    staged: false,
    json: false,
    failOnWarn: false,
    base: process.env.UI_GUARD_BASE || '',
    allowlist: DEFAULT_ALLOWLIST,
    frontendRoot: DEFAULT_FRONTEND_ROOT,
    help: false
  }

  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index]
    if (arg === '--all') options.all = true
    else if (arg === '--staged') options.staged = true
    else if (arg === '--json') options.json = true
    else if (arg === '--fail-on-warn') options.failOnWarn = true
    else if (arg === '--help' || arg === '-h') options.help = true
    else if (arg === '--base') options.base = argv[++index] || ''
    else if (arg === '--allowlist') options.allowlist = path.resolve(argv[++index] || '')
    else if (arg === '--frontend-root') options.frontendRoot = path.resolve(argv[++index] || '')
    else throw new Error(`Unknown option: ${arg}`)
  }

  if (options.all && options.staged) {
    throw new Error('--all and --staged cannot be used together')
  }
  return options
}

function usage() {
  return `UI Guard checks newly added or modified frontend content by default.

Usage:
  node scripts/ui-guard.mjs [options]

Options:
  --base <ref>          Compare the working tree with this Git ref (default: origin/main)
  --staged              Check staged content against HEAD
  --all                 Audit all current frontend files as a historical scan
  --allowlist <path>    Use another allowlist JSON file
  --fail-on-warn        Return a non-zero exit code when warnings exist
  --json                Print machine-readable JSON
  --frontend-root <dir> Override the frontend root
  -h, --help            Show this help
`
}

function loadAllowlist(filePath) {
  const parsed = JSON.parse(fs.readFileSync(filePath, 'utf8'))
  if (parsed.version !== 1 || !Array.isArray(parsed.entries)) {
    throw new Error(`Invalid UI Guard allowlist: ${filePath}`)
  }
  return parsed
}

function resolveBase(repoRoot, requested) {
  const candidates = [requested, 'origin/main', 'main', 'HEAD'].filter(Boolean)
  for (const candidate of candidates) {
    const resolved = runGit(['rev-parse', '--verify', `${candidate}^{commit}`], repoRoot, { allowFailure: true })
    if (resolved) return candidate
  }
  throw new Error('Unable to resolve a Git base ref for UI Guard')
}

function walkFiles(root) {
  const result = []
  const stack = [root]
  while (stack.length) {
    const current = stack.pop()
    for (const entry of fs.readdirSync(current, { withFileTypes: true })) {
      if (['node_modules', 'dist', '.git', 'graphify-out'].includes(entry.name)) continue
      const fullPath = path.join(current, entry.name)
      if (entry.isDirectory()) stack.push(fullPath)
      else result.push(fullPath)
    }
  }
  return result
}

function listChangedFiles({ repoRoot, frontendRoot, base, all, staged }) {
  if (all) {
    return walkFiles(frontendRoot)
      .filter((file) => /\.(?:vue|[cm]?[jt]sx?|s?css|json)$/.test(file))
      .map((file) => normalizePath(path.relative(frontendRoot, file)))
  }

  const frontendRepoPath = normalizePath(path.relative(repoRoot, frontendRoot))
  const diffArgs = staged
    ? ['diff', '--cached', '--name-only', '--diff-filter=ACMR', 'HEAD', '--', frontendRepoPath]
    : ['diff', '--name-only', '--diff-filter=ACMR', base, '--', frontendRepoPath]
  const tracked = runGit(diffArgs, repoRoot, { allowFailure: true }).split('\n').filter(Boolean)
  const untracked = staged
    ? []
    : runGit(['ls-files', '--others', '--exclude-standard', '--', frontendRepoPath], repoRoot, { allowFailure: true }).split('\n').filter(Boolean)

  return [...new Set([...tracked, ...untracked])]
    .map((repoFile) => normalizePath(path.relative(frontendRepoPath, repoFile)))
    .filter((file) => file && !file.startsWith('..'))
}

function readCurrentContent({ repoRoot, frontendRoot, file, staged }) {
  const repoFile = normalizePath(path.relative(repoRoot, path.join(frontendRoot, file)))
  if (staged) {
    return runGit(['show', `:${repoFile}`], repoRoot, { allowFailure: true, trim: false })
  }
  const fullPath = path.join(frontendRoot, file)
  return fs.existsSync(fullPath) ? fs.readFileSync(fullPath, 'utf8') : ''
}

function readBaseContent({ repoRoot, frontendRoot, file, base, all, staged }) {
  if (all) return ''
  const repoFile = normalizePath(path.relative(repoRoot, path.join(frontendRoot, file)))
  const ref = staged ? 'HEAD' : base
  return runGit(['show', `${ref}:${repoFile}`], repoRoot, { allowFailure: true, trim: false })
}

function diffAddedLineNumbers(baseContent, currentContent) {
  if (!baseContent) {
    return new Set(currentContent.split(/\r?\n/).map((_, index) => index + 1))
  }
  if (baseContent === currentContent) return new Set()

  const before = baseContent.split(/\r?\n/)
  const after = currentContent.split(/\r?\n/)
  const maxCells = 4_000_000
  if (before.length * after.length > maxCells) {
    // Large historical monoliths are checked by a bounded prefix/suffix comparison.
    let prefix = 0
    while (prefix < before.length && prefix < after.length && before[prefix] === after[prefix]) prefix += 1
    let suffix = 0
    while (
      suffix < before.length - prefix
      && suffix < after.length - prefix
      && before[before.length - 1 - suffix] === after[after.length - 1 - suffix]
    ) suffix += 1
    const changed = new Set()
    for (let line = prefix + 1; line <= after.length - suffix; line += 1) changed.add(line)
    return changed
  }

  let previous = new Uint32Array(after.length + 1)
  const rows = [previous]
  for (let i = 1; i <= before.length; i += 1) {
    const row = new Uint32Array(after.length + 1)
    for (let j = 1; j <= after.length; j += 1) {
      row[j] = before[i - 1] === after[j - 1]
        ? previous[j - 1] + 1
        : Math.max(previous[j], row[j - 1])
    }
    rows.push(row)
    previous = row
  }

  const unchangedAfter = new Set()
  let i = before.length
  let j = after.length
  while (i > 0 && j > 0) {
    if (before[i - 1] === after[j - 1]) {
      unchangedAfter.add(j)
      i -= 1
      j -= 1
    } else if (rows[i - 1][j] >= rows[i][j - 1]) {
      i -= 1
    } else {
      j -= 1
    }
  }
  return new Set(after.map((_, index) => index + 1).filter((line) => !unchangedAfter.has(line)))
}

function makeFinding(severity, rule, file, line, message, suggestion, sourceLine = '') {
  return { severity, rule, file, line, message, suggestion, sourceLine: sourceLine.trim() }
}

function packageNameMatches(name, patterns) {
  return patterns.some((pattern) => name === pattern || name.startsWith(`${pattern}/`))
}

function classifyDependency(name, allowlist) {
  const approved = allowlist.approvedDependencies || {}
  if ((approved.frameworks || []).includes(name)) return 'approved-framework'
  if ((approved.icons || []).includes(name)) return 'approved-icon'
  if ((approved.runtimeUi || []).includes(name)) return 'approved-runtime-ui'
  if (packageNameMatches(name, KNOWN_UI_FRAMEWORKS)) return 'ui-framework'
  if (packageNameMatches(name, KNOWN_ICON_LIBRARIES)) return 'icon-library'
  if (packageNameMatches(name, KNOWN_DESIGN_SYSTEMS)) return 'design-system'
  if (/(?:^|[-/@])(?:ui|components?|design|theme|icons?|charts?|grids?|tables?|editor|modal|tabs?|canvas|svg|datepickers?|selects?|forms?)(?:$|[-/])/i.test(name)) {
    return 'runtime-ui'
  }
  return 'runtime'
}

function extractImports(sourceLine) {
  const imports = []
  const patterns = [
    /\bfrom\s+['"]([^'"]+)['"]/g,
    /\bimport\s*\(\s*['"]([^'"]+)['"]\s*\)/g,
    /@(?:use|import)\s+['"]([^'"]+)['"]/g
  ]
  for (const pattern of patterns) {
    for (const match of sourceLine.matchAll(pattern)) imports.push(match[1])
  }
  return imports.filter((value) => !value.startsWith('.') && !value.startsWith('@/') && !value.startsWith('~/'))
}

function countTagValues(source, tagName, attribute, fallback = '') {
  const result = new Set()
  const pattern = new RegExp(`<${tagName}\\b[^>]*>`, 'g')
  for (const tag of source.match(pattern) || []) {
    const match = tag.match(new RegExp(`\\b${attribute}\\s*=\\s*["']([^"']+)["']`))
    result.add(match?.[1] || fallback)
  }
  return result
}

function lineNumberAt(source, offset) {
  return source.slice(0, offset).split(/\r?\n/).length
}

function rangeTouchesAdded(source, start, end, addedLineNumbers) {
  const startLine = lineNumberAt(source, start)
  const endLine = lineNumberAt(source, end)
  for (let line = startLine; line <= endLine; line += 1) {
    if (addedLineNumbers.has(line)) return true
  }
  return false
}

function hasAccessibleName(attributes, body = '') {
  if (/\b(?:aria-label|aria-labelledby|title)\s*=/.test(attributes)) return true
  const visible = body
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/<el-icon\b[^>]*>[\s\S]*?<\/el-icon>/gi, '')
    .replace(/<svg-icon\b[^>]*\/?\s*>/gi, '')
    .replace(/<[^>]+>/g, '')
    .replace(/{{[\s\S]*?}}/g, 'dynamic-label')
    .replace(/&nbsp;/g, ' ')
    .trim()
  return Boolean(visible)
}

function clickHandlerMayBeAsync(source, attributes) {
  const expression = (attributes.match(/@click(?:\.[\w-]+)*\s*=\s*["']([^"']+)["']/) || [])[1] || ''
  const handler = (expression.match(/^\s*([A-Za-z_$][\w$]*)/) || [])[1]
  if (!handler) return true
  const escaped = handler.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const patterns = [
    new RegExp(`(?:async\\s+)?function\\s+${escaped}\\s*\\(`),
    new RegExp(`(?:const|let)\\s+${escaped}\\s*=\\s*(?:async\\s*)?\\(`)
  ]
  const matches = patterns.map((pattern) => ({ pattern, match: pattern.exec(source) })).filter((item) => item.match)
  if (!matches.length) return true
  const start = Math.min(...matches.map((item) => item.match.index))
  const tail = source.slice(start, start + 5000)
  const endMatch = /\n}\s*(?=\n|<\/script>)/.exec(tail)
  const segment = endMatch ? tail.slice(0, endMatch.index + endMatch[0].length) : tail
  return /\basync\s+function\b|\bawait\b|\.then\s*\(|\.submit\s*\(/.test(segment)
}

function auditInteractiveMarkup({ file, source, addedLineNumbers }) {
  const findings = []
  const inspectMatches = (pattern, callback) => {
    for (const match of source.matchAll(pattern)) {
      const start = match.index || 0
      const end = start + match[0].length
      if (!rangeTouchesAdded(source, start, end, addedLineNumbers)) continue
      callback(match, start)
    }
  }

  inspectMatches(/<el-button\b((?:"[^"]*"|'[^']*'|[^"'<>])*)>([\s\S]*?)<\/el-button>/gi, (match, start) => {
    const attributes = match[1] || ''
    const body = match[2] || ''
    if (!hasAccessibleName(attributes, body, source, start)) {
      findings.push(makeFinding('warning', 'icon-button-name', file, lineNumberAt(source, start),
        'An Element Plus button has no visible text, aria-label, title, or aria-labelledby.',
        'Keep concise action text, or add an accurate aria-label/title for a truly icon-only control.', match[0].split(/\r?\n/)[0]))
    }
  })

  inspectMatches(/<el-button\b((?:"[^"]*"|'[^']*'|[^"'<>])*)\/>/gi, (match, start) => {
    const attributes = match[1] || ''
    if (!hasAccessibleName(attributes, '', source, start)) {
      findings.push(makeFinding('warning', 'icon-button-name', file, lineNumberAt(source, start),
        'A self-closing Element Plus button has no accessible name.',
        'Add an accurate aria-label or title; a visual Tooltip alone is not an accessible name.', match[0]))
    }
  })

  inspectMatches(/<button\b((?:"[^"]*"|'[^']*'|[^"'<>])*)>([\s\S]*?)<\/button>/gi, (match, start) => {
    const attributes = match[1] || ''
    const body = match[2] || ''
    const line = lineNumberAt(source, start)
    if (!/\btype\s*=/.test(attributes)) {
      findings.push(makeFinding('warning', 'native-button-type', file, line,
        'A native button omits its type and may submit a surrounding form unexpectedly.',
        'Set type="button" unless this is intentionally the form submit control.', match[0].split(/\r?\n/)[0]))
    }
    if (!hasAccessibleName(attributes, body, source, start)) {
      findings.push(makeFinding('warning', 'native-button-name', file, line,
        'A native button has no accessible name.',
        'Add visible action text or an accurate aria-label.', match[0].split(/\r?\n/)[0]))
    }
  })

  inspectMatches(/<(div|span|li|article)\b((?:"[^"]*"|'[^']*'|[^"'<>])*)>/gi, (match, start) => {
    const attributes = match[2] || ''
    if (!/(?:^|\s)@click(?:\.[\w-]+)*\s*=/.test(attributes)) return
    if (!/\brole\s*=/.test(attributes) || !/\btabindex\s*=/.test(attributes)) {
      findings.push(makeFinding('warning', 'nonsemantic-click-target', file, lineNumberAt(source, start),
        `Clickable <${match[1].toLowerCase()}> content is not fully keyboard-operable.`,
        'Use a native button/link, or provide role, tabindex, and equivalent keyboard handlers.', match[0]))
    }
  })

  inspectMatches(/<img\b((?:"[^"]*"|'[^']*'|[^"'<>])*)>/gi, (match, start) => {
    if (!/\balt\s*=/.test(match[1] || '')) {
      findings.push(makeFinding('warning', 'image-alt', file, lineNumberAt(source, start),
        'An image has no alt attribute.',
        'Provide meaningful alt text, or alt="" for a purely decorative image.', match[0]))
    }
  })

  inspectMatches(/<el-radio(?:-button)?\b((?:"[^"]*"|'[^']*'|[^"'<>])*)\/?\s*>/gi, (match, start) => {
    const attributes = match[1] || ''
    if (/\b:?label\s*=/.test(attributes) && !/\b:?value\s*=/.test(attributes)) {
      findings.push(makeFinding('warning', 'deprecated-radio-value', file, lineNumberAt(source, start),
        'An Element Plus Radio uses label as its bound value, which is deprecated.',
        'Move the model value to value/:value and keep visible text in the default slot.', match[0]))
    }
  })

  inspectMatches(/<el-(dialog|drawer)\b((?:"[^"]*"|'[^']*'|[^"'<>])*)>\s*<template\s+#header\b/gi, (match, start) => {
    const headerStart = start + match[0].lastIndexOf('<template')
    const headerEnd = source.indexOf('</template>', headerStart)
    const headerBlock = source.slice(headerStart, headerEnd < 0 ? headerStart + 800 : headerEnd)
    const exposesTitleId = /#header\s*=\s*["'][^"']*\btitleId\b[^"']*["']/.test(headerBlock)
    const bindsTitleId = /:id\s*=\s*["']titleId["']/.test(headerBlock)
    if (!exposesTitleId || !bindsTitleId) {
      findings.push(makeFinding('warning', 'dialog-accessible-name', file, lineNumberAt(source, start),
        `An Element Plus ${match[1] === 'drawer' ? 'Drawer' : 'Dialog'} custom header is not connected to titleId.`,
        'Expose titleId/titleClass from the header slot and bind :id="titleId" to the visible title wrapper.', match[0].split(/\r?\n/)[0]))
    }
  })

  inspectMatches(/<el-(dialog|drawer)\b((?:"[^"]*"|'[^']*'|[^"'<>])*)>/gi, (match, start) => {
    const attributes = match[2] || ''
    const afterOpen = source.slice(start + match[0].length, start + match[0].length + 300)
    const hasTitle = /\b:?title\s*=/.test(attributes)
    const hasCustomHeader = /^\s*<template\s+#header\b/.test(afterOpen)
    if (!hasTitle && !hasCustomHeader) {
      findings.push(makeFinding('warning', 'dialog-accessible-name', file, lineNumberAt(source, start),
        `An Element Plus ${match[1] === 'drawer' ? 'Drawer' : 'Dialog'} has no title or connected custom header.`,
        'Provide title/:title, or connect a custom header through titleId.', match[0].split(/\r?\n/)[0]))
    }
  })

  inspectMatches(/<template\s+#footer\b[^>]*>([\s\S]*?)<\/template>/gi, (footerMatch, footerStart) => {
    const body = footerMatch[1] || ''
    const buttons = [...body.matchAll(/<el-button\b((?:"[^"]*"|'[^']*'|[^"'<>])*)>([\s\S]*?)<\/el-button>/gi)].map((match) => ({
      attributes: match[1] || '',
      label: (match[2] || '').replace(/<[^>]+>/g, '').replace(/{{[\s\S]*?}}/g, '动态操作').replace(/\s+/g, ''),
      offset: footerStart + (footerMatch[0].indexOf(match[0]) || 0)
    }))
    const cancelIndex = buttons.findIndex((button) => /^(?:取消|关闭|返回)$/.test(button.label))
    const confirmIndex = buttons.findIndex((button) => /(?:确定|确认|保存|提交|创建|新增|添加|修改|授权|恢复|开始导入|开始导出|生成)/.test(button.label))
    if (confirmIndex >= 0 && cancelIndex > confirmIndex) {
      const button = buttons[confirmIndex]
      findings.push(makeFinding('warning', 'dialog-footer-order', file, lineNumberAt(source, button.offset),
        'The Dialog footer places the primary confirmation before cancel/close.',
        'Keep cancel/close first and the primary confirmation last.', button.label))
    }
    if (confirmIndex >= 0) {
      const button = buttons[confirmIndex]
      if (clickHandlerMayBeAsync(source, button.attributes) && !/\b:loading\s*=|\bloading\s*=/.test(button.attributes)) {
        findings.push(makeFinding('warning', 'dialog-submit-loading', file, lineNumberAt(source, button.offset),
          'A Dialog confirmation action has no loading state.',
          'Bind the existing submit state to :loading and block duplicate submission without changing the request flow.', button.label))
      }
    }
  })

  return findings
}

function nearestStyleSelector(lines, index) {
  const selectors = []
  for (let cursor = index; cursor >= Math.max(0, index - 12); cursor -= 1) {
    const line = lines[cursor]
    const brace = line.indexOf('{')
    if (brace < 0) continue
    const selector = line.slice(0, brace).trim()
    if (!selector) continue
    selectors.unshift(selector)
    if (!selector.startsWith('&')) break
  }
  return selectors.join(' ')
}

function styleBlockFromLine(lines, index) {
  const block = []
  let depth = 0
  let started = false
  for (let cursor = index; cursor < Math.min(lines.length, index + 100); cursor += 1) {
    const line = lines[cursor]
    block.push(line)
    const opens = (line.match(/{/g) || []).length
    const closes = (line.match(/}/g) || []).length
    if (opens) started = true
    depth += opens - closes
    if (started && depth <= 0) break
  }
  return block.join('\n')
}

function coreStyleIsUnsafe(block) {
  if (HARD_CODED_COLOR_PATTERN.test(block)) {
    HARD_CODED_COLOR_PATTERN.lastIndex = 0
    return true
  }
  HARD_CODED_COLOR_PATTERN.lastIndex = 0
  for (const match of block.matchAll(/border-radius\s*:\s*(\d+(?:\.\d+)?)px/gi)) {
    if (Number(match[1]) > 16) return true
  }
  for (const match of block.matchAll(/(?:^|[;{]\s*)(?:color|background(?:-color)?)\s*:\s*([^;]+)/gim)) {
    const value = match[1].trim()
    if (!/^(?:transparent|inherit|var\(|color-mix\()/i.test(value)) return true
  }
  for (const match of block.matchAll(/box-shadow\s*:\s*([^;]+)/gi)) {
    const value = match[1].trim()
    if (!/^none\b|^var\(|color-mix\(/i.test(value)) return true
  }
  return false
}

function analyzeVueFile({ file, currentContent, baseContent = '', addedLineNumbers, thresholds = {} }) {
  const findings = []
  const lines = currentContent.split(/\r?\n/)
  const businessView = file.startsWith('src/views/') && file.endsWith('.vue')
  const newFile = !baseContent
  const colorHits = []
  const metricHits = []
  const absoluteHits = []
  const coreStyleHits = []
  const scriptStart = lines.findIndex((line) => /^\s*<script\b/.test(line))
  const scriptEnd = scriptStart >= 0 ? lines.findIndex((line, index) => index > scriptStart && /^\s*<\/script>/.test(line)) : -1
  const styleStart = lines.findIndex((line) => /^\s*<style\b/.test(line))
  let actionDepth = 0

  for (let index = 0; index < lines.length; index += 1) {
    const lineNumber = index + 1
    const sourceLine = lines[index]
    const section = styleStart >= 0 && index >= styleStart
      ? 'style'
      : (scriptStart >= 0 && index >= scriptStart && (scriptEnd < 0 || index <= scriptEnd) ? 'script' : 'template')

    const opensAction = /<(?:el-button|button|el-dropdown-item|el-link)\b/i.test(sourceLine)
      && !/\/>/.test(sourceLine)
    if (opensAction) actionDepth += 1

    if (addedLineNumbers.has(lineNumber)) {
      if (DATA_SVG_PATTERN.test(sourceLine)) {
        findings.push(makeFinding('error', 'data-svg-uri', file, lineNumber,
          'data:image/svg+xml bypasses the project icon system.',
          'Use an existing Element Plus icon or SvgIcon asset.', sourceLine))
      }

      for (const dependency of extractImports(sourceLine)) {
        if (packageNameMatches(dependency, KNOWN_UI_FRAMEWORKS)) {
          findings.push(makeFinding('error', 'unapproved-ui-framework', file, lineNumber,
            `Import from unapproved UI framework "${dependency}".`,
            'Use Element Plus and existing project components.', sourceLine))
        } else if (packageNameMatches(dependency, KNOWN_ICON_LIBRARIES)) {
          findings.push(makeFinding('error', 'unapproved-icon-library', file, lineNumber,
            `Import from unapproved icon library "${dependency}".`,
            'Use @element-plus/icons-vue, SvgIcon, or IconSelect.', sourceLine))
        } else if (packageNameMatches(dependency, KNOWN_DESIGN_SYSTEMS)) {
          findings.push(makeFinding('error', 'second-design-system', file, lineNumber,
            `Import from second design system "${dependency}".`,
            'Use the existing SCSS variables and Element Plus theme.', sourceLine))
        }
      }

      if (businessView && section === 'template') {
        if (/<svg(?:\s|>)/i.test(sourceLine)) {
          findings.push(makeFinding('error', 'business-inline-svg', file, lineNumber,
            'Inline SVG was added to a business view.',
            'Map the icon to @element-plus/icons-vue or SvgIcon; register real visualization exceptions.', sourceLine))
        }
        if (/<canvas\b/i.test(sourceLine)) {
          findings.push(makeFinding('error', 'canvas-ui-simulation', file, lineNumber,
            'Canvas was added to a business view.',
            'Use normal DOM and Element Plus for UI; allowlist only a real chart, map, diagram, or flow.', sourceLine))
        }
        const imageWindow = lines.slice(Math.max(0, index - 3), Math.min(lines.length, index + 4)).join(' ')
        if (SCREENSHOT_PATTERN.test(imageWindow) && /<img\b|background(?:-image)?\s*:/i.test(imageWindow)) {
          findings.push(makeFinding('error', 'screenshot-ui-simulation', file, lineNumber,
            'A screenshot or prototype image appears to be used as interface content.',
            'Implement semantic regions with existing components; images may only be real business content.', sourceLine))
        }
        if (EMOJI_PATTERN.test(sourceLine) && (actionDepth > 0 || /@click\b|role=["']button["']/i.test(sourceLine))) {
          findings.push(makeFinding('error', 'emoji-operation-icon', file, lineNumber,
            'Emoji was added inside an interactive operation.',
            'Use an existing formal icon and keep accessible text.', sourceLine))
        }
      }

      if (businessView && section === 'style') {
        const colors = sourceLine.match(HARD_CODED_COLOR_PATTERN) || []
        for (const color of colors) colorHits.push({ line: lineNumber, value: color, sourceLine })
        const metrics = sourceLine.match(HARD_CODED_METRIC_PATTERN) || []
        for (const metric of metrics) metricHits.push({ line: lineNumber, value: metric, sourceLine })
        if (ABSOLUTE_PATTERN.test(sourceLine)) absoluteHits.push({ line: lineNumber, sourceLine })
        if (/(?:\.el-(?:button|tabs?|switch|badge|dialog)|\.(?:custom|page|business)[-_]?(?:button|tabs?|switch|badge))\b/i.test(sourceLine)
          && coreStyleIsUnsafe(styleBlockFromLine(lines, index))) {
          coreStyleHits.push({ line: lineNumber, sourceLine })
        }
        const selectorContext = nearestStyleSelector(lines, index)
        const iconNamedSelector = /\.[\w-]*(?:icon|arrow|chevron|spinner)[\w-]*/i.test(selectorContext)
        const syntheticSelector = (iconNamedSelector || />\s*i(?:::|\b)/i.test(selectorContext))
          && /(?:::before|::after|>\s*i(?:::|\b))/i.test(selectorContext)
        if (syntheticSelector
          && /(?:content\s*:|border(?:-[a-z]+)?\s*:[^;]*solid)/i.test(sourceLine)) {
          findings.push(makeFinding('warning', 'css-drawn-icon', file, lineNumber,
            'CSS appears to draw or synthesize an icon.',
            'Use an existing Element Plus icon or SvgIcon.', sourceLine))
        }
      }
    }

    if (/<\/(?:el-button|button|el-dropdown-item|el-link)>/i.test(sourceLine)) {
      actionDepth = Math.max(0, actionDepth - 1)
    }
  }

  const colorThreshold = Number(thresholds.hardcodedColorCount || 4)
  const colorUniqueThreshold = Number(thresholds.hardcodedColorUnique || 3)
  if (colorHits.length >= colorThreshold && new Set(colorHits.map((hit) => hit.value.toLowerCase())).size >= colorUniqueThreshold) {
    const first = colorHits[0]
    findings.push(makeFinding('warning', 'hardcoded-colors', file, first.line,
      `${colorHits.length} added hardcoded color values introduce a page-local palette.`,
      'Use existing --app-*, --surface-*, --health-*, --support-*, or --el-* semantic variables.', first.sourceLine))
  }

  const metricThreshold = Number(thresholds.hardcodedMetricCount || 8)
  const metricGroups = { spacing: [], radius: [], shadow: [], font: [] }
  for (const hit of metricHits) {
    const property = (hit.value.match(/^([\w-]+)/) || [])[1] || ''
    const numeric = (hit.value.match(/-?\d*\.?\d+(?:px|rem|em)/) || [])[0] || hit.value
    if (property === 'border-radius') metricGroups.radius.push(numeric)
    else if (property === 'box-shadow') metricGroups.shadow.push(numeric)
    else if (property === 'font-size') metricGroups.font.push(numeric)
    else metricGroups.spacing.push(numeric)
  }
  const uniqueCount = (values) => new Set(values).size
  const densityReasons = [
    ['spacing values', uniqueCount(metricGroups.spacing), Number(thresholds.spacingUniqueCount || 13)],
    ['font sizes', uniqueCount(metricGroups.font), Number(thresholds.fontSizeUniqueCount || 9)],
    ['radii', uniqueCount(metricGroups.radius), Number(thresholds.radiusUniqueCount || 8)],
    ['shadows', uniqueCount(metricGroups.shadow), Number(thresholds.shadowUniqueCount || 5)]
  ].filter(([, count, threshold]) => count >= threshold)
  if (metricHits.length >= metricThreshold && densityReasons.length) {
    const first = metricHits[0]
    findings.push(makeFinding('warning', 'hardcoded-shape-spacing', file, first.line,
      `The page introduces an unusually broad visual scale: ${densityReasons.map(([label, count]) => `${count} ${label}`).join(', ')}.`,
      'Reuse the incumbent page rhythm and Element Plus/project variables; allow only content-specific dimensions.', first.sourceLine))
  }

  const absoluteThreshold = Number(thresholds.absoluteCount || 4)
  if (absoluteHits.length >= absoluteThreshold) {
    const first = absoluteHits[0]
    findings.push(makeFinding('warning', 'absolute-layout', file, first.line,
      `${absoluteHits.length} added absolute-position declarations may be reproducing a prototype by coordinates.`,
      'Use grid, flex, normal flow, or an approved topology/visualization exception.', first.sourceLine))
  }

  if (coreStyleHits.length) {
    const first = coreStyleHits[0]
    findings.push(makeFinding('warning', 'custom-core-control-style', file, first.line,
      'Page-level styles override or recreate a core button, Tab, Switch, Badge, or Dialog.',
      'Use the standard Element Plus variant and global project theme; keep overrides narrowly semantic.', first.sourceLine))
  }

  if (newFile && DUPLICATE_COMPONENT_PATTERN.test(file)) {
    findings.push(makeFinding('warning', 'duplicate-component', file, 1,
      'The new component name suggests a duplicate status, Tabs, Switch, loading, empty, Dialog, or pagination component.',
      'Use DictTag, Element Plus, or an existing shared component; document cross-domain need before adding a wrapper.'))
  }

  if (newFile && file.startsWith('src/views/') && /\/components\/(?:Pagination|Tabs?|Switch|Dialog|Empty|Loading|Status)[^/]*\.vue$/i.test(file)) {
    findings.push(makeFinding('warning', 'duplicate-page-component', file, 1,
      'A page-local component duplicates a common control category.',
      'Map it to the project catalog or Element Plus before keeping page-local code.'))
  }

  if (businessView && addedLineNumbers.size) {
    const currentSizes = countTagValues(currentContent, 'el-button', 'size')
    const baseSizes = countTagValues(baseContent, 'el-button', 'size')
    currentSizes.delete('')
    baseSizes.delete('')
    if (currentSizes.size > 1 && baseSizes.size <= 1) {
      findings.push(makeFinding('warning', 'mixed-button-sizes', file, 1,
        `The page now uses multiple explicit button sizes: ${[...currentSizes].join(', ')}.`,
        'Use one size per operation region and explain any intentional density boundary.'))
    }

    const currentTabs = countTagValues(currentContent, 'el-tabs', 'type', 'default')
    const baseTabs = countTagValues(baseContent, 'el-tabs', 'type', 'default')
    if (currentTabs.size > 1 && baseTabs.size <= 1) {
      findings.push(makeFinding('warning', 'mixed-tab-styles', file, 1,
        `The page now mixes Tab styles: ${[...currentTabs].join(', ')}.`,
        'Use one Tab visual mode within the business area.'))
    }
  }

  if (file.endsWith('.vue') && addedLineNumbers.size) {
    findings.push(...auditInteractiveMarkup({ file, source: currentContent, addedLineNumbers }))
  }

  return findings
}

function analyzeGenericSource({ file, currentContent, addedLineNumbers }) {
  const findings = []
  const lines = currentContent.split(/\r?\n/)
  for (const lineNumber of addedLineNumbers) {
    const sourceLine = lines[lineNumber - 1] || ''
    if (DATA_SVG_PATTERN.test(sourceLine)) {
      findings.push(makeFinding('error', 'data-svg-uri', file, lineNumber,
        'data:image/svg+xml bypasses the project icon system.',
        'Use an existing Element Plus icon or SvgIcon asset.', sourceLine))
    }
    for (const dependency of extractImports(sourceLine)) {
      if (packageNameMatches(dependency, KNOWN_UI_FRAMEWORKS)) {
        findings.push(makeFinding('error', 'unapproved-ui-framework', file, lineNumber,
          `Import from unapproved UI framework "${dependency}".`,
          'Use Element Plus and current project components.', sourceLine))
      } else if (packageNameMatches(dependency, KNOWN_ICON_LIBRARIES)) {
        findings.push(makeFinding('error', 'unapproved-icon-library', file, lineNumber,
          `Import from unapproved icon library "${dependency}".`,
          'Use @element-plus/icons-vue, SvgIcon, or IconSelect.', sourceLine))
      } else if (packageNameMatches(dependency, KNOWN_DESIGN_SYSTEMS)) {
        findings.push(makeFinding('error', 'second-design-system', file, lineNumber,
          `Import from second design system "${dependency}".`,
          'Use current SCSS variables and Element Plus theme.', sourceLine))
      }
    }
  }
  return findings
}

function analyzePackage({ file, currentContent, baseContent, allowlist }) {
  if (!currentContent) return []
  const findings = []
  const current = JSON.parse(currentContent)
  const base = baseContent ? JSON.parse(baseContent) : { dependencies: {}, devDependencies: {} }
  for (const section of ['dependencies', 'devDependencies']) {
    const currentDependencies = current[section] || {}
    const baseDependencies = base[section] || {}
    for (const [name, version] of Object.entries(currentDependencies)) {
      if (!(name in baseDependencies)) {
        const kind = classifyDependency(name, allowlist)
        if (kind === 'ui-framework') {
          findings.push(makeFinding('error', 'unapproved-ui-framework', file, 1,
            `New unapproved UI framework dependency: ${name}@${version}.`,
            'Use Element Plus; dependency additions require explicit approval.'))
        } else if (kind === 'icon-library') {
          findings.push(makeFinding('error', 'unapproved-icon-library', file, 1,
            `New unapproved icon dependency: ${name}@${version}.`,
            'Use @element-plus/icons-vue and the local SVG catalog.'))
        } else if (kind === 'design-system') {
          findings.push(makeFinding('error', 'second-design-system', file, 1,
            `New second design-system dependency: ${name}@${version}.`,
            'Use current SCSS variables and Element Plus.'))
        } else if (section === 'dependencies' && kind === 'runtime-ui') {
          findings.push(makeFinding('error', 'unapproved-runtime-ui-dependency', file, 1,
            `New runtime UI dependency is not approved: ${name}@${version}.`,
            'Map the need to existing dependencies or obtain explicit approval and update the exception ledger.'))
        } else if (section === 'dependencies') {
          findings.push(makeFinding('warning', 'new-runtime-dependency', file, 1,
            `New runtime dependency requires scope review: ${name}@${version}.`,
            'Confirm it is necessary, explicitly approved, and not duplicating current UI capability.'))
        }
      } else if (baseDependencies[name] !== version) {
        const kind = classifyDependency(name, allowlist)
        const severity = ['approved-framework', 'approved-icon', 'approved-runtime-ui'].includes(kind) ? 'error' : 'warning'
        findings.push(makeFinding(severity, 'dependency-version-change', file, 1,
          `Dependency version changed: ${name} ${baseDependencies[name]} -> ${version}.`,
          'Dependency upgrades are outside ordinary UI work; restore the version or obtain explicit approval.'))
      }
    }
  }
  return findings
}

function globToRegExp(glob) {
  let output = '^'
  for (let index = 0; index < glob.length; index += 1) {
    const char = glob[index]
    if (char === '*' && glob[index + 1] === '*') {
      output += '.*'
      index += 1
    } else if (char === '*') output += '[^/]*'
    else if (char === '?') output += '[^/]'
    else output += char.replace(/[|\\{}()[\]^$+?.]/g, '\\$&')
  }
  return new RegExp(`${output}$`)
}

function entryMatches(entry, finding) {
  if (entry.rule !== finding.rule && entry.rule !== '*') return false
  if (!entry.paths.some((glob) => globToRegExp(glob).test(finding.file))) return false
  if (entry.lineStart && finding.line < entry.lineStart) return false
  if (entry.lineEnd && finding.line > entry.lineEnd) return false
  if (entry.linePattern) {
    try {
      if (!new RegExp(entry.linePattern).test(finding.sourceLine || '')) return false
    } catch {
      return false
    }
  }
  return true
}

function applyAllowlist(findings, allowlist, today = new Date()) {
  return findings.map((finding) => {
    for (const entry of allowlist.entries) {
      if (!entryMatches(entry, finding)) continue
      if (entry.expires && new Date(`${entry.expires}T23:59:59Z`) < today) {
        return {
          ...finding,
          allowlistExpired: true,
          allowlistId: entry.id,
          message: `${finding.message} Allowlist entry ${entry.id} expired on ${entry.expires}.`
        }
      }
      return {
        ...finding,
        severity: 'exception',
        allowlistId: entry.id,
        reason: entry.reason
      }
    }
    return finding
  })
}

function dedupeFindings(findings) {
  const seen = new Set()
  return findings.filter((finding) => {
    const key = [finding.severity, finding.rule, finding.file, finding.line, finding.message].join('|')
    if (seen.has(key)) return false
    seen.add(key)
    return true
  })
}

function formatFinding(finding) {
  const label = finding.severity.toUpperCase()
  const location = `${finding.file}:${finding.line || 1}`
  const exception = finding.allowlistId ? ` (${finding.allowlistId})` : ''
  const reason = finding.reason ? `\n  exception: ${finding.reason}` : ''
  return `${label} [${finding.rule}] ${location}${exception}\n  ${finding.message}\n  fix: ${finding.suggestion}${reason}`
}

async function main() {
  const options = parseArgs(process.argv.slice(2))
  if (options.help) {
    process.stdout.write(usage())
    return 0
  }

  const frontendRoot = path.resolve(options.frontendRoot)
  const repoRoot = runGit(['rev-parse', '--show-toplevel'], frontendRoot)
  const allowlist = loadAllowlist(options.allowlist)
  const base = options.all ? null : resolveBase(repoRoot, options.staged ? 'HEAD' : options.base)
  if (!options.all && !options.staged && !gitSucceeds(['merge-base', '--is-ancestor', base, 'HEAD'], repoRoot)) {
    throw new Error(`Base ref ${base} is not an ancestor of HEAD. Synchronize this worktree before running UI Guard.`)
  }
  const files = listChangedFiles({ repoRoot, frontendRoot, base, all: options.all, staged: options.staged })
  const findings = []

  for (const file of files) {
    const currentContent = readCurrentContent({ repoRoot, frontendRoot, file, staged: options.staged })
    if (!currentContent) continue
    const baseContent = options.all && file === 'package.json'
      ? currentContent
      : readBaseContent({ repoRoot, frontendRoot, file, base, all: options.all, staged: options.staged })
    const addedLineNumbers = options.all
      ? new Set(currentContent.split(/\r?\n/).map((_, index) => index + 1))
      : diffAddedLineNumbers(baseContent, currentContent)
    if (!addedLineNumbers.size && file !== 'package.json') continue

    if (file === 'package.json') {
      findings.push(...analyzePackage({ file, currentContent, baseContent, allowlist }))
    } else if (file.endsWith('.vue')) {
      findings.push(...analyzeVueFile({
        file,
        currentContent,
        baseContent,
        addedLineNumbers,
        thresholds: allowlist.thresholds || {}
      }))
    } else if (/\.(?:[cm]?[jt]sx?|s?css)$/.test(file) && file.startsWith('src/')) {
      findings.push(...analyzeGenericSource({ file, currentContent, addedLineNumbers }))
    }
  }

  const reviewed = dedupeFindings(applyAllowlist(findings, allowlist))
  const summary = {
    errors: reviewed.filter((finding) => finding.severity === 'error').length,
    warnings: reviewed.filter((finding) => finding.severity === 'warning').length,
    exceptions: reviewed.filter((finding) => finding.severity === 'exception').length
  }
  const report = {
    mode: options.all ? 'all' : (options.staged ? 'staged' : 'diff'),
    base,
    frontendRoot,
    filesChecked: files,
    findings: reviewed,
    summary
  }

  if (options.json) {
    process.stdout.write(`${JSON.stringify(report, null, 2)}\n`)
  } else {
    process.stdout.write(`UI Guard (${report.mode})\n`)
    process.stdout.write(`Base: ${base || 'historical full scan'}\n`)
    process.stdout.write(`Files checked: ${files.length}\n`)
    if (!reviewed.length) process.stdout.write('PASS: no UI governance findings.\n')
    else process.stdout.write(`${reviewed.map(formatFinding).join('\n\n')}\n`)
    process.stdout.write(`Summary: ${summary.errors} errors, ${summary.warnings} warnings, ${summary.exceptions} exceptions\n`)
  }

  return summary.errors > 0 || (options.failOnWarn && summary.warnings > 0) ? 1 : 0
}

export {
  analyzePackage,
  analyzeVueFile,
  applyAllowlist,
  classifyDependency,
  diffAddedLineNumbers,
  globToRegExp
}

const isMain = process.argv[1]
  && pathToFileURL(path.resolve(process.argv[1])).href === import.meta.url

if (isMain) {
  main()
    .then((exitCode) => {
      process.exitCode = exitCode
    })
    .catch((error) => {
      process.stderr.write(`UI Guard failed: ${error.message}\n`)
      process.exitCode = 2
    })
}
