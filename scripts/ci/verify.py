#!/usr/bin/env python3
"""Fail-closed CI gates. Python stdlib only; run from repository root."""
import argparse
import hashlib
import json
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile
from fractions import Fraction
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REPORTS = {
    'domain': ROOT / 'domain/build/reports/jacoco/test/jacocoTestReport.xml',
    'app': ROOT / 'app/build/reports/jacoco/jacocoDebugReport/jacocoDebugReport.xml',
}
BASELINE = ROOT / 'config/quality/coverage.json'


def require(condition, message):
    if not condition:
        raise ValueError(message)


def coverage(initialize=False):
    measured = {}
    for module, path in REPORTS.items():
        counter = ET.parse(path).getroot().find("counter[@type='LINE']")
        require(counter is not None, f'{module}: missing LINE counter')
        covered, missed = int(counter.attrib['covered']), int(counter.attrib['missed'])
        require(covered + missed > 0, f'{module}: no instrumented lines')
        measured[module] = {'covered': covered, 'total': covered + missed}
    if initialize:
        require(not BASELINE.exists(), 'Initial coverage already recorded; cannot overwrite')
        BASELINE.write_text(json.dumps(measured, indent=2) + '\n')
    expected = json.loads(BASELINE.read_text())
    for module, actual in measured.items():
        minimum = expected[module]
        require(Fraction(actual['covered'], actual['total']) >=
                Fraction(minimum['covered'], minimum['total']), f'{module}: coverage regressed: {actual} < {minimum}')
        print(f"{module}: {100 * actual['covered'] / actual['total']:.2f}% lines")


def junit(directory, expected=None):
    paths = list(Path(directory).rglob('TEST-*.xml'))
    require(paths, f'No JUnit reports in {directory}')
    roots = [ET.parse(p).getroot() for p in paths]
    for root in roots:
        for suite in root.iter('testsuite'):
            require(all(int(suite.get(key, '0')) == 0 for key in ('failures', 'errors', 'skipped')),
                    f'Failed/skipped suite: {suite.attrib}')
    cases = [c for root in roots for c in root.iter('testcase')]
    require(cases, 'No test cases executed')
    for case in cases:
        require(not any(case.find(tag) is not None for tag in ('failure', 'error', 'skipped')),
                f"Failed/skipped test: {case.attrib}")
    if expected:
        require(any(c.get('classname') == expected for c in cases), f'Missing mandatory suite {expected}')
    print(f'{len(cases)} tests passed without skips')


def bundle():
    directory = ROOT / 'app/src/main/assets/lsa'
    manifest = json.loads((directory / 'lsa-manifest.json').read_text())
    for filename, field in [('modelo_lsa.tflite', 'modelSha256'), ('catalogo_senas.json', 'catalogSha256')]:
        require(hashlib.sha256((directory / filename).read_bytes()).hexdigest() == manifest[field], f'{filename}: hash mismatch')
    require((manifest['frames'], manifest['coords'], manifest['numClasses'], manifest['contractVersion']) == (40, 168, 64, 3), 'Unexpected Eva contract')
    fixture = json.loads((ROOT / 'app/src/androidTest/assets/fixture_android.json').read_text())
    require(fixture['casos'] and fixture['toleranciaLogits'] == 0.001, 'Missing reference cases or wrong tolerance')
    require(fixture['contractVersion'] == manifest['contractVersion'], 'Fixture contract mismatch')
    print('Eva package hashes and reference fixture verified')


def apk(path, apkanalyzer):
    manifest = subprocess.check_output([apkanalyzer, 'manifest', 'print', path], text=True)
    root = ET.fromstring(manifest)
    android = '{http://schemas.android.com/apk/res/android}'
    require(not any(n.get(android + 'name') == 'android.permission.INTERNET'
                    for n in root if n.tag.startswith('uses-permission')), 'APK requests INTERNET')
    application = root.find('application')
    require(application is not None and application.get(android + 'allowBackup') == 'false', 'Backup must be disabled')
    if 'release' in Path(path).name:
        require(application.get(android + 'debuggable', 'false') == 'false', 'Release is debuggable')
    with zipfile.ZipFile(path) as archive:
        models = [i for i in archive.infolist() if i.filename.endswith(('.tflite', '.task'))]
        require(len(models) >= 2, 'Missing packaged model/extractor')
        require(all(i.compress_type == zipfile.ZIP_STORED for i in models), 'Models are compressed')
    print(f'{path}: offline, backup and model packaging verified')


def sarif(directory):
    reports = list(Path(directory).rglob('*.sarif'))
    require(reports, 'No SARIF results produced')
    for path in reports:
        data = json.loads(path.read_text())
        require(data.get('runs'), f'{path}: no analysis runs')
        for run in data['runs']:
            rules = {r['id']: r for r in run['tool']['driver'].get('rules', [])}
            for result in run.get('results', []):
                rule = rules.get(result.get('ruleId'), {})
                severity = float(rule.get('properties', {}).get('security-severity', 0))
                level = result.get('level', rule.get('defaultConfiguration', {}).get('level', 'warning'))
                require(severity < 7 and level != 'error',
                        f"Blocking CodeQL result: {result.get('ruleId')} severity={severity} level={level}")


def baseline_entries(path, content):
    root = ET.fromstring(content)
    if path.endswith('detekt-baseline.xml'):
        return {e.text for e in root.iter('ID')}
    if path.endswith('ktlint-baseline.xml'):
        return {(f.get('name'), tuple(sorted(e.attrib.items()))) for f in root.iter('file') for e in f}
    if path.endswith('lint-baseline.xml'):
        return {(i.get('id'), i.get('message'), tuple(l.get('file') for l in i.findall('location'))) for i in root.findall('issue')}
    return {ET.tostring(e, encoding='unicode') for e in root}


def ratchet(base):
    for path in ['config/quality/coverage.json', 'config/quality/detekt-baseline.xml',
                 'config/quality/ktlint-baseline.xml', 'config/quality/lint-baseline.xml',
                 'config/quality/spotbugs-exclude.xml']:
        old = subprocess.run(['git', 'show', f'{base}:{path}'], capture_output=True, text=True)
        # Bootstrap only: no policy exists on the base branch yet.
        if old.returncode:
            exists = subprocess.run(['git', 'cat-file', '-e', base], capture_output=True)
            require(exists.returncode == 0, 'Missing comparison commit')
            continue
        new = (ROOT / path).read_text()
        if path.endswith('.json'):
            before, after = json.loads(old.stdout), json.loads(new)
            for module, value in before.items():
                require(Fraction(after[module]['covered'], after[module]['total']) >= Fraction(value['covered'], value['total']), f'{module}: minimum coverage reduced')
        else:
            require(baseline_entries(path, new) <= baseline_entries(path, old.stdout), f'{path}: baseline grew')


def exceptions():
    from datetime import date
    root = ET.parse(ROOT / 'config/quality/dependency-suppressions.xml').getroot()
    namespace = root.tag.split('}')[0].lstrip('{')
    ns = {'s': namespace}
    for item in root:
        require(item.tag.endswith('suppress'), 'Unknown suppression element')
        require(date.fromisoformat(item.attrib['until'].split('Z')[0]) > date.today(), 'Expired exception')
        notes = item.findtext('s:notes', default='', namespaces=ns)
        require(all(key in notes for key in ('finding:', 'owner:', 'reason:')),
                'Exception needs finding, owner and reason')
        require(item.find('s:packageUrl', ns) is not None,
                'Exception needs an exact package URL')
        require(item.find('s:cve', ns) is not None or item.find('s:cpe', ns) is not None,
                'Exception needs an exact CVE or false-positive CPE')
        require(not any(c.get('regex') == 'true' for c in item), 'Regex suppressions are forbidden')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('command', choices=['coverage', 'init-coverage', 'junit', 'bundle', 'apk', 'ratchet', 'exceptions', 'sarif'])
    parser.add_argument('args', nargs='*')
    args = parser.parse_args()
    if args.command == 'init-coverage':
        coverage(True)
    else:
        globals()[args.command](*args.args)


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, KeyError, ET.ParseError) as error:
        sys.exit(str(error))
