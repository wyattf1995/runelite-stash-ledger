package com.stashledger.items;

import com.stashledger.model.BuildStatus;
import com.stashledger.model.ContentsStatus;
import com.stashledger.model.StashEntry;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemVariationMapping;

/**
 * Creates a thread-safe snapshot of source-declared equipment candidates for
 * every STASH that is not already built and filled. This is intentionally a
 * planner marker, never an item-count or equipment-fulfillment evaluator.
 */
public final class NeededItemProvider implements NeededItemLookup
{
    private final IntUnaryOperator canonicalize;
    private final Map<String, Set<Integer>> requirementIdsByStash;
    private volatile Set<Integer> neededIds = Collections.emptySet();

    public NeededItemProvider(ItemManager itemManager, Map<String, Set<Integer>> requirementIdsByStash)
    {
        this(itemManager::canonicalize, requirementIdsByStash);
    }

    NeededItemProvider(IntUnaryOperator canonicalize, Map<String, Set<Integer>> requirementIdsByStash)
    {
        this.canonicalize = canonicalize;
        this.requirementIdsByStash = Map.copyOf(requirementIdsByStash);
    }

    /** Replaces the snapshot atomically; callers may supply any account's full ledger snapshot. */
    public void refresh(List<StashEntry> entries)
    {
        Set<Integer> next = new HashSet<>();
        if (entries != null)
        {
            for (StashEntry entry : entries)
            {
                if (entry == null || isComplete(entry))
                {
                    continue;
                }
                for (int requirementId : requirementIdsByStash.getOrDefault(entry.getDefinition().getId(), Set.of()))
                {
                    int canonicalId = canonicalize.applyAsInt(requirementId);
                    next.add(canonicalId);
                    for (int variationId : ItemVariationMapping.getVariations(canonicalId))
                    {
                        next.add(canonicalize.applyAsInt(variationId));
                    }
                }
            }
        }
        neededIds = Collections.unmodifiableSet(next);
    }

    public void clear()
    {
        neededIds = Collections.emptySet();
    }

    @Override
    public boolean isNeeded(int itemId)
    {
        return neededIds.contains(canonicalize.applyAsInt(itemId));
    }

    public Optional<NeededItemDetail> detailFor(int itemId)
    {
        int canonicalId = canonicalize.applyAsInt(itemId);
        return neededIds.contains(canonicalId)
            ? Optional.of(new NeededItemDetail(canonicalId))
            : Optional.empty();
    }

    private static boolean isComplete(StashEntry entry)
    {
        return entry.getBuildStatus() == BuildStatus.BUILT
            && entry.getContentsStatus() == ContentsStatus.FILLED;
    }
}
