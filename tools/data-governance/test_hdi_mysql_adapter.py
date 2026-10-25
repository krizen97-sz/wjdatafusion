import json
import os
from pathlib import Path
import shutil
import tempfile
import unittest
import hdi_mysql_adapter as adapter


@unittest.skipUnless(os.environ.get('HDI_PLATFORM_ADAPTERS') and os.environ.get('HDI_ORIGINAL_BUNDLE'), 'verified original/platform bundles not configured')
class PlatformAdapterProvenanceTests(unittest.TestCase):
    def setUp(self):
        self.bundle=Path(os.environ['HDI_ORIGINAL_BUNDLE'])
        self.original=Path(os.environ['HDI_PLATFORM_ADAPTERS'])
        self.temp=tempfile.TemporaryDirectory(prefix='mysql-adapter-receipt-test-')
        self.root=Path(self.temp.name)/'platform'
        shutil.copytree(self.original,self.root)

    def tearDown(self): self.temp.cleanup()

    def test_unmodified_receipt_and_parameter_sources_verify(self):
        manifest=adapter.verify(self.root,self.bundle)
        self.assertEqual({p['adapterName'] for p in manifest['plugins']},{'mysqlInput','mysqlOutput'})
        provenance=json.loads((self.root/'parameter-provenance.json').read_text())
        for plugin in manifest['plugins']:
            descriptor=json.loads((self.root/plugin['descriptor']).read_text())
            selected={p['name'] for p in descriptor['adapterParams']}
            self.assertEqual(selected,{p['name'] for p in provenance[plugin['adapterName']] if p['included']})
            self.assertTrue(all(p['references'] for p in provenance[plugin['adapterName']] if p['included']))

    def test_changed_jar_is_rejected(self):
        jar=self.root/adapter.JAR
        jar.write_bytes(jar.read_bytes()+b'tampered')
        with self.assertRaises(ValueError): adapter.verify(self.root,self.bundle)

    def test_original_dependency_digest_cannot_be_relabelled(self):
        path=self.root/adapter.MANIFEST
        manifest=json.loads(path.read_text())
        manifest['plugins'][0]['dependencies'][0]['sha256']='0'*64
        path.write_text(json.dumps(manifest))
        with self.assertRaises(ValueError): adapter.verify(self.root,self.bundle)

    def test_arbitrary_plugin_class_is_rejected(self):
        path=self.root/adapter.MANIFEST
        manifest=json.loads(path.read_text())
        manifest['plugins'][0]['className']='unreviewed.Executable'
        path.write_text(json.dumps(manifest))
        with self.assertRaises(ValueError): adapter.verify(self.root,self.bundle)

    def test_unlisted_jar_is_rejected(self):
        (self.root/'lib/unlisted.jar').write_bytes(b'x')
        with self.assertRaises(ValueError): adapter.verify(self.root,self.bundle)


if __name__=='__main__': unittest.main()
