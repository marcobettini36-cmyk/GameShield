"""Regression guard for this Normal-only change; baseline precedes new features."""
import hashlib
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


class StrongIsolationTest(unittest.TestCase):
    def test_common_and_strong_sources_are_identical(self):
        expected = json.loads((ROOT / 'scripts/strong_normal_change_baseline.json').read_text(encoding='utf-8'))
        paths = []
        for directory in ('app/src/main/java', 'app/src/main/res', 'app/src/strong', 'native-patches'):
            paths.extend(p for p in (ROOT / directory).rglob('*') if p.is_file())
        paths.append(ROOT / 'app/src/main/AndroidManifest.xml')
        actual = {p.relative_to(ROOT).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in paths}
        self.assertEqual(expected, actual, 'This Normal change must not alter shared/Strong sources')

    def test_strong_flavor_identity_unchanged(self):
        gradle = (ROOT / 'app/build.gradle').read_text(encoding='utf-8')
        self.assertIn("strong { dimension 'protection'; minSdk 30; applicationIdSuffix '.strong'; buildConfigField 'boolean', 'STRONG', 'true'; resValue 'string', 'app_name', 'GameShield Strong' }", gradle)
        self.assertIn("versionCode 5", gradle)
        self.assertIn("versionName '0.3.2'", gradle)


if __name__ == '__main__':
    unittest.main()
