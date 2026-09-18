import tempfile
from pathlib import Path
import subprocess
import sys
import unittest

from render_upload_location import render


class UploadLocationTest(unittest.TestCase):
    def test_root_upstream_removes_public_prefix(self):
        text = render('http://10.0.0.5:8080/')
        self.assertIn('proxy_pass http://10.0.0.5:8080/document/workspace/documents/upload;', text)
        self.assertIn('location = /prod-api/document/workspace/documents/upload', text)
        self.assertIn('client_max_body_size 0;', text)

    def test_context_prefix_is_preserved(self):
        self.assertIn('proxy_pass https://backend.internal/api/document/workspace/documents/upload;',
                      render('https://backend.internal/api/'))

    def test_no_uri_keeps_original_uri_in_nginx(self):
        self.assertIn('proxy_pass http://backend:8080;', render('http://backend:8080'))

    def test_ipv6_and_alternative_public_prefix(self):
        text = render('http://[::1]:8080/;', '/dev-api/')
        self.assertIn('proxy_pass http://[::1]:8080/document/workspace/documents/upload;', text)
        self.assertIn('location = /dev-api/document/workspace/documents/upload', text)

    def test_ambiguous_or_unsafe_values_are_rejected(self):
        for upstream in ['http://$backend/', 'http://x/; include bad;', 'http://x/\n}',
                         'http://user:secret@x/', 'http://x/?token=a', 'http://x/#part',
                         'file:///etc/passwd', 'http://x:99999/', 'http://x/context']:
            with self.subTest(upstream=upstream):
                with self.assertRaises(ValueError):
                    render(upstream)
        with self.assertRaises(ValueError):
            render('http://x/', '/bad/;')

    def test_cli_does_not_overwrite_existing_configuration(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / 'nginx.conf'
            output.write_text('original configuration')
            script = Path(__file__).with_name('render_upload_location.py')
            result = subprocess.run([sys.executable, str(script), '--proxy-pass', 'http://backend/',
                                     '--output', str(output)], capture_output=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertEqual(output.read_text(), 'original configuration')


if __name__ == '__main__':
    unittest.main()
