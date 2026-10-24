#!/usr/bin/env python3
"""Deterministic Beacon API fixture. Not a consensus node or validator simulator."""
import argparse
import json
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

GENESIS = '0x' + 'b' * 64
ROOT = '0x' + 'a' * 64
KEY = '0x' + 'c' * 96
GENESIS_TIME = int(time.time()) - 3840 - 600
parser = argparse.ArgumentParser()
parser.add_argument('--port', type=int, default=19596)
parser.add_argument('--scenario-file', type=Path, help='File containing success, unavailable, optimistic, wrong-network or missing-validator')
args = parser.parse_args()

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        scenario = args.scenario_file.read_text().strip() if args.scenario_file and args.scenario_file.exists() else 'success'
        if scenario == 'unavailable':
            return self.respond(503, {'message': 'Injected Beacon outage'})
        if self.path == '/eth/v1/beacon/genesis':
            return self.respond(200, {'data': {'genesis_time': str(GENESIS_TIME), 'genesis_validators_root': ROOT if scenario == 'wrong-network' else GENESIS, 'genesis_fork_version': '0x00000000'}})
        if self.path == '/eth/v1/node/version':
            return self.respond(200, {'data': {'version': 'chainwatch-fixture/1 (not a node)'}})
        if self.path == '/eth/v1/config/spec':
            return self.respond(200, {'data': {'SECONDS_PER_SLOT': '12', 'SLOTS_PER_EPOCH': '32'}})
        flags = {'execution_optimistic': scenario == 'optimistic', 'finalized': True}
        if self.path == '/eth/v1/beacon/headers/finalized':
            slot = 0 if scenario == 'stale' else (int(time.time()) - GENESIS_TIME - 600) // 12
            return self.respond(200, {**flags, 'data': {'root': ROOT, 'canonical': True, 'header': {'message': {'slot': str(slot), 'state_root': ROOT}}}})
        if self.path == f'/eth/v1/beacon/states/{ROOT}/validators/42' and scenario != 'missing-validator':
            return self.respond(200, {**flags, 'data': {'index': '42', 'balance': '32000000001', 'status': 'active_ongoing', 'validator': {
                'pubkey': KEY, 'withdrawal_credentials': '0x' + '0' * 64, 'effective_balance': '32000000000', 'slashed': False,
                'activation_eligibility_epoch': '0', 'activation_epoch': '1', 'exit_epoch': '18446744073709551615', 'withdrawable_epoch': '18446744073709551615'}}})
        self.respond(404, {'message': 'Not found'})
    def respond(self, code, body):
        payload = json.dumps(body).encode()
        self.send_response(code)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

print(f'Beacon fixture at http://127.0.0.1:{args.port}; genesis={GENESIS}; validator=42', flush=True)
ThreadingHTTPServer(('127.0.0.1', args.port), Handler).serve_forever()
