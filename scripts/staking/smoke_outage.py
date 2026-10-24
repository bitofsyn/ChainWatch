#!/usr/bin/env python3
"""Pause a named local Teku process, assert timeout and restore it in finally.

This tests an unresponsive node, not container destruction or a validator restart.
Only use the dedicated disposable ChainWatch devnet. Never kill this script with SIGKILL.
"""
import argparse
import json
from pathlib import Path
import re
import signal
import subprocess
import time
from datetime import datetime, timezone
from urllib.request import Request, urlopen

parser = argparse.ArgumentParser()
parser.add_argument('--container', required=True, help='Dedicated cl-N-teku-geth--... container')
parser.add_argument('--epoch', type=int, required=True)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
if not re.fullmatch(r'cl-\d+-teku-geth--[a-f0-9]+', args.container):
    parser.error('Expected a dedicated Kurtosis Teku container name')
if not 1 <= args.epoch <= 1_000_000:
    parser.error('Epoch must be 1..1000000')

def docker(*arguments):
    return subprocess.check_output(['docker', *arguments], text=True, timeout=30)

def call(path, body=None):
    with urlopen(Request('http://127.0.0.1:18090/api/ops/staking' + path,
                         data=json.dumps(body).encode() if body is not None else None,
                         headers={'Content-Type': 'application/json'}), timeout=200) as response:
        return json.load(response)

def attempt():
    started = time.monotonic()
    run = call('/reconciliations', {'epoch': args.epoch})
    return run, round(time.monotonic() - started, 3)

def interrupted(signum, frame):
    raise KeyboardInterrupt(f'Interrupted by signal {signum}')

signal.signal(signal.SIGTERM, interrupted)
info = json.loads(docker('inspect', args.container))[0]
assert info['State']['Running'] and not info['State']['Paused'], 'Target must be running and unpaused'
assert info['Config']['Image'].startswith('ethpandaops/teku'), 'Unexpected image'
assert call('/observations')['network'].startswith('local-teku-'), 'Local devnet application required'
# Establish that the selected container belongs to the same network without reading validator keys.
ports = info['NetworkSettings']['Ports'].get('4000/tcp') or []
assert len(ports) == 1, 'Expected one Beacon REST port'
with urlopen('http://127.0.0.1:' + ports[0]['HostPort'] + '/eth/v1/beacon/genesis', timeout=5) as response:
    genesis = json.load(response)['data']
before, normal_seconds = attempt()
assert before['status'] == 'MATCHED', before
assert before['report']['genesisTime'] == genesis['genesis_time'], 'Wrong devnet container'
paused = False
try:
    paused = True
    docker('pause', args.container)
    failed, failure_seconds = attempt()
    assert failed['status'] == 'INCOMPLETE' and failed['errorCode'] == 'UPSTREAM_TIMEOUT', failed
finally:
    if paused and json.loads(docker('inspect', args.container))[0]['State']['Paused']:
        docker('unpause', args.container)
recovered, recovery_seconds = attempt()
assert recovered['status'] == 'MATCHED', recovered
assert before['report'] == recovered['report'], 'Finalized accounting changed after recovery'
history = {r['id']: r for r in call('/reconciliations')['runs']}
assert {before['id'], failed['id'], recovered['id']}.issubset(history)
assert call(f"/reconciliations/{before['id']}/evidence"), 'Baseline evidence lost'
assert call(f"/reconciliations/{failed['id']}/evidence"), 'Failed request evidence lost'
report = {
    'result': 'PASS', 'recordedAt': datetime.now(timezone.utc).isoformat(),
    'fault': 'docker pause/unpause: unresponsive Beacon process; no validator restart',
    'container': args.container, 'network': before['network'], 'epoch': args.epoch,
    'runs': [{'id': run['id'], 'status': run['status'], 'errorCode': run['errorCode'], 'elapsedSeconds': seconds}
             for run, seconds in [(before, normal_seconds), (failed, failure_seconds), (recovered, recovery_seconds)]],
    'residualsGwei': [row['residualGwei'] for row in recovered['report']['validators']],
    'sameReportAfterRecovery': True, 'containerResumed': not json.loads(docker('inspect', args.container))[0]['State']['Paused']
}
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps(report, indent=2))
