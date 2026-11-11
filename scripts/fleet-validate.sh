#!/usr/bin/env bash
# The fleet job succeeds when evidence was collected. summary.json.passed is the
# validation verdict; a failing build remains a failing candidate, with its logs exported.
set -uo pipefail
mkdir -p build/validation
exec > >(tee build/validation/build.log) 2>&1
bash scripts/validate-candidate.sh
STASH_VALIDATION_EXIT=$?
python3 scripts/package-validation.py "$STASH_VALIDATION_EXIT"
