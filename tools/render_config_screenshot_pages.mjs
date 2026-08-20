#!/usr/bin/env node

import fs from 'node:fs'
import path from 'node:path'

const args = Object.fromEntries(process.argv.slice(2).reduce((pairs, value, index, all) => {
  if (value.startsWith('--')) pairs.push([value.slice(2), all[index + 1]])
  return pairs
}, []))

if (!args.package || !args.dist || !args.out) {
  throw new Error('usage: --package <delivery-dir> --dist <dist-dir> --out <html-dir>')
}

const packageDir = path.resolve(args.package)
const distDir = path.resolve(args.dist)
const outDir = path.resolve(args.out)
fs.mkdirSync(outDir, { recursive: true })

function escapeHtml(value) {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
}

function page(title, subtitle, content, tone = '#52a8ff') {
  const lines = content.trimEnd().split('\n')
  const rendered = lines.map((line, index) => (
    `<div class="line"><span class="no">${String(index + 1).padStart(2, '0')}</span><span class="code">${escapeHtml(line) || '&nbsp;'}</span></div>`
  )).join('')
  return `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><style>
    *{box-sizing:border-box} body{margin:0;background:#eef3f8;color:#dbe8f7;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC",sans-serif}
    .sheet{width:1600px;min-height:900px;padding:52px 58px;background:linear-gradient(135deg,#edf3f8 0%,#f8fbfd 100%)}
    .bar{display:flex;align-items:center;justify-content:space-between;margin-bottom:24px;color:#17365d}
    h1{margin:0;font-size:30px;letter-spacing:.5px}.sub{font-size:17px;color:#667085}.window{overflow:hidden;border-radius:18px;background:#101826;box-shadow:0 26px 60px rgba(25,53,86,.20)}
    .chrome{height:52px;display:flex;align-items:center;padding:0 20px;border-bottom:1px solid #26374c;background:#172235}.dots{display:flex;gap:9px}.dot{width:12px;height:12px;border-radius:50%}.red{background:#ff6b6b}.yellow{background:#ffd166}.green{background:#5bd28d}.file{margin-left:22px;color:#c2d2e5;font:600 15px ui-monospace,SFMono-Regular,Menlo,monospace}
    .codebox{padding:12px 0 18px;font:15px/1.35 ui-monospace,SFMono-Regular,Menlo,"PingFang SC",monospace}.line{display:grid;grid-template-columns:64px 1fr;min-height:20px}.line:hover{background:#17263a}.no{text-align:right;padding-right:18px;color:#60758e;user-select:none}.code{white-space:pre;color:#e5edf6}.line:nth-child(3n+1) .code{color:#d8e9fb}
    .accent{height:5px;background:${tone}}
  </style></head><body><main class="sheet"><div class="bar"><h1>${escapeHtml(title)}</h1><div class="sub">${escapeHtml(subtitle)}</div></div><section class="window"><div class="accent"></div><div class="chrome"><div class="dots"><i class="dot red"></i><i class="dot yellow"></i><i class="dot green"></i></div><div class="file">${escapeHtml(title)}</div></div><div class="codebox">${rendered}</div></section></main></body></html>`
}

function write(name, title, subtitle, content, tone) {
  fs.writeFileSync(path.join(outDir, `${name}.html`), page(title, subtitle, content, tone))
}

const readPackage = (relative) => fs.readFileSync(path.join(packageDir, relative), 'utf8')
const files = fs.readdirSync(distDir).sort()
const staticFiles = fs.readdirSync(path.join(distDir, 'static')).sort()

write('deploy-01-directory', '交付目录与现场目录', '按此位置复制即可', `交付包
├── backend/wjdatafusion-admin-v3.9.1-20260810.jar  → /opt/rynew/backend/
├── frontend/rynew-frontend-v3.9.1-20260810.tar.gz → /opt/rynew/frontend/
├── database/rynew-init-v3.9.1-site-auto-demo.sql  → MySQL rynew 库
├── config/external/application.yml                 → /opt/rynew/config/
├── config/external/application-druid.yml           → /opt/rynew/config/
├── config/start-backend.sh                         → /opt/rynew/
└── config/rynew-nginx.conf                         → Nginx 站点配置目录

现场一次创建目录：
install -d /opt/rynew/{backend,frontend,config,upload,logs}`, '#4d9fff')

write('deploy-02-application-yml', 'application.yml', '端口 · Redis · 密钥', readPackage('config/external/application.yml'), '#4d9fff')
write('deploy-03-application-druid', 'application-druid.yml', 'MySQL 数据源', readPackage('config/external/application-druid.yml'), '#5bc78d')
write('deploy-04-start-script', 'start-backend.sh', '直接 java -jar + 外部 Spring 配置', readPackage('config/start-backend.sh'), '#f3b94f')
write('deploy-05-frontend-dist', '前端 dist 目录', 'index.html 位于根目录', `dist/
${files.map((name) => `├── ${name}${fs.statSync(path.join(distDir, name)).isDirectory() ? '/' : ''}`).join('\n')}

dist/static/
${staticFiles.map((name) => `├── ${name}/`).join('\n')}

部署命令：
tar -xzf rynew-frontend-v3.9.1-20260810.tar.gz -C /opt/rynew/frontend`, '#9a7ff2')
write('deploy-06-nginx', 'rynew-nginx.conf', 'dist 根目录 + /prod-api/ 反向代理', readPackage('config/rynew-nginx.conf'), '#4d9fff')

console.log('html_pages=6')
