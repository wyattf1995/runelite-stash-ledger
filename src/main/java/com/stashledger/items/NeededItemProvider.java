package com.stashledger.items;

import com.stashledger.model.BuildStatus;
import com.stashledger.model.ContentsStatus;
import com.stashledger.model.StashEntry;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import net.runelite.client.game.ItemManager;

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
        Map<String, Set<Integer>> copy = new HashMap<>();
        for (Map.Entry<String, Set<Integer>> entry : requirementIdsByStash.entrySet())
        {
            copy.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }
        this.requirementIdsByStash = Map.copyOf(copy);
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
                    next.add(canonicalize.applyAsInt(requirementId));
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
        Set<Integer> snapshot = neededIds;
        return !snapshot.isEmpty() && snapshot.contains(canonicalize.applyAsInt(itemId));
    }

    public Optional<NeededItemDetail> detailFor(int itemId)
    {
        if (neededIds.isEmpty())
        {
            return Optional.empty();
        }
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
