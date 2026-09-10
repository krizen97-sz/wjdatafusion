import io
import tempfile
import unittest
import zipfile
from pathlib import Path
from verify_nar import verify

class NarVerificationTest(unittest.TestCase):
    def jar(self, artifact, version, class_name):
        out = io.BytesIO()
        with zipfile.ZipFile(out, 'w') as z:
            z.writestr(f'META-INF/maven/com.hm.governance/{artifact}/pom.properties', f'groupId=com.hm.governance\nartifactId={artifact}\nversion={version}\n')
            z.writestr(class_name, b'synthetic-class')
        return out.getvalue()
    def test_clean_pair_passes_and_stale_or_duplicate_classes_fail(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'test.nar'
            def create(extra=None):
                with zipfile.ZipFile(path, 'w') as z:
                    z.writestr('core.jar', self.jar('governance-compatibility-core', '1.2.3', 'com/hm/governance/core/A.class'))
                    z.writestr('processor.jar', self.jar('governance-nifi-processors', '1.2.3', 'com/hm/governance/nifi/B.class'))
                    if extra: z.writestr('old.jar', extra)
            create(); self.assertEqual(2, verify(path, '1.2.3')['governanceClasses'])
            create(self.jar('governance-compatibility-core', '1.2.2', 'com/hm/governance/core/A.class'))
            with self.assertRaises(ValueError): verify(path, '1.2.3')
            create(self.jar('other', '1.2.3', 'com/hm/governance/nifi/B.class'))
            with self.assertRaises(ValueError): verify(path, '1.2.3')
    def test_wrong_declared_version_fails(self):
        with tempfile.TemporaryDirectory() as temp:
            path=Path(temp)/'x.nar'
            with zipfile.ZipFile(path,'w') as z:z.writestr('core.jar',self.jar('governance-compatibility-core','1.0.0','com/hm/governance/A.class'))
            with self.assertRaises(ValueError):verify(path,'1.2.3')

if __name__=='__main__':unittest.main()
