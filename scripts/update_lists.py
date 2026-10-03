#!/usr/bin/env python3
"""Merge curated rules, HaGeZi GPLv3 feed and available ADM PDFs, atomically.

An unavailable ADM source preserves its previous snapshot. Invalid community
data aborts the update. No scraping of gambling sites and no invented domains.
"""
import argparse
import datetime
import hashlib
import html
import io
import json
import re
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HAGEZI = "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/wildcard/gambling-onlydomains.txt"
ADM_PAGES = {
    "adm-inhibited": "https://www.adm.gov.it/portale/siti-web-inibiti-giochi",
    "adm-authorized": "https://www.adm.gov.it/portale/monopoli/giochi/gioco_distanza/concessionari",
}
DOMAIN = re.compile(r"(?<![\w@-])(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}(?![\w-])", re.I)

def fetch(url):
    request = urllib.request.Request(url, headers={"User-Agent": "GameShield-list-builder/0.1", "Accept-Encoding": "identity"})
    with urllib.request.urlopen(request, timeout=45) as response:
        if urllib.parse.urlparse(response.url).scheme != "https":
            raise ValueError("HTTPS required")
        data = response.read(32 * 1024 * 1024 + 1)
        if len(data) > 32 * 1024 * 1024:
            raise ValueError("Feed too large")
        return data

def normalize(value):
    value = value.strip().lower().removeprefix("*.").rstrip(".")
    try:
        value = value.encode("idna").decode("ascii")
    except UnicodeError:
        return None
    if len(value) > 253 or not DOMAIN.fullmatch(value):
        return None
    return value

def parse(text):
    result = set()
    for line in text.splitlines():
        line = line.split("#", 1)[0].strip()
        if not line or line.startswith("!"):
            continue
        domain = normalize(line)
        if domain is None:
            raise ValueError(f"Invalid domain line: {line[:80]}")
        result.add(domain)
    if not result:
        raise ValueError("Empty list")
    return result

def atomic(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(text, encoding="utf-8", newline="\n")
    temporary.replace(path)

def adm_domains(page_url):
    from pypdf import PdfReader
    page = fetch(page_url).decode("utf-8", errors="replace")
    # Select only relevant ADM document links; never crawl arbitrary links.
    links = re.findall(r'href=["\']([^"\']+)["\']', page, re.I)
    selected = []
    for link in links:
        url = urllib.parse.urljoin(page_url, html.unescape(link))
        lower = url.lower()
        if urllib.parse.urlparse(url).hostname != "www.adm.gov.it":
            continue
        if (".pdf" in lower or "/documents/" in lower) and any(term in lower for term in ("inibit", "canali", "concession", "siti")):
            selected.append(url)
    result = set()
    for url in list(dict.fromkeys(selected))[:8]:
        content = fetch(url)
        if not content.startswith(b"%PDF"):
            continue
        reader = PdfReader(io.BytesIO(content))
        if len(reader.pages) > 600:
            raise ValueError("ADM PDF too large")
        for page in reader.pages:
            for domain in DOMAIN.findall(page.extract_text() or ""):
                domain = normalize(domain.removeprefix("www."))
                if domain and not domain.endswith("adm.gov.it") and not domain.endswith("aams.gov.it"):
                    result.add(domain)
    if len(result) < 20:
        raise ValueError("No usable ADM domain list found")
    return result

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--offline", action="store_true", help="Validate and merge existing snapshots")
    args = parser.parse_args()
    sources, warnings = {}, []
    domains = parse((ROOT / "feeds/curated.txt").read_text(encoding="utf-8"))
    domains |= parse((ROOT / "feeds/bypass.txt").read_text(encoding="utf-8"))
    snapshot_dir = ROOT / "feeds/snapshots"
    if not args.offline:
        community = parse(fetch(HAGEZI).decode("utf-8"))
        if len(community) < 10000:
            raise ValueError("Community list unexpectedly small; keeping existing feed")
        atomic(snapshot_dir / "hagezi.txt", "\n".join(sorted(community)) + "\n")
        for key, url in ADM_PAGES.items():
            try:
                data = adm_domains(url)
                atomic(snapshot_dir / f"{key}.txt", "\n".join(sorted(data)) + "\n")
            except Exception as error:
                warnings.append(f"{key}: {error}; previous snapshot retained if available")
    if snapshot_dir.exists():
        for path in sorted(snapshot_dir.glob("*.txt")):
            data = parse(path.read_text(encoding="utf-8")); domains |= data
            sources[path.stem] = {"count": len(data), "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
    if len(domains) < 20:
        raise ValueError("Merged feed too small")
    content = "# GameShield: gambling domains and common DNS bypass endpoints\n" + "\n".join(sorted(domains)) + "\n"
    atomic(ROOT / "feeds/gambling.txt", content)
    atomic(ROOT / "app/src/main/assets/gambling.txt", content)
    metadata = {"generated_at": datetime.datetime.now(datetime.timezone.utc).isoformat(), "count": len(domains), "sha256": hashlib.sha256(content.encode()).hexdigest(), "sources": sources, "warnings": warnings}
    atomic(ROOT / "feeds/metadata.json", json.dumps(metadata, indent=2) + "\n")
    print(json.dumps(metadata, indent=2))

if __name__ == "__main__":
    main()
