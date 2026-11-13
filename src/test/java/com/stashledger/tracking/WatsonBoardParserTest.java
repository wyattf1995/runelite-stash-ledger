package com.stashledger.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.stashledger.model.StashDefinition;
import com.stashledger.model.StashTier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;

public class WatsonBoardParserTest
{
    @Test
    public void acceptsCompleteSixTierBoardWithTwoOneAndZeroTicks()
    {
        List<StashDefinition> definitions = definitions();
        Map<StashTier, List<WatsonBoardParser.Cell>> board = completeBoard(definitions);

        Optional<Map<String, Boolean>> result = WatsonBoardParser.parse(board, aliases(definitions), definitions);

        assertTrue(result.isPresent());
        assertTrue(result.get().get("beginner"));
        assertFalse(result.get().get("easy"));
        assertFalse(result.get().get("medium"));
        assertTrue(result.get().get("hard"));
    }

    @Test
    public void rejectsUnknownDuplicatePartialAndMalformedBoardRows()
    {
        List<StashDefinition> definitions = definitions();
        Map<StashTier, List<WatsonBoardParser.Cell>> unknown = completeBoard(definitions);
        unknown.put(StashTier.BEGINNER, Collections.singletonList(cell(4, "not in catalog")));
        assertFalse(WatsonBoardParser.parse(unknown, aliases(definitions), definitions).isPresent());

        Map<StashTier, List<WatsonBoardParser.Cell>> duplicate = completeBoard(definitions);
        duplicate.put(StashTier.BEGINNER, Arrays.asList(cell(4, "Beginner place"), cell(4, "Beginner place")));
        assertFalse(WatsonBoardParser.parse(duplicate, aliases(definitions), definitions).isPresent());

        Map<StashTier, List<WatsonBoardParser.Cell>> partial = completeBoard(definitions);
        partial.remove(StashTier.MASTER);
        assertFalse(WatsonBoardParser.parse(partial, aliases(definitions), definitions).isPresent());

        Map<StashTier, List<WatsonBoardParser.Cell>> malformed = completeBoard(definitions);
        malformed.put(StashTier.ELITE, Arrays.asList(cell(4, "Elite place"), cell(5, null), cell(5, null), cell(5, null)));
        assertFalse(WatsonBoardParser.parse(malformed, aliases(definitions), definitions).isPresent());
    }

    @Test
    public void normalizesPresentationTagsPunctuationAndWhitespace()
    {
        assertEquals("warriors guild bank", WatsonBoardParser.normalize(" <col=ff00ff>Warriors’ Guild - bank</col> "));
        assertEquals("warriorsguild", WatsonBoardParser.normalize("Warriors’_Guild"));
    }

    @Test
    public void normalizesKnownLineBreakMarkupWithoutJoiningLocationWords()
    {
        assertEquals("centre of canifis", WatsonBoardParser.normalize("Centre of<br>Canifis"));
        assertEquals("centre of canifis", WatsonBoardParser.normalize("Centre of<BR />Canifis"));
        assertEquals("centre of canifis", WatsonBoardParser.normalize("<col=ff00ff>Centre of</col><br/>Canifis"));
        assertEquals("centre ofcanifis", WatsonBoardParser.normalize("Centre of<col=ff00ff>Canifis</col>"));
    }

    @Test
    public void detailedParsePreservesLegacySuccessAndReturnsAnImmutableSnapshot()
    {
        List<StashDefinition> definitions = definitions();
        Map<StashTier, List<WatsonBoardParser.Cell>> board = completeBoard(definitions);

        WatsonBoardParser.Result detailed = WatsonBoardParser.parseDetailed(board, aliases(definitions), definitions);

        assertEquals(WatsonBoardParser.Failure.NONE, detailed.getFailure());
        assertNull(detailed.getTier());
        assertTrue(detailed.getContents().isPresent());
        assertEquals(WatsonBoardParser.parse(board, aliases(definitions), definitions), detailed.getContents());
        try
        {
            detailed.getContents().get().put("new", true);
            throw new AssertionError("Successful snapshots must be immutable");
        }
        catch (UnsupportedOperationException expected)
        {
            // The parser owns the immutable complete snapshot used by the account state update.
        }
    }

    @Test
    public void detailedParseKeepsFailuresFailClosedAndTierScoped()
    {
        List<StashDefinition> definitions = definitions();

        Map<StashTier, List<WatsonBoardParser.Cell>> unknown = completeBoard(definitions);
        unknown.put(StashTier.BEGINNER, Collections.singletonList(cell(4, "not in catalog")));
        assertFailure(unknown, definitions, WatsonBoardParser.Failure.UNKNOWN_LOCATION, StashTier.BEGINNER);

        Map<StashTier, List<WatsonBoardParser.Cell>> duplicate = completeBoard(definitions);
        duplicate.put(StashTier.EASY, Arrays.asList(cell(4, "Easy place"), cell(4, "Easy place")));
        assertFailure(duplicate, definitions, WatsonBoardParser.Failure.DUPLICATE_LOCATION, StashTier.EASY);

        Map<StashTier, List<WatsonBoardParser.Cell>> malformed = completeBoard(definitions);
        malformed.put(StashTier.ELITE, Arrays.asList(cell(4, "Elite place"), cell(5, null), cell(5, null), cell(5, null)));
        assertFailure(malformed, definitions, WatsonBoardParser.Failure.MALFORMED_ROW, StashTier.ELITE);

        Map<StashTier, List<WatsonBoardParser.Cell>> missing = completeBoard(definitions);
        missing.remove(StashTier.MASTER);
        assertFailure(missing, definitions, WatsonBoardParser.Failure.MISSING_TIER, StashTier.MASTER);

        List<StashDefinition> incompleteDefinitions = new ArrayList<>(definitions);
        incompleteDefinitions.add(new StashDefinition("beginner-second", 99, "Beginner second", StashTier.BEGINNER,
            0, 0, 0, Collections.emptyList()));
        Map<StashTier, Map<String, String>> incompleteAliases = aliases(incompleteDefinitions);
        Map<StashTier, List<WatsonBoardParser.Cell>> incomplete = completeBoard(definitions);
        WatsonBoardParser.Result result = WatsonBoardParser.parseDetailed(incomplete, incompleteAliases, incompleteDefinitions);
        assertEquals(WatsonBoardParser.Failure.INCOMPLETE_TIER, result.getFailure());
        assertEquals(StashTier.BEGINNER, result.getTier());
        assertFalse(result.getContents().isPresent());
        assertTrue(result.getDiagnostic().toLowerCase(Locale.ROOT).contains("beginner"));
        assertTrue(result.getDiagnostic().contains("1"));
        assertTrue(result.getDiagnostic().contains("2"));
    }

    @Test
    public void detailedUnknownDiagnosticIsSanitizedAndBounded()
    {
        List<StashDefinition> definitions = definitions();
        String untrusted = "<col=ff00ff>Private\n" + String.join("", Collections.nCopies(200, "x")) + "</col>";
        Map<StashTier, List<WatsonBoardParser.Cell>> board = completeBoard(definitions);
        board.put(StashTier.HARD, Collections.singletonList(cell(4, untrusted)));

        WatsonBoardParser.Result result = WatsonBoardParser.parseDetailed(board, aliases(definitions), definitions);

        assertEquals(WatsonBoardParser.Failure.UNKNOWN_LOCATION, result.getFailure());
        assertEquals(StashTier.HARD, result.getTier());
        assertFalse(result.getContents().isPresent());
        assertFalse(result.getDiagnostic().contains("<col="));
        assertFalse(result.getDiagnostic().contains("\n"));
        assertTrue("Diagnostic must not echo an unbounded widget string", result.getDiagnostic().length() <= 200);
    }

    private static void assertFailure(Map<StashTier, List<WatsonBoardParser.Cell>> board,
        List<StashDefinition> definitions, WatsonBoardParser.Failure expected, StashTier tier)
    {
        WatsonBoardParser.Result result = WatsonBoardParser.parseDetailed(board, aliases(definitions), definitions);
        assertEquals(expected, result.getFailure());
        assertEquals(tier, result.getTier());
        assertFalse(result.getContents().isPresent());
        assertTrue("Diagnostics should explain the failing tier",
            result.getDiagnostic().toLowerCase(Locale.ROOT).contains(tier.name().toLowerCase(Locale.ROOT)));
    }

    private static Map<StashTier, List<WatsonBoardParser.Cell>> completeBoard(List<StashDefinition> definitions)
    {
        Map<StashTier, List<WatsonBoardParser.Cell>> board = new EnumMap<>(StashTier.class);
        for (StashDefinition definition : definitions)
        {
            List<WatsonBoardParser.Cell> cells = new ArrayList<>();
            cells.add(cell(4, definition.getName()));
            if (definition.getTier() == StashTier.BEGINNER || definition.getTier() == StashTier.HARD)
            {
                cells.add(cell(5, null));
                cells.add(cell(5, null));
            }
            else if (definition.getTier() == StashTier.EASY)
            {
                cells.add(cell(5, null));
            }
            board.put(definition.getTier(), cells);
        }
        return board;
    }

    private static Map<StashTier, Map<String, String>> aliases(List<StashDefinition> definitions)
    {
        Map<StashTier, Map<String, String>> result = new EnumMap<>(StashTier.class);
        for (StashDefinition definition : definitions)
        {
            result.computeIfAbsent(definition.getTier(), ignored -> new java.util.LinkedHashMap<>())
                .put(definition.getName(), definition.getId());
        }
        return result;
    }

    private static List<StashDefinition> definitions()
    {
        List<StashDefinition> definitions = new ArrayList<>();
        for (StashTier tier : StashTier.values())
        {
            String id = tier.name().toLowerCase();
            definitions.add(new StashDefinition(id, tier.ordinal() + 1, title(tier) + " place", tier, 0, 0, 0, Collections.emptyList()));
        }
        return definitions;
    }

    private static String title(StashTier tier)
    {
        return tier.name().substring(0, 1) + tier.name().substring(1).toLowerCase();
    }

    private static WatsonBoardParser.Cell cell(int type, String text)
    {
        return new WatsonBoardParser.Cell(type, text);
    }
}
