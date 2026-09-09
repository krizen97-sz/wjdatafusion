#!/usr/bin/env node
/** Local-only acceptance gateway. No administrative credentials are read or injected. */
import http from 'node:http';
import https from 'node:https';
import { constants } from 'node:fs';
import { open, readFile, realpath, stat } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const HOP_HEADERS = new Set(['connection', 'keep-alive', 'proxy-authenticate', 'proxy-authorization', 'te', 'trailer', 'transfer-encoding', 'upgrade']);
const MIME = new Map(Object.entries({ '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.mjs': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.svg': 'image/svg+xml', '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.webp': 'image/webp', '.gif': 'image/gif', '.ico': 'image/x-icon', '.woff': 'font/woff', '.woff2': 'font/woff2', '.ttf': 'font/ttf', '.otf': 'font/otf', '.eot': 'application/vnd.ms-fontobject', '.pdf': 'application/pdf', '.docx': 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' }));
const PRIVATE_SEGMENTS = new Set(['private', 'credentials', 'downloads', 'evidence', 'node_modules']);

export function requestPath(target) {
  if (typeof target !== 'string' || !target.startsWith('/') || target.startsWith('//')) throw new Error('invalid-request-target');
  const raw = target.split('?', 1)[0];
  let decoded;
  try { decoded = decodeURIComponent(raw); } catch { throw new Error('invalid-encoding'); }
  if (/[\\\x00-\x1f\x7f]/u.test(decoded) || decoded.startsWith('//') || decoded.includes('%')) throw new Error('invalid-path');
  if (decoded.split('/').some((segment) => segment === '.' || segment === '..')) throw new Error('path-traversal');
  return decoded;
}

export function publicAuthority(host, port = 10443) {
  const value = String(host ?? '').toLowerCase();
  if (value === `localhost:${port}`) return { host: 'localhost', port, authority: value };
  if (value === `127.0.0.1:${port}`) return { host: '127.0.0.1', port, authority: value };
  throw new Error('unexpected-host');
}

export function routeTarget(target) {
  const pathname = requestPath(target);
  for (const prefix of ['/prod-api', '/dev-api']) {
    if (pathname === prefix || pathname.startsWith(`${prefix}/`)) {
      // Prefixes must be literal so an encoded path cannot alter the routing boundary.
      if (!(target === prefix || target.startsWith(prefix + '/') || target.startsWith(prefix + '?'))) throw new Error('encoded-route-prefix');
      const rest = target.slice(prefix.length);
      return { kind: 'backend', pathname, target: rest.startsWith('/') ? rest : '/' + rest };
    }
  }
  if (pathname === '/nifi' || pathname.startsWith('/nifi/') || /^\/nifi-[a-z0-9_-]+(?:\/|$)/i.test(pathname)) return { kind: 'nifi', pathname, target };
  return { kind: 'static', pathname, target };
}

function cleanHopHeaders(original) {
  const nominated = String(original.connection ?? '').split(',').map((name) => name.trim().toLowerCase()).filter(Boolean);
  const result = { ...original };
  for (const name of [...HOP_HEADERS, ...nominated]) delete result[name];
  return result;
}

export function upstreamHeaders(original, kind, authority) {
  const headers = cleanHopHeaders(original);
  for (const name of Object.keys(headers)) {
    if (name === 'forwarded' || name.startsWith('x-forwarded-') || name.startsWith('x-proxy') || name.startsWith('x-proxied')) delete headers[name];
  }
  headers.host = kind === 'nifi' ? 'localhost:9443' : '127.0.0.1:8083';
  headers['x-forwarded-for'] = '127.0.0.1';
  headers['x-forwarded-proto'] = 'https';
  if (kind === 'nifi') {
    headers['x-proxyscheme'] = 'https';
    headers['x-proxyhost'] = authority.host;
    headers['x-proxyport'] = String(authority.port);
    // No extra context prefix: /nifi and /nifi-* are kept exactly as received.
  }
  return headers;
}

function send(res, status, message, method = 'GET') {
  if (res.headersSent) { res.destroy(); return; }
  res.writeHead(status, { 'Content-Type': 'text/plain; charset=utf-8', 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' });
  res.end(method === 'HEAD' ? undefined : message + '\n');
}

export function staticPathAllowed(pathname) {
  const segments = pathname.split('/').filter(Boolean);
  if (segments.some((segment) => segment.startsWith('.') || PRIVATE_SEGMENTS.has(segment.toLowerCase()))) return false;
  const extension = path.extname(pathname).toLowerCase();
  return !['.json', '.json5', '.jsonl', '.map', '.pem', '.key', '.p12', '.cnf', '.properties', '.env', '.toml', '.yaml', '.yml'].includes(extension);
}

async function containedFile(dist, candidate) {
  const resolved = await realpath(candidate);
  if (resolved !== dist && !resolved.startsWith(dist + path.sep)) throw new Error('symlink-escape');
  const handle = await open(resolved, constants.O_RDONLY | (constants.O_NOFOLLOW ?? 0));
  const info = await handle.stat();
  if (!info.isFile()) { await handle.close(); throw new Error('not-file'); }
  return { handle, info, resolved };
}

async function serveStatic(req, res, pathname, distRoot) {
  if (!['GET', 'HEAD'].includes(req.method)) { send(res, 405, 'Method not allowed', req.method); return; }
  if (!staticPathAllowed(pathname)) { send(res, 403, 'Static resource denied', req.method); return; }
  let dist;
  try { dist = await realpath(distRoot); if (!(await stat(dist)).isDirectory()) throw new Error('not-directory'); }
  catch { send(res, 503, 'Frontend build is not ready', req.method); return; }
  let file;
  try {
    file = await containedFile(dist, path.resolve(dist, '.' + (pathname === '/' ? '/index.html' : pathname)));
  } catch (error) {
    if (error.message === 'symlink-escape') { send(res, 403, 'Static resource denied', req.method); return; }
    const wantsHtml = String(req.headers.accept ?? '').toLowerCase().includes('text/html');
    if (req.method !== 'GET' || !wantsHtml || path.extname(pathname)) { send(res, 404, 'Not found', req.method); return; }
    try { file = await containedFile(dist, path.join(dist, 'index.html')); }
    catch { send(res, 503, 'Frontend build is not ready', req.method); return; }
  }
  const type = MIME.get(path.extname(file.resolved).toLowerCase());
  if (!type || !staticPathAllowed(file.resolved.slice(dist.length))) { await file.handle.close(); send(res, 403, 'Static resource denied', req.method); return; }
  res.writeHead(200, { 'Content-Type': type, 'Content-Length': file.info.size, 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff', 'X-Frame-Options': 'SAMEORIGIN' });
  if (req.method === 'HEAD') { await file.handle.close(); res.end(); return; }
  const stream = file.handle.createReadStream();
  stream.on('error', () => res.destroy());
  res.on('close', () => stream.destroy());
  stream.pipe(res);
}

function proxy(req, res, route, authority, options) {
  const isNifi = route.kind === 'nifi';
  const transport = isNifi ? https : http;
  const headers = upstreamHeaders(req.headers, route.kind, authority);
  const port = isNifi ? (options.nifiPort ?? 9443) : (options.backendPort ?? 8083);
  // Test ports are injectable through the module API only; the production CLI fixes both targets.
  if (options.testUpstreamHost) headers.host = `localhost:${port}`;
  const upstream = transport.request({ hostname: '127.0.0.1', port, servername: isNifi ? 'localhost' : undefined, method: req.method, path: route.target, headers, ca: isNifi ? options.nifiCa : undefined, rejectUnauthorized: true, timeout: 120_000 }, (response) => {
    res.writeHead(response.statusCode ?? 502, cleanHopHeaders(response.headers));
    response.on('error', () => res.destroy());
    response.pipe(res);
  });
  upstream.on('timeout', () => upstream.destroy(new Error('upstream-timeout')));
  upstream.on('error', () => send(res, 502, 'Local upstream is not available', req.method));
  req.on('aborted', () => upstream.destroy());
  res.on('close', () => { if (!res.writableFinished) upstream.destroy(); });
  req.pipe(upstream);
}

export function createGatewayHandler(options) {
  return async (req, res) => {
    let authority;
    try { authority = publicAuthority(req.headers.host, options.publicPort ?? 10443); }
    catch { send(res, 421, 'Unexpected local gateway host', req.method); return; }
    let route;
    try { route = routeTarget(req.url); }
    catch { send(res, 400, 'Invalid request path', req.method); return; }
    if (route.pathname === '/__gateway_health') {
      if (req.method !== 'GET') { send(res, 405, 'Method not allowed', req.method); return; }
      const built = await stat(path.join(options.distRoot, 'index.html')).then((info) => info.isFile()).catch(() => false);
      res.writeHead(200, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
      res.end(JSON.stringify({ service: 'rynew-local-gateway', loopbackOnly: true, frontendBuilt: built }));
      return;
    }
    try {
      if (route.kind === 'static') await serveStatic(req, res, route.pathname, options.distRoot);
      else proxy(req, res, route, authority, options);
    } catch { send(res, 500, 'Local gateway request failed', req.method); }
  };
}

export async function startGateway({ runtimeRoot, distRoot }) {
  const root = await realpath(runtimeRoot);
  const marker = JSON.parse(await readFile(path.join(root, 'runtime.json'), 'utf8'));
  if (marker.owner !== 'rynew-data-governance-runtime' || marker.runtimeRoot !== root) throw new Error('runtime-owner-mismatch');
  const privateFile = async (name) => {
    const file = await realpath(path.join(root, 'private', name));
    if (!file.startsWith(path.join(root, 'private') + path.sep) || ((await stat(file)).mode & 0o077)) throw new Error('private-file-permission-or-path');
    return readFile(file);
  };
  const [key, cert, nifiCa] = await Promise.all([privateFile('gateway-key.pem'), privateFile('gateway-cert.pem'), privateFile('nifi-ca.pem')]);
  const server = https.createServer({ key, cert, minVersion: 'TLSv1.2', requestTimeout: 300_000, headersTimeout: 60_000 }, createGatewayHandler({ distRoot: path.resolve(distRoot), nifiCa }));
  server.on('upgrade', (_req, socket) => socket.end('HTTP/1.1 501 Not Implemented\r\nConnection: close\r\n\r\n'));
  await new Promise((resolve, reject) => { server.once('error', reject); server.listen(10443, '127.0.0.1', resolve); });
  return server;
}

async function main() {
  const args = process.argv.slice(2);
  const option = (name) => { const index = args.indexOf(name); return index >= 0 ? args[index + 1] : undefined; };
  const runtimeRoot = option('--runtime-root');
  const distRoot = option('--dist-root');
  if (!runtimeRoot || !distRoot) throw new Error('runtime-root-and-dist-root-required');
  const server = await startGateway({ runtimeRoot, distRoot });
  console.log(JSON.stringify({ service: 'rynew-local-gateway', bind: '127.0.0.1:10443', pid: process.pid, frontendReadyRequired: false }));
  const shutdown = () => { server.close(() => process.exit(0)); setTimeout(() => { server.closeAllConnections(); process.exit(0); }, 10_000).unref(); };
  process.on('SIGTERM', shutdown);
  process.on('SIGINT', shutdown);
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  main().catch((error) => { console.error(JSON.stringify({ service: 'rynew-local-gateway', error: error.code ?? 'startup-failed' })); process.exitCode = 1; });
}
