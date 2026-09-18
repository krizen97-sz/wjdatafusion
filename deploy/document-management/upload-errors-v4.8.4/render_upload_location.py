#!/usr/bin/env python3
"""Render a document upload location using the site's existing proxy_pass.

Only generates a new file. Does not edit/reload nginx or contact any server.
Use only for an ordinary prefix location without rewrite or variable upstreams.
"""
import argparse
from pathlib import Path
import re
from urllib.parse import urlsplit, urlunsplit

SUFFIX = 'document/workspace/documents/upload'


def render(proxy_pass, public_prefix='/prod-api/'):
    if not re.fullmatch(r'/[A-Za-z0-9_/-]+/', public_prefix) or '//' in public_prefix:
        raise ValueError('public-prefix must be a plain /prefix/ path')
    upstream = proxy_pass.strip().removesuffix(';')
    if re.search(r'[\s{};$\\"\'<>]', upstream):
        raise ValueError('Variables, quotes and complex proxy_pass require manual review')
    url = urlsplit(upstream)
    if url.scheme not in ('http', 'https') or not url.hostname or url.username or url.password or url.query or url.fragment:
        raise ValueError('Use an http(s) upstream without credentials, variables, query or fragment')
    if url.port is not None and not 1 <= url.port <= 65535:
        raise ValueError('Invalid upstream port')
    if url.path and (not re.fullmatch(r'/[A-Za-z0-9_/~.-]*', url.path) or not url.path.endswith('/')):
        raise ValueError('An upstream URI must be a plain prefix ending in /; otherwise review manually')
    # A proxy_pass without URI keeps the complete original request URI. With a
    # URI, replace the original prefix with that URI, following nginx semantics.
    target = urlunsplit((url.scheme, url.netloc, url.path + SUFFIX, '', '')) if url.path else upstream
    return f'''# Add inside the actual matching server; retain existing auth/TLS headers.
# Do not add a second identical exact location. Review rewrite/variable cases manually.
location = {public_prefix}{SUFFIX} {{
    client_max_body_size 0;
    client_body_timeout 600s;
    proxy_pass {target};
    proxy_http_version 1.1;
    proxy_request_buffering off;
    proxy_intercept_errors off;
    proxy_connect_timeout 30s;
    proxy_read_timeout 600s;
    proxy_send_timeout 600s;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}}
'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--proxy-pass', required=True, help='Exact static value in the existing /prod-api/ location')
    parser.add_argument('--public-prefix', default='/prod-api/')
    parser.add_argument('--output', type=Path, required=True, help='New output file; existing files are never overwritten')
    args = parser.parse_args()
    try:
        content = render(args.proxy_pass, args.public_prefix)
    except ValueError as error:
        parser.error(str(error))
    with args.output.open('x', encoding='utf-8') as stream:
        stream.write(content)
    print('Generated candidate configuration:', args.output)
    print('Review auth/TLS/rewrite settings, insert in the matching server, run nginx -t, then reload.')


if __name__ == '__main__':
    main()
