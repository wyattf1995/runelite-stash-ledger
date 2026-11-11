package com.stashledger.data;

import com.stashledger.model.StashDefinition;
import com.stashledger.model.StashTier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Immutable, client-independent view of the current RuneLite STASH catalog. */
public final class StashCatalog
{
    private final List<StashDefinition> definitions;
    private final Map<String, StashDefinition> byId;
    private final Map<StashTier, Map<String, String>> watsonAliases;

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
}
