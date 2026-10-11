#!/usr/bin/env python3
"""Check shareable source files, never generated builds or private workspace data."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
EXCLUDED = {'.git', '.gradle', '.kotlin', '.idea', 'build', '__pycache__'}
PROHIBITED_DIRS = {'backend', 'deploy', 'secrets-local', 'experimental', 'gold_labeling', 'whatsapp-collector', 'prompts'}
PROHIBITED_TEXT = [
    ('private production host', re.compile(r'https?://(?!(?:127\.0\.0\.1|10\.0\.2\.2)(?:[:/]))(?:[0-9]+(?:\.[0-9]+){3}|[^\s"<>/]+\.sslip\.io)')),
    ('credential material', re.compile(r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----|\bsk-(?:proj-|svcacct-)?[A-Za-z0-9_-]{20,}')),
]
errors = []
for path in ROOT.rglob('*'):
    rel = path.relative_to(ROOT)
    if any(part in EXCLUDED for part in rel.parts):
        continue
    if any(part in PROHIBITED_DIRS for part in rel.parts):
        errors.append(f'Private directory: {rel}')
        continue
    if not path.is_file():
        continue
    if path.is_symlink():
        errors.append(f'Symlink: {rel}')
        continue
    if path.name == 'local.properties':
        continue  # SDK path only; archive and Git exclude this local file.
    if path.name.startswith('.env') or path.suffix.lower() in {'.jks', '.keystore', '.p12', '.pfx', '.bundle', '.apk', '.aab'}:
        errors.append(f'Private/generated file: {rel}')
    if path.suffix in {'.kt', '.kts', '.xml', '.json', '.md', '.properties', '.toml', '.py'}:
        text = path.read_text(encoding='utf-8-sig')
        # Scanner contains literal patterns; don't flag its own deny-list.
        if rel.as_posix() == 'tools/check-public-package.py':
            continue
        for label, regex in PROHIBITED_TEXT:
            if regex.search(text):
                errors.append(f'{label}: {rel}')
for error in errors:
    print(error, file=sys.stderr)
if errors:
    sys.exit(1)
print('Public source boundary check passed. This is a targeted check, not a full security audit.')
