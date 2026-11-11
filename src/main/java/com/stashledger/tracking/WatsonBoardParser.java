package com.stashledger.tracking;

import com.stashledger.model.StashDefinition;
import com.stashledger.model.StashTier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Parses a fully captured Watson board without changing any account state.
 *
 * <p>Its input adapter owns RuneLite widget access. This parser only accepts a
 * complete, tier-scoped snapshot; callers can therefore leave previous content
 * state intact when the board changed shape or has not finished loading.</p>
 */
public final class WatsonBoardParser
{
    public static final class Cell
    {
        private final int type;
        private final String text;

        public Cell(int type, String text)
        {
            this.type = type;
            this.text = text;
        }

        public int getType()
        {
            return type;
        }

        public String getText()
        {
            return text;
        }
    }

    private WatsonBoardParser()
    {
    }

    public static Optional<Map<String, Boolean>> parse(
        Map<StashTier, List<Cell>> tiers,
        Map<StashTier, Map<String, String>> aliases,
        List<StashDefinition> definitions)
    {
        if (tiers == null || aliases == null || definitions == null)
        {
            return Optional.empty();
        }

        Map<StashTier, Set<String>> idsByTier = idsByTier(definitions);
        if (idsByTier == null)
        {
            return Optional.empty();
        }

        Map<String, Boolean> parsed = new LinkedHashMap<>();
        for (StashTier tier : StashTier.values())
        {
            List<Cell> cells = tiers.get(tier);
            Map<String, String> tierAliases = normalizedAliases(aliases.get(tier), idsByTier.get(tier));
            if (cells == null || tierAliases == null)
            {
                return Optional.empty();
            }

            Optional<Map<String, Boolean>> tierValues = parseTier(cells, tierAliases);
            if (!tierValues.isPresent() || tierValues.get().size() != idsByTier.get(tier).size()
                || !tierValues.get().keySet().equals(idsByTier.get(tier)))
            {
                return Optional.empty();
            }
            parsed.putAll(tierValues.get());
        }

        return parsed.size() == definitions.size()
            ? Optional.of(Collections.unmodifiableMap(parsed))
            : Optional.empty();
    }

    /** Normalizes only presentation differences seen in noticeboard labels. */
    public static String normalize(String value)
    {
        if (value == null)
        {
            return "";
        }
        return value.replaceAll("<[^>]*>", "")
            .replace("'", "")
            .replace("’", "")
            .replace("-", "")
            .replace("_", "")
            .trim()
            .replaceAll("\\s+", " ")
            .toLowerCase(Locale.ROOT);
    }

    private static Map<StashTier, Set<String>> idsByTier(List<StashDefinition> definitions)
    {
        Map<StashTier, Set<String>> result = new EnumMap<>(StashTier.class);
        for (StashTier tier : StashTier.values())
        {
            result.put(tier, new HashSet<>());
        }
        Set<String> allIds = new HashSet<>();
        for (StashDefinition definition : definitions)
        {
            if (definition == null || !allIds.add(definition.getId())
                || !result.get(definition.getTier()).add(definition.getId()))
            {
                return null;
            }
        }
        return result;
    }

    private static Map<String, String> normalizedAliases(Map<String, String> aliases, Set<String> validIds)
    {
        if (aliases == null)
        {
            return null;
        }
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, String> entry : aliases.entrySet())
        {
            String normalized = normalize(entry.getKey());
            if (normalized.isEmpty() || entry.getValue() == null || !validIds.contains(entry.getValue())
                || result.put(normalized, entry.getValue()) != null)
            {
                return null;
            }
        }
        return result;
    }

    private static Optional<Map<String, Boolean>> parseTier(List<Cell> cells, Map<String, String> aliases)
    {
        List<BoardRow> rows = new ArrayList<>();
        String location = null;
        int checks = 0;
        for (Cell cell : cells)
        {
            if (cell == null || (cell.getType() != 4 && cell.getType() != 5))
            {
                return Optional.empty();
            }
            if (cell.getType() == 4)
            {
                if (location != null)
                {
                    rows.add(new BoardRow(location, checks));
                }
                location = normalize(cell.getText());
                if (location.isEmpty())
                {
                    return Optional.empty();
                }
                checks = 0;
            }
            else
            {
                if (location == null || ++checks > 2)
                {
                    return Optional.empty();
                }
            }
        }
        if (location == null)
        {
            return Optional.empty();
        }
        rows.add(new BoardRow(location, checks));

        Map<String, Boolean> result = new LinkedHashMap<>();
        for (BoardRow row : rows)
        {
            String id = aliases.get(row.location);
            if (id == null || result.put(id, row.checks == 2) != null)
            {
                return Optional.empty();
            }
        }
        return Optional.of(result);
    }

    private static final class BoardRow
    {
        private final String location;
        private final int checks;

        private BoardRow(String location, int checks)
        {
            this.location = location;
            this.checks = checks;
        }
    }
}
