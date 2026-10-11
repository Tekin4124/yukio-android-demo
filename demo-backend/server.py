#!/usr/bin/env python3
"""Local, stateless demonstration API. Does not contain the Yükio parser."""
import argparse
import json
import re
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit

MAX_BODY_BYTES = 32768
MAX_TEXT_CHARS = 8000
CITY_NAMES = ['İSTANBUL', 'ANKARA', 'İZMİR', 'BURSA', 'ANTALYA', 'KONYA',
              'ADANA', 'MERSİN', 'KOCAELİ', 'SAMSUN', 'TEKİRDAĞ', 'TRABZON']


def normalize(value):
    return value.translate(str.maketrans('ıİşŞğĞüÜöÖçÇ', 'iIsSgGuUoOcC')).upper().strip()


CITIES = {normalize(city): city for city in CITY_NAMES}
ROUTE = re.compile(r'^\s*([A-Z]+)\s*(?:->|→)\s*([A-Z]+)(?:\s*\|\s*(.*))?\s*$')


def parse_demo(text):
    """Only the documented CITY -> CITY | explicit fields format is supported."""
    offers = []
    for line in text.splitlines():
        match = ROUTE.fullmatch(normalize(line))
        if not match:
            continue
        origin, destination, fields = match.groups()
        if origin not in CITIES or destination not in CITIES or origin == destination:
            continue
        fields = fields or ''
        vehicle = re.search(r'\b(TIR|KAMYONET|KAMYON|PANELVAN)\b', fields)
        tonnage = re.search(r'\b(\d{1,2}(?:[.,]\d{1,2})?)\s*TON\b', fields)
        price = re.search(r'\b(\d{1,3}(?:\.\d{3})+|\d{1,7})\s*(?:TL|TRY)\b', fields)
        offers.append({
            'gonderici_il': CITIES[origin], 'gonderici_ilce': None,
            'alici_il': CITIES[destination], 'alici_ilce': None,
            'arac_tipi': vehicle[1] if vehicle else None,
            'dorse_tipi': None,
            'tonaj': tonnage[1].replace(',', '.') + ' ton' if tonnage else None,
            'fiyat': price[1].replace('.', '') if price else None,
            'telefon': None, 'firma': None, 'yetkili': None,
            'yuk_cinsi': 'DEMO',
            'rota_noktalari': [
                {'rol': 'ALIS', 'sira': 0, 'il': CITIES[origin], 'ilce': None},
                {'rol': 'TESLIM', 'sira': 1, 'il': CITIES[destination], 'ilce': None},
            ],
        })
    return {'ilanlar': offers, 'hata': None}


DEMO_PAGE = '''<!doctype html><html lang="tr"><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>Yükio Demo</title>
<body><h1>Yükio yerel demo</h1><p>Her satırda ŞEHİR -&gt; ŞEHİR | ARAÇ | TONAJ | FİYAT yaz.
Bu basit örnek gerçek parser değildir. Gerçek kişisel mesaj kullanma.</p>
<textarea id="message" rows="6" cols="60">İSTANBUL -> ANKARA | TIR | 20 ton | 18000 TL
BURSA -> İZMİR | KAMYON | 8 ton | 9000 TL</textarea><p><button id="send">Dene</button></p><pre id="result"></pre>
<script>document.getElementById('send').onclick=async()=>{
 const r=await fetch('/parse-ilan',{method:'POST',headers:{'Content-Type':'application/json'},
 body:JSON.stringify({text:document.getElementById('message').value})});
 document.getElementById('result').textContent=JSON.stringify(await r.json(),null,2);
};</script></body></html>'''


class DemoHandler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        pass  # Never log request bodies, identifiers or paths.

    def respond(self, status, payload, content_type='application/json; charset=utf-8'):
        data = json.dumps(payload, ensure_ascii=False).encode() if content_type.startswith('application/json') else payload.encode()
        self.send_response(status)
        self.send_header('Content-Type', content_type)
        self.send_header('Content-Length', str(len(data)))
        self.send_header('Cache-Control', 'no-store')
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.send_header('Content-Security-Policy', "default-src 'none'; script-src 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'")
        self.send_header('X-Parse-Source', 'LOCAL_DEMO')
        self.send_header('X-Parse-Requires-Review', '0')
        self.end_headers()
        self.wfile.write(data)

    def valid_host(self):
        # Block browser DNS rebinding: only explicit loopback hostnames allowed.
        host = (self.headers.get('Host') or '').split(':')[0].lower()
        return host in {'localhost', '127.0.0.1', '10.0.2.2'}

    def do_GET(self):
        if not self.valid_host():
            return self.respond(403, {'hata': 'LOCAL_HOST_REQUIRED'})
        path = urlsplit(self.path).path
        if path == '/':
            return self.respond(200, DEMO_PAGE, 'text/html; charset=utf-8')
        if path in {'/health', '/healthz'}:
            return self.respond(200, {'ok': True, 'demo': True})
        if path == '/privacy':
            return self.respond(200, 'Yerel demo: mesajlar diske kaydedilmez veya uzak servise iletilmez. Sentetik veri kullanın.', 'text/plain; charset=utf-8')
        self.respond(404, {'hata': 'NOT_FOUND'})

    def do_POST(self):
        if not self.valid_host():
            return self.respond(403, {'hata': 'LOCAL_HOST_REQUIRED'})
        if urlsplit(self.path).path != '/parse-ilan':
            return self.respond(404, {'hata': 'NOT_FOUND'})
        # No cross-origin website should send requests to the local demo API.
        origin = self.headers.get('Origin')
        if origin and origin != f'http://{self.headers.get("Host")}':
            return self.respond(403, {'hata': 'CROSS_ORIGIN_REJECTED'})
        if self.headers.get_content_type() != 'application/json':
            return self.respond(415, {'hata': 'JSON_REQUIRED'})
        try:
            length = int(self.headers.get('Content-Length', '-1'))
        except ValueError:
            return self.respond(400, {'hata': 'INVALID_LENGTH'})
        if length < 0:
            return self.respond(411, {'hata': 'LENGTH_REQUIRED'})
        if length > MAX_BODY_BYTES:
            return self.respond(413, {'hata': 'BODY_TOO_LARGE'})
        self.connection.settimeout(5)
        try:
            payload = json.loads(self.rfile.read(length))
        except (ValueError, TimeoutError):
            return self.respond(400, {'hata': 'INVALID_JSON'})
        if not isinstance(payload, dict) or not isinstance(payload.get('text'), str):
            return self.respond(400, {'hata': 'TEXT_REQUIRED'})
        if len(payload['text']) > MAX_TEXT_CHARS:
            return self.respond(413, {'hata': 'TEXT_TOO_LARGE'})
        self.respond(200, parse_demo(payload['text']))


def create_server(port=3000):
    return ThreadingHTTPServer(('127.0.0.1', port), DemoHandler)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--port', type=int, default=3000)
    args = parser.parse_args()
    with create_server(args.port) as server:
        print(f'Yükio demo: http://127.0.0.1:{server.server_port} — Ctrl+C ile durdur.', flush=True)
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            pass
