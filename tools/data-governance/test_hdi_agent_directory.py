import http.client
import json
import socket
import threading
import unittest

from hdi_agent_directory import DeploymentAddress, DirectoryServer, DIRECTORY_PATH


class DirectoryTests(unittest.TestCase):
    def setUp(self):
        self.target_socket = socket.socket()
        self.target_socket.bind(("127.0.0.1", 0))
        self.target_socket.listen(10)
        self.target = DeploymentAddress(self.target_socket.getsockname()[1])
        self.server = DirectoryServer(0, self.target)
        self.thread = threading.Thread(target=self.server.serve_forever, kwargs={"poll_interval": 0.01}, daemon=True)
        self.thread.start()

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(1)
        self.target_socket.close()

    def request(self, method="GET", path=DIRECTORY_PATH, headers=None, body=None):
        client = http.client.HTTPConnection("127.0.0.1", self.server.server_port, timeout=2)
        try:
            client.request(method, path, body=body, headers=headers or {})
            response = client.getresponse()
            return response.status, dict(response.getheaders()), json.loads(response.read())
        finally:
            client.close()

    def test_actual_local_address_matches_original_dto_without_simulated_status(self):
        status, headers, data = self.request(headers={"Token": "never-reflect-this-sdk-token"})
        self.assertEqual(status, 200)
        self.assertEqual(data, self.target.response())
        self.assertEqual(data["data"]["address"][0]["port"], self.target.port)
        self.assertEqual(headers["Cache-Control"], "no-store")
        serialized = json.dumps(data).lower()
        for forbidden in ("never-reflect", "ready", "online", "authorized", "license", "heartbeat"):
            self.assertNotIn(forbidden, serialized)
        self.assertEqual(self.server.server_address[0], "127.0.0.1")

    def test_absent_target_does_not_publish_a_success(self):
        self.target_socket.close()
        status, _, body = self.request()
        self.assertEqual(status, 503)
        self.assertNotIn("data", body)

    def test_remote_ambiguous_or_privileged_targets_are_rejected(self):
        for value in ("http://localhost:14312/detl", "http://192.0.2.1:14312/detl", "http://127.0.0.1:80/detl",
                      "http://u:p@127.0.0.1:14312/detl", "http://127.0.0.1:14312/detl?next=evil",
                      "http://127.0.0.1:14312/detl#fragment", "http://127.0.0.1:14312/%64etl",
                      "http://127.0.0.1:14312/../detl", "https://127.0.0.1:14312/detl"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                DeploymentAddress.parse(value)
        self.assertEqual(DeploymentAddress.parse("http://127.0.0.1:14312/detl/").port, 14312)

    def test_exact_read_only_path_cannot_register_or_mutate(self):
        for method in ("POST", "PUT", "PATCH", "DELETE", "OPTIONS"):
            with self.subTest(method=method):
                self.assertEqual(self.request(method=method, body=b"{}")[0], 405)
        for path in ("/", DIRECTORY_PATH + "?x=1", "/service/rs/v1/remote/agent/register", "/svrService/v1/service/other/other"):
            with self.subTest(path=path):
                self.assertEqual(self.request(path=path)[0], 404)
        self.assertEqual(self.request(body=b"not-allowed")[0], 400)

    def test_browser_and_rebound_host_requests_are_refused(self):
        for headers in ({"Origin": "https://example.test"}, {"Sec-Fetch-Site": "cross-site"}, {"Host": "foreign.example"}):
            with self.subTest(headers=headers):
                self.assertEqual(self.request(headers=headers)[0], 403)


if __name__ == "__main__":
    unittest.main()
