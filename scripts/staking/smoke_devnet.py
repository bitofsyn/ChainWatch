#!/usr/bin/env python3
"""Verify two advancing finalized checkpoints through the local application API."""
import json
import time
from urllib.request import Request, urlopen

BASE = 'http://127.0.0.1:18090/api/ops/staking/observations'
def call(path='', method='GET'):
    with urlopen(Request(BASE + path, method=method), timeout=30) as response:
        return json.load(response)

assert call()['network'].startswith('local-teku-'), 'Only the local Teku development profile is supported'
deadline = time.monotonic() + 600
first = None
while time.monotonic() < deadline:
    run = call(method='POST')
    assert run['status'] == 'OBSERVED', run
    slot = int(run['slot'])
    if slot > 0:
        evidence = call(f"/{run['id']}/evidence")
        header = evidence['checkpoint']['data']['header']['message']
        assert header['state_root'] == run['stateRoot'] and header['slot'] == run['slot']
        assert len(run['validators']) == 3 and all(v['status'] == 'active_ongoing' for v in run['validators'])
        if first is None:
            first = run
        elif slot > int(first['slot']):
            assert run['stateRoot'] != first['stateRoot']
            ids = {r['id'] for r in call()['observations']}
            assert first['id'] in ids and run['id'] in ids
            print(json.dumps({'result': 'PASS', 'environment': 'local Geth + Teku + PostgreSQL',
                'first': {k:first[k] for k in ('id','slot','epoch','stateRoot')},
                'second': {k:run[k] for k in ('id','slot','epoch','stateRoot')},
                'clientVersion':run['clientVersion']}, indent=2))
            break
    print(f"Waiting for advancing finality: run={run['id']} slot={slot}", flush=True)
    time.sleep(12)
else:
    raise SystemExit('FAIL: finalized checkpoint did not advance in 10 minutes')
