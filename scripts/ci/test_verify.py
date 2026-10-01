import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import verify


class GatesTest(unittest.TestCase):
    def test_bundle_rejects_missing_artifacts_and_empty_reference_cases(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            assets = root / 'app/src/main/assets/lsa'
            assets.mkdir(parents=True)
            model = assets / 'modelo_lsa.tflite'
            catalog = assets / 'catalogo_senas.json'
            # These bytes exercise the packaging gate, not model inference.
            model.write_bytes(b'synthetic-packaging-fixture')
            catalog.write_bytes(b'{}')
            manifest = {'frames': 40, 'coords': 168, 'numClasses': 64, 'contractVersion': 3,
                        'modelSha256': hashlib.sha256(model.read_bytes()).hexdigest(),
                        'catalogSha256': hashlib.sha256(catalog.read_bytes()).hexdigest()}
            (assets / 'lsa-manifest.json').write_text(json.dumps(manifest))
            fixture = root / 'app/src/androidTest/assets/fixture_android.json'
            fixture.parent.mkdir(parents=True)
            reference = {'casos': [{}], 'toleranciaLogits': 0.001, 'contractVersion': 3}
            fixture.write_text(json.dumps(reference))
            with patch.object(verify, 'ROOT', root):
                verify.bundle()
                for artifact in (model, catalog, fixture):
                    with self.subTest(missing=artifact.name):
                        content = artifact.read_bytes()
                        artifact.unlink()
                        with self.assertRaises(OSError):
                            verify.bundle()
                        artifact.write_bytes(content)
                for key, invalid in [('casos', []), ('toleranciaLogits', 0.01),
                                     ('contractVersion', 2)]:
                    with self.subTest(field=key):
                        fixture.write_text(json.dumps({**reference, key: invalid}))
                        with self.assertRaises(ValueError):
                            verify.bundle()

    def test_junit_requires_tests_and_rejects_failures_and_skips(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(ValueError):
                verify.junit(directory)
            report = Path(directory) / 'TEST-fixture.xml'
            for content in ['<testsuite/>', '<testsuite><testcase><skipped/></testcase></testsuite>',
                            '<testsuite><testcase><failure/></testcase></testsuite>',
                            '<testsuite><testcase><error/></testcase></testsuite>']:
                report.write_text(content)
                with self.assertRaises(ValueError):
                    verify.junit(directory)
            report.write_text('<testsuite><testcase classname="Required"/></testsuite>')
            verify.junit(directory, 'Required')
            with self.assertRaises(ValueError):
                verify.junit(directory, 'Absent')

    def test_coverage_regression_and_empty_report_fail(self):
        with tempfile.TemporaryDirectory() as directory:
            baseline = Path(directory) / 'coverage.json'
            report = Path(directory) / 'report.xml'
            baseline.write_text(json.dumps({'app': {'covered': 50, 'total': 100}}))
            with patch.object(verify, 'BASELINE', baseline), patch.object(verify, 'REPORTS', {'app': report}):
                for covered, missed, passes in [(50, 50, True), (49, 51, False), (0, 0, False)]:
                    report.write_text(f'<report><counter type="LINE" covered="{covered}" missed="{missed}"/></report>')
                    if passes:
                        verify.coverage()
                    else:
                        with self.assertRaises(ValueError):
                            verify.coverage()
                with self.assertRaises(ValueError):
                    verify.coverage(initialize=True)

    def test_high_security_alert_blocks_even_when_scan_completed(self):
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / 'scan.sarif'
            data = {'runs': [{'tool': {'driver': {'rules': [
                {'id': 'synthetic-rule', 'properties': {'security-severity': '8.0'}}]}},
                'results': [{'ruleId': 'synthetic-rule', 'level': 'warning'}]}]}
            report.write_text(json.dumps(data))
            with self.assertRaises(ValueError):
                verify.sarif(directory)
            data['runs'][0]['results'] = []
            report.write_text(json.dumps(data))
            verify.sarif(directory)

    def test_baseline_replacement_does_not_hide_new_debt(self):
        original = '<SmellBaseline><CurrentIssues><ID>Old</ID></CurrentIssues></SmellBaseline>'
        replacement = original.replace('Old', 'New')
        self.assertFalse(verify.baseline_entries('detekt-baseline.xml', replacement) <=
                         verify.baseline_entries('detekt-baseline.xml', original))


if __name__ == '__main__':
    unittest.main()
