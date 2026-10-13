#!/usr/bin/env python3
"""Loopback-only original-bundle probe. No fake task/session/API implementation."""
import argparse
from functools import partial
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import mimetypes
from urllib.parse import urlsplit, unquote

PROBE = b'''<!doctype html><html lang="zh-CN"><meta charset="UTF-8">
<title>HDI original module probe</title><link rel="icon" href="data:,">
<link rel="stylesheet" href="/detl/static/css/188.c7f3bc5a.css">
<link rel="stylesheet" href="/detl/static/css/index.13312c3e.css">
<pre id="result">Loading original DETL exports...</pre>
<script src="/detl/detlModuleEntry.js"></script>
<script type="module">
import {loadOriginalModules} from '/native-task-host.mjs';
try {
 const {Vue, VueI18n, hui, components} = await loadOriginalModules();
 const result = {phase:'original-exports-loaded', vue:Vue.version,
  i18n:VueI18n.version, hui:hui.version, exports:Object.keys(components).sort(),
  themeLoaded:!!getComputedStyle(document.documentElement).getPropertyValue('--h-color-brand'),
  taskApiConnected:false, taskMounted:false, executionVerified:false};
 if (new URLSearchParams(location.search).has('bootstrap')) {
  Vue.use(VueI18n);
  const i18n = new VueI18n({locale:'zh_CN',messages:{}});
  try {
   await components.AsyncTaskList(i18n,'zh_CN');
   result.nativeBootstrapRejected = false;
  } catch (error) {
   result.nativeBootstrapRejected = error.response?.status === 503;
   result.nativeBootstrapStatus = error.response?.status;
  }
 }
 window.probeResult = result;
 document.getElementById('result').textContent = JSON.stringify(result,null,2);
} catch (error) {
 window.probeResult = {phase:'failed',error:String(error)};
 document.getElementById('result').textContent = JSON.stringify(window.probeResult,null,2);
}
</script></html>'''


class Handler(BaseHTTPRequestHandler):
    def __init__(self, *args, vendor, host, **kwargs):
        self.vendor, self.host = vendor.resolve(), host.resolve()
        super().__init__(*args, **kwargs)

    def do_GET(self):
        path = unquote(urlsplit(self.path).path)
        if path == '/probe':
            self.reply(200, PROBE, 'text/html; charset=utf-8')
            return
        if path == '/native-task-host.mjs':
            self.reply(200, self.host.read_bytes(), 'text/javascript')
            return
        if path == '/detl/':
            self.reply(503, b'Original task API is not configured.', 'text/plain')
            return
        if not path.startswith('/detl/') or any(p in ('.', '..') for p in path.split('/')) or '\\' in path or '%' in path:
            self.send_error(404)
            return
        candidate = self.vendor / path[len('/detl/'):]
        if not candidate.resolve().is_relative_to(self.vendor) or not candidate.is_file():
            self.send_error(404)
            return
        # The explicit vendor directory contains public assets only, never runtime/conf.
        if candidate.suffix not in {'.js', '.css', '.json', '.png', '.svg', '.jpg', '.jpeg', '.gif', '.woff', '.woff2', '.ttf', '.eot', '.ico'}:
            self.send_error(404)
            return
        self.reply(200, candidate.read_bytes(), mimetypes.guess_type(path)[0] or 'application/octet-stream')

    def reply(self, code, content, content_type):
        self.send_response(code)
        self.send_header('Content-Type', content_type)
        self.send_header('Cache-Control', 'no-store')
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.send_header('Content-Security-Policy', "default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:; connect-src 'self'; frame-ancestors 'none'")
        self.send_header('Content-Length', str(len(content)))
        self.end_headers()
        self.wfile.write(content)

    def log_message(self, fmt, *args):
        # Do not record query strings or response data.
        print(self.command, urlsplit(self.path).path, flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--vendor-public', type=Path, required=True)
    parser.add_argument('--port', type=int, default=19091)
    args = parser.parse_args()
    host = Path(__file__).resolve().parents[2] / 'data-governance/hdi-native/host/native-task-host.mjs'
    if not (args.vendor_public / 'detlModuleEntry.js').is_file():
        parser.error('Expected original public assets, not an installation/runtime directory')
    server = ThreadingHTTPServer(('127.0.0.1', args.port), partial(Handler, vendor=args.vendor_public, host=host))
    print(f'Original module probe: http://127.0.0.1:{args.port}/probe', flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == '__main__':
    main()
