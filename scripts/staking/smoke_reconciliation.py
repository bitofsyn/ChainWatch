#!/usr/bin/env python3
"""Local devnet acceptance: epoch equality, repeatability, incomplete future epoch, evidence."""
import argparse
import json
from urllib.request import Request, urlopen

parser = argparse.ArgumentParser()
parser.add_argument('--epoch', type=int, required=True)
args = parser.parse_args()
BASE = 'http://127.0.0.1:18090/api/ops/staking'
def call(path, body=None):
    with urlopen(Request(BASE + path, data=json.dumps(body).encode() if body is not None else None,
                         headers={'Content-Type': 'application/json'}), timeout=200) as response:
        return json.load(response)
assert call('/observations')['network'].startswith('local-teku-'), 'Local devnet profile required'
runs=[]
for _ in range(2):
    run=call('/reconciliations', {'epoch':args.epoch})
    assert run['status']=='MATCHED', run
    report=run['report']
    assert report['settlementEligible'] is False and report['linkedBlocks']>1
    assert all(row['residualGwei']=='0' for row in report['validators'])
    for row in report['validators']:
        assert int(row['openingGwei'])+int(row['consensusRewardsGwei'])-int(row['consensusPenaltiesGwei'])-int(row['withdrawalsGwei'])==int(row['closingGwei'])
    assert len(call(f"/reconciliations/{run['id']}/evidence"))>10
    runs.append(run)
assert runs[0]['id']!=runs[1]['id']
assert runs[0]['report']==runs[1]['report'], 'Same finalized interval must be repeatable'
future=call('/reconciliations', {'epoch':1_000_000})
assert future['status']=='INCOMPLETE' and future['errorCode']=='EPOCH_NOT_FINALIZED'
assert len(call(f"/reconciliations/{future['id']}/evidence"))>0
print(json.dumps({'result':'PASS','epoch':args.epoch,'matchedRuns':[r['id'] for r in runs],
    'futureRun':future['id'],'report':runs[-1]['report']}, indent=2))
