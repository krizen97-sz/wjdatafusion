#!/usr/bin/env python3
"""Task-local address metadata for the unmodified DETL Agent discovery client.

This is not an authentication, readiness, license, or heartbeat implementation.
The original Agent still registers directly with the original DETL service.
"""
from __future__ import annotations

import argparse
import json
import signal
import socket
import sys
import threading
from dataclasses import dataclass
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit

DIRECTORY_PATH = "/svrService/v1/service/detl/detl"


@dataclass(frozen=True)
class DeploymentAddress:
    port: int

    def __post_init__(self):
        if type(self.port) is not int or not 1024 <= self.port <= 65535:
            raise ValueError("DETL port is invalid")

    @classmethod
    def parse(cls, value: str) -> "DeploymentAddress":
        try:
            parsed = urlsplit(value)
            if (parsed.scheme != "http" or parsed.hostname != "127.0.0.1"
                    or parsed.username is not None or parsed.password is not None
                    or parsed.path not in ("/detl", "/detl/") or parsed.query or parsed.fragment
                    or not parsed.port or not 1024 <= parsed.port <= 65535
                    or parsed.netloc != f"127.0.0.1:{parsed.port}"):
                raise ValueError()
            return cls(parsed.port)
        except (ValueError, TypeError) as error:
            raise ValueError("DETL address must be http://127.0.0.1:<port>/detl with an unprivileged port") from error

    def reachable(self) -> bool:
        try:
            with socket.create_connection(("127.0.0.1", self.port), timeout=1):
                return True
        except OSError:
            return False

    def response(self) -> dict:
        # Exact ServiceInfoDto / ServiceAddressInfoDto / KeyValueItem fields read by original AgentConfigService.
        return {"code": "0", "data": {
            "configs": [{"key": "@context", "value": "/detl"}],
            "address": [{"key": "webPort", "ip": "127.0.0.1", "port": self.port, "netprotocol": "http"}],
        }}


class DirectoryServer(ThreadingHTTPServer):
    daemon_threads = True
    block_on_close = False

    def __init__(self, listen_port: int, target: DeploymentAddress):
        if not (listen_port == 0 or 1024 <= listen_port <= 65535):
            raise ValueError("Directory listener must use an unprivileged local port")
        if listen_port == target.port:
            raise ValueError("Directory and DETL ports must differ")
        self.target = target
        super().__init__(("127.0.0.1", listen_port), DirectoryHandler)
        if self.server_port == target.port:
            self.server_close()
            raise ValueError("Directory cannot publish itself as DETL")


class DirectoryHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "RYNEW-Deployment-Directory"
    sys_version = ""

    def setup(self):
        super().setup()
        self.connection.settimeout(3)

    def log_message(self, _format, *args):
        # Never log request headers, SDK Token, arbitrary query text, or caller-controlled body.
        pass

    def reply(self, status: int, payload: dict):
        body = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        self.close_connection = True
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Connection", "close")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        expected_host = f"127.0.0.1:{self.server.server_port}"
        if (self.client_address[0] != "127.0.0.1" or self.headers.get("Host") != expected_host
                or self.headers.get("Origin") is not None or self.headers.get("Sec-Fetch-Site") is not None):
            self.reply(403, {"code": "DIRECTORY_LOCAL_ONLY"})
            return
        if self.path != DIRECTORY_PATH:
            self.reply(404, {"code": "DIRECTORY_NOT_FOUND"})
            return
        try:
            has_body = self.headers.get("Transfer-Encoding") is not None or int(self.headers.get("Content-Length", "0")) != 0
        except ValueError:
            has_body = True
        if has_body:
            self.reply(400, {"code": "DIRECTORY_BODY_NOT_ALLOWED"})
            return
        if not self.server.target.reachable():
            self.reply(503, {"code": "DIRECTORY_TARGET_NOT_LISTENING"})
            return
        # No Token is invented, validated, reflected, or forwarded here. This only publishes a local address.
        self.reply(200, self.server.target.response())

    def reject_method(self):
        self.reply(405, {"code": "DIRECTORY_READ_ONLY"})

    do_POST = do_PUT = do_PATCH = do_DELETE = do_HEAD = do_OPTIONS = reject_method


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--detl-url", required=True, help="Actual local original DETL endpoint, not a remote server")
    parser.add_argument("--listen-port", type=int, default=19910)
    args = parser.parse_args(argv)
    try:
        target = DeploymentAddress.parse(args.detl_url)
        server = DirectoryServer(args.listen_port, target)
    except (ValueError, OSError):
        print("Local directory configuration or listener unavailable", file=sys.stderr)
        return 2
    stopped = threading.Event()
    for kind in (signal.SIGTERM, signal.SIGINT):
        signal.signal(kind, lambda _signum, _frame: stopped.set())
    worker = threading.Thread(target=server.serve_forever, kwargs={"poll_interval": 0.2}, daemon=True)
    worker.start()
    print(json.dumps({"url": f"http://127.0.0.1:{server.server_port}", "path": DIRECTORY_PATH,
                      "kind": "deployment-address-only"}), flush=True)
    try:
        stopped.wait()
    finally:
        server.shutdown()
        server.server_close()
        worker.join(timeout=2)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
