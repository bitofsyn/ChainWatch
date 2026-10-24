#!/usr/bin/env python3
"""Local fixture-only success -> outage -> recovery smoke test; no public RPC traffic."""
import argparse
import json
from pathlib import Path
from urllib.request import Request, urlopen

parser = argparse.ArgumentParser()
parser.add_argument('--scenario-file', type=Path, default=Path('/tmp/chainwatch-staking-scenario'))
args = parser.parse_args()
base = 'http://127.0.0.1:18090/api/ops/staking/observations'
def call(path='', method='GET'):
    with urlopen(Request(base + path, method=method), timeout=15) as response:
        return json.load(response)

previous = args.scenario_file.read_bytes() if args.scenario_file.exists() else None
results = []
assert call()['network'] == 'local-fixture', 'Fixture smoke requires local-fixture; no requests were triggered'
try:
    for scenario, expected in [('success', 'OBSERVED'), ('unavailable', 'INCOMPLETE'), ('success', 'OBSERVED')]:
        args.scenario_file.write_text(scenario)
        run = call(method='POST')
        assert run['network'] == 'local-fixture', 'Only run this script against the staking-poc profile'
        assert run['status'] == expected, run
        if expected == 'INCOMPLETE':
            assert run['errorCode'] == 'BEACON_HTTP_503' and run['validators'] is None, run
        else:
            assert run['validators'][0]['balanceGwei'] == '32000000001', run
            assert call(f"/{run['id']}/evidence")['version']['data']['version'].startswith('chainwatch-fixture/')
        results.append({'id': run['id'], 'scenario': scenario, 'status': run['status'], 'errorCode': run['errorCode']})
    ids = {r['id'] for r in call()['observations']}
    assert all(r['id'] in ids for r in results), 'History did not retain every attempt'
    print(json.dumps({'result': 'PASS', 'environment': 'local HTTP fixture + Spring Boot; database selected by active profile', 'runs': results}, indent=2))
finally:
    if previous is None:
        args.scenario_file.unlink(missing_ok=True)
    else:
        args.scenario_file.write_bytes(previous)
