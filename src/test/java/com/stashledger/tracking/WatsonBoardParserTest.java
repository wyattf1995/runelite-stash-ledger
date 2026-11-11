package com.stashledger.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.stashledger.model.StashDefinition;
import com.stashledger.model.StashTier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
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
        assertEquals("warriors guild bank", WatsonBoardParser.normalize(" <col=ff00ff>Warriors’_Guild - bank</col> "));
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
            result.put(definition.getTier(), Collections.singletonMap(definition.getName(), definition.getId()));
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
