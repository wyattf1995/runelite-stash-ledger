#!/usr/bin/env bash
set -euo pipefail
mkdir -p build/validation
exec > >(tee build/validation/build.log) 2>&1
java -version
./gradlew --no-daemon --max-workers=2 test
python3 scripts/prove-guards.py
./gradlew --no-daemon --max-workers=2 cleanTest test jar shadowJar
python3 - <<'PY'
import hashlib, json, pathlib, zipfile, xml.etree.ElementTree as ET
root = pathlib.Path('.')
reports = list(root.glob('build/test-results/test/TEST-*.xml'))
summary = {'tests': 0, 'failures': 0, 'errors': 0, 'skipped': 0}
for report in reports:
    node = ET.parse(report).getroot()
    for key in summary: summary[key] += int(node.attrib.get(key, 0))
assert summary['tests'] > 0 and summary['failures'] == summary['errors'] == summary['skipped'] == 0, summary
summary['artifacts'] = {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in root.glob('build/libs/*.jar')}
(root/'build/validation/summary.json').write_text(json.dumps(summary, indent=2)+'\n')
with zipfile.ZipFile('build/validation/test-evidence.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
    for pattern in ['build/reports/tests/test/**/*', 'build/test-results/test/*.xml', 'build/ui/*.png', 'build/validation/*.json', 'build/validation/*-red.log', 'build/validation/*-green.log']:
        for p in root.glob(pattern):
            if p.is_file(): archive.write(p, str(p))
print(json.dumps(summary))
PY
