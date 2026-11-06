#!/usr/bin/env python3
"""Archive publicly linked doc.ruoyi.vip pages for a user's offline reference."""
import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import hashlib
from html import escape
from html.parser import HTMLParser
import json
from pathlib import Path
import re
import time
from urllib.parse import urldefrag, urljoin, urlsplit
from urllib.request import Request, urlopen

BASE = "https://doc.ruoyi.vip"
SEEDS = ["/ruoyi-vue/", "/ruoyi/", "/ruoyi-cloud/", "/ruoyi-app/"]
VOID = {"area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr"}


def fetch(url):
    request = Request(url, headers={"User-Agent": "RYNEW-offline-reference/1.0 (public documentation only)"})
    with urlopen(request, timeout=40) as response:
        data = response.read(30 * 1024 * 1024 + 1)
        if len(data) > 30 * 1024 * 1024:
            raise ValueError("resource exceeds archive limit")
        return data


def page_url(url):
    parsed = urlsplit(url)
    if parsed.netloc != "doc.ruoyi.vip" or parsed.scheme != "https":
        return None
    if not any(parsed.path.startswith(prefix) for prefix in SEEDS):
        return None
    if not (parsed.path.endswith("/") or parsed.path.endswith(".html")):
        return None
    if parsed.path.endswith("/") and parsed.path not in SEEDS:
        return BASE + parsed.path.rstrip("/") + ".html"
    return BASE + parsed.path


def page_name(url):
    path = urlsplit(url).path.strip("/")
    return path.replace("/", "__") + ("index.html" if url.endswith("/") else "")


def resource_url(url):
    return "https://" + url[7:] if url.startswith("http://") else url


class Article(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=False)
        self.depth = 0
        self.parts, self.links, self.images, self.text = [], set(), set(), []
        self.headings = 0

    def handle_starttag(self, tag, attrs):
        values = dict(attrs)
        if tag == "a" and values.get("href"):
            self.links.add(values["href"])
        if not self.depth and "theme-default-content" in values.get("class", ""):
            self.depth = 1
            return
        if not self.depth:
            return
        if tag not in VOID:
            self.depth += 1
        self.parts.append(self.get_starttag_text())
        if tag == "img" and values.get("src"):
            self.images.add(values["src"])
        if tag == "h2":
            self.headings += 1

    def handle_startendtag(self, tag, attrs):
        self.handle_starttag(tag, attrs)
        if tag not in VOID:
            self.handle_endtag(tag)

    def handle_endtag(self, tag):
        if not self.depth:
            return
        if tag not in VOID:
            self.depth -= 1
        if self.depth:
            self.parts.append("</" + tag + ">")

    def handle_data(self, value):
        if self.depth:
            self.parts.append(value)
            self.text.append(value)

    def handle_entityref(self, name):
        if self.depth:
            self.parts.append("&" + name + ";")
            self.text.append("&" + name + ";")

    def handle_charref(self, name):
        if self.depth:
            self.parts.append("&#" + name + ";")
            self.text.append("&#" + name + ";")


class OfflineHTML(HTMLParser):
    def __init__(self, source, pages, assets):
        super().__init__(convert_charrefs=False)
        self.source, self.pages, self.assets = source, pages, assets
        self.parts, self.skip = [], 0

    def handle_starttag(self, tag, attrs):
        if tag in {"script", "iframe", "object"}:
            self.skip += 1
            return
        if self.skip:
            return
        clean = []
        for key, value in attrs:
            if key.lower().startswith("on") or key in {"srcset", "integrity", "crossorigin"}:
                continue
            if value is not None and key in {"href", "src"}:
                if value.lower().startswith("javascript:"):
                    continue
                absolute, fragment = urldefrag(urljoin(self.source, value))
                if key == "src":
                    absolute = resource_url(absolute)
                canonical = page_url(absolute) if key == "href" else None
                if key == "href" and canonical in self.pages:
                    value = page_name(canonical) + ("#" + fragment if fragment else "")
                elif key == "src" and absolute in self.assets:
                    value = "../assets/" + self.assets[absolute]
                elif not value.startswith("#"):
                    value = absolute + ("#" + fragment if fragment else "")
            clean.append(key if value is None else key + '="' + escape(value, quote=True) + '"')
        self.parts.append("<" + tag + (" " + " ".join(clean) if clean else "") + ">")

    def handle_endtag(self, tag):
        if tag in {"script", "iframe", "object"} and self.skip:
            self.skip -= 1
        elif not self.skip:
            self.parts.append("</" + tag + ">")

    def handle_data(self, value):
        if not self.skip:
            self.parts.append(value)

    def handle_entityref(self, value):
        if not self.skip:
            self.parts.append("&" + value + ";")

    def handle_charref(self, value):
        if not self.skip:
            self.parts.append("&#" + value + ";")


STYLE = "body{max-width:1100px;margin:2rem auto;padding:1rem;font:16px/1.7 system-ui,sans-serif;color:#222}pre{padding:1rem;background:#f4f4f4;overflow:auto}code{white-space:pre-wrap}img{max-width:100%}table{border-collapse:collapse}td,th{border:1px solid #bbb;padding:.4rem}a{color:#165da3}nav{padding:.7rem;background:#edf3f8}.line-numbers-wrapper{display:none}"


def archive(destination, resume=False):
    if destination.exists() and any(destination.iterdir()) and not resume:
        raise ValueError("output directory must be empty; existing archives are never overwritten")
    previous = json.loads((destination / "manifest.json").read_text()) if resume and (destination / "manifest.json").is_file() else {}
    if previous and previous.get("origin") != BASE:
        raise ValueError("not an archive produced by this collector")
    for name in ["raw", "pages", "assets", "text"]:
        (destination / name).mkdir(parents=True, exist_ok=True)
    pending = [BASE + path for path in SEEDS]
    pages, failures, aliases = {}, [], set()
    while pending:
        url = pending.pop(0)
        if url in pages:
            continue
        if len(pages) >= 250:
            raise ValueError("page limit reached; audit the site routes before continuing")
        try:
            cached = destination / "raw" / page_name(url)
            data = cached.read_bytes() if resume and cached.is_file() else fetch(url)
            parsed = Article()
            parsed.feed(data.decode("utf-8"))
            if not parsed.parts:
                raise ValueError("expected server-rendered article missing")
            pages[url] = (data, parsed)
            (destination / "raw" / page_name(url)).write_bytes(data)
            for link in sorted(parsed.links):
                candidate = page_url(urljoin(url, link))
                observed = urldefrag(urljoin(url, link))[0]
                if candidate and observed != candidate:
                    aliases.add((observed, candidate))
                if candidate and candidate not in pages and candidate not in pending:
                    pending.append(candidate)
            print(json.dumps({"page": url, "discovered": len(pages) + len(pending)}), flush=True)
        except Exception as error:
            failures.append({"url": url, "error_type": type(error).__name__})
        time.sleep(.15)
    image_urls = sorted({resource_url(urldefrag(urljoin(url, image))[0]) for url, (_, parsed) in pages.items()
                         for image in parsed.images if urlsplit(urljoin(url, image)).scheme in {"http", "https"}})

    def save_image(url):
        try:
            extension = Path(urlsplit(url).path).suffix.lower()
            if not re.fullmatch(r"\.(png|jpg|jpeg|gif|svg|webp|ico)", extension):
                extension = ".image"
            name = hashlib.sha256(url.encode()).hexdigest()[:24] + extension
            cached = destination / "assets" / name
            data = cached.read_bytes() if resume and cached.is_file() else fetch(url)
            (destination / "assets" / name).write_bytes(data)
            return url, name, hashlib.sha256(data).hexdigest()
        except Exception as error:
            return url, None, type(error).__name__

    with ThreadPoolExecutor(max_workers=3) as executor:
        image_results = list(executor.map(save_image, image_urls))
    assets = {url: name for url, name, _ in image_results if name}
    manifest = {"origin": BASE, "retrieved_at": previous.get("retrieved_at", datetime.now(timezone.utc).isoformat()),
                "scope": "all publicly linked pages under ruoyi, ruoyi-vue, ruoyi-cloud and ruoyi-app; no paid material or off-site tutorials",
                "usage": "local offline reference; original attribution retained; no documentation-source license assumed",
                "pages": [], "page_failures": failures, "asset_failures": [], "assets": [],
                "normalized_routes": [{"observed": a, "canonical": b} for a, b in sorted(aliases)]}
    for url, (raw, parsed) in sorted(pages.items()):
        rewrite = OfflineHTML(url, pages, assets)
        rewrite.feed("".join(parsed.parts))
        html = '<!doctype html><html lang="zh-CN"><meta charset="UTF-8"><meta name="viewport" content="width=device-width"><style>' + STYLE + '</style><nav><a href="../index.html">离线目录</a> · 官方来源：<a href="' + escape(url) + '">' + escape(url) + '</a></nav>' + "".join(rewrite.parts) + '</html>'
        (destination / "pages" / page_name(url)).write_text(html, encoding="utf-8")
        (destination / "text" / (page_name(url) + ".txt")).write_text("Source: " + url + "\n\n" + "\n".join(parsed.text), encoding="utf-8")
        manifest["pages"].append({"url": url, "path": "pages/" + page_name(url), "sha256": hashlib.sha256(raw).hexdigest(), "sections": parsed.headings})
    for url, name, digest in image_results:
        manifest["assets" if name else "asset_failures"].append({"url": url, **({"path": "assets/" + name, "sha256": digest} if name else {"error_type": digest})})
    index = '<!doctype html><meta charset="UTF-8"><style>' + STYLE + '</style><h1>若依官网公开文档离线目录</h1><p>主开发基线使用RuoYi-Vue；其他系列仅供明确涉及时参考。外站文章/视频不在本归档范围。</p><ul>'
    index += "".join('<li><a href="pages/' + escape(page_name(url)) + '">' + escape(urlsplit(url).path) + '</a></li>' for url in sorted(pages))
    (destination / "index.html").write_text(index + '</ul>', encoding="utf-8")
    (destination / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"pages": len(pages), "assets": len(assets), "page_failures": len(failures), "asset_failures": len(manifest["asset_failures"])}))
    return 1 if failures else 0


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--resume", action="store_true", help="Reuse this collector's existing raw pages and image bytes")
    arguments = parser.parse_args()
    raise SystemExit(archive(arguments.output.resolve(), arguments.resume))
