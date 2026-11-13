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
    ('watson-pandemonium-live-label', 'src/main/java/com/stashledger/data/GeneratedStashDefinitions.java',
     '.put("the bar on the pandemonium", "PANDEMONIUM_BAR")', '.put("pandemonium bar", "PANDEMONIUM_BAR")',
     'com.stashledger.CatalogWatsonIntegrationTest', 'parsesAll119AgainstPinnedNoticeboardLabelFixture'),
    ('watson-line-breaks', 'src/main/java/com/stashledger/tracking/WatsonBoardParser.java', 'value.replaceAll("(?i)<br\\\\s*/?>", " ")', 'value', 'com.stashledger.tracking.WatsonBoardParserTest', 'normalizesKnownLineBreakMarkupWithoutJoiningLocationWords'),
    ('watson-no-partial-commit', 'src/main/java/com/stashledger/tracking/WatsonBoardParser.java', 'return failure(tierValues.failure, tier, tierValues.diagnostic);', 'return new Result(Failure.NONE, null, "source-only incomplete proof", Collections.unmodifiableMap(parsed));', 'com.stashledger.StashLedgerPluginTest', 'unmatchedNoticeboardRowCannotPartiallyReplaceSavedContents'),
    ('watson-bounded-diagnostic', 'src/main/java/com/stashledger/tracking/WatsonBoardParser.java', 'bounded(row.location)', 'row.location', 'com.stashledger.tracking.WatsonBoardParserTest', 'detailedUnknownDiagnosticIsSanitizedAndBounded'),
    ('watson-retry-detail', 'src/main/java/com/stashledger/StashLedgerPlugin.java', ': latestBoardDiagnostic + " Saved contents are unchanged.";', ': "Noticeboard could not be matched completely. Saved contents are unchanged.";', 'com.stashledger.StashLedgerPluginTest', 'finalNoticeboardRetryExplainsTheFailureWithoutChangingSavedContents'),
    ('watson-new-board-detail', 'src/main/java/com/stashledger/StashLedgerPlugin.java', 'boardAttempts = 0;\n            latestBoardDiagnostic = null;\n            requestScan();', 'boardAttempts = 0;\n            requestScan();', 'com.stashledger.StashLedgerPluginTest', 'noticeboardDiagnosticsClearForANewBoardAndAccount'),
    ('watson-save-detail', 'src/main/java/com/stashledger/StashLedgerPlugin.java', '"Contents sync is disabled because saved data could not be read."', '"Noticeboard could not be matched completely."', 'com.stashledger.StashLedgerPluginTest', 'finalNoticeboardRetryReportsUnreadableSaveInsteadOfAStaleBoardFailure'),
    ('watson-construction-detail', 'src/main/java/com/stashledger/StashLedgerPlugin.java', '"Construction scan is incomplete. Use Refresh to try again."', '"Noticeboard could not be matched completely."', 'com.stashledger.StashLedgerPluginTest', 'finalNoticeboardRetryReportsConstructionScanInsteadOfAStaleBoardFailure'),
    ('dot-tier-mode', 'src/main/java/com/stashledger/items/StashNeededItemOverlay.java',
     'if (config.dotColorMode() != DotColorMode.CLUE_TIER || tiers.isEmpty())', 'if (true)',
     'com.stashledger.items.StashNeededItemOverlayTest', 'tierDotsUseOneDistinctConfiguredColorPerTierInEnumOrder'),
    ('dot-single-mode', 'src/main/java/com/stashledger/items/StashNeededItemOverlay.java',
     'if (config.dotColorMode() != DotColorMode.CLUE_TIER || tiers.isEmpty())', 'if (tiers.isEmpty())',
     'com.stashledger.items.StashNeededItemOverlayTest', 'singleColorModeShowsOneConfiguredDotForAllRelevantTiers'),
    ('dot-live-color', 'src/main/java/com/stashledger/items/StashNeededItemOverlay.java',
     'case EASY: return configuredColor(config.easyDotColor());', 'case EASY: return PLANNER_COLOR;',
     'com.stashledger.items.StashNeededItemOverlayTest', 'tierDotColorsReadTheCurrentConfigurationOnEveryRender'),
    ('dot-all-tiers', 'src/main/java/com/stashledger/items/StashNeededItemOverlay.java',
     'for (int index = 0; index < tiers.size(); index++)', 'for (int index = 0; index < Math.min(1, tiers.size()); index++)',
     'com.stashledger.items.StashNeededItemOverlayTest', 'sixTierDotsFitWithinTheItemBoundsWithoutSharingPixels'),
    ('dot-tier-metadata', 'src/main/java/com/stashledger/items/NeededItemDetail.java',
     'found.add(stash.getDefinition().getTier());', '',
     'com.stashledger.items.NeededItemDetailTest', 'tiersAreDistinctImmutableAndAlwaysUseClueTierOrder'),
    ('dot-tier-tooltip', 'src/main/java/com/stashledger/items/StashNeededItemOverlay.java',
     'if (!detail.getTiers().isEmpty())', 'if (false)',
     'com.stashledger.items.StashNeededItemOverlayTest', 'tooltipListsAllDotTiersEvenWhenLocationsAreCapped'),
    ('watson-board-labels', 'src/main/java/com/stashledger/data/GeneratedStashDefinitions.java',
     '.put("centre of canifis", "CENTRE_OF_CANIFIS")', '.put("canifis", "CENTRE_OF_CANIFIS")',
     'com.stashledger.CatalogWatsonIntegrationTest', 'parsesAll119AgainstPinnedNoticeboardLabelFixture'),
    ('fill-sidebar-action', 'src/main/java/com/stashledger/ui/StashLedgerPanel.java',
     'else if (eligible) actions.fillStash(id);', 'else if (eligible) { /* source-only negative proof */ }',
     'com.stashledger.ui.StashLedgerPanelTest', 'fillThisStashSelectsOnlyAConfirmedBuiltEmptyCard'),
    ('fill-persistent-banner', 'src/main/java/com/stashledger/ui/StashLedgerPanel.java',
     'fillBanner.setVisible(active);', 'fillBanner.setVisible(false);',
     'com.stashledger.ui.StashLedgerPanelTest', 'activeFillFocusShowsPersistentBannerAndClearCallbackWhenCardIsFilteredOut'),
    ('highlight-deduplicate', 'src/main/java/com/stashledger/items/NeededItemProvider.java',
     '.put(entry.getDefinition().getId(), entry);', '.put(String.valueOf(requirementId), entry);',
     'com.stashledger.items.NeededItemProviderTest', 'canonicalRequirementVariantsListEachDestinationOnlyOnce'),
    ('highlight-one-focus', 'src/main/java/com/stashledger/items/NeededItemProvider.java',
     'fillStashId.equals(entry.getDefinition().getId())', 'true',
     'com.stashledger.items.NeededItemProviderTest', 'focusedPlanIncludesOnlyOneBuiltEmptyStashAndItsImmutableDetails'),
    ('outline-visible', 'src/main/java/com/stashledger/items/StashNeededItemOverlay.java',
     'graphics.drawImage(outline, bounds.x, bounds.y, null);', '',
     'com.stashledger.items.StashNeededItemOverlayTest', 'outlineUsesOriginalWidgetIdAndQuantity'),
    ('hover-menu', 'src/main/java/com/stashledger/items/StashNeededItemOverlay.java',
     ' || client.isMenuOpen()', '',
     'com.stashledger.items.StashNeededItemOverlayTest', 'hoverAddsTooltipOnlyForCandidateUnderMouseWhenMenusAreClosed'),
    ('hover-drag', 'src/main/java/com/stashledger/items/StashNeededItemOverlay.java',
     ' || client.isDraggingWidget()', '',
     'com.stashledger.items.StashNeededItemOverlayTest', 'hoverRespectsPointerDraggingDisabledDetailsAndUnrelatedItems'),
    ('hover-bounded-locations', 'src/main/java/com/stashledger/items/StashNeededItemOverlay.java',
     'Math.min(6, stashes.size())', 'stashes.size()',
     'com.stashledger.items.StashNeededItemOverlayTest', 'globalTooltipExplainsUncertaintyAndBoundsSharedDestinations'),
    ('fill-eligibility', 'src/main/java/com/stashledger/StashLedgerPlugin.java',
     'if (!canFillStash(id, state.snapshot())) { return; }', '',
     'com.stashledger.StashLedgerPluginTest', 'fillModeRejectsUnknownUnbuiltFilledAndStaleEligibility'),
    ('fill-invalidation', 'src/main/java/com/stashledger/StashLedgerPlugin.java',
     'if (fillStashId != null && (!loggedIn || !canFillStash(fillStashId, snapshot)))', 'if (false)',
     'com.stashledger.StashLedgerPluginTest', 'fillModeEndsWhenSelectedStashIsFilledOrNoLongerKnownBuilt'),
    ('fill-focus-wiring', 'src/main/java/com/stashledger/StashLedgerPlugin.java',
     'neededItems.refresh(snapshot, fillStashId)', 'neededItems.refresh(snapshot, null)',
     'com.stashledger.StashLedgerPluginTest', 'fillModeRestrictsHighlightsWithoutChangingContentsAndCanBeStopped'),
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
     'entry == null || (focused ? !isFillableFocus(entry, fillStashId) : isComplete(entry))',
     'entry == null || (focused && !isFillableFocus(entry, fillStashId))',
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
    ('shutdown-client-serialization', 'src/main/java/com/stashledger/StashLedgerPlugin.java',
     'clientThread.invokeLater(this::clearClientState);', 'clearClientState();',
     'com.stashledger.StashLedgerPluginTest', 'shutdownDuringRefreshCannotLeaveOverviewMarkersBehind'),
    ('restart-client-serialization', 'src/main/java/com/stashledger/StashLedgerPlugin.java',
     'clientThread.invokeLater(() -> initializeClientState(currentLifecycle));', 'initializeClientState(currentLifecycle);',
     'com.stashledger.StashLedgerPluginTest', 'rapidRestartDoesNotClearTheNewSessionsOverlay'),
    ('stop-during-initialization', 'src/main/java/com/stashledger/StashLedgerPlugin.java',
     'if (currentLifecycle != lifecycle) { running = false; return; }', '',
     'com.stashledger.StashLedgerPluginTest', 'stopDuringInitializationCannotReactivateThePlugin'),
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
