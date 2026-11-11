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
    ('complete-construction', 'src/main/java/com/stashledger/StashLedgerPlugin.java',
     'if (!saveHealthy || !constructionReady)', 'if (!saveHealthy)',
     'com.stashledger.StashLedgerPluginTest', 'incompleteConstructionCannotReportContentsSyncedOrReviveStaleContents'),
    ('map-account-epoch', 'src/main/java/com/stashledger/StashLedgerPlugin.java',
     ' || mapEpoch != accountEpoch', '',
     'com.stashledger.StashLedgerPluginTest', 'staleMapCallbackCannotCrossAccountEpoch'),
    ('watson-parentheses', 'src/main/java/com/stashledger/tracking/WatsonBoardParser.java',
     '.replaceAll("[^\\\\p{L}\\\\p{N}\\\\s]", "")', '.replace("\'", "").replace("’", "").replace("-", "").replace("_", "")',
     'com.stashledger.CatalogWatsonIntegrationTest', 'parsesAll119WithActualParenthesizedBoardNamesAndTierCollisions'),
    ('account-epoch', 'src/main/java/com/stashledger/StashLedgerPlugin.java',
     ' || expectedEpoch != accountEpoch', '',
     'com.stashledger.StashLedgerPluginTest', 'oldRenderedSidebarCannotMutateANewerSession'),
    ('built-contents-guard', 'src/main/java/com/stashledger/state/LedgerState.java',
     'status == null || builds.get(id) != BuildStatus.BUILT', 'status == null',
     'com.stashledger.state.LedgerStateTest', 'onlyBuiltUnitsAcceptContentsConfirmation'),
    ('detail-width', 'src/main/java/com/stashledger/ui/StashLedgerPanel.java',
     'return new JLabel("<html><div style=\'width:120pt\'>" + escapeHtml(text) + "</div></html>");',
     'return new JLabel(text);',
     'com.stashledger.ui.StashLedgerPanelTest', 'longDetailMaterialsAndClueRequirementsFitAtSidebarWidth'),
    ('hide-filled', 'src/main/java/com/stashledger/ui/StashLedgerPanel.java',
     '.filter(entry -> !hideFilled || entry.getContentsStatus() != ContentsStatus.FILLED)', '',
     'com.stashledger.ui.StashLedgerPanelTest', 'hideFilledComposesWithShortlistBuildTierAndSearchFilters'),
    ('map-hide-filled', 'src/main/java/com/stashledger/map/StashMapMarkers.java',
     'if (hideFilled && entry.getContentsStatus() == ContentsStatus.FILLED)', 'if (false)',
     'com.stashledger.map.StashMapMarkersTest', 'hidesOnlyConfirmedFilledAndKeepsUnknown'),
    ('interaction-confirmation', 'src/main/java/com/stashledger/tracking/StashInteractionTracker.java',
     'if (DEPOSIT_SUCCESS_MESSAGE.equals(message))', 'if (false)',
     'com.stashledger.tracking.StashInteractionTrackerTest', 'exactDepositAfterMatchingSearchProducesOneFilledUpdate'),
    ('interaction-proximity', 'src/main/java/com/stashledger/StashLedgerPlugin.java',
     ' || !nearInteractedStash(definition)', '',
     'com.stashledger.StashLedgerPluginTest', 'contentsConfirmationCannotCrossAccountOrProximityBoundary'),
    ('visual-account', 'src/main/java/com/stashledger/StashLedgerPlugin.java',
     'boolean loggedIn = profile != null && client.getGameState() == GameState.LOGGED_IN\n            && Objects.equals(profile, configManager.getRSProfileKey());',
     'boolean loggedIn = profile != null && client.getGameState() == GameState.LOGGED_IN;',
     'com.stashledger.StashLedgerPluginTest', 'overviewCannotExposeStateToAnotherActiveProfile'),
    ('highlight-filled', 'src/main/java/com/stashledger/items/NeededItemProvider.java',
     'entry == null || isComplete(entry)', 'entry == null',
     'com.stashledger.items.NeededItemProviderTest', 'keepsCandidatesForUnknownUnbuiltAndBuiltUnfilledButExcludesBuiltFilled'),
    ('highlight-clear', 'src/main/java/com/stashledger/StashLedgerPlugin.java',
     'else { neededItems.clear(); }', 'else { /* source-only negative proof */ }',
     'com.stashledger.StashLedgerPluginTest', 'itemHighlightsClearAcrossProfilesAndWhenDisabled'),
    ('highlight-empty-snapshot', 'src/main/java/com/stashledger/items/NeededItemProvider.java',
     '!snapshot.isEmpty() && ', '',
     'com.stashledger.items.NeededItemProviderTest', 'doesNotCanonicalizeAfterAccountStateIsCleared'),
    ('source-item-exclusions', 'src/main/java/com/stashledger/data/GeneratedStashItemRequirements.java',
     'if (!excluded.contains(itemId))', 'if (true)',
     'com.stashledger.data.StashItemRequirementsTest', 'preservesSourceDeclaredVariationExclusionsAndPositiveGroups'),
    ('font-safe-display', 'src/main/java/com/stashledger/ui/StashLedgerPanel.java',
     'return displayText(value).replace("&", "&amp;")', 'return value.replace("&", "&amp;")',
     'com.stashledger.ui.StashLedgerPanelTest', 'unicodeStatusMessageIsRenderedAndTooledWithFontSafeText'),
    ('catalog-all-locations', 'src/main/java/com/stashledger/data/GeneratedStashDefinitions.java',
     'Arrays.asList(points)', 'List.of(points[0])',
     'com.stashledger.data.StashCatalogTest', 'preservesEveryPinnedRuntimeWorldPoint'),
    ('tracking-all-locations', 'src/main/java/com/stashledger/tracking/StashInteractionTracker.java',
     'definition.getWorldPoints().stream().noneMatch(', 'definition.getWorldPoints().stream().limit(1).noneMatch(',
     'com.stashledger.tracking.StashInteractionTrackerTest', 'matchesAlternateCoordinatesWithoutAcceptingWrongPlaneOrObject'),
    ('proximity-all-locations', 'src/main/java/com/stashledger/StashLedgerPlugin.java',
     'definition.getWorldPoints().stream().anyMatch(stash ->', 'definition.getWorldPoints().stream().limit(1).anyMatch(stash ->',
     'com.stashledger.StashLedgerPluginTest', 'alternateLocationsConfirmTheSameLogicalStash'),
    ('overview-all-locations', 'src/main/java/com/stashledger/map/StashMapMarkers.java',
     'for (WorldPoint location : definition.getWorldPoints())', 'for (WorldPoint location : definition.getWorldPoints().subList(0, 1))',
     'com.stashledger.map.StashMapMarkersTest', 'everyPhysicalLocationSharesOneUnitsStateAndLifecycle'),
    ('selected-all-locations', 'src/main/java/com/stashledger/StashLedgerPlugin.java',
     'for (WorldPoint location : definition.getWorldPoints())', 'for (WorldPoint location : definition.getWorldPoints().subList(0, 1))',
     'com.stashledger.StashLedgerPluginTest', 'selectedMapActionOwnsAndClearsEveryLocation'),
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
