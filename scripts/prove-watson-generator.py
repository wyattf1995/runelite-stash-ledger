#!/usr/bin/env python3
"""Prove the Watson generator guards with source-only changes and exact restoration."""
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'build/validation'
OUT.mkdir(parents=True, exist_ok=True)
SOURCE = ROOT / 'scripts/generate-catalog.py'
TEST = 'test_watson_aliases_use_wrapper_identifier_unless_the_source_overrides_it'
PROOFS = [
    ('watson-generator-default', 'else entry.group(1)', 'else args[0][1:-1]'),
    ('watson-generator-warriors',
     'if stash_id in {"WARRIORS_GUILD_BANK", "WARRIORS_GUILD_BANK_29047"}:',
     'if False:'),
]
records = []


def run_test(label):
    result = subprocess.run(
        ['python3', 'scripts/test_generate_catalog.py', '-v', 'GenerateCatalogTest.' + TEST],
        cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    (OUT / (label + '.log')).write_text(result.stdout)
    return result.returncode, result.stdout


try:
    for label, needle, replacement in PROOFS:
        original = SOURCE.read_bytes()
        text = original.decode()
        if text.count(needle) != 1:
            raise RuntimeError('Expected one source guard: ' + label)
        record = {'guard': label, 'source': 'scripts/generate-catalog.py',
                  'sourceSha256': hashlib.sha256(original).hexdigest(), 'test': TEST}
        records.append(record)
        try:
            SOURCE.write_text(text.replace(needle, replacement))
            code, output = run_test(label + '-red')
            record.update(redExitCode=code, namedTestFailed=('FAIL: ' + TEST) in output)
            if code == 0 or not record['namedTestFailed']:
                raise RuntimeError('Named generator guard did not fail: ' + label)
        finally:
            SOURCE.write_bytes(original)
            record['restoredSha256'] = hashlib.sha256(SOURCE.read_bytes()).hexdigest()
            if record['restoredSha256'] != record['sourceSha256']:
                raise RuntimeError('Source restoration mismatch: ' + label)
        code, output = run_test(label + '-green')
        record.update(greenExitCode=code, namedTestPassed=code == 0 and TEST in output and '\nOK\n' in output)
        if not record['namedTestPassed']:
            raise RuntimeError('Restored generator guard did not pass: ' + label)
finally:
    (OUT / 'watson-generator-proofs.json').write_text(json.dumps(records, indent=2) + '\n')
