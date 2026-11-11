#!/usr/bin/env python3
"""Run source-only guard removals, require named test failures, restore exact bytes.

This runs only in the isolated fleet workspace after the baseline suite passes.
No test source is modified. A failed proof exits nonzero; original files are always
restored, and hashes are recorded before the final complete green build.
"""
import hashlib
import json
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'build/validation'
OUT.mkdir(parents=True, exist_ok=True)
PROOFS = [
    ('watson-parentheses', 'src/main/java/com/stashledger/tracking/WatsonBoardParser.java',
     '.replaceAll("[^\\\\p{L}\\\\p{N}\\\\s]", "")', '.replace("\'", "").replace("’", "").replace("-", "").replace("_", "")',
     'com.stashledger.CatalogWatsonIntegrationTest', 'parsesAll119WithActualParenthesizedBoardNamesAndTierCollisions'),
    ('account-epoch', 'src/main/java/com/stashledger/StashLedgerPlugin.java',
     ' || expectedEpoch != accountEpoch', '',
     'com.stashledger.StashLedgerPluginTest', 'oldRenderedSidebarCannotMutateANewerSession'),
    ('built-contents-guard', 'src/main/java/com/stashledger/state/LedgerState.java',
     'status == null || builds.get(id) != BuildStatus.BUILT', 'status == null',
     'com.stashledger.state.LedgerStateTest', 'onlyBuiltUnitsAcceptContentsConfirmation'),
]
results = []

def run_test(label, class_name, method):
    with (OUT / (label + '.log')).open('w') as log:
        result = subprocess.run(['./gradlew', '--no-daemon', '--max-workers=2',
                                 'cleanTest', 'test', '--tests', class_name + '.' + method],
                                cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
    xml_path = ROOT / ('build/test-results/test/TEST-' + class_name + '.xml')
    cases = [] if not xml_path.exists() else ET.parse(xml_path).getroot().findall('testcase')
    case = next((c for c in cases if c.attrib.get('name') == method), None)
    failed = case is not None and case.find('failure') is not None
    passed = case is not None and not failed and case.find('skipped') is None
    return result.returncode, failed, passed

try:
    for label, path, needle, replacement, class_name, method in PROOFS:
        source = ROOT / path
        original = source.read_bytes()
        text = original.decode()
        if text.count(needle) != 1:
            raise RuntimeError('Guard removal must match exactly once: ' + label)
        sha = hashlib.sha256(original).hexdigest()
        record = {'guard': label, 'source': path, 'sourceSha256': sha,
                  'test': class_name + '.' + method}
        results.append(record)
        try:
            source.write_text(text.replace(needle, replacement))
            code, failed, _ = run_test(label + '-red', class_name, method)
            record.update(redExitCode=code, namedTestFailed=failed)
            if code == 0 or not failed:
                raise RuntimeError('Named guarding test did not go red: ' + label)
        finally:
            source.write_bytes(original)
            record['restoredSha256'] = hashlib.sha256(source.read_bytes()).hexdigest()
            if record['restoredSha256'] != sha:
                raise RuntimeError('Source restoration mismatch: ' + label)
        code, _, passed = run_test(label + '-green', class_name, method)
        record.update(greenExitCode=code, namedTestPassed=passed)
        if code != 0 or not passed:
            raise RuntimeError('Restored guarding test did not pass: ' + label)
finally:
    (OUT / 'guard-proofs.json').write_text(json.dumps(results, indent=2) + '\n')
