import http.client
import json
import threading
import unittest
from server import create_server, parse_demo


class DemoTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = create_server(0)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join()

    def request(self, method, path, body=None, headers=None):
        client = http.client.HTTPConnection('127.0.0.1', self.server.server_port, timeout=3)
        client.request(method, path, body, headers or {})
        response = client.getresponse()
        data = response.read()
        status, response_headers = response.status, dict(response.getheaders())
        client.close()
        return status, response_headers, data

    def test_multiline_routes_and_fields(self):
        offers = parse_demo('İSTANBUL -> ANKARA | TIR | 20 ton | 18.000 TL\nBURSA → İZMİR | KAMYON | 8 ton | 9000 TL')['ilanlar']
        self.assertEqual(len(offers), 2)
        self.assertEqual(offers[0]['fiyat'], '18000')
        self.assertEqual(offers[1]['tonaj'], '8 ton')
        self.assertEqual(offers[0]['rota_noktalari'][1]['il'], 'ANKARA')
        self.assertTrue(all(offer['telefon'] is None for offer in offers))

    def test_unknown_and_incomplete_text_does_not_invent_routes(self):
        self.assertEqual(parse_demo('rastgele yazı\nİSTANBUL -> YOK\nANKARA -> ANKARA')['ilanlar'], [])

    def test_parse_endpoint(self):
        status, headers, data = self.request('POST', '/parse-ilan', json.dumps({'text': 'İZMİR -> BURSA | KAMYONET'}), {'Content-Type': 'application/json'})
        self.assertEqual(status, 200)
        self.assertEqual(headers['X-Parse-Source'], 'LOCAL_DEMO')
        self.assertEqual(json.loads(data)['ilanlar'][0]['arac_tipi'], 'KAMYONET')

    def test_invalid_payload(self):
        for body in ['{', '[]', '{"text":123}']:
            self.assertEqual(self.request('POST', '/parse-ilan', body, {'Content-Type': 'application/json'})[0], 400)

    def test_size_limits(self):
        self.assertEqual(self.request('POST', '/parse-ilan', 'x' * 32769, {'Content-Type': 'application/json'})[0], 413)
        self.assertEqual(self.request('POST', '/parse-ilan', json.dumps({'text': 'x' * 8001}), {'Content-Type': 'application/json'})[0], 413)

    def test_private_and_billing_endpoints_do_not_exist(self):
        for path in ['/v1/admin', '/billing/status', '/billing/play/sync', '/v1/parser-samples/events']:
            self.assertEqual(self.request('POST', path, '{}', {'Content-Type': 'application/json'})[0], 404)

    def test_dns_rebinding_and_cross_origin_rejected(self):
        self.assertEqual(self.request('GET', '/', headers={'Host': 'attacker.invalid'})[0], 403)
        self.assertEqual(self.request('POST', '/parse-ilan', '{}', {'Content-Type': 'application/json', 'Origin': 'http://attacker.invalid'})[0], 403)

    def test_health_and_no_store(self):
        status, headers, data = self.request('GET', '/health')
        self.assertEqual(status, 200)
        self.assertTrue(json.loads(data)['demo'])
        self.assertEqual(headers['Cache-Control'], 'no-store')


if __name__ == '__main__':
    unittest.main()
