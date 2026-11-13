#!/usr/bin/env python3
"""Collect real outputs even if validation fails; never fabricate missing artifacts."""
import hashlib
import json
from pathlib import Path
import sys
import zipfile

root = Path(__file__).resolve().parents[1]
out = root / 'build/validation'
code = int(sys.argv[1])
summary_path = out / 'summary.json'
summary = json.loads(summary_path.read_text()) if summary_path.exists() else {}
summary.update(passed=code == 0, validationExitCode=code)
summary['artifacts'] = {str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest()
                        for p in (root / 'build/libs').glob('*.jar')}
if code == 0:
    required = ['build/libs/stash-ledger-0.1.3.jar', 'build/libs/stash-ledger-0.1.3-all.jar',
                'build/ui/stash-ledger.png', 'build/validation/guard-proofs.json']
    summary['passed'] = all((root / p).is_file() for p in required)
    if not summary['passed']:
        summary['collectionError'] = 'A required successful-build artifact is missing'
summary_path.write_text(json.dumps(summary, indent=2) + '\n')
bundle = out / 'validation-bundle.zip'
with zipfile.ZipFile(bundle, 'w', zipfile.ZIP_STORED) as archive:
    for pattern in ['build/libs/*.jar', 'build/reports/tests/test/**/*',
                    'build/test-results/test/*.xml', 'build/ui/*.png',
                    'build/validation/*.json', 'build/validation/*.log']:
        for path in sorted(root.glob(pattern)):
            if path.is_file(): archive.write(path, str(path.relative_to(root)))
print('Validation verdict: ' + ('PASS' if summary['passed'] else 'FAIL'))
print(json.dumps(summary, sort_keys=True))
