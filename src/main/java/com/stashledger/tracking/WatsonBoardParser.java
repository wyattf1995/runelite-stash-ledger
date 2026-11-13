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
    public enum Failure
    {
        NONE, INVALID_INPUT, INVALID_CATALOG, MISSING_TIER, INVALID_ALIASES,
        MALFORMED_ROW, UNKNOWN_LOCATION, DUPLICATE_LOCATION, INCOMPLETE_TIER
    }

    public static final class Result
    {
        private final Failure failure;
        private final StashTier tier;
        private final String diagnostic;
        private final Map<String, Boolean> contents;

        private Result(Failure failure, StashTier tier, String diagnostic, Map<String, Boolean> contents)
        {
            this.failure = failure;
            this.tier = tier;
            this.diagnostic = diagnostic;
            this.contents = contents;
        }

        public Failure getFailure() { return failure; }
        public StashTier getTier() { return tier; }
        public String getDiagnostic() { return diagnostic; }
        public Optional<Map<String, Boolean>> getContents() { return Optional.ofNullable(contents); }
    }

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
        return parseDetailed(tiers, aliases, definitions).getContents();
    }

    /** Failed results never expose a partial contents map. */
    public static Result parseDetailed(
        Map<StashTier, List<Cell>> tiers,
        Map<StashTier, Map<String, String>> aliases,
        List<StashDefinition> definitions)
    {
        if (tiers == null || aliases == null || definitions == null)
        {
            return failure(Failure.INVALID_INPUT, null, "Noticeboard data is unavailable.");
        }

        Map<StashTier, Set<String>> idsByTier = idsByTier(definitions);
        if (idsByTier == null)
        {
            return failure(Failure.INVALID_CATALOG, null, "STASH catalog data is invalid.");
        }

        Map<String, Boolean> parsed = new LinkedHashMap<>();
        for (StashTier tier : StashTier.values())
        {
            List<Cell> cells = tiers.get(tier);
            if (cells == null)
            {
                return failure(Failure.MISSING_TIER, tier, tierName(tier) + " tier is not loaded.");
            }
            Map<String, String> tierAliases = normalizedAliases(aliases.get(tier), idsByTier.get(tier));
            if (tierAliases == null)
            {
                return failure(Failure.INVALID_ALIASES, tier, tierName(tier) + " tier aliases are invalid.");
            }

            TierResult tierValues = parseTier(cells, tierAliases, tier);
            if (tierValues.failure != Failure.NONE)
            {
                return failure(tierValues.failure, tier, tierValues.diagnostic);
            }
            if (tierValues.values.size() != idsByTier.get(tier).size()
                || !tierValues.values.keySet().equals(idsByTier.get(tier)))
            {
                return failure(Failure.INCOMPLETE_TIER, tier, tierName(tier) + " tier is incomplete: matched "
                    + tierValues.values.size() + " of " + idsByTier.get(tier).size() + " locations.");
            }
            parsed.putAll(tierValues.values);
        }

        if (parsed.size() != definitions.size())
        {
            return failure(Failure.INVALID_CATALOG, null, "STASH catalog data is inconsistent.");
        }
        return new Result(Failure.NONE, null, "Noticeboard matched.",
            Collections.unmodifiableMap(new LinkedHashMap<>(parsed)));
    }

    /** Normalizes only presentation differences seen in noticeboard labels. */
    public static String normalize(String value)
    {
        if (value == null)
        {
            return "";
        }
        return value.replaceAll("(?i)<br\\s*/?>", " ")
            .replaceAll("<[^>]*>", "")
            // The source board labels include parentheses, e.g. Fishing Guild (Outside).
            // Match the catalog generator's punctuation removal exactly.
            .replaceAll("[^\\p{L}\\p{N}\\s]", "")
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
            if (definition == null || definition.getId() == null || definition.getTier() == null || !allIds.add(definition.getId())
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

    private static TierResult parseTier(List<Cell> cells, Map<String, String> aliases, StashTier tier)
    {
        List<BoardRow> rows = new ArrayList<>();
        String location = null;
        int checks = 0;
        for (Cell cell : cells)
        {
            if (cell == null || (cell.getType() != 4 && cell.getType() != 5))
            {
                return TierResult.failure(Failure.MALFORMED_ROW, tierName(tier) + " tier has an unexpected row.");
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
                    return TierResult.failure(Failure.MALFORMED_ROW, tierName(tier) + " tier has an empty location.");
                }
                checks = 0;
            }
            else
            {
                if (location == null || ++checks > 2)
                {
                    return TierResult.failure(Failure.MALFORMED_ROW, tierName(tier) + " tier has misplaced status markers.");
                }
            }
        }
        if (location == null)
        {
            return TierResult.failure(Failure.MALFORMED_ROW, tierName(tier) + " tier has no locations.");
        }
        rows.add(new BoardRow(location, checks));

        Map<String, Boolean> result = new LinkedHashMap<>();
        for (BoardRow row : rows)
        {
            String id = aliases.get(row.location);
            if (id == null)
            {
                return TierResult.failure(Failure.UNKNOWN_LOCATION, tierName(tier)
                    + " tier has an unknown location: " + bounded(row.location) + ".");
            }
            if (result.put(id, row.checks == 2) != null)
            {
                return TierResult.failure(Failure.DUPLICATE_LOCATION, tierName(tier) + " tier repeats a location.");
            }
        }
        return TierResult.success(result);
    }

    private static Result failure(Failure failure, StashTier tier, String diagnostic)
    {
        return new Result(failure, tier, diagnostic, null);
    }

    private static String bounded(String value)
    {
        return value.length() <= 80 ? value : value.substring(0, 80);
    }

    private static String tierName(StashTier tier)
    {
        String value = tier.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static final class TierResult
    {
        private final Failure failure;
        private final String diagnostic;
        private final Map<String, Boolean> values;

        private TierResult(Failure failure, String diagnostic, Map<String, Boolean> values)
        {
            this.failure = failure;
            this.diagnostic = diagnostic;
            this.values = values;
        }

        private static TierResult success(Map<String, Boolean> values)
        {
            return new TierResult(Failure.NONE, "", values);
        }

        private static TierResult failure(Failure failure, String diagnostic)
        {
            return new TierResult(failure, diagnostic, null);
        }
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
