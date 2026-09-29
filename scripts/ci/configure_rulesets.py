#!/usr/bin/env python3
"""Review/apply GitHub rules only after both protected heads pass CI."""
import argparse
import json
import subprocess
from pathlib import Path

REPO = 'Tiago-Davila/helpi'
CHECKS = ['branch-policy', 'quality', 'unit-contract', 'android-tests', 'build', 'security', 'ci-gate']


def api(path, method='GET', data=None):
    command = ['gh', 'api', f'repos/{REPO}' + (f'/{path}' if path else ''), '--method', method]
    if data is not None:
        command += ['--input', '-']
    return json.loads(subprocess.check_output(command, input=json.dumps(data).encode() if data is not None else None))


def ruleset(branch):
    return {
        'name': branch, 'target': 'branch', 'enforcement': 'active', 'bypass_actors': [],
        'conditions': {'ref_name': {'include': [f'refs/heads/{branch}'], 'exclude': []}},
        'rules': [
            {'type': 'deletion'}, {'type': 'non_fast_forward'},
            {'type': 'pull_request', 'parameters': {
                'required_approving_review_count': 0, 'dismiss_stale_reviews_on_push': False,
                'require_code_owner_review': False, 'require_last_push_approval': False,
                'required_review_thread_resolution': True,
                'allowed_merge_methods': ['merge'] if branch == 'main' else ['squash', 'merge'],
            }},
            {'type': 'required_status_checks', 'parameters': {
                'strict_required_status_checks_policy': True,
                'do_not_enforce_on_create': False,
                'required_status_checks': [{'context': name, 'integration_id': 15368} for name in CHECKS],
            }},
            {'type': 'code_scanning', 'parameters': {'code_scanning_tools': [{
                'tool': 'CodeQL', 'security_alerts_threshold': 'high_or_higher',
                'alerts_threshold': 'errors',
            }]}},
        ],
    }


def preflight(branch):
    head = api(f'branches/{branch}')['commit']['sha']
    for name in ['ci', 'branch-policy', 'dependency-graph', 'dependency-submit']:
        api(f'contents/.github/workflows/{name}.yml?ref={head}')
    runs = api(f'actions/workflows/ci.yml/runs?branch={branch}&event=push&head_sha={head}&per_page=1')['workflow_runs']
    if not runs or runs[0]['conclusion'] != 'success':
        raise SystemExit(f'{branch}@{head}: latest push CI has not succeeded; rules NOT changed')
    alerts = api(f'code-scanning/alerts?state=open&ref=refs/heads/{branch}&per_page=100')
    high = [a for a in alerts if a['rule'].get('security_severity_level') in ('high', 'critical')]
    if high:
        raise SystemExit(f'{branch}: unresolved high/critical CodeQL alerts; rules NOT changed')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    payloads = {branch: ruleset(branch) for branch in ('develop', 'main')}
    if not args.apply:
        print(json.dumps(payloads, indent=2))
        return
    for branch in payloads:
        preflight(branch)
    existing = api('rulesets')
    backup = {str(r['id']): api(f"rulesets/{r['id']}") for r in existing}
    target = Path('build/reports/rulesets-before.json')
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(backup, indent=2) + '\n')
    for branch, payload in payloads.items():
        matches = [r for r in existing if r['name'] == branch]
        if len(matches) > 1:
            raise SystemExit(f'Ambiguous rulesets for {branch}')
        path = f"rulesets/{matches[0]['id']}" if matches else 'rulesets'
        result = api(path, 'PUT' if matches else 'POST', payload)
        actual = api(f"rulesets/{result['id']}")
        assert actual['enforcement'] == 'active' and not actual['bypass_actors']
        print(f"{branch}: {result['_links']['html']['href']}")
    api('', 'PATCH', {'security_and_analysis': {
        'secret_scanning': {'status': 'enabled'},
        'secret_scanning_push_protection': {'status': 'enabled'},
    }})


if __name__ == '__main__':
    main()
