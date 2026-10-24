#!/usr/bin/env python3
"""Connect only to a loopback devnet; discover its test genesis, then start Spring."""
import argparse
import json
import os
from pathlib import Path
import re
from urllib.parse import urlparse
from urllib.request import urlopen

parser = argparse.ArgumentParser()
parser.add_argument('--beacon-url', required=True, help='Kurtosis cl-1-teku-geth http URL')
parser.add_argument('--execution-url', help='Kurtosis el-1-geth-teku rpc HTTP URL; enables reconciliation')
args = parser.parse_args()
url = urlparse(args.beacon_url)
if url.scheme != 'http' or url.hostname not in ('127.0.0.1', 'localhost') or url.username or url.query or url.fragment:
    parser.error('Only local HTTP devnets are supported')
with urlopen(args.beacon_url.rstrip('/') + '/eth/v1/beacon/genesis', timeout=5) as response:
    genesis = json.load(response)['data']
    root = genesis['genesis_validators_root']
    genesis_time = genesis['genesis_time']
if not re.fullmatch(r'[0-9]{1,20}', genesis_time):
    raise SystemExit('Invalid genesis time')
if not re.fullmatch(r'0x[0-9a-fA-F]{64}', root):
    raise SystemExit('Invalid devnet genesis root')
print('Local devnet genesis:', root, flush=True)
os.chdir(Path(__file__).resolve().parents[2] / 'backend')
options = ' '.join([
    '--spring.profiles.active=staking-poc,staking-postgres',
    '--chainwatch.staking.network=local-teku-' + genesis_time,
    '--chainwatch.staking.beacon-url=' + args.beacon_url.rstrip('/'),
    '--chainwatch.staking.genesis-validators-root=' + root,
    '--chainwatch.staking.validator-ids=0,1,2',
])
if args.execution_url:
    execution = urlparse(args.execution_url)
    if execution.scheme != 'http' or execution.hostname not in ('127.0.0.1', 'localhost') or execution.username or execution.query or execution.fragment:
        parser.error('Execution URL must be local HTTP')
    options += ' --chainwatch.staking.reconciliation.enabled=true --chainwatch.staking.reconciliation.execution-url=' + args.execution_url
os.execv('./gradlew', ['./gradlew', 'bootRun', '--args=' + options])
