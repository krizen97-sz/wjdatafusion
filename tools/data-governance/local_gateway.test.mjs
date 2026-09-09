import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import http from 'node:http';
import https from 'node:https';
import { mkdtemp, mkdir, writeFile, readFile, rm, symlink, chmod } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { createGatewayHandler, requestPath, routeTarget, upstreamHeaders, publicAuthority } from './local_gateway.mjs';

let directory, dist, backend, nifi, gateway, noBuild, untrusted;
const servers = [];
async function listen(server) { servers.push(server); await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve)); return server.address().port; }
async function request(server, target, options = {}) {
  return new Promise((resolve, reject) => {
    const req = http.request({ hostname: '127.0.0.1', port: server.address().port, path: target, method: options.method ?? 'GET', headers: { host: 'localhost:10443', ...options.headers } }, (res) => {
      const chunks = []; res.on('data', (chunk) => chunks.push(chunk));
      res.on('end', () => resolve({ status: res.statusCode, headers: res.headers, body: Buffer.concat(chunks).toString() }));
    });
    req.on('error', reject); req.end(options.body);
  });
}

before(async () => {
  directory = await mkdtemp(path.join(os.tmpdir(), 'rynew-gateway-test-'));
  dist = path.join(directory, 'dist'); await mkdir(dist);
  await writeFile(path.join(dist, 'index.html'), '<html>fixture SPA</html>');
  await writeFile(path.join(dist, 'app.js'), 'console.log("fixture");');
  await writeFile(path.join(dist, 'settings.json'), '{"shouldNotBeServed":true}');
  await mkdir(path.join(dist, 'private')); await writeFile(path.join(dist, 'private', 'hidden.js'), 'private fixture');
  await writeFile(path.join(directory, 'outside.html'), 'outside fixture');
  await symlink(path.join(directory, 'outside.html'), path.join(dist, 'outside.html'));
  await symlink(path.join(dist, 'private', 'hidden.js'), path.join(dist, 'alias.js'));
  const key = path.join(directory, 'key.pem'), cert = path.join(directory, 'cert.pem'), config = path.join(directory, 'tls.cnf');
  await writeFile(config, '[req]\ndistinguished_name=dn\nprompt=no\nx509_extensions=ext\n[dn]\nCN=localhost\n[ext]\nsubjectAltName=DNS:localhost,IP:127.0.0.1\n');
  execFileSync('/usr/bin/openssl', ['req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-keyout', key, '-out', cert, '-days', '1', '-config', config], { stdio: 'ignore' });
  await chmod(key, 0o600);
  const respond = (req, res) => {
    const chunks = []; req.on('data', (chunk) => chunks.push(chunk)); req.on('end', () => {
      res.writeHead(200, { 'Content-Type': 'application/json', 'Content-Security-Policy': "frame-ancestors 'self'", 'X-Frame-Options': 'SAMEORIGIN', 'Set-Cookie': ['fixture-session=opaque; Secure; HttpOnly; SameSite=Strict', 'fixture-csrf=nonce; Secure; SameSite=Strict'] });
      res.end(JSON.stringify({ path: req.url, method: req.method, headers: req.headers, body: Buffer.concat(chunks).toString() }));
    });
  };
  backend = http.createServer(respond); const backendPort = await listen(backend);
  const nifiCa = await readFile(cert);
  nifi = https.createServer({ key: await readFile(key), cert: nifiCa }, respond); const nifiPort = await listen(nifi);
  const options = { distRoot: dist, nifiCa, backendPort, nifiPort, testUpstreamHost: true };
  gateway = http.createServer(createGatewayHandler(options)); await listen(gateway);
  noBuild = http.createServer(createGatewayHandler({ ...options, distRoot: path.join(directory, 'not-built') })); await listen(noBuild);
  untrusted = http.createServer(createGatewayHandler({ ...options, nifiCa: undefined })); await listen(untrusted);
});

after(async () => {
  await Promise.all(servers.map((server) => new Promise((resolve) => { server.closeAllConnections(); server.close(resolve); })));
  await rm(directory, { recursive: true, force: true });
});

test('API prefix stripping preserves query and body', async () => {
  for (const prefix of ['/prod-api', '/dev-api']) {
    const response = await request(gateway, prefix + '/fixture?a=1', { method: 'POST', body: 'sample-input', headers: { 'content-type': 'text/plain', authorization: 'Bearer user-fixture' } });
    assert.equal(response.status, 200);
    const result = JSON.parse(response.body);
    assert.equal(result.path, '/fixture?a=1'); assert.equal(result.body, 'sample-input');
    assert.equal(result.headers.authorization, 'Bearer user-fixture');
  }
});

test('NiFi proxy keeps paths, user auth, cookies and CSRF without forged identities', async () => {
  const response = await request(gateway, '/nifi-api/flow/about?fixture=1', { headers: { authorization: 'Bearer browser-fixture', cookie: 'fixture-session=opaque', 'request-token': 'nonce', 'x-proxiedentitieschain': '<forged-user>', 'x-proxiedentitygroups': '<administrators>', 'x-proxyhost': 'attacker.example', 'x-forwarded-host': 'attacker.example' } });
  assert.equal(response.status, 200);
  const result = JSON.parse(response.body);
  assert.equal(result.path, '/nifi-api/flow/about?fixture=1');
  assert.equal(result.headers.authorization, 'Bearer browser-fixture');
  assert.equal(result.headers.cookie, 'fixture-session=opaque'); assert.equal(result.headers['request-token'], 'nonce');
  assert.equal(result.headers['x-proxyscheme'], 'https'); assert.equal(result.headers['x-proxyhost'], 'localhost'); assert.equal(result.headers['x-proxyport'], '10443');
  assert.equal(result.headers['x-proxiedentitieschain'], undefined); assert.equal(result.headers['x-proxiedentitygroups'], undefined); assert.equal(result.headers['x-forwarded-host'], undefined); assert.equal(result.headers['x-proxycontextpath'], undefined);
  assert.equal(response.headers['content-security-policy'], "frame-ancestors 'self'"); assert.equal(response.headers['x-frame-options'], 'SAMEORIGIN');
  assert.equal(response.headers['set-cookie'].length, 2);
});

test('NiFi TLS certificate verification cannot be silently disabled', async () => {
  assert.equal((await request(untrusted, '/nifi-api/flow/about')).status, 502);
});

test('missing dist yields 503 and does not prevent gateway startup', async () => {
  assert.equal((await request(noBuild, '/', { headers: { accept: 'text/html' } })).status, 503);
  const response = await request(noBuild, '/__gateway_health');
  assert.equal(response.status, 200); assert.equal(JSON.parse(response.body).frontendBuilt, false);
});

test('static JSON, private paths, credentials and dotfiles are denied', async () => {
  for (const name of ['/settings.json', '/SETTINGS.JSON', '/private/hidden.js', '/private/nifi-credentials.json', '/.env', '/assets/source.js.map']) assert.equal((await request(gateway, name)).status, 403);
});

test('traversal and ambiguous encodings never reach files or upstreams', async () => {
  for (const name of ['/assets/../../private/a', '/assets/%2e%2e/a', '/assets/%252e%252e/a', '/assets/%5c../a', '/%00x', '/bad%ZZ', '//attacker.example/x']) assert.equal((await request(gateway, name)).status, 400);
});

test('symlinks cannot escape dist or bypass private resource checks', async () => {
  assert.equal((await request(gateway, '/outside.html')).status, 403);
  assert.equal((await request(gateway, '/alias.js')).status, 403);
});

test('SPA fallback is restricted to GET HTML navigation', async () => {
  assert.equal((await request(gateway, '/data-governance/workbench', { headers: { accept: 'text/html' } })).status, 200);
  assert.equal((await request(gateway, '/data-governance/workbench', { headers: { accept: 'application/json' } })).status, 404);
  assert.equal((await request(gateway, '/data-governance/workbench', { method: 'POST', headers: { accept: 'text/html' } })).status, 405);
  assert.equal((await request(gateway, '/data-governance/workbench', { method: 'HEAD', headers: { accept: 'text/html' } })).status, 404);
  assert.equal((await request(gateway, '/missing.js', { headers: { accept: 'text/html' } })).status, 404);
});

test('existing static asset HEAD has headers without a body', async () => {
  const response = await request(gateway, '/app.js', { method: 'HEAD' });
  assert.equal(response.status, 200); assert.equal(response.body, ''); assert.match(response.headers['content-type'], /javascript/);
});

test('unexpected hosts are rejected before proxying', async () => {
  assert.equal((await request(gateway, '/nifi-api/flow/about', { headers: { host: 'attacker.example:10443' } })).status, 421);
  assert.equal((await request(gateway, '/', { headers: { host: 'localhost:9443' } })).status, 421);
});

test('NiFi route family and API boundaries remain explicit', () => {
  assert.equal(routeTarget('/nifi').kind, 'nifi'); assert.equal(routeTarget('/nifi-standard-content-viewer/a').kind, 'nifi');
  assert.equal(routeTarget('/nifievil').kind, 'static'); assert.equal(routeTarget('/prod-apievil').kind, 'static');
  assert.equal(routeTarget('/prod-api?a=1').target, '/?a=1');
  assert.throws(() => routeTarget('/%70rod-api/test'));
});

test('headers never inject credentials and honor connection header stripping', () => {
  const result = upstreamHeaders({ connection: 'x-remove', 'x-remove': 'value', authorization: 'Bearer user-fixture' }, 'nifi', publicAuthority('127.0.0.1:10443'));
  assert.equal(result['x-remove'], undefined); assert.equal(result.authorization, 'Bearer user-fixture'); assert.equal(result['x-proxyhost'], '127.0.0.1');
  assert.throws(() => requestPath('https://attacker.example/a'));
});
