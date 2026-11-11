package com.stashledger.data;

import com.stashledger.model.StashDefinition;
import com.stashledger.model.StashTier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable, client-independent view of the current RuneLite STASH catalog. */
public final class StashCatalog
{
    private final List<StashDefinition> definitions;
    private final Map<String, StashDefinition> byId;
    private final Map<StashTier, Map<String, String>> watsonAliases;
    private final Map<String, Set<Integer>> itemRequirementIds;

    public StashCatalog()
    {
        definitions = GeneratedStashDefinitions.load();
        Map<String, StashDefinition> index = new HashMap<>();
        for (StashDefinition definition : definitions)
        {
            StashDefinition previous = index.put(definition.getId(), definition);
            if (previous != null)
            {
                throw new IllegalStateException("Duplicate STASH definition: " + definition.getId());
            }
        }
        byId = Map.copyOf(index);
        watsonAliases = GeneratedStashDefinitions.watsonAliases();
        itemRequirementIds = GeneratedStashItemRequirements.load();
        if (!itemRequirementIds.keySet().equals(byId.keySet()))
        {
            throw new IllegalStateException("STASH item requirements do not match the catalog");
        }
    }

    public List<StashDefinition> getDefinitions()
    {
        return definitions;
    }

    public StashDefinition getById(String id)
    {
        return byId.get(id);
    }

    /**
     * Maps normalized noticeboard location text to catalog IDs. Callers should
     * remove tags and punctuation, lowercase, and collapse whitespace before lookup.
     * The tier keeps otherwise-colliding locations (such as Warriors' Guild) distinct.
     */
    public Map<String, String> getWatsonAliases(StashTier tier)
    {
        return watsonAliases.getOrDefault(tier, Map.of());
    }

    /**
     * Exact source-declared equipment candidate IDs, keyed by STASH ID.
     * These are for planner highlighting only; they do not prove a player has
     * completed a composite or alternative equipment requirement.
     */
    public Map<String, Set<Integer>> getItemRequirementIds()
    {
        return itemRequirementIds;
    }
}
